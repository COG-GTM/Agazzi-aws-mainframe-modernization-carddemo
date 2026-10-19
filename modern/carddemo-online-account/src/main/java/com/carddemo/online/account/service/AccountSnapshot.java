package com.carddemo.online.account.service;

import com.carddemo.domain.entity.Account;
import com.carddemo.domain.entity.Customer;
import com.carddemo.online.account.support.CobolText;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code ACUP-OLD-DETAILS} (9500-STORE-FETCHED-DATA): the values read before the update, in screen
 * layout (BMS field names), plus the row versions read ({@code @Version}).
 */
public record AccountSnapshot(
    long acctId,
    int custId,
    Map<String, String> fields,
    Map<String, BigDecimal> amounts,
    String phone1,
    String phone2,
    long accountVersion,
    long customerVersion)
    implements Serializable {

  public static AccountSnapshot of(Account a, Customer c) {
    Map<String, String> f = new LinkedHashMap<>();
    f.put("ACCTSID", CobolText.zoned(a.getAcctId(), 11));
    f.put("ACSTTUS", a.getAcctActiveStatus());
    putDate(f, "OPN", a.getAcctOpenDate());
    putDate(f, "EXP", a.getAcctExpirationDate());
    putDate(f, "RIS", a.getAcctReissueDate());
    f.put("AADDGRP", a.getAcctGroupId());
    String ssn = CobolText.zoned(c.getCustSsn(), 9);
    f.put("ACSTNUM", CobolText.zoned(c.getCustId(), 9));
    f.put("ACTSSN1", ssn.substring(0, 3));
    f.put("ACTSSN2", ssn.substring(3, 5));
    f.put("ACTSSN3", ssn.substring(5, 9));
    f.put("ACSTFCO", CobolText.zoned(c.getCustFicoCreditScore(), 3));
    String dob = CobolText.pad(c.getCustDobYyyyMmDd(), 10);
    f.put("DOBYEAR", dob.substring(0, 4));
    f.put("DOBMON", dob.substring(5, 7));
    f.put("DOBDAY", dob.substring(8, 10));
    f.put("ACSFNAM", c.getCustFirstName());
    f.put("ACSMNAM", c.getCustMiddleName());
    f.put("ACSLNAM", c.getCustLastName());
    f.put("ACSADL1", c.getCustAddrLine1());
    f.put("ACSADL2", c.getCustAddrLine2());
    f.put("ACSCITY", c.getCustAddrLine3());
    f.put("ACSSTTE", c.getCustAddrStateCd());
    f.put("ACSZIPC", c.getCustAddrZip());
    f.put("ACSCTRY", c.getCustAddrCountryCd());
    String p1 = CobolText.pad(c.getCustPhoneNum1(), 15);
    String p2 = CobolText.pad(c.getCustPhoneNum2(), 15);
    f.put("ACSPH1A", p1.substring(1, 4));
    f.put("ACSPH1B", p1.substring(5, 8));
    f.put("ACSPH1C", p1.substring(9, 13));
    f.put("ACSPH2A", p2.substring(1, 4));
    f.put("ACSPH2B", p2.substring(5, 8));
    f.put("ACSPH2C", p2.substring(9, 13));
    f.put("ACSGOVT", c.getCustGovtIssuedId());
    f.put("ACSEFTC", c.getCustEftAccountId());
    f.put("ACSPFLG", c.getCustPriCardHolderInd());
    Map<String, BigDecimal> m = new LinkedHashMap<>();
    m.put("ACURBAL", a.getAcctCurrBal());
    m.put("ACRDLIM", a.getAcctCreditLimit());
    m.put("ACSHLIM", a.getAcctCashCreditLimit());
    m.put("ACRCYCR", a.getAcctCurrCycCredit());
    m.put("ACRCYDB", a.getAcctCurrCycDebit());
    return new AccountSnapshot(
        a.getAcctId(), c.getCustId(), f, m, p1, p2, a.getVersion(), c.getVersion());
  }

  private static void putDate(Map<String, String> f, String prefix, String date) {
    String d = CobolText.pad(date, 10);
    f.put(prefix + "YEAR", d.substring(0, 4));
    f.put(prefix + "MON", d.substring(5, 7));
    f.put(prefix + "DAY", d.substring(8, 10));
  }

  /** 9700-CHECK-CHANGE-IN-REC: {@code true} when the rows still hold the fetched values. */
  public boolean unchangedIn(Account a, Customer c) {
    AccountSnapshot now = of(a, c);
    Map<String, String> g = now.fields;
    boolean acct =
        eq(g, "ACSTTUS")
            && amountsEqual(now.amounts)
            && eq(g, "OPNYEAR", "OPNMON", "OPNDAY", "EXPYEAR", "EXPMON", "EXPDAY")
            && eq(g, "RISYEAR", "RISMON", "RISDAY")
            && CobolText.pad(g.get("AADDGRP"), 10)
                .toLowerCase(java.util.Locale.ROOT)
                .equals(
                    CobolText.pad(fields.get("AADDGRP"), 10).toLowerCase(java.util.Locale.ROOT));
    if (!acct) {
      return false;
    }
    return eqUpper(g, "ACSFNAM", "ACSMNAM", "ACSLNAM", "ACSADL1", "ACSADL2", "ACSCITY")
        && eqUpper(g, "ACSSTTE", "ACSCTRY", "ACSGOVT")
        && eq(g, "ACSZIPC", "ACTSSN1", "ACTSSN2", "ACTSSN3", "DOBYEAR", "DOBMON", "DOBDAY")
        && eq(g, "ACSEFTC", "ACSPFLG", "ACSTFCO")
        && now.phone1.equals(phone1)
        && now.phone2.equals(phone2);
  }

  private boolean amountsEqual(Map<String, BigDecimal> other) {
    for (Map.Entry<String, BigDecimal> e : amounts.entrySet()) {
      if (e.getValue().compareTo(other.get(e.getKey())) != 0) {
        return false;
      }
    }
    return true;
  }

  private boolean eq(Map<String, String> g, String... names) {
    for (String n : names) {
      if (!String.valueOf(g.get(n)).equals(String.valueOf(fields.get(n)))) {
        return false;
      }
    }
    return true;
  }

  private boolean eqUpper(Map<String, String> g, String... names) {
    for (String n : names) {
      String a = String.valueOf(g.get(n)).toUpperCase(java.util.Locale.ROOT);
      if (!a.equals(String.valueOf(fields.get(n)).toUpperCase(java.util.Locale.ROOT))) {
        return false;
      }
    }
    return true;
  }
}
