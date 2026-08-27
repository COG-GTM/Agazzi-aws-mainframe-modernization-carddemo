package com.carddemo.migration;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;
class SampleParsersTest {
 private static final Path DATA=Paths.get("..","..","app","data","ASCII");
 @Test void parsesAllNineFiles() throws Exception {
  assertThat(SampleParsers.accounts(DATA)).hasSize(50).first().satisfies(x->{assertThat(x.getAcctId()).isEqualTo(1);assertThat(x.getCurrentBalance()).isEqualByComparingTo("194.00");assertThat(x.getCurrentBalance().scale()).isEqualTo(2);});
  assertThat(SampleParsers.cards(DATA)).hasSize(50).first().satisfies(x->assertThat(x.getAccountId()).isEqualTo(50L));
  assertThat(SampleParsers.cardXrefs(DATA)).hasSize(50).first().satisfies(x->{assertThat(x.getCustomerId()).isEqualTo(50L);assertThat(x.getAccountId()).isEqualTo(50L);});
  assertThat(SampleParsers.customers(DATA)).hasSize(50).first().satisfies(x->{assertThat(x.getCustomerId()).isEqualTo(1L);assertThat(x.getFirstName()).isEqualTo("Immanuel");});
  assertThat(SampleParsers.dailyTransactions(DATA)).hasSize(300).first().satisfies(x->{assertThat(x.getAmount()).isEqualByComparingTo("504.77");assertThat(x.getMerchantId()).isEqualTo(800000000L);});
  assertThat(SampleParsers.dailyTransactions(DATA).get(1).getAmount()).isEqualByComparingTo("-919.00");
  assertThat(SampleParsers.disclosureGroups(DATA)).hasSize(51).first().satisfies(x->assertThat(x.getInterestRate()).isEqualByComparingTo("15.00"));
  assertThat(SampleParsers.tranCatBalances(DATA)).hasSize(50).first().satisfies(x->assertThat(x.getBalance()).isEqualByComparingTo("0.00"));
  assertThat(SampleParsers.transactionCategories(DATA)).hasSize(18); assertThat(SampleParsers.transactionTypes(DATA)).hasSize(7);
 }
}
