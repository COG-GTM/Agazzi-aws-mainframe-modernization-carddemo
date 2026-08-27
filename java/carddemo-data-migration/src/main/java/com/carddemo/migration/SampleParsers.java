package com.carddemo.migration;

import com.carddemo.domain.*;
import com.carddemo.domain.util.ZonedDecimalCodec;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

public final class SampleParsers {
  private static final FixedWidthLayout ACCOUNT = FixedWidthLayout.of(
      "acct-id", 0, 11, FixedWidthLayout.Kind.LONG, "active-status", 11, 1, FixedWidthLayout.Kind.TEXT,
      "current-balance", 12, 12, FixedWidthLayout.Kind.MONEY, "credit-limit", 24, 12, FixedWidthLayout.Kind.MONEY,
      "cash-credit-limit", 36, 12, FixedWidthLayout.Kind.MONEY, "open-date", 48, 10, FixedWidthLayout.Kind.TEXT,
      "expiration-date", 58, 10, FixedWidthLayout.Kind.TEXT, "reissue-date", 68, 10, FixedWidthLayout.Kind.TEXT,
      "current-cycle-credit", 78, 12, FixedWidthLayout.Kind.MONEY, "current-cycle-debit", 90, 12, FixedWidthLayout.Kind.MONEY,
      "address-zip", 102, 10, FixedWidthLayout.Kind.TEXT, "group-id", 112, 10, FixedWidthLayout.Kind.TEXT);
  private static final FixedWidthLayout CARD = FixedWidthLayout.of(
      "card-number", 0, 16, FixedWidthLayout.Kind.TEXT, "account-id", 16, 11, FixedWidthLayout.Kind.LONG,
      "cvv-code", 27, 3, FixedWidthLayout.Kind.INTEGER, "embossed-name", 30, 50, FixedWidthLayout.Kind.TEXT,
      "expiration-date", 80, 10, FixedWidthLayout.Kind.TEXT, "active-status", 90, 1, FixedWidthLayout.Kind.TEXT);
  private static final FixedWidthLayout CARD_XREF = FixedWidthLayout.of(
      "card-number", 0, 16, FixedWidthLayout.Kind.TEXT, "customer-id", 16, 9, FixedWidthLayout.Kind.LONG,
      "account-id", 25, 11, FixedWidthLayout.Kind.LONG);
  private static final FixedWidthLayout CUSTOMER = FixedWidthLayout.of(
      "customer-id", 0, 9, FixedWidthLayout.Kind.LONG, "first-name", 9, 25, FixedWidthLayout.Kind.TEXT,
      "middle-name", 34, 25, FixedWidthLayout.Kind.TEXT, "last-name", 59, 25, FixedWidthLayout.Kind.TEXT,
      "address-line-1", 84, 50, FixedWidthLayout.Kind.TEXT, "address-line-2", 134, 50, FixedWidthLayout.Kind.TEXT,
      "address-line-3", 184, 50, FixedWidthLayout.Kind.TEXT, "state-code", 234, 2, FixedWidthLayout.Kind.TEXT,
      "country-code", 236, 3, FixedWidthLayout.Kind.TEXT, "zip", 239, 10, FixedWidthLayout.Kind.TEXT,
      "phone-1", 249, 15, FixedWidthLayout.Kind.TEXT, "phone-2", 264, 15, FixedWidthLayout.Kind.TEXT,
      "ssn", 279, 9, FixedWidthLayout.Kind.LONG, "govt-issued-id", 288, 20, FixedWidthLayout.Kind.TEXT,
      "date-of-birth", 308, 10, FixedWidthLayout.Kind.TEXT, "eft-account-id", 318, 10, FixedWidthLayout.Kind.TEXT,
      "primary-card-holder", 328, 1, FixedWidthLayout.Kind.TEXT, "fico-credit-score", 329, 3, FixedWidthLayout.Kind.INTEGER);
  private static final FixedWidthLayout TRANSACTION = FixedWidthLayout.of(
      "id", 0, 16, FixedWidthLayout.Kind.TEXT, "type-code", 16, 2, FixedWidthLayout.Kind.TEXT,
      "category-code", 18, 4, FixedWidthLayout.Kind.INTEGER, "source", 22, 10, FixedWidthLayout.Kind.TEXT,
      "description", 32, 100, FixedWidthLayout.Kind.TEXT, "amount", 132, 11, FixedWidthLayout.Kind.MONEY,
      "merchant-id", 143, 9, FixedWidthLayout.Kind.LONG, "merchant-name", 152, 50, FixedWidthLayout.Kind.TEXT,
      "merchant-city", 202, 50, FixedWidthLayout.Kind.TEXT, "merchant-zip", 252, 10, FixedWidthLayout.Kind.TEXT,
      "card-number", 262, 16, FixedWidthLayout.Kind.TEXT, "original-timestamp", 278, 26, FixedWidthLayout.Kind.TEXT,
      "processing-timestamp", 304, 26, FixedWidthLayout.Kind.TEXT);
  private static final FixedWidthLayout DISCLOSURE_GROUP = FixedWidthLayout.of(
      "group-id", 0, 10, FixedWidthLayout.Kind.TEXT, "type-code", 10, 2, FixedWidthLayout.Kind.TEXT,
      "category-code", 12, 4, FixedWidthLayout.Kind.INTEGER, "interest-rate", 16, 6, FixedWidthLayout.Kind.MONEY);
  private static final FixedWidthLayout TRAN_CAT_BALANCE = FixedWidthLayout.of(
      "account-id", 0, 11, FixedWidthLayout.Kind.LONG, "type-code", 11, 2, FixedWidthLayout.Kind.TEXT,
      "category-code", 13, 4, FixedWidthLayout.Kind.INTEGER, "balance", 17, 11, FixedWidthLayout.Kind.MONEY);
  private static final FixedWidthLayout TRANSACTION_CATEGORY = FixedWidthLayout.of(
      "type-code", 0, 2, FixedWidthLayout.Kind.TEXT, "category-code", 2, 4, FixedWidthLayout.Kind.INTEGER,
      "description", 6, 50, FixedWidthLayout.Kind.TEXT);
  private static final FixedWidthLayout TRANSACTION_TYPE = FixedWidthLayout.of(
      "type", 0, 2, FixedWidthLayout.Kind.TEXT, "description", 2, 50, FixedWidthLayout.Kind.TEXT);

