package com.carddemo.ext.authorization.service;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.ext.authorization.config.ConditionalOnAuthorizationExtension;
import com.carddemo.ext.authorization.model.AuthFraud;
import com.carddemo.ext.authorization.model.AuthFraudId;
import com.carddemo.ext.authorization.model.PendingAuthDetail;
import com.carddemo.ext.authorization.store.AuthFraudRepository;
import com.carddemo.ext.authorization.store.AuthKey;
import com.carddemo.ext.authorization.store.PendingAuthDatabase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * COPAUS1C {@code MARK-AUTH-FRAUD} + COPAUS2C: toggles PA-AUTH-FRAUD ('F' confirmed / 'R' removed),
 * writes the AUTHFRDS row (INSERT, UPDATE on duplicate key -803) and REPLaces the detail.
 */
@Service
@ConditionalOnAuthorizationExtension
public class AuthFraudService {

  public static final String MSG_REMOVED = "AUTH FRAUD REMOVED...";
  public static final String MSG_MARKED = "AUTH MARKED FRAUD...";
  private static final DateTimeFormatter MMDDYY = DateTimeFormatter.ofPattern("MM/dd/yy");

  private final PendingAuthDatabase db;
  private final AuthFraudRepository frauds;
  private final BusinessClock clock;

  public AuthFraudService(PendingAuthDatabase db, AuthFraudRepository frauds, BusinessClock clock) {
    this.db = db;
    this.frauds = frauds;
    this.clock = clock;
  }

  /** Returns the CPVD message, or empty when the authorization does not exist. */
  @Transactional
  public Optional<String> toggle(long acctId, long custId, AuthKey key) {
    Optional<PendingAuthDetail> found = db.findFirstChild(acctId, key);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    PendingAuthDetail d = found.get();
    String action = "F".equals(d.getAuthFraud()) ? "R" : "F";
    LocalDate today = clock.today();
    d.setAuthFraud(action);
    d.setFraudRptDate(MMDDYY.format(today));
    LocalDateTime ts = authTimestamp(d);
    Optional<AuthFraud> existing = frauds.findById(new AuthFraudId(d.getCardNum(), ts));
    AuthFraud f = existing.orElseGet(() -> newRow(d, ts, acctId, custId));
    f.setAuthFraud(action);
    f.setFraudRptDate(today);
    frauds.save(f);
    db.replace(d);
    return Optional.of("R".equals(action) ? MSG_REMOVED : MSG_MARKED);
  }

  /**
   * {@code TIMESTAMP_FORMAT(YY-MM-DD HH24.MI.SSNNNNNN)} from the original date and the key time.
   */
  public static LocalDateTime authTimestamp(PendingAuthDetail d) {
    String date = d.getAuthOrigDate();
    long t = 999_999_999L - d.getAuthTime9c();
    int ms = (int) (t % 1000);
    long hhmmss = t / 1000;
    return LocalDateTime.of(
        2000 + digits(date, 0),
        Math.max(1, digits(date, 2)),
        Math.max(1, digits(date, 4)),
        (int) (hhmmss / 10000) % 24,
        (int) (hhmmss / 100 % 100) % 60,
        (int) (hhmmss % 100) % 60,
        ms * 1_000_000);
  }

  private static int digits(String s, int off) {
    String v = s == null || s.length() < off + 2 ? "00" : s.substring(off, off + 2);
    return v.chars().allMatch(Character::isDigit) ? Integer.parseInt(v) : 0;
  }

  private static AuthFraud newRow(PendingAuthDetail d, LocalDateTime ts, long acct, long cust) {
    AuthFraud f = new AuthFraud();
    f.setCardNum(d.getCardNum());
    f.setAuthTs(ts);
    f.setAuthType(d.getAuthType());
    f.setCardExpiryDate(d.getCardExpiryDate());
    f.setMessageType(d.getMessageType());
    f.setMessageSource(d.getMessageSource());
    f.setAuthIdCode(d.getAuthIdCode());
    f.setAuthRespCode(d.getAuthRespCode());
    f.setAuthRespReason(d.getAuthRespReason());
    f.setProcessingCode(String.format("%06d", d.getProcessingCode()));
    f.setTransactionAmt(d.getTransactionAmt());
    f.setApprovedAmt(d.getApprovedAmt());
    f.setMerchantCategoryCode(d.getMerchantCategoryCode());
    f.setAcqrCountryCode(d.getAcqrCountryCode());
    f.setPosEntryMode(d.getPosEntryMode());
    f.setMerchantId(d.getMerchantId());
    f.setMerchantName(d.getMerchantName());
    f.setMerchantCity(d.getMerchantCity());
    f.setMerchantState(d.getMerchantState());
    f.setMerchantZip(d.getMerchantZip());
    f.setTransactionId(d.getTransactionId());
    f.setMatchStatus(d.getMatchStatus());
    f.setAcctId(BigDecimal.valueOf(acct));
    f.setCustId(BigDecimal.valueOf(cust));
    return f;
  }
}
