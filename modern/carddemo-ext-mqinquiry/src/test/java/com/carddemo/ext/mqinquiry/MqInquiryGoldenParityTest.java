package com.carddemo.ext.mqinquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carddemo.contracts.port.MessageChannelPort.ChannelMessage;
import com.carddemo.domain.entity.Account;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.platform.messaging.InMemoryMessageChannel;
import com.carddemo.platform.time.FixedBusinessClock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Golden gate for CDRD/CDRA: replays golden/x3-mqinquiry/online/&lt;TRANID&gt;/requests.txt and
 * compares {@code queue|correlid|payload|} byte for byte with the GnuCOBOL run of CODATE01/COACCT01
 * (src/test/golden/mqgolden.py), using the legacy queue names.
 */
@Tag("parity")
class MqInquiryGoldenParityTest {

  @ParameterizedTest(name = "{0}")
  @CsvSource({
    "CDRD, CARD.DEMO.REQUEST.DATE, CARD.DEMO.REPLY.DATE",
    "CDRA, CARD.DEMO.REQUEST.ACCT, CARD.DEMO.REPLY.ACCT"
  })
  void repliesMatchGolden(String tranId, String inputQueue, String replyQueue) throws Exception {
    Path dir = SeedAccounts.goldenDir(tranId);
    List<String> requests =
        Files.readAllLines(dir.resolve("requests.txt"), StandardCharsets.ISO_8859_1);
    String expected =
        Files.readString(dir.resolve("expected-replies.txt"), StandardCharsets.ISO_8859_1);

    Map<Long, Account> seed = SeedAccounts.load();
    AccountRepository repo = mock(AccountRepository.class);
    when(repo.findById(anyLong()))
        .thenAnswer(i -> Optional.ofNullable(seed.get(i.<Long>getArgument(0))));
    Function<String, InquiryOutcome> program =
        tranId.equals("CDRD")
            ? new SystemDateInquiry(FixedBusinessClock.golden())::process
            : new AccountInquiry(repo, inputQueue)::process;

    InMemoryMessageChannel channel = new InMemoryMessageChannel();
    MqInquiryProcessor processor = new MqInquiryProcessor(channel, "CARD.DEMO.ERROR");
    StringBuilder actual = new StringBuilder();
    for (int i = 0; i < requests.size(); i++) {
      String correl = "CORR%09d".formatted(i + 1);
      channel.send(
          inputQueue,
          requests.get(i),
          Map.of("correlationId", correl, "replyTo", "CARD.DEMO.CLIENT.REPLY"));
      ChannelMessage request = channel.receive(inputQueue, Duration.ZERO).orElseThrow();
      processor.handle(request, inputQueue, replyQueue, program);
      ChannelMessage reply = channel.receive(replyQueue, Duration.ZERO).orElseThrow();
      actual
          .append(replyQueue)
          .append('|')
          .append(reply.correlationId())
          .append('|')
          .append(reply.payload())
          .append("|\n");
    }
    assertThat(channel.receive("CARD.DEMO.ERROR", Duration.ZERO)).isEmpty();
    assertThat(actual.toString()).isEqualTo(expected);
  }
}
