package com.carddemo.online.account.service;

import com.carddemo.domain.entity.Account;
import com.carddemo.domain.entity.Customer;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.online.account.support.CobolText;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 9600-WRITE-PROCESSING: READ UPDATE of ACCTDAT and CUSTDAT, 9700 compare with the fetched values,
 * REWRITE of both records in one unit of work (SYNCPOINT ROLLBACK on failure). READ UPDATE is a
 * {@code PESSIMISTIC_WRITE} row lock held on both rows until commit, so nothing can slip in between
 * the 9700 compare and the REWRITEs; writers holding a stale copy elsewhere fail on
 * {@code @Version}.
 */
@Component
public class AccountUpdateWriter {

  /** Outcome of 9600, mapped by 2000-DECIDE-ACTION to the next ACUP-CHANGE-ACTION. */
  public enum Result {
    DONE,
    COULD_NOT_LOCK_ACCT,
    COULD_NOT_LOCK_CUST,
    DATA_CHANGED,
    UPDATE_FAILED
  }

  /** Test seam invoked between the 9700 check and the REWRITEs. */
  public interface BeforeRewrite {
    void beforeRewrite(long acctId);
  }

  private final AccountRepository accounts;
  private final CustomerRepository customers;
  private final ObjectProvider<BeforeRewrite> hooks;
  private final EntityManager em;

  public AccountUpdateWriter(
      AccountRepository accounts,
      CustomerRepository customers,
      ObjectProvider<BeforeRewrite> hooks,
      EntityManager em) {
    this.em = em;
    this.accounts = accounts;
    this.customers = customers;
    this.hooks = hooks;
  }

  @Transactional
  public Result write(
      long acctId,
      int custId,
      AccountSnapshot old,
      Map<String, String> nw,
      Map<String, BigDecimal> amounts) {
    Optional<Account> acct =
        Optional.ofNullable(em.find(Account.class, acctId, LockModeType.PESSIMISTIC_WRITE));
    if (acct.isEmpty()) {
      return Result.COULD_NOT_LOCK_ACCT;
    }
    Optional<Customer> cust =
        Optional.ofNullable(em.find(Customer.class, custId, LockModeType.PESSIMISTIC_WRITE));
    if (cust.isEmpty()) {
      return Result.COULD_NOT_LOCK_CUST;
    }
    Account a = acct.get();
    Customer c = cust.get();
    if (!old.unchangedIn(a, c)) {
      return Result.DATA_CHANGED;
    }
    hooks.ifAvailable(h -> h.beforeRewrite(acctId));
    a.setAcctActiveStatus(x(nw, "ACSTTUS", 1));
    a.setAcctCurrBal(amounts.get("ACURBAL"));
    a.setAcctCreditLimit(amounts.get("ACRDLIM"));
    a.setAcctCashCreditLimit(amounts.get("ACSHLIM"));
    a.setAcctCurrCycCredit(amounts.get("ACRCYCR"));
    a.setAcctCurrCycDebit(amounts.get("ACRCYDB"));
    a.setAcctOpenDate(date(nw, "OPN"));
    a.setAcctExpirationDate(date(nw, "EXP"));
    a.setAcctReissueDate(date(nw, "RIS"));
    a.setAcctGroupId(x(nw, "AADDGRP", 10));
    c.setCustFirstName(x(nw, "ACSFNAM", 25));
    c.setCustMiddleName(x(nw, "ACSMNAM", 25));
    c.setCustLastName(x(nw, "ACSLNAM", 25));
    c.setCustAddrLine1(x(nw, "ACSADL1", 50));
    c.setCustAddrLine2(x(nw, "ACSADL2", 50));
    c.setCustAddrLine3(x(nw, "ACSCITY", 50));
    c.setCustAddrStateCd(x(nw, "ACSSTTE", 2));
    c.setCustAddrCountryCd(x(nw, "ACSCTRY", 3));
    c.setCustAddrZip(x(nw, "ACSZIPC", 10));
    c.setCustPhoneNum1(phone(nw, "ACSPH1"));
    c.setCustPhoneNum2(phone(nw, "ACSPH2"));
    c.setCustSsn(Integer.valueOf(x(nw, "ACTSSN1", 3) + x(nw, "ACTSSN2", 2) + x(nw, "ACTSSN3", 4)));
    c.setCustGovtIssuedId(x(nw, "ACSGOVT", 20));
    c.setCustDobYyyyMmDd(date(nw, "DOB"));
    c.setCustEftAccountId(x(nw, "ACSEFTC", 10));
    c.setCustPriCardHolderInd(x(nw, "ACSPFLG", 1));
    c.setCustFicoCreditScore(Integer.valueOf(x(nw, "ACSTFCO", 3)));
    accounts.saveAndFlush(a);
    customers.saveAndFlush(c);
    return Result.DONE;
  }

  /** New value moved to {@code PIC X(n)}; LOW-VALUES are stored as spaces (PostgreSQL text). */
  private static String x(Map<String, String> nw, String field, int n) {
    String v = nw.get(field);
    return CobolText.pad(v == null ? "" : v.replace('\0', ' '), n);
  }

  /** {@code STRING year '-' mon '-' day}. */
  private static String date(Map<String, String> nw, String prefix) {
    return x(nw, prefix + "YEAR", 4)
        + "-"
        + x(nw, prefix + "MON", 2)
        + "-"
        + x(nw, prefix + "DAY", 2);
  }

  /** {@code STRING '(' A ')' B '-' C} into {@code PIC X(15)}. */
  private static String phone(Map<String, String> nw, String prefix) {
    return CobolText.pad(
        "(" + x(nw, prefix + "A", 3) + ")" + x(nw, prefix + "B", 3) + "-" + x(nw, prefix + "C", 4),
        15);
  }
}
