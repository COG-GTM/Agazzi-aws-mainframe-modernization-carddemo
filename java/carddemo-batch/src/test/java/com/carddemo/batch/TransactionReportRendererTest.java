package com.carddemo.batch;

import com.carddemo.domain.CardXref;
import com.carddemo.domain.Transaction;
import com.carddemo.domain.TransactionCategory;
import com.carddemo.domain.TransactionCategoryId;
import com.carddemo.domain.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionReportRendererTest {

    @Test
    void rendersCopybookHeaderDetailAndTotals() {
        Transaction transaction = new Transaction();
        transaction.setId("0000000000000001");
        transaction.setCardNumber("0000000000000001");
        transaction.setTypeCode("01");
        transaction.setCategoryCode(1);
        transaction.setSource("ATM");
        transaction.setAmount(new BigDecimal("12.34"));
        CardXref xref = new CardXref();
        xref.setCardNumber(transaction.getCardNumber());
        xref.setAccountId(42L);
        TransactionType type = new TransactionType();
        type.setType("01");
        type.setDescription("Purchase");
        TransactionCategory category = new TransactionCategory();
        category.setId(new TransactionCategoryId("01", 1));
        category.setDescription("Retail");

        Map<String, CardXref> xrefs = Map.of(transaction.getCardNumber(), xref);
        Map<String, TransactionType> types = Map.of("01", type);
        Map<TransactionCategoryId, TransactionCategory> categories =
                Map.of(category.getId(), category);
        TransactionReportRenderer renderer = new TransactionReportRenderer(
                xrefs,
                types,
                categories,
                List.of(transaction),
                "2022-01-01",
                "2022-12-31");

        List<String> lines = renderer.render();
        assertThat(lines.get(0)).isEqualTo(
                fixed("DALYREPT", 38)
                        + fixed("Daily Transaction Report", 41)
                        + fixed("Date Range: ", 12)
                        + fixed("2022-01-01", 10)
                        + fixed(" to ", 4)
                        + fixed("2022-12-31", 10)
                        + " ".repeat(18));
        assertThat(lines.get(1)).isEqualTo(" ".repeat(133));
        assertThat(lines.get(2)).isEqualTo(
                fixed("Transaction ID", 17)
                        + fixed("Account ID", 12)
                        + fixed("Transaction Type", 19)
                        + fixed("Tran Category", 35)
                        + fixed("Tran Source", 14)
                        + " "
                        + fixed("        Amount", 16)
                        + " ".repeat(19));
        assertThat(lines.get(3)).hasSize(133)
                .isEqualTo("-".repeat(133));
        assertThat(lines.get(4)).isEqualTo(
                fixed("0000000000000001", 16)
                        + " "
                        + fixed("42", 11)
                        + " "
                        + fixed("01", 2)
                        + "-"
                        + fixed("Purchase", 15)
                        + " "
                        + "0001"
                        + "-"
                        + fixed("Retail", 29)
                        + " "
                        + fixed("ATM", 10)
                        + "    "
                        + "          12.34  "
                        + " ".repeat(19));
        assertThat(lines.get(4)).hasSize(133);
        assertThat(lines.get(5)).startsWith("Account Total");
        assertThat(lines.get(6)).isEqualTo("-".repeat(133));
        assertThat(lines.get(7)).startsWith("Page Total");
        assertThat(lines.get(8)).startsWith("Grand Total");
        assertThat(lines).allSatisfy(line -> assertThat(line).hasSize(133));
    }

    @Test
    void rendersMissingDescriptionsWithCobolLiterals() {
        Transaction transaction = new Transaction();
        transaction.setId("0000000000000001");
        transaction.setCardNumber("0000000000000001");
        transaction.setTypeCode("99");
        transaction.setCategoryCode(9999);
        transaction.setSource("SYSTEM");
        transaction.setAmount(new BigDecimal("-1.21"));
        TransactionReportRenderer renderer = new TransactionReportRenderer(
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                List.of(transaction),
                "0000-01-01",
                "9999-12-31");

        String detail = renderer.render().stream()
                .filter(line -> line.startsWith(transaction.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(detail).contains("99-INVALID TRAN");
        assertThat(detail).contains("9999-INVALID TRAN CATG KEY");
        assertThat(detail).hasSize(133);
    }

    private static String fixed(String value, int width) {
        return value + " ".repeat(width - value.length());
    }
}
