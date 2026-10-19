package com.carddemo.ext.authorization.service;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.domain.entity.Account;
import com.carddemo.domain.entity.CardXref;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.ext.authorization.batch.PendingAuthPurgeService;
import com.carddemo.ext.authorization.codec.AuthorizationReply;
import com.carddemo.ext.authorization.codec.AuthorizationRequest;
import com.carddemo.ext.authorization.config.ConditionalOnAuthorizationExtension;
import com.carddemo.ext.authorization.model.PendingAuthDetail;
import com.carddemo.ext.authorization.model.PendingAuthSummary;
import com.carddemo.ext.authorization.store.PendingAuthDatabase;
import com.carddemo.platform.math.CobolMath;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** COPAUA0C {@code 5000-PROCESS-AUTH}: decision, reply and IMS update for one CP00 request. */
@Service
@ConditionalOnAuthorizationExtension
public class AuthorizationDecisionService {

  private static final Logger LOG = LoggerFactory.getLogger(AuthorizationDecisionService.class);
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

  private final CardXrefRepository xrefs;
  private final AccountRepository accounts;
  private final CustomerRepository customers;
  private final PendingAuthDatabase db;
  private final BusinessClock clock;

  public AuthorizationDecisionService(
      CardXrefRepository xrefs,
      AccountRepository accounts,
      CustomerRepository customers,
      PendingAuthDatabase db,
      BusinessClock clock) {
    this.xrefs = xrefs;
    this.accounts = accounts;
    this.customers = customers;
    this.db = db;
    this.clock = clock;
  }

  /**
   * Reply plus {@code endTask}: an IMS ISRT failure (I004, ERR-CRITICAL) ends the CP00 task via
   * {@code 9990-END-ROUTINE} after the reply was sent; CICS RETURN still commits the summary REPL.
   */
  public record Outcome(AuthorizationReply reply, boolean endTask) {}

  @Transactional
  public AuthorizationReply process(AuthorizationRequest rq) {
    return decide(rq).reply();
  }

  @Transactional
  public Outcome decide(AuthorizationRequest rq) {
    Optional<CardXref> xref = xrefs.findById(rq.cardNum());
    Optional<Account> acct = xref.flatMap(x -> accounts.findById(x.getXrefAcctId()));
    boolean custFound = xref.flatMap(x -> customers.findById(x.getXrefCustId())).isPresent();
    Optional<PendingAuthSummary> summary = xref.flatMap(x -> db.findFirst(x.getXrefAcctId()));

    boolean decline = false;
    boolean insufficient = false;
    if (summary.isPresent()) {
      BigDecimal available =
          available(summary.get().getCreditLimit(), summary.get().getCreditBalance());
      if (rq.transactionAmt().compareTo(available) > 0) {
        decline = true;
        insufficient = true;
      }
    } else if (acct.isPresent()) {
      BigDecimal available =
          available(acct.get().getAcctCreditLimit(), acct.get().getAcctCurrBal());
      if (rq.transactionAmt().compareTo(available) > 0) {
        decline = true;
        insufficient = true;
      }
    } else {
      decline = true;
    }

    String reason = "0000";
    if (decline) {
      if (xref.isEmpty() || acct.isEmpty() || !custFound) {
        reason = "3100";
      } else if (insufficient) {
        reason = "4100";
      } else {
        reason = "9000";
      }
    }
    AuthorizationReply reply =
        new AuthorizationReply(
            rq.cardNum(),
            rq.transactionId(),
            rq.authTime(),
            decline ? "05" : "00",
            reason,
            decline ? ZERO : rq.transactionAmt());

    boolean endTask = false;
    if (xref.isPresent()) {
      PendingAuthSummary s = updateSummary(xref.get(), acct, summary, rq, reply);
      endTask = !insertDetail(s.getAcctId(), rq, reply);
    }
    return new Outcome(reply, endTask);
  }

