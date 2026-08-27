package com.carddemo.migration;

import com.carddemo.domain.Account;
import com.carddemo.domain.Card;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.Customer;
import com.carddemo.domain.DailyTransaction;
import com.carddemo.domain.Transaction;
import com.carddemo.domain.DisclosureGroup;
import com.carddemo.domain.DisclosureGroupId;
import com.carddemo.domain.TranCatBalance;
import com.carddemo.domain.TranCatBalanceId;
import com.carddemo.domain.TransactionCategory;
import com.carddemo.domain.TransactionCategoryId;
import com.carddemo.domain.TransactionType;
import com.carddemo.domain.util.ZonedDecimalCodec;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class SampleParsers {
  private static final FixedWidthLayout ACCOUNT = FixedWidthLayout.of(
      300,
      FixedWidthLayout.longField("acct-id", 0, 11),
      FixedWidthLayout.text("active-status", 11, 1),
      FixedWidthLayout.money("current-balance", 12, 12),
      FixedWidthLayout.money("credit-limit", 24, 12),
      FixedWidthLayout.money("cash-credit-limit", 36, 12),
      FixedWidthLayout.text("open-date", 48, 10),
      FixedWidthLayout.text("expiration-date", 58, 10),
      FixedWidthLayout.text("reissue-date", 68, 10),
      FixedWidthLayout.money("current-cycle-credit", 78, 12),
      FixedWidthLayout.money("current-cycle-debit", 90, 12),
      FixedWidthLayout.text("address-zip", 102, 10),
      FixedWidthLayout.text("group-id", 112, 10));
  private static final FixedWidthLayout CARD = FixedWidthLayout.of(
      150,
      FixedWidthLayout.text("card-number", 0, 16),
      FixedWidthLayout.longField("account-id", 16, 11),
      FixedWidthLayout.integer("cvv-code", 27, 3),
      FixedWidthLayout.text("embossed-name", 30, 50),
      FixedWidthLayout.text("expiration-date", 80, 10),
      FixedWidthLayout.text("active-status", 90, 1));
  private static final FixedWidthLayout CARD_XREF = FixedWidthLayout.of(
      50,
      FixedWidthLayout.text("card-number", 0, 16),
      FixedWidthLayout.longField("customer-id", 16, 9),
      FixedWidthLayout.longField("account-id", 25, 11));
  private static final FixedWidthLayout CUSTOMER = FixedWidthLayout.of(
      500,
      FixedWidthLayout.longField("customer-id", 0, 9),
      FixedWidthLayout.text("first-name", 9, 25),
      FixedWidthLayout.text("middle-name", 34, 25),
      FixedWidthLayout.text("last-name", 59, 25),
      FixedWidthLayout.text("address-line-1", 84, 50),
      FixedWidthLayout.text("address-line-2", 134, 50),
      FixedWidthLayout.text("address-line-3", 184, 50),
      FixedWidthLayout.text("state-code", 234, 2),
      FixedWidthLayout.text("country-code", 236, 3),
      FixedWidthLayout.text("zip", 239, 10),
      FixedWidthLayout.text("phone-1", 249, 15),
      FixedWidthLayout.text("phone-2", 264, 15),
      FixedWidthLayout.longField("ssn", 279, 9),
      FixedWidthLayout.text("govt-issued-id", 288, 20),
      FixedWidthLayout.text("date-of-birth", 308, 10),
      FixedWidthLayout.text("eft-account-id", 318, 10),
      FixedWidthLayout.text("primary-card-holder", 328, 1),
      FixedWidthLayout.integer("fico-credit-score", 329, 3));
  private static final FixedWidthLayout TRANSACTION = FixedWidthLayout.of(
      350,
      FixedWidthLayout.text("id", 0, 16),
      FixedWidthLayout.text("type-code", 16, 2),
      FixedWidthLayout.integer("category-code", 18, 4),
      FixedWidthLayout.text("source", 22, 10),
      FixedWidthLayout.text("description", 32, 100),
      FixedWidthLayout.money("amount", 132, 11),
      FixedWidthLayout.longField("merchant-id", 143, 9),
      FixedWidthLayout.text("merchant-name", 152, 50),
      FixedWidthLayout.text("merchant-city", 202, 50),
      FixedWidthLayout.text("merchant-zip", 252, 10),
      FixedWidthLayout.text("card-number", 262, 16),
      FixedWidthLayout.text("original-timestamp", 278, 26),
      FixedWidthLayout.text("processing-timestamp", 304, 26));
  private static final FixedWidthLayout DISCLOSURE_GROUP = FixedWidthLayout.of(
      50,
      FixedWidthLayout.text("group-id", 0, 10),
      FixedWidthLayout.text("type-code", 10, 2),
      FixedWidthLayout.integer("category-code", 12, 4),
      FixedWidthLayout.money("interest-rate", 16, 6));
  private static final FixedWidthLayout TRAN_CAT_BALANCE = FixedWidthLayout.of(
      50,
      FixedWidthLayout.longField("account-id", 0, 11),
      FixedWidthLayout.text("type-code", 11, 2),
      FixedWidthLayout.integer("category-code", 13, 4),
      FixedWidthLayout.money("balance", 17, 11));
  private static final FixedWidthLayout TRANSACTION_CATEGORY = FixedWidthLayout.of(
      60,
      FixedWidthLayout.text("type-code", 0, 2),
      FixedWidthLayout.integer("category-code", 2, 4),
      FixedWidthLayout.text("description", 6, 50));
  private static final FixedWidthLayout TRANSACTION_TYPE = FixedWidthLayout.of(
      60,
      FixedWidthLayout.text("type", 0, 2),
      FixedWidthLayout.text("description", 2, 50));

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

  public static String serializeDailyTransaction(DailyTransaction value) {
    Map<String, Object> fields = new HashMap<>();
    fields.put("id", value.getId());
    fields.put("type-code", value.getTypeCode());
    fields.put("category-code", value.getCategoryCode());
    fields.put("source", value.getSource());
    fields.put("description", value.getDescription());
    fields.put("amount", value.getAmount());
    fields.put("merchant-id", value.getMerchantId());
    fields.put("merchant-name", value.getMerchantName());
    fields.put("merchant-city", value.getMerchantCity());
    fields.put("merchant-zip", value.getMerchantZip());
    fields.put("card-number", value.getCardNumber());
    fields.put("original-timestamp", value.getOriginalTimestamp());
    fields.put("processing-timestamp", value.getProcessingTimestamp());
    return TRANSACTION.format(fields);
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
