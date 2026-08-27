package com.carddemo.batch;

import com.carddemo.domain.CardXref;
import com.carddemo.domain.Transaction;
import com.carddemo.domain.TransactionCategory;
import com.carddemo.domain.TransactionCategoryId;
import com.carddemo.domain.TransactionType;
import com.carddemo.domain.util.CobolPicture;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders the fixed-width CVTRA07Y transaction report records.
 */
public final class TransactionReportRenderer {

    private static final int REPORT_WIDTH = 133;
    private static final int PAGE_SIZE = 20;

    private final Map<String, CardXref> xrefsByCard;
    private final Map<String, TransactionType> typesByCode;
    private final Map<TransactionCategoryId, TransactionCategory> categoriesById;
    private final List<Transaction> transactions;
    private final String startDate;
    private final String endDate;

    public TransactionReportRenderer(
            Map<String, CardXref> xrefsByCard,
            Map<String, TransactionType> typesByCode,
            Map<TransactionCategoryId, TransactionCategory> categoriesById,
            List<Transaction> transactions,
            String startDate,
            String endDate) {
        this.xrefsByCard = xrefsByCard;
        this.typesByCode = typesByCode;
        this.categoriesById = categoriesById;
        this.transactions = transactions;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public List<String> render() {
        List<String> lines = new ArrayList<>();
        BigDecimal accountTotal = BigDecimal.ZERO.setScale(2);
        BigDecimal pageTotal = BigDecimal.ZERO.setScale(2);
        BigDecimal grandTotal = BigDecimal.ZERO.setScale(2);
        String currentCard = null;
        int detailCount = 0;

        for (Transaction transaction : transactions) {
            if (lines.isEmpty()) {
                lines.addAll(headerBlock());
            }
            if (currentCard != null
                    && !currentCard.equals(transaction.getCardNumber())) {
                lines.add(accountTotal(accountTotal));
                lines.add(separator());
                accountTotal = BigDecimal.ZERO.setScale(2);
            }
            currentCard = transaction.getCardNumber();
            lines.add(detail(transaction));
            BigDecimal amount = transaction.getAmount().setScale(2);
            accountTotal = accountTotal.add(amount).setScale(2);
            pageTotal = pageTotal.add(amount).setScale(2);
            grandTotal = grandTotal.add(amount).setScale(2);
            detailCount++;
            if (detailCount == PAGE_SIZE) {
                lines.add(pageTotal(pageTotal));
                pageTotal = BigDecimal.ZERO.setScale(2);
                lines.addAll(headerBlock());
                detailCount = 0;
            }
        }
        if (!lines.isEmpty()) {
            lines.add(accountTotal(accountTotal));
            lines.add(separator());
            if (detailCount > 0) {
                lines.add(pageTotal(pageTotal));
            }
            lines.add(grandTotal(grandTotal));
        }
        return lines;
    }

    private List<String> headerBlock() {
        return List.of(
                reportNameHeader(),
                " ".repeat(REPORT_WIDTH),
                headerOne(),
                separator());
    }

    private String reportNameHeader() {
        return record(field("DALYREPT", 38)
                + field("Daily Transaction Report", 41)
                + field("Date Range: ", 12)
                + field(startDate, 10)
                + field(" to ", 4)
                + field(endDate, 10));
    }

    private String headerOne() {
        return record(field("Transaction ID", 17)
                + field("Account ID", 12)
                + field("Transaction Type", 19)
                + field("Tran Category", 35)
                + field("Tran Source", 14)
                + " "
                + field("        Amount", 16));
    }

    private String detail(Transaction transaction) {
        CardXref xref = xrefsByCard.get(transaction.getCardNumber());
        TransactionType type = typesByCode.get(transaction.getTypeCode());
        TransactionCategory category = categoriesById.get(
                new TransactionCategoryId(
                        transaction.getTypeCode(),
                        transaction.getCategoryCode()));
        String accountId = xref == null ? "" : String.valueOf(xref.getAccountId());
        String typeDescription = type == null
                ? "INVALID TRANSACTION TYPE"
                : type.getDescription();
        String categoryDescription = category == null
                ? "INVALID TRAN CATG KEY"
                : category.getDescription();
        return record(field(transaction.getId(), 16)
                + " "
                + field(accountId, 11)
                + " "
                + field(transaction.getTypeCode(), 2)
                + "-"
                + field(typeDescription, 15)
                + " "
                + String.format("%04d", transaction.getCategoryCode())
                + "-"
                + field(categoryDescription, 29)
                + " "
                + field(transaction.getSource(), 10)
                + "    "
                + CobolPicture.signedLeading(transaction.getAmount())
                + "  ");
    }

    private String pageTotal(BigDecimal amount) {
        return record(field("Page Total", 11)
                + ".".repeat(86)
                + CobolPicture.signedLeadingAlways(amount));
    }

    private String accountTotal(BigDecimal amount) {
        return record(field("Account Total", 13)
                + ".".repeat(84)
                + CobolPicture.signedLeadingAlways(amount));
    }

    private String grandTotal(BigDecimal amount) {
        return record(field("Grand Total", 11)
                + ".".repeat(86)
                + CobolPicture.signedLeadingAlways(amount));
    }

    private static String separator() {
        return "-".repeat(REPORT_WIDTH);
    }

    private static String record(String value) {
        return field(value, REPORT_WIDTH);
    }

    private static String field(String value, int width) {
        String normalized = value == null ? "" : value;
        if (normalized.length() > width) {
            return normalized.substring(0, width);
        }
        return normalized + " ".repeat(width - normalized.length());
    }
}
