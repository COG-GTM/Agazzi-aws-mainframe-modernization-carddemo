package com.carddemo.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SampleParsersTest {

    private static final Path DATA = Path.of("..", "..", "app", "data", "ASCII");

    @Test
    void parsesAccounts() throws Exception {
        assertThat(SampleParsers.accounts(DATA))
                .hasSize(50)
                .first()
                .satisfies(account -> {
                    assertThat(account.getAcctId()).isEqualTo(1);
                    assertThat(account.getCurrentBalance()).isEqualByComparingTo("194.00");
                });
    }

    @Test
    void parsesCards() throws Exception {
        assertThat(SampleParsers.cards(DATA))
                .hasSize(50)
                .first()
                .satisfies(card -> assertThat(card.getAccountId()).isEqualTo(50L));
    }

    @Test
    void parsesCardXrefs() throws Exception {
        assertThat(SampleParsers.cardXrefs(DATA))
                .hasSize(50)
                .first()
                .satisfies(xref -> {
                    assertThat(xref.getCustomerId()).isEqualTo(50L);
                    assertThat(xref.getAccountId()).isEqualTo(50L);
                });
    }

    @Test
    void parsesCustomers() throws Exception {
        assertThat(SampleParsers.customers(DATA))
                .hasSize(50)
                .first()
                .satisfies(customer -> {
                    assertThat(customer.getCustomerId()).isEqualTo(1L);
                    assertThat(customer.getFirstName()).isEqualTo("Immanuel");
                });
    }

    @Test
    void parsesDailyTransactions() throws Exception {
        assertThat(SampleParsers.dailyTransactions(DATA))
                .hasSize(300)
                .first()
                .satisfies(transaction -> {
                    assertThat(transaction.getMerchantId()).isEqualTo(800000000L);
                    assertThat(transaction.getOriginalTimestamp()).hasSize(26);
                });
    }

    @Test
    void parsesDisclosureGroups() throws Exception {
        assertThat(SampleParsers.disclosureGroups(DATA))
                .hasSize(51)
                .first()
                .satisfies(group -> assertThat(group.getInterestRate())
                        .isEqualByComparingTo("15.00"));
    }

    @Test
    void parsesTransactionCategoryBalances() throws Exception {
        assertThat(SampleParsers.tranCatBalances(DATA))
                .hasSize(50)
                .first()
                .satisfies(balance -> assertThat(balance.getBalance())
                        .isEqualByComparingTo("0.00"));
    }

    @Test
    void parsesTransactionCategories() throws Exception {
        assertThat(SampleParsers.transactionCategories(DATA)).hasSize(18);
    }

    @Test
    void parsesTransactionTypes() throws Exception {
        assertThat(SampleParsers.transactionTypes(DATA)).hasSize(7);
    }

    @Test
    void parsesKnownOverpunchValues() throws Exception {
        assertThat(SampleParsers.dailyTransactions(DATA).get(0).getAmount())
                .isEqualByComparingTo("504.77");
        assertThat(SampleParsers.dailyTransactions(DATA).get(0).getAmount().scale())
                .isEqualTo(2);
        assertThat(SampleParsers.dailyTransactions(DATA).get(1).getAmount())
                .isEqualByComparingTo("-919.00");
    }

    @Test
    void serializesDailyTransactionTo350Characters() throws Exception {
        String serialized = SampleParsers.serializeDailyTransaction(
                SampleParsers.dailyTransactions(DATA).get(0));

        assertThat(serialized).hasSize(350);
        assertThat(serialized.substring(132, 143)).isEqualTo("0000005047G");
    }
}