  private SampleParsers() {}

  private static List<String> lines(Path path) throws IOException {
    return Files.readAllLines(path).stream()
        .map(line -> line.endsWith("\r") ? line.substring(0, line.length() - 1) : line)
        .toList();
  }

  private static String text(FixedWidthLayout layout, String line, String field) {
    return layout.read(line, field).trim();
  }

  private static long longValue(FixedWidthLayout layout, String line, String field) {
    String value = text(layout, line, field);
    return value.isBlank() ? 0 : Long.parseLong(value);
  }

  private static int intValue(FixedWidthLayout layout, String line, String field) {
    String value = text(layout, line, field);
    return value.isBlank() ? 0 : Integer.parseInt(value);
  }

  private static BigDecimal money(FixedWidthLayout layout, String line, String field) {
    return ZonedDecimalCodec.decode(text(layout, line, field));
  }

  private static <T> List<T> parse(Path path, Function<String, T> parser) throws IOException {
    return lines(path).stream().map(parser).toList();
  }

  public static List<Account> accounts(Path dir) throws IOException {
    return parse(dir.resolve("acctdata.txt"), line -> {
      Account value = new Account();
      value.setAcctId(longValue(ACCOUNT, line, "acct-id"));
      value.setActiveStatus(text(ACCOUNT, line, "active-status"));
      value.setCurrentBalance(money(ACCOUNT, line, "current-balance"));
      value.setCreditLimit(money(ACCOUNT, line, "credit-limit"));
      value.setCashCreditLimit(money(ACCOUNT, line, "cash-credit-limit"));
      value.setOpenDate(text(ACCOUNT, line, "open-date"));
      value.setExpirationDate(text(ACCOUNT, line, "expiration-date"));
      value.setReissueDate(text(ACCOUNT, line, "reissue-date"));
      value.setCurrentCycleCredit(money(ACCOUNT, line, "current-cycle-credit"));
      value.setCurrentCycleDebit(money(ACCOUNT, line, "current-cycle-debit"));
      value.setAddressZip(text(ACCOUNT, line, "address-zip"));
      value.setGroupId(text(ACCOUNT, line, "group-id"));
      return value;
    });
  }

