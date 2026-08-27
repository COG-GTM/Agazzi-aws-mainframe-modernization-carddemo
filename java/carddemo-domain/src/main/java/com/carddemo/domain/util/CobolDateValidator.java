package com.carddemo.domain.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

/**
 * Java equivalent of the CEEDAYS validation performed by CSUTLDTC.
 *
 * <p>The LE runtime's internal era and supported-range diagnostics cannot be
 * reproduced exactly with {@code java.time}; those cases are mapped to the
 * general invalid-date result.</p>
 */
public final class CobolDateValidator {

    private static final String MASK = "YYYY-MM-DD";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT);

    private CobolDateValidator() {
    }

    public record Result(int severity, int messageNumber, String resultText, String message) {
    }

    public static Result validate(String date, String mask) {
        String value = date == null ? "" : date;
        String format = mask == null ? "" : mask;
        int severity = 0;
        int messageNumber = 0;
        String result = result("Date is valid");

        if (value.isBlank() || value.length() < MASK.length()) {
            severity = 3;
            messageNumber = 2507;
            result = result("Insufficient");
        } else if (!MASK.equals(format)) {
            severity = 3;
            messageNumber = 2518;
            result = result("Bad Pic String");
        } else if (hasNonNumericDatePart(value)) {
            severity = 3;
            messageNumber = 2520;
            result = result("Nonnumeric data");
        } else if (!hasDateShape(value)) {
            severity = 3;
            messageNumber = 2508;
            result = result("Datevalue error");
        } else if ("0000".equals(value.substring(0, 4))) {
            severity = 3;
            messageNumber = 2521;
            result = result("YearInEra is 0");
        } else {
            try {
                LocalDate.parse(value, DATE_FORMATTER);
            } catch (DateTimeParseException exception) {
                int month = Integer.parseInt(value.substring(5, 7));
                severity = 3;
                if (month < 1 || month > 12) {
                    messageNumber = 2517;
                    result = result("Invalid month");
                } else {
                    messageNumber = 2508;
                    result = result("Datevalue error");
                }
            }
        }

        return new Result(
                severity,
                messageNumber,
                result,
                formatMessage(severity, messageNumber, result, value, format));
    }

    private static boolean hasNonNumericDatePart(String value) {
        int[] digitPositions = {0, 1, 2, 3, 5, 6, 8, 9};
        for (int position : digitPositions) {
            if (position >= value.length() || !Character.isDigit(value.charAt(position))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDateShape(String value) {
        return value.length() == 10
                && value.charAt(4) == '-'
                && value.charAt(7) == '-';
    }

    private static String result(String value) {
        return String.format(Locale.ROOT, "%-15s", value);
    }

    private static String formatMessage(
            int severity,
            int messageNumber,
            String result,
            String date,
            String mask) {
        return String.format(
                Locale.ROOT,
                "%04d%-11s%04d %-15s %-9s%-10s %-10s%-10s %s",
                severity,
                "Mesg Code: ",
                messageNumber,
                result,
                "TstDate: ",
                fit(date, 10),
                "Mask used:",
                fit(mask, 10),
                "   ");
    }

    private static String fit(String value, int width) {
        if (value.length() >= width) {
            return value.substring(0, width);
        }
        return String.format(Locale.ROOT, "%-" + width + "s", value);
    }
}
