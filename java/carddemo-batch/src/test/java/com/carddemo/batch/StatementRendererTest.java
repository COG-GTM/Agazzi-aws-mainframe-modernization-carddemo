package com.carddemo.batch;

import com.carddemo.domain.Account;
import com.carddemo.domain.Customer;
import com.carddemo.domain.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatementRendererTest {

    @Test
    void rendersFixedWidthCobolStatementLines() {
        Account account = new Account();
        account.setAcctId(42L);
        account.setCurrentBalance(new BigDecimal("1234.56"));
        Customer customer = new Customer();
        customer.setFirstName("Ada");
        customer.setMiddleName("M");
        customer.setLastName("Lovelace");
        customer.setAddressLine1("1 Analytical Engine Way");
        customer.setAddressLine2("London");
        customer.setAddressLine3("Computing House");
        customer.setStateCode("LN");
        customer.setCountryCode("GBR");
        customer.setZip("NW1");
        customer.setFicoCreditScore(800);
        Transaction transaction = new Transaction();
        transaction.setId("0000000000000001");
        transaction.setDescription("Purchase");
        transaction.setAmount(new BigDecimal("-12.34"));

        List<String> lines = new StatementRenderer().text(
                account, customer, List.of(transaction));
        assertThat(lines.get(0)).isEqualTo(
                "*******************************START OF STATEMENT*******************************");
        assertThat(lines.get(1)).isEqualTo(
                "Ada M Lovelace" + " ".repeat(66));
        assertThat(lines.get(8)).isEqualTo(
                "Account ID         :42" + " ".repeat(18) + " ".repeat(40));
        assertThat(lines.get(9)).isEqualTo(
                "Current Balance    :000001234.56 " + " ".repeat(47));
        assertThat(lines.get(14)).isEqualTo(
                fixed("Tran ID         ", 16)
                        + fixed("Tran Details    ", 51)
                        + fixed("  Tran Amount", 13));
        assertThat(lines.get(16)).isEqualTo(
                "0000000000000001 Purchase" + " ".repeat(41)
                        + "$       12.34-");
        assertThat(lines.get(18)).isEqualTo(
                "Total EXP:" + " ".repeat(56) + "$       12.34-");
        assertThat(lines.get(19)).isEqualTo(
                "********************************END OF STATEMENT********************************");
        assertThat(lines).allSatisfy(line -> assertThat(line).hasSize(80));
    }

    @Test
    void preservesCobolHtmlPrologueAndTableStructure() {
        Account account = new Account();
        account.setAcctId(42L);
        account.setCurrentBalance(new BigDecimal("0.00"));
        Customer customer = new Customer();
        customer.setFirstName("Ada");
        customer.setMiddleName("");
        customer.setLastName("Lovelace");
        customer.setAddressLine1("");
        customer.setAddressLine2("");
        customer.setAddressLine3("");
        customer.setStateCode("");
        customer.setCountryCode("");
        customer.setZip("");
        customer.setFicoCreditScore(800);

        List<String> lines = new StatementRenderer().html(
                account, customer, List.of());
        assertThat(lines).contains("<tr>");
        assertThat(lines).anyMatch(line ->
                line.contains("<td colspan=\"3\"")
                        && line.contains("background-color:#1d1d96b3"));
        assertThat(lines).anyMatch(line ->
                line.contains("Statement for Account Number: 42"));
        assertThat(lines).anyMatch(line -> line.contains("Basic Details"));
        assertThat(lines).anyMatch(line -> line.contains("Transaction Summary"));
        assertThat(lines).anyMatch(line -> line.contains("Total EXP:"));
    }

    private static String fixed(String value, int width) {
        return value + " ".repeat(width - value.length());
    }
}
