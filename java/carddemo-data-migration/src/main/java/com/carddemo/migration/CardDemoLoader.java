package com.carddemo.migration;

import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.domain.repository.DailyTransactionRepository;
import com.carddemo.domain.repository.DisclosureGroupRepository;
import com.carddemo.domain.repository.TranCatBalanceRepository;
import com.carddemo.domain.repository.TransactionCategoryRepository;
import com.carddemo.domain.repository.TransactionTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Component
public class CardDemoLoader implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardDemoLoader.class);

    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final CardRepository cards;
    private final CardXrefRepository xrefs;
    private final TransactionTypeRepository types;
    private final TransactionCategoryRepository categories;
    private final DisclosureGroupRepository disclosures;
    private final TranCatBalanceRepository balances;
    private final DailyTransactionRepository daily;
    private final JdbcTemplate jdbc;

    @Value("${loader.input-dir:app/data/ASCII}")
    private String configuredInputDir;

    public CardDemoLoader(
            CustomerRepository customers,
            AccountRepository accounts,
            CardRepository cards,
            CardXrefRepository xrefs,
            TransactionTypeRepository types,
            TransactionCategoryRepository categories,
            DisclosureGroupRepository disclosures,
            TranCatBalanceRepository balances,
            DailyTransactionRepository daily,
            JdbcTemplate jdbc) {
        this.customers = customers;
        this.accounts = accounts;
        this.cards = cards;
        this.xrefs = xrefs;
        this.types = types;
        this.categories = categories;
        this.disclosures = disclosures;
        this.balances = balances;
        this.daily = daily;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> configuredValues = args.getOptionValues("loader.input-dir");
        Path directory = Paths.get(
                configuredValues == null ? configuredInputDir : configuredValues.get(0));
        if (args.containsOption("reset")) {
            reset();
        }

        report("customer", customers.saveAll(SampleParsers.customers(directory)).size());
        report("account", accounts.saveAll(SampleParsers.accounts(directory)).size());
        report("card", cards.saveAll(SampleParsers.cards(directory)).size());
        report("xref", xrefs.saveAll(SampleParsers.cardXrefs(directory)).size());
        report("trantype", types.saveAll(SampleParsers.transactionTypes(directory)).size());
        report("trancatg", categories.saveAll(SampleParsers.transactionCategories(directory)).size());
        report("discgrp", disclosures.saveAll(SampleParsers.disclosureGroups(directory)).size());
        report("tcatbal", balances.saveAll(SampleParsers.tranCatBalances(directory)).size());
        report("dailytran", daily.saveAll(SampleParsers.dailyTransactions(directory)).size());
    }

    private void report(String name, int count) {
        LOGGER.info("{}: {} records", name, count);
    }

    private void reset() {
        List<String> tables = List.of(
                "daily_transaction_reject",
                "daily_transaction",
                "tran_cat_balance",
                "disclosure_group",
                "transaction_category",
                "transaction_type",
                "transaction_record",
                "card_xref",
                "card",
                "account",
                "customer");
        tables.forEach(table -> jdbc.execute("DELETE FROM " + table));
    }
}
