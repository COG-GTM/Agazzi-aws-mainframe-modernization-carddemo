package com.carddemo.migration;
import com.carddemo.domain.repository.*;
import org.springframework.boot.ApplicationArguments; import org.springframework.boot.ApplicationRunner; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.*; import java.util.*; import java.io.IOException;
@Component
public class CardDemoLoader implements ApplicationRunner {
 private final CustomerRepository customers; private final AccountRepository accounts; private final CardRepository cards; private final CardXrefRepository xrefs; private final TransactionTypeRepository types; private final TransactionCategoryRepository categories; private final DisclosureGroupRepository disclosures; private final TranCatBalanceRepository balances; private final DailyTransactionRepository daily; private final JdbcTemplate jdbc;
 @Value("${loader.input-dir:app/data/ASCII}") private String configuredInputDir;
 public CardDemoLoader(CustomerRepository c,AccountRepository a,CardRepository ca,CardXrefRepository x,TransactionTypeRepository t,TransactionCategoryRepository tc,DisclosureGroupRepository d,TranCatBalanceRepository b,DailyTransactionRepository dt,JdbcTemplate j){customers=c;accounts=a;cards=ca;xrefs=x;types=t;categories=tc;disclosures=d;balances=b;daily=dt;jdbc=j;}
 public void run(ApplicationArguments args)throws Exception{Path dir=Paths.get(args.getOptionValues("loader.input-dir")==null?configuredInputDir:args.getOptionValues("loader.input-dir").get(0));if(args.containsOption("reset"))reset();
  report("customer",customers.saveAll(SampleParsers.customers(dir)).size());report("account",accounts.saveAll(SampleParsers.accounts(dir)).size());report("card",cards.saveAll(SampleParsers.cards(dir)).size());report("xref",xrefs.saveAll(SampleParsers.cardXrefs(dir)).size());report("trantype",types.saveAll(SampleParsers.transactionTypes(dir)).size());report("trancatg",categories.saveAll(SampleParsers.transactionCategories(dir)).size());report("discgrp",disclosures.saveAll(SampleParsers.disclosureGroups(dir)).size());report("tcatbal",balances.saveAll(SampleParsers.tranCatBalances(dir)).size());report("dailytran",daily.saveAll(SampleParsers.dailyTransactions(dir)).size());
 }
 private void report(String n,int count){System.out.printf("%s: %d records%n",n,count);}
 private void reset(){List<String> tables=List.of("daily_transaction_reject","daily_transaction","tran_cat_balance","disclosure_group","transaction_category","transaction_type","transaction_record","card_xref","card","account","customer");tables.forEach(t->jdbc.execute("DELETE FROM "+t));}
}
