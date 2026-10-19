package com.carddemo.online.transaction.service;

import com.carddemo.domain.entity.Account;
import com.carddemo.domain.entity.CardXref;
import com.carddemo.domain.entity.Transaction;
import com.carddemo.online.transaction.file.AccountFile;
import com.carddemo.online.transaction.file.ReadResult;
import com.carddemo.online.transaction.file.Resp;
import com.carddemo.online.transaction.file.TransactFile;
import com.carddemo.online.transaction.file.XrefFile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** In-memory VSAM files (TreeMap = KSDS key order) for the unit tests. */
final class Fakes {

  private Fakes() {}

  static Transaction tran(String id, String amt, String desc) {
    Transaction t = new Transaction();
    t.setTranId(id);
    t.setTranTypeCd("01");
    t.setTranCatCd(5);
    t.setTranSource(CobolFmt.pad("POS TERM", 10));
    t.setTranDesc(CobolFmt.pad(desc, 100));
    t.setTranAmt(new BigDecimal(amt));
    t.setTranMerchantId(123);
    t.setTranMerchantName(CobolFmt.pad("SHOP", 50));
    t.setTranMerchantCity(CobolFmt.pad("CITY", 50));
    t.setTranMerchantZip(CobolFmt.pad("12345", 10));
    t.setTranCardNum("4111111111111111");
    t.setTranOrigTs("2022-06-01 10:00:00.000000");
    t.setTranProcTs("2022-06-02 10:00:00.000000");
    t.setFiller(CobolFmt.pad("", 20));
    return t;
  }

  static String id(int n) {
    return CobolFmt.digits(n, 16);
  }

  static final class Transact implements TransactFile {
    final TreeMap<String, Transaction> rows = new TreeMap<>();
    int locks;
    Resp writeResp;

    Transact with(int count) {
      for (int i = 1; i <= count; i++) {
        rows.put(id(i), tran(id(i), i + ".50", "DESC " + i));
      }
      return this;
    }

    @Override
    public ReadResult<Transaction> read(String tranId) {
      Transaction t = rows.get(tranId);
      return t == null ? ReadResult.of(Resp.NOTFND) : ReadResult.normal(t);
    }

    @Override
    public Optional<Transaction> firstAtOrAfter(String key) {
      return Optional.ofNullable(rows.ceilingEntry(key)).map(Map.Entry::getValue);
    }

    @Override
    public Optional<Transaction> firstAfter(String key) {
      return Optional.ofNullable(rows.higherEntry(key)).map(Map.Entry::getValue);
    }

    @Override
    public Optional<Transaction> lastBefore(String key) {
      return Optional.ofNullable(rows.lowerEntry(key)).map(Map.Entry::getValue);
    }

    @Override
    public Optional<Transaction> last() {
      return rows.isEmpty() ? Optional.empty() : Optional.of(rows.lastEntry().getValue());
    }

    @Override
    public void lockForAppend() {
      locks++;
    }

    @Override
    public Resp write(Transaction t) {
      if (writeResp != null) {
        return writeResp;
      }
      if (rows.containsKey(t.getTranId())) {
        return Resp.DUPREC;
      }
      rows.put(t.getTranId(), t);
      return Resp.NORMAL;
    }
  }

  static final class Accounts implements AccountFile {
    final Map<Long, Account> rows = new TreeMap<>();
    Resp readResp;
    Resp rewriteResp = Resp.NORMAL;
    final List<Account> rewritten = new ArrayList<>();

    Accounts with(long id, String bal) {
      Account a = new Account();
      a.setAcctId(id);
      a.setAcctCurrBal(new BigDecimal(bal));
      rows.put(id, a);
      return this;
    }

    @Override
    public ReadResult<Account> readForUpdate(long acctId) {
      if (readResp != null) {
        return ReadResult.of(readResp);
      }
      Account a = rows.get(acctId);
      return a == null ? ReadResult.of(Resp.NOTFND) : ReadResult.normal(a);
    }

    @Override
    public Resp rewrite(Account account) {
      rewritten.add(account);
      return rewriteResp;
    }
  }

  static final class Xref implements XrefFile {
    final List<CardXref> rows = new ArrayList<>();
    Resp forced;

    Xref with(String card, long acct) {
      CardXref x = new CardXref();
      x.setXrefCardNum(card);
      x.setXrefAcctId(acct);
      x.setXrefCustId((int) acct);
      rows.add(x);
      return this;
    }

    @Override
    public ReadResult<CardXref> readByCard(String cardNum) {
      if (forced != null) {
        return ReadResult.of(forced);
      }
      return rows.stream()
          .filter(x -> x.getXrefCardNum().equals(cardNum))
          .findFirst()
          .map(ReadResult::normal)
          .orElseGet(() -> ReadResult.of(Resp.NOTFND));
    }

    @Override
    public ReadResult<CardXref> readByAcct(long acctId) {
      if (forced != null) {
        return ReadResult.of(forced);
      }
      List<CardXref> m = rows.stream().filter(x -> x.getXrefAcctId() == acctId).toList();
      if (m.isEmpty()) {
        return ReadResult.of(Resp.NOTFND);
      }
      return new ReadResult<>(m.size() > 1 ? Resp.DUPKEY : Resp.NORMAL, m.get(0));
    }
  }
}
