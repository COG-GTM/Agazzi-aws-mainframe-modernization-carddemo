package com.carddemo.batch.interest.testsupport;

import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.contracts.record.AccountRecord;
import com.carddemo.contracts.record.CardXrefRecord;
import com.carddemo.contracts.record.DisclosureGroupRecord;
import com.carddemo.contracts.record.TranCatBalanceRecord;
import com.carddemo.copybook.CoreCodecs;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Loads golden datasets into the V1 tables and unloads them in VSAM key order. */
public final class FixtureDb {

  /** Table, column list in copybook order, key order. */
  public enum Table {
    ACCOUNT(
        "account",
        "acct_id, acct_active_status, acct_curr_bal, acct_credit_limit, acct_cash_credit_limit,"
            + " acct_open_date, acct_expiration_date, acct_reissue_date, acct_curr_cyc_credit,"
            + " acct_curr_cyc_debit, acct_addr_zip, acct_group_id, filler",
        "acct_id",
        CoreCodecs.ACCOUNT),
    CARD_XREF(
        "card_xref",
        "xref_card_num, xref_cust_id, xref_acct_id, filler",
        "xref_card_num",
        CoreCodecs.CARD_XREF),
    TRAN_CAT_BALANCE(
        "tran_cat_balance",
        "trancat_acct_id, trancat_type_cd, trancat_cd, tran_cat_bal, filler",
        "trancat_acct_id, trancat_type_cd, trancat_cd",
        CoreCodecs.TRAN_CAT_BALANCE),
    DISCLOSURE_GROUP(
        "disclosure_group",
        "dis_acct_group_id, dis_tran_type_cd, dis_tran_cat_cd, dis_int_rate, filler",
        "dis_acct_group_id, dis_tran_type_cd, dis_tran_cat_cd",
        CoreCodecs.DISCLOSURE_GROUP);

    final String name;
    final String columns;
    final String key;
    final RecordCodec<? extends Record> codec;

    Table(String name, String columns, String key, RecordCodec<? extends Record> codec) {
      this.name = name;
      this.columns = columns;
      this.key = key;
      this.codec = codec;
    }

    public RecordCodec<? extends Record> codec() {
      return codec;
    }
  }

  private final JdbcTemplate jdbc;

  public FixtureDb(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void clear() {
    for (String t :
        List.of("transaction", "tran_cat_balance", "disclosure_group", "card_xref", "account")) {
      jdbc.update("DELETE FROM " + t);
    }
  }

  public void load(Table table, List<String> lines) {
    int n = table.columns.split(",").length;
    String sql =
        "INSERT INTO %s (%s) VALUES (%s)"
            .formatted(table.name, table.columns, String.join(", ", "?".repeat(n).split("")));
    for (String line : lines) {
      Record r = table.codec.decodeLine(line);
      RecordComponent[] comps = r.getClass().getRecordComponents();
      Object[] args = new Object[comps.length];
      for (int i = 0; i < comps.length; i++) {
        try {
          args[i] = comps[i].getAccessor().invoke(r);
        } catch (ReflectiveOperationException e) {
          throw new IllegalStateException(e);
        }
      }
      jdbc.update(sql, args);
    }
  }

  /** Table rows encoded with the copybook codec, in key order (a KSDS unload). */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public List<String> unload(Table table) {
    Class<? extends Record> type = table.codec.recordType();
    RecordComponent[] comps = type.getRecordComponents();
    Class<?>[] types = new Class<?>[comps.length];
    for (int i = 0; i < comps.length; i++) {
      types[i] = comps[i].getType();
    }
    List<String> out = new ArrayList<>();
    jdbc.query(
        "SELECT %s FROM %s ORDER BY %s".formatted(table.columns, table.name, table.key),
        rs -> {
          Object[] args = new Object[comps.length];
          for (int i = 0; i < comps.length; i++) {
            Object v = rs.getObject(i + 1);
            if (types[i] == long.class) {
              v = ((Number) v).longValue();
            } else if (types[i] == int.class) {
              v = ((Number) v).intValue();
            } else if (types[i] == BigDecimal.class) {
              v = (BigDecimal) v;
            }
            args[i] = v;
          }
          try {
            Record r = type.getDeclaredConstructor(types).newInstance(args);
            out.add(((RecordCodec) table.codec).encodeLine(r));
          } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
          }
        });
    return out;
  }

  public long count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
  }

  /** Convenience typed decoders used by the assertions. */
  public static final RecordCodec<AccountRecord> ACCOUNT = CoreCodecs.ACCOUNT;

  public static final RecordCodec<CardXrefRecord> XREF = CoreCodecs.CARD_XREF;
  public static final RecordCodec<TranCatBalanceRecord> TCATBAL = CoreCodecs.TRAN_CAT_BALANCE;
  public static final RecordCodec<DisclosureGroupRecord> DISCGRP = CoreCodecs.DISCLOSURE_GROUP;
}
