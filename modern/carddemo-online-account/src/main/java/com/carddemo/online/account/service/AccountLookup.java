package com.carddemo.online.account.service;

import com.carddemo.domain.entity.Account;
import com.carddemo.domain.entity.CardXref;
import com.carddemo.domain.entity.Customer;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.online.account.support.CobolText;
import java.util.Comparator;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paragraphs 9200-GETCARDXREF-BYACCT, 9300-GETACCTDATA-BYACCT and 9400-GETCUSTDATA-BYCUST, shared
 * by COACTVWC and COACTUPC (identical code and messages in both programs).
 */
@Component
public class AccountLookup {

  /** DFHRESP(NOTFND). */
  static final int RESP_NOTFND = 13;

  /** RESP2 of a READ that does not find the key. */
  static final int RESP2_NOTFND = 80;

  /** Outcome of 9000-READ-ACCT; {@code message} is the WS-RETURN-MSG text, if any. */
  public record Result(
      CardXref xref, Account account, Customer customer, boolean acctFilterNotOk, String message) {

    public boolean foundAccount() {
      return account != null;
    }

    public boolean foundCustomer() {
      return customer != null;
    }
  }

  private final CardXrefRepository xrefs;
  private final AccountRepository accounts;
  private final CustomerRepository customers;

  public AccountLookup(
      CardXrefRepository xrefs, AccountRepository accounts, CustomerRepository customers) {
    this.xrefs = xrefs;
    this.accounts = accounts;
    this.customers = customers;
  }

  /** 9000-READ-ACCT: xref by account (AIX CXACAIX), then ACCTDAT, then CUSTDAT. */
  @Transactional(readOnly = true)
  public Result read(long acctId) {
    String acctKey = CobolText.zoned(acctId, 11);
    // AIX CXACAIX is non-unique: READ returns the first duplicate, i.e. the lowest card number.
    Optional<CardXref> xref =
        xrefs.findByXrefAcctId(acctId).stream().min(Comparator.comparing(CardXref::getXrefCardNum));
    if (xref.isEmpty()) {
      return new Result(
          null,
          null,
          null,
          true,
          "Account:"
              + acctKey
              + " not found in Cross ref file.  Resp:"
              + resp()
              + " Reas:"
              + reas());
    }
    Optional<Account> account = accounts.findById(acctId);
    if (account.isEmpty()) {
      return new Result(
          xref.get(),
          null,
          null,
          true,
          "Account:"
              + acctKey
              + " not found in Acct Master file.Resp:"
              + resp()
              + " Reas:"
              + reas());
    }
    int custId = xref.get().getXrefCustId();
    Optional<Customer> customer = customers.findById(custId);
    if (customer.isEmpty()) {
      return new Result(
          xref.get(),
          account.get(),
          null,
          false,
          "CustId:"
              + CobolText.zoned(custId, 9)
              + " not found in customer master.Resp: "
              + resp()
              + " REAS:"
              + reas());
    }
    return new Result(xref.get(), account.get(), customer.get(), false, null);
  }

  /** {@code MOVE WS-RESP-CD TO ERROR-RESP}: 9 digits of S9(9) COMP left-justified in X(10). */
  private static String resp() {
    return CobolText.pad(CobolText.zoned(RESP_NOTFND, 9), 10);
  }

  private static String reas() {
    return CobolText.pad(CobolText.zoned(RESP2_NOTFND, 9), 10);
  }
}