  private static BigDecimal available(BigDecimal limit, BigDecimal balance) {
    return CobolMath.store(limit.subtract(balance), 9, 2, CobolMath.Mode.TRUNCATE);
  }

  private PendingAuthSummary updateSummary(
      CardXref xref,
      Optional<Account> acct,
      Optional<PendingAuthSummary> found,
      AuthorizationRequest rq,
      AuthorizationReply reply) {
    PendingAuthSummary s =
        found.orElseGet(
            () -> {
              PendingAuthSummary n = new PendingAuthSummary();
              n.setAcctId(xref.getXrefAcctId());
              n.setCustId(xref.getXrefCustId());
              return n;
            });
    acct.ifPresent(
        a -> {
          s.setCreditLimit(CobolMath.fit(a.getAcctCreditLimit(), 9, 2));
          s.setCashLimit(CobolMath.fit(a.getAcctCashCreditLimit(), 9, 2));
        });
    if (reply.approved()) {
      s.setApprovedAuthCnt((short) (s.getApprovedAuthCnt() + 1));
      s.setApprovedAuthAmt(add(s.getApprovedAuthAmt(), reply.approvedAmt()));
      s.setCreditBalance(add(s.getCreditBalance(), reply.approvedAmt()));
      s.setCashBalance(ZERO);
    } else {
      s.setDeclinedAuthCnt((short) (s.getDeclinedAuthCnt() + 1));
      s.setDeclinedAuthAmt(add(s.getDeclinedAuthAmt(), rq.transactionAmt()));
    }
    return found.isPresent() ? db.replace(s) : db.insert(s);
  }

  private static BigDecimal add(BigDecimal a, BigDecimal b) {
    return CobolMath.add(a, b, 9, 2, CobolMath.Mode.TRUNCATE);
  }

  private boolean insertDetail(long acctId, AuthorizationRequest rq, AuthorizationReply reply) {
    LocalDateTime now = clock.now();
    long timeWithMs =
        (now.getHour() * 10000L + now.getMinute() * 100L + now.getSecond()) * 1000L
            + now.getNano() / 1_000_000;
    PendingAuthDetail d = new PendingAuthDetail();
    d.setAcctId(acctId);
    d.setAuthDate9c(99999 - PendingAuthPurgeService.yyddd(now.toLocalDate()));
    d.setAuthTime9c(999_999_999L - timeWithMs);
    d.setAuthOrigDate(rq.authDate());
    d.setAuthOrigTime(rq.authTime());
    d.setCardNum(rq.cardNum());
    d.setAuthType(rq.authType());
    d.setCardExpiryDate(rq.cardExpiryDate());
    d.setMessageType(rq.messageType());
    d.setMessageSource(rq.messageSource());
    d.setProcessingCode(rq.processingCode());
    d.setTransactionAmt(rq.transactionAmt());
    d.setMerchantCategoryCode(rq.merchantCategoryCode());
    d.setAcqrCountryCode(rq.acqrCountryCode());
    d.setPosEntryMode((short) rq.posEntryMode());
    d.setMerchantId(rq.merchantId());
    d.setMerchantName(rq.merchantName());
    d.setMerchantCity(rq.merchantCity());
    d.setMerchantState(rq.merchantState());
    d.setMerchantZip(rq.merchantZip());
    d.setTransactionId(rq.transactionId());
    d.setAuthIdCode(reply.authIdCode());
    d.setAuthRespCode(reply.authRespCode());
    d.setAuthRespReason(reply.authRespReason());
    d.setApprovedAmt(reply.approvedAmt());
    d.setMatchStatus(reply.approved() ? "P" : "D");
    d.setAuthFraud(" ");
    d.setFraudRptDate(" ".repeat(8));
    try {
      db.insert(d);
      return true;
    } catch (PendingAuthDatabase.DuplicateSegmentException e) {
      LOG.error("COPAUA0C I004 IMS INSERT DETL FAILED II card={}", rq.cardNum());
      return false;
    }
  }
}
