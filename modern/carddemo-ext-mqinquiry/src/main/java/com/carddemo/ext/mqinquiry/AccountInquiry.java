package com.carddemo.ext.mqinquiry;

import com.carddemo.contracts.record.AccountRecord;
import com.carddemo.copybook.CoreCodecs;
import com.carddemo.domain.entity.Account;
import com.carddemo.domain.repository.AccountRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * COACCT01 4000-PROCESS-REQUEST-REPLY. The request is REQUEST-MSG-COPY: {@code WS-FUNC PIC X(04)},
 * {@code WS-KEY PIC 9(11)}, filler. Only {@code WS-FUNC = 'INQA' AND WS-KEY > ZEROES} reads
 * ACCTDAT; the reply is WS-ACCT-RESPONSE with the CVACT01Y fields moved byte for byte.
 */
public final class AccountInquiry {

  static final String FUNCTION_INQUIRY = "INQA";
  static final long DFHRESP_IOERR = 17;

  private final AccountRepository accounts;
  private final String inputQueue;

  public AccountInquiry(AccountRepository accounts, String inputQueue) {
    this.accounts = accounts;
    this.inputQueue = inputQueue;
  }

  public InquiryOutcome process(String request) {
    String copy = MqMessageLayout.buffer(request);
    String func = copy.substring(0, 4);
    String key = copy.substring(4, 15);
    if (!(FUNCTION_INQUIRY.equals(func) && displayValue(key) > 0)) {
      return InquiryOutcome.reply(
          "INVALID REQUEST PARAMETERS " + "ACCT ID : " + key + "FUNCTION : " + func);
    }
    Optional<Account> account;
    try {
      account = isDigits(key) ? accounts.findById(Long.parseLong(key)) : Optional.empty();
    } catch (RuntimeException e) {
      return InquiryOutcome.error(
          MqErrorMessage.format("", "ERROR WHILE READING ACCTFILE", DFHRESP_IOERR, 0, inputQueue));
    }
    return account
        .map(a -> InquiryOutcome.reply(response(a.toRecord())))
        .orElseGet(() -> InquiryOutcome.reply("INVALID REQUEST PARAMETERS " + "ACCT ID : " + key));
  }

  /** WS-ACCT-RESPONSE built from the CVACT01Y image (MOVE between identical PICs copies bytes). */
  static String response(AccountRecord record) {
    String r = new String(CoreCodecs.ACCOUNT.encode(record), StandardCharsets.US_ASCII);
    return "ACCOUNT ID : "
        + r.substring(0, 11)
        + "ACCOUNT STATUS : "
        + r.substring(11, 12)
        + "BALANCE : "
        + r.substring(12, 24)
        + "CREDIT LIMIT : "
        + r.substring(24, 36)
        + "CASH LIMIT : "
        + r.substring(36, 48)
        + "OPEN DATE : "
        + r.substring(48, 58)
        + "EXPR DATE : "
        + r.substring(58, 68)
        + "REIS DATE : "
        + r.substring(68, 78)
        + "CREDIT BAL : "
        + r.substring(78, 90)
        + "DEBIT BAL : "
        + r.substring(90, 102)
        + "GROUP ID : "
        + r.substring(112, 122);
  }

  /**
   * Numeric value of an unsigned DISPLAY field as compared by the reference runtime (GnuCOBOL):
   * each byte contributes {@code byte - '0'} at its decimal weight, so spaces count negative.
   */
  static long displayValue(String digits) {
    long value = 0;
    for (int i = 0; i < digits.length(); i++) {
      value = value * 10 + (digits.charAt(i) - '0');
    }
    return value;
  }

  private static boolean isDigits(String key) {
    return key.chars().allMatch(c -> c >= '0' && c <= '9');
  }
}