  public static List<Card> cards(Path dir) throws IOException {
    return parse(dir.resolve("carddata.txt"), line -> {
      Card value = new Card();
      value.setCardNumber(text(CARD, line, "card-number"));
      value.setAccountId(longValue(CARD, line, "account-id"));
      value.setCvvCode(intValue(CARD, line, "cvv-code"));
      value.setEmbossedName(text(CARD, line, "embossed-name"));
      value.setExpirationDate(text(CARD, line, "expiration-date"));
      value.setActiveStatus(text(CARD, line, "active-status"));
      return value;
    });
  }

  public static List<CardXref> cardXrefs(Path dir) throws IOException {
    return parse(dir.resolve("cardxref.txt"), line -> {
      CardXref value = new CardXref();
      value.setCardNumber(text(CARD_XREF, line, "card-number"));
      value.setCustomerId(longValue(CARD_XREF, line, "customer-id"));
      value.setAccountId(longValue(CARD_XREF, line, "account-id"));
      return value;
    });
  }

  public static List<Customer> customers(Path dir) throws IOException {
    return parse(dir.resolve("custdata.txt"), line -> {
      Customer value = new Customer();
      value.setCustomerId(longValue(CUSTOMER, line, "customer-id"));
      value.setFirstName(text(CUSTOMER, line, "first-name"));
      value.setMiddleName(text(CUSTOMER, line, "middle-name"));
      value.setLastName(text(CUSTOMER, line, "last-name"));
      value.setAddressLine1(text(CUSTOMER, line, "address-line-1"));
      value.setAddressLine2(text(CUSTOMER, line, "address-line-2"));
      value.setAddressLine3(text(CUSTOMER, line, "address-line-3"));
      value.setStateCode(text(CUSTOMER, line, "state-code"));
      value.setCountryCode(text(CUSTOMER, line, "country-code"));
      value.setZip(text(CUSTOMER, line, "zip"));
      value.setPhone1(text(CUSTOMER, line, "phone-1"));
      value.setPhone2(text(CUSTOMER, line, "phone-2"));
      value.setSsn(longValue(CUSTOMER, line, "ssn"));
      value.setGovtIssuedId(text(CUSTOMER, line, "govt-issued-id"));
      value.setDateOfBirth(text(CUSTOMER, line, "date-of-birth"));
      value.setEftAccountId(text(CUSTOMER, line, "eft-account-id"));
      value.setPrimaryCardHolder(text(CUSTOMER, line, "primary-card-holder"));
      value.setFicoCreditScore(intValue(CUSTOMER, line, "fico-credit-score"));
      return value;
    });
  }

