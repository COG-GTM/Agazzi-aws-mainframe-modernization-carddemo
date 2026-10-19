package com.carddemo.ext.authorization;

import com.carddemo.domain.entity.Account;
import com.carddemo.domain.entity.CardXref;
import com.carddemo.domain.entity.Customer;
import jakarta.persistence.Column;
import java.lang.reflect.Field;
import java.math.BigDecimal;

/** Builds V1 rows with every NOT NULL column filled (spaces / zeros), like an INITIALIZE. */
public final class X1Fixtures {
  private X1Fixtures() {}

  public static <T> T initialize(T entity) {
    try {
      for (Field f : entity.getClass().getDeclaredFields()) {
        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
          continue;
        }
        f.setAccessible(true);
        if (f.get(entity) != null) {
          continue;
        }
        Column c = f.getAnnotation(Column.class);
        Class<?> t = f.getType();
        if (t == String.class) {
          f.set(entity, " ".repeat(c == null ? 1 : Math.min(c.length(), 255)));
        } else if (t == BigDecimal.class) {
          f.set(entity, BigDecimal.ZERO.setScale(c == null ? 0 : c.scale()));
        } else if (t == Integer.class) {
          f.set(entity, 0);
        } else if (t == Long.class) {
          f.set(entity, 0L);
        } else if (t == Short.class) {
          f.set(entity, (short) 0);
        }
      }
      return entity;
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  public static Account account(long id, String limit, String balance) {
    Account a = new Account();
    a.setAcctId(id);
    a.setAcctActiveStatus("Y");
    a.setAcctCreditLimit(new BigDecimal(limit));
    a.setAcctCashCreditLimit(new BigDecimal("500.00"));
    a.setAcctCurrBal(new BigDecimal(balance));
    return initialize(a);
  }

  public static Customer customer(int id) {
    Customer c = new Customer();
    c.setCustId(id);
    c.setCustFirstName(pad("Immanuel", 25));
    c.setCustMiddleName(pad("Madeline", 25));
    c.setCustLastName(pad("Kessler", 25));
    c.setCustAddrLine1(pad("618 Deshaun Route", 50));
    c.setCustAddrLine2(pad("Apt. 802", 50));
    c.setCustAddrLine3(pad("Altenwerthshire", 50));
    c.setCustAddrStateCd("NC");
    c.setCustAddrZip(pad("12546", 10));
    c.setCustPhoneNum1(pad("(908)119-8310", 15));
    return initialize(c);
  }

  public static CardXref xref(String card, int cust, long acct) {
    CardXref x = new CardXref();
    x.setXrefCardNum(card);
    x.setXrefCustId(cust);
    x.setXrefAcctId(acct);
    return initialize(x);
  }

  public static String pad(String s, int n) {
    return s.length() >= n ? s.substring(0, n) : s + " ".repeat(n - s.length());
  }

  /** CCPAURQY CSV request (18 fields). */
  public static String request(String card, String amount, String txnId) {
    return String.join(
        ",",
        "220718",
        "120000",
        card,
        "AUTH",
        "1225",
        "0100",
        "POS",
        "000000",
        amount,
        "5411",
        "840",
        "05",
        "MERCH0000000001",
        "TEST MERCHANT",
        "SEATTLE",
        "WA",
        "98101",
        txnId);
  }
}
