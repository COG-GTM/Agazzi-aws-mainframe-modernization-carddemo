package com.carddemo.batch;

import com.carddemo.domain.Card;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.Customer;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.core.io.FileSystemResource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

final class ReportSupport {

    private ReportSupport() {
    }

    static FlatFileItemWriter<String> writer(Path output) {
        return new FlatFileItemWriterBuilder<String>()
                .name("reportWriter-" + output.getFileName())
                .resource(new FileSystemResource(output))
                .lineAggregator(item -> item)
                .shouldDeleteIfExists(true)
                .build();
    }

    static List<String> banner(String program, String... lines) {
        return Stream.concat(
                        Stream.of(
                                "START OF EXECUTION OF PROGRAM " + program),
                        Stream.concat(
                                Arrays.stream(lines),
                                Stream.of(
                                        "END OF EXECUTION OF PROGRAM " + program)))
                .toList();
    }

    static String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2).toPlainString();
    }

    static String cardRecord(Card card) {
        return fixed(card.getCardNumber(), 16)
                + fixed(String.valueOf(card.getAccountId()), 11)
                + fixed(String.valueOf(card.getCvvCode()), 3)
                + fixed(card.getEmbossedName(), 50)
                + fixed(card.getExpirationDate(), 10)
                + fixed(card.getActiveStatus(), 1)
                + " ".repeat(59);
    }

    static String xrefRecord(CardXref xref) {
        return fixed(xref.getCardNumber(), 16)
                + fixed(String.valueOf(xref.getCustomerId()), 9)
                + fixed(String.valueOf(xref.getAccountId()), 11)
                + " ".repeat(14);
    }

    static String customerRecord(Customer customer) {
        return fixed(String.valueOf(customer.getCustomerId()), 9)
                + fixed(customer.getFirstName(), 25)
                + fixed(customer.getMiddleName(), 25)
                + fixed(customer.getLastName(), 25)
                + fixed(customer.getAddressLine1(), 50)
                + fixed(customer.getAddressLine2(), 50)
                + fixed(customer.getAddressLine3(), 50)
                + fixed(customer.getStateCode(), 2)
                + fixed(customer.getCountryCode(), 3)
                + fixed(customer.getZip(), 10)
                + fixed(customer.getPhone1(), 15)
                + fixed(customer.getPhone2(), 15)
                + fixed(String.valueOf(customer.getSsn()), 9)
                + fixed(customer.getGovtIssuedId(), 20)
                + fixed(customer.getDateOfBirth(), 10)
                + fixed(customer.getEftAccountId(), 10)
                + fixed(customer.getPrimaryCardHolder(), 1)
                + fixed(String.valueOf(customer.getFicoCreditScore()), 3)
                + " ".repeat(168);
    }

    private static String fixed(String value, int width) {
        String normalized = value == null ? "" : value;
        if (normalized.length() > width) {
            return normalized.substring(0, width);
        }
        return normalized + " ".repeat(width - normalized.length());
    }
}
