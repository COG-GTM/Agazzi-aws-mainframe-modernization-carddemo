package com.carddemo.ext.authorization.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.port.MessageChannelPort;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.ext.authorization.MutableBusinessClock;
import com.carddemo.ext.authorization.X1Fixtures;
import com.carddemo.ext.authorization.codec.AuthorizationReply;
import com.carddemo.ext.authorization.codec.AuthorizationRequest;
import com.carddemo.ext.authorization.model.PendingAuthDetail;
import com.carddemo.ext.authorization.model.PendingAuthSummary;
import com.carddemo.ext.authorization.service.AuthorizationDecisionService;
import com.carddemo.ext.authorization.store.AuthFraudRepository;
import com.carddemo.ext.authorization.store.PendingAuthDetailRepository;
import com.carddemo.ext.authorization.store.PendingAuthSummaryRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AuthorizationFlowTest {

  static final String CARD = "4111111111111111";

  @Autowired AccountRepository accounts;
  @Autowired CustomerRepository customers;
  @Autowired CardXrefRepository xrefs;
  @Autowired PendingAuthSummaryRepository summaries;
  @Autowired PendingAuthDetailRepository details;
  @Autowired AuthFraudRepository frauds;
  @Autowired AuthorizationDecisionService decisions;
  @Autowired AuthorizationRequestProcessor processor;
  @Autowired MessageChannelPort channel;
  @Autowired MutableBusinessClock clock;

  @BeforeEach
  void seed() {
    frauds.deleteAll();
    details.deleteAll();
    summaries.deleteAll();
    xrefs.deleteAll();
    accounts.deleteAll();
    customers.deleteAll();
    accounts.save(X1Fixtures.account(1L, "1000.00", "200.00"));
    customers.save(X1Fixtures.customer(7));
    xrefs.save(X1Fixtures.xref(CARD, 7, 1L));
    clock.set(LocalDateTime.of(2022, 7, 18, 12, 0));
  }

  private AuthorizationReply decide(String card, String amount) {
    return decisions.process(AuthorizationRequest.parse(X1Fixtures.request(card, amount, "T1")));
  }

  @Test
  void approvesWithinAccountAvailableCreditAndCreatesSummaryAndDetail() {
    AuthorizationReply r = decide(CARD, "100.00");
    assertThat(r.authRespCode()).isEqualTo("00");
    assertThat(r.authRespReason()).isEqualTo("0000");
    assertThat(r.approvedAmt()).isEqualByComparingTo("100.00");
    PendingAuthSummary s = summaries.findById(1L).orElseThrow();
    assertThat(s.getApprovedAuthCnt()).isEqualTo((short) 1);
    assertThat(s.getApprovedAuthAmt()).isEqualByComparingTo("100.00");
    assertThat(s.getCreditBalance()).isEqualByComparingTo("100.00");
    List<PendingAuthDetail> d = details.findByAcctIdOrderByAuthDate9cAscAuthTime9cAsc(1L);
    assertThat(d).hasSize(1);
    assertThat(d.get(0).getAuthDate9c()).isEqualTo(99999 - 22199);
    assertThat(d.get(0).getAuthTime9c()).isEqualTo(999_999_999L - 120_000_000L);
    assertThat(d.get(0).getMatchStatus()).isEqualTo("P");
    assertThat(d.get(0).getAuthFraud()).isEqualTo(" ");
  }

  @Test
  void declinesInsufficientFundsAndUnknownCard() {
    AuthorizationReply big = decide(CARD, "900.00");
    assertThat(big.authRespCode()).isEqualTo("05");
    assertThat(big.authRespReason()).isEqualTo("4100");
    assertThat(big.approvedAmt()).isEqualByComparingTo("0");
    PendingAuthSummary s = summaries.findById(1L).orElseThrow();
    assertThat(s.getDeclinedAuthCnt()).isEqualTo((short) 1);
    assertThat(s.getDeclinedAuthAmt()).isEqualByComparingTo("900.00");
    assertThat(details.findAll().get(0).getMatchStatus()).isEqualTo("D");

    AuthorizationReply unknown = decide("4000000000000000", "1.00");
    assertThat(unknown.authRespCode()).isEqualTo("05");
    assertThat(unknown.authRespReason()).isEqualTo("3100");
  }

  @Test
  void newestAuthorizationSortsFirstOnInvertedKey() {
    decide(CARD, "10.00");
    clock.set(LocalDateTime.of(2022, 7, 18, 12, 5));
    decide(CARD, "20.00");
    clock.set(LocalDateTime.of(2022, 7, 19, 9, 0));
    decide(CARD, "30.00");
    List<PendingAuthDetail> d = details.findByAcctIdOrderByAuthDate9cAscAuthTime9cAsc(1L);
    assertThat(d)
        .extracting(PendingAuthDetail::getTransactionAmt)
        .usingElementComparator(java.math.BigDecimal::compareTo)
        .containsExactly(
            new java.math.BigDecimal("30.00"),
            new java.math.BigDecimal("20.00"),
            new java.math.BigDecimal("10.00"));
  }

  @Test
  void cp00RequestReplyOverMessageChannel() {
    String id =
        channel.send(
            "carddemo-auth-request",
            X1Fixtures.request(CARD, "50.00", "TXN000000000001"),
            Map.of("replyTo", "carddemo-auth-reply"));
    assertThat(processor.runOnce(Duration.ofMillis(200))).isEqualTo(1);
    Optional<MessageChannelPort.ChannelMessage> reply =
        channel.receive("carddemo-auth-reply", Duration.ofSeconds(1));
    assertThat(reply).isPresent();
    assertThat(reply.get().correlationId()).isEqualTo(id);
    assertThat(reply.get().payload())
        .startsWith(CARD + ",TXN000000000001,")
        .contains(",00,0000,")
        .endsWith("         50.00,");
  }

  @Test
  void duplicateDetailKeyRepliesCommitsSummaryAndEndsTask() {
    for (String t : List.of("TXN000000000001", "TXN000000000002", "TXN000000000003")) {
      channel.send(
          "carddemo-auth-request",
          X1Fixtures.request(CARD, "10.00", t),
          Map.of("replyTo", "carddemo-auth-reply"));
    }
    assertThat(processor.runOnce(Duration.ofMillis(200))).isEqualTo(2);
    assertThat(channel.receive("carddemo-auth-reply", Duration.ofMillis(200))).isPresent();
    assertThat(channel.receive("carddemo-auth-reply", Duration.ofMillis(200))).isPresent();
    assertThat(channel.receive("carddemo-auth-reply", Duration.ofMillis(200))).isEmpty();
    assertThat(summaries.findById(1L).orElseThrow().getApprovedAuthCnt()).isEqualTo((short) 2);
    assertThat(details.findAll()).hasSize(1);
    assertThat(channel.receive("carddemo-auth-request", Duration.ofMillis(200))).isPresent();
  }
}
