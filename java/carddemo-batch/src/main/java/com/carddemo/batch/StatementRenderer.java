package com.carddemo.batch;

import com.carddemo.domain.Account;
import com.carddemo.domain.Customer;
import com.carddemo.domain.Transaction;
import com.carddemo.domain.util.CobolPicture;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders CBSTM03A statement records and their HTML equivalents.
 */
public final class StatementRenderer {

    public List<String> text(
            Account account,
            Customer customer,
            List<Transaction> transactions) {
        List<String> lines = new ArrayList<>();
        lines.add("*".repeat(31) + "START OF STATEMENT" + "*".repeat(31));
        lines.add(field(name(customer), 75) + " ".repeat(5));
        lines.add(field(customer.getAddressLine1(), 50) + " ".repeat(30));
        lines.add(field(customer.getAddressLine2(), 50) + " ".repeat(30));
        lines.add(field(addressLine3(customer), 80));
        lines.add("-".repeat(80));
        lines.add(center("Basic Details", 80));
        lines.add("-".repeat(80));
        lines.add("Account ID         :" + field(String.valueOf(account.getAcctId()), 20)
                + " ".repeat(40));
        lines.add("Current Balance    :"
                + CobolPicture.unsignedTrailingSign(account.getCurrentBalance())
                + " ".repeat(47));
        lines.add("FICO Score         :" + field(String.valueOf(customer.getFicoCreditScore()), 20)
                + " ".repeat(40));
        lines.add("-".repeat(80));
        lines.add(center("TRANSACTION SUMMARY ", 80));
        lines.add("-".repeat(80));
        lines.add(field("Tran ID         ", 16)
                + field("Tran Details    ", 51)
                + field("  Tran Amount", 13));
        lines.add("-".repeat(80));
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (Transaction transaction : transactions) {
            lines.add(field(transaction.getId(), 16)
                    + " "
                    + field(transaction.getDescription(), 49)
                    + "$"
                    + CobolPicture.signedTrailing(transaction.getAmount()));
            total = total.add(transaction.getAmount()).setScale(2);
        }
        lines.add("-".repeat(80));
        lines.add("Total EXP:"
                + " ".repeat(56)
                + "$"
                + CobolPicture.signedTrailing(total));
        lines.add("*".repeat(32) + "END OF STATEMENT" + "*".repeat(32));
        return lines;
    }

    public List<String> html(
            Account account,
            Customer customer,
            List<Transaction> transactions) {
        List<String> lines = new ArrayList<>();
        lines.add("<tr>");
        lines.add("<td colspan=\"3\" style=\"padding:0px 5px; "
                + "background-color:#1d1d96b3;\">");
        lines.add("<h3>Statement for Account Number: "
                + account.getAcctId() + "</h3>");
        lines.add("</td>");
        lines.add("</tr>");
        lines.add("<tr>");
        lines.add("<td colspan=\"3\" style=\"padding:0px 5px; "
                + "background-color:#FFAF33;\">");
        lines.add("<p style=\"font-size:16px\">Bank of XYZ</p>");
        lines.add("<p>410 Terry Ave N</p>");
        lines.add("<p>Seattle WA 99999</p>");
        lines.add("</td>");
        lines.add("</tr>");
        lines.add("<tr>");
        lines.add("<td colspan=\"3\" style=\"padding:0px 5px; "
                + "background-color:#f2f2f2;\">");
        lines.add("<p style=\"font-size:16px\">" + name(customer) + "</p>");
        lines.add("<p>" + customer.getAddressLine1() + "</p>");
        lines.add("<p>" + customer.getAddressLine2() + "</p>");
        lines.add("<p>" + addressLine3(customer) + "</p>");
        lines.add("</td>");
        lines.add("</tr>");
        lines.add("<tr>");
        lines.add("<td colspan=\"3\" style=\"padding:0px 5px; "
                + "background-color:#33FFD1; text-align:center;\">");
        lines.add("<p style=\"font-size:16px\">Basic Details</p>");
        lines.add("</td>");
        lines.add("</tr>");
        lines.add("<tr>");
        lines.add("<td colspan=\"3\" style=\"padding:0px 5px; "
                + "background-color:#f2f2f2;\">");
        lines.add("<p>Account ID         : " + account.getAcctId() + "</p>");
        lines.add("<p>Current Balance    : "
                + CobolPicture.unsignedTrailingSign(account.getCurrentBalance())
                + "</p>");
        lines.add("<p>FICO Score         : " + customer.getFicoCreditScore() + "</p>");
        lines.add("</td>");
        lines.add("</tr>");
        lines.add("<tr>");
        lines.add("<td colspan=\"3\" style=\"padding:0px 5px; "
                + "background-color:#33FFD1; text-align:center;\">");
        lines.add("<p style=\"font-size:16px\">Transaction Summary</p>");
        lines.add("</td>");
        lines.add("</tr>");
        lines.add("<tr>");
        lines.add("<td style=\"width:25%; padding:0px 5px; background-color:#33FF5E; "
                + "text-align:left;\"><p style=\"font-size:16px\">Tran ID</p></td>");
        lines.add("<td style=\"width:55%; padding:0px 5px; background-color:#33FF5E; "
                + "text-align:left;\"><p style=\"font-size:16px\">Tran Details</p></td>");
        lines.add("<td style=\"width:20%; padding:0px 5px; background-color:#33FF5E; "
                + "text-align:right;\"><p style=\"font-size:16px\">Amount</p></td>");
        lines.add("</tr>");
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (Transaction transaction : transactions) {
            lines.add("<tr>");
            lines.add("<td style=\"width:25%; padding:0px 5px; background-color:#f2f2f2; "
                    + "text-align:left;\"><p>" + transaction.getId() + "</p></td>");
            lines.add("<td style=\"width:55%; padding:0px 5px; background-color:#f2f2f2; "
                    + "text-align:left;\"><p>" + transaction.getDescription() + "</p></td>");
            lines.add("<td style=\"width:20%; padding:0px 5px; background-color:#f2f2f2; "
                    + "text-align:right;\"><p>"
                    + CobolPicture.signedTrailing(transaction.getAmount())
                    + "</p></td>");
            lines.add("</tr>");
            total = total.add(transaction.getAmount()).setScale(2);
        }
        lines.add("<tr>");
        lines.add("<td colspan=\"3\" style=\"padding:0px 5px; "
                + "background-color:#FFAF33;\"><h3>Total EXP: "
                + CobolPicture.signedTrailing(total) + "</h3></td>");
        lines.add("</tr>");
        lines.add("<tr>");
        lines.add("<td colspan=\"3\" style=\"padding:0px 5px; "
                + "background-color:#1d1d96b3;\"><h3>End of Statement</h3></td>");
        lines.add("</tr>");
        return lines;
    }

    public static String name(Customer customer) {
        return String.join(" ",
                customer.getFirstName(),
                customer.getMiddleName(),
                customer.getLastName()).trim();
    }

    private static String addressLine3(Customer customer) {
        return String.join(" ",
                customer.getAddressLine3(),
                customer.getStateCode(),
                customer.getCountryCode(),
                customer.getZip()).trim();
    }

    private static String center(String value, int width) {
        int left = (width - value.length()) / 2;
        int right = width - value.length() - left;
        return " ".repeat(left) + value + " ".repeat(right);
    }

    private static String field(String value, int width) {
        String normalized = value == null ? "" : value;
        if (normalized.length() > width) {
            return normalized.substring(0, width);
        }
        return normalized + " ".repeat(width - normalized.length());
    }
}