  private static Transaction transaction(FixedWidthLayout layout, String line) {
    Transaction value = new Transaction();
    value.setId(text(layout, line, "id"));
    value.setTypeCode(text(layout, line, "type-code"));
    value.setCategoryCode(intValue(layout, line, "category-code"));
    value.setSource(text(layout, line, "source"));
    value.setDescription(text(layout, line, "description"));
    value.setAmount(money(layout, line, "amount"));
    value.setMerchantId(longValue(layout, line, "merchant-id"));
    value.setMerchantName(text(layout, line, "merchant-name"));
    value.setMerchantCity(text(layout, line, "merchant-city"));
    value.setMerchantZip(text(layout, line, "merchant-zip"));
    value.setCardNumber(text(layout, line, "card-number"));
    value.setOriginalTimestamp(text(layout, line, "original-timestamp"));
    value.setProcessingTimestamp(text(layout, line, "processing-timestamp"));
    return value;
  }

  public static List<DailyTransaction> dailyTransactions(Path dir) throws IOException {
    return parse(dir.resolve("dailytran.txt"), line -> {
      DailyTransaction value = new DailyTransaction();
      value.setId(text(TRANSACTION, line, "id"));
      value.setTypeCode(text(TRANSACTION, line, "type-code"));
      value.setCategoryCode(intValue(TRANSACTION, line, "category-code"));
      value.setSource(text(TRANSACTION, line, "source"));
      value.setDescription(text(TRANSACTION, line, "description"));
      value.setAmount(money(TRANSACTION, line, "amount"));
      value.setMerchantId(longValue(TRANSACTION, line, "merchant-id"));
      value.setMerchantName(text(TRANSACTION, line, "merchant-name"));
      value.setMerchantCity(text(TRANSACTION, line, "merchant-city"));
      value.setMerchantZip(text(TRANSACTION, line, "merchant-zip"));
      value.setCardNumber(text(TRANSACTION, line, "card-number"));
      value.setOriginalTimestamp(text(TRANSACTION, line, "original-timestamp"));
      value.setProcessingTimestamp(text(TRANSACTION, line, "processing-timestamp"));
      return value;
    });
  }

  public static List<Transaction> transactions(Path dir) throws IOException {
    return parse(dir.resolve("transact.txt"), line -> transaction(TRANSACTION, line));
  }

  public static List<DisclosureGroup> disclosureGroups(Path dir) throws IOException {
    return parse(dir.resolve("discgrp.txt"), line -> {
      DisclosureGroup value = new DisclosureGroup();
      value.setId(new DisclosureGroupId(text(DISCLOSURE_GROUP, line, "group-id"),
          text(DISCLOSURE_GROUP, line, "type-code"), intValue(DISCLOSURE_GROUP, line, "category-code")));
      value.setInterestRate(money(DISCLOSURE_GROUP, line, "interest-rate"));
      return value;
    });
  }

  public static List<TranCatBalance> tranCatBalances(Path dir) throws IOException {
    return parse(dir.resolve("tcatbal.txt"), line -> {
      TranCatBalance value = new TranCatBalance();
      value.setId(new TranCatBalanceId(longValue(TRAN_CAT_BALANCE, line, "account-id"),
          text(TRAN_CAT_BALANCE, line, "type-code"), intValue(TRAN_CAT_BALANCE, line, "category-code")));
      value.setBalance(money(TRAN_CAT_BALANCE, line, "balance"));
      return value;
    });
  }

  public static List<TransactionCategory> transactionCategories(Path dir) throws IOException {
    return parse(dir.resolve("trancatg.txt"), line -> {
      TransactionCategory value = new TransactionCategory();
      value.setId(new TransactionCategoryId(text(TRANSACTION_CATEGORY, line, "type-code"),
          intValue(TRANSACTION_CATEGORY, line, "category-code")));
      value.setDescription(text(TRANSACTION_CATEGORY, line, "description"));
      return value;
    });
  }

  public static List<TransactionType> transactionTypes(Path dir) throws IOException {
    return parse(dir.resolve("trantype.txt"), line -> {
      TransactionType value = new TransactionType();
      value.setType(text(TRANSACTION_TYPE, line, "type"));
      value.setDescription(text(TRANSACTION_TYPE, line, "description"));
      return value;
    });
  }
}
