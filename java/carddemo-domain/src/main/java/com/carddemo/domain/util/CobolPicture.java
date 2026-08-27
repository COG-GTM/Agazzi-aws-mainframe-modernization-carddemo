package com.carddemo.domain.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Formats the edited numeric pictures used by the COBOL reports.
 */
public final class CobolPicture {

    private CobolPicture() {
    }

    public static String signedLeading(BigDecimal value) {
        return grouped(value, false);
    }

    public static String signedLeadingAlways(BigDecimal value) {
        return grouped(value, true);
    }

    public static String signedTrailing(BigDecimal value) {
        return trailing(value, true);
    }

    public static String unsignedTrailingSign(BigDecimal value) {
        return trailing(value, false);
    }

    private static String grouped(BigDecimal value, boolean alwaysLeadingSign) {
        BigDecimal normalized = (value == null ? BigDecimal.ZERO : value)
                .setScale(2, RoundingMode.DOWN);
        if (normalized.signum() == 0) {
            return " ".repeat(15);
        }
        boolean negative = normalized.signum() < 0;
        String digits = normalized.abs().movePointRight(2).toBigIntegerExact()
                .toString();
        if (digits.length() > 11) {
            throw new IllegalArgumentException("Value exceeds COBOL picture width");
        }
        digits = "0".repeat(11 - digits.length()) + digits;
        String integer = digits.substring(0, 9);
        String fraction = digits.substring(9);
        integer = groupedInteger(suppress(integer));
        String body = integer + "." + fraction;
        return (negative ? "-" : (alwaysLeadingSign ? "+" : " ")) + body;
    }

    private static String trailing(BigDecimal value, boolean suppressLeadingZeroes) {
        BigDecimal normalized = (value == null ? BigDecimal.ZERO : value)
                .setScale(2, RoundingMode.DOWN);
        boolean negative = normalized.signum() < 0;
        String digits = normalized.abs().movePointRight(2).toBigIntegerExact()
                .toString();
        if (digits.length() > 11) {
            throw new IllegalArgumentException("Value exceeds COBOL picture width");
        }
        digits = "0".repeat(11 - digits.length()) + digits;
        String integer = digits.substring(0, 9);
        if (suppressLeadingZeroes) {
            integer = suppress(integer);
        }
        return integer + "." + digits.substring(9) + (negative ? "-" : " ");
    }

    private static String suppress(String digits) {
        int firstSignificant = -1;
        for (int index = 0; index < digits.length(); index++) {
            if (digits.charAt(index) != '0') {
                firstSignificant = index;
                break;
            }
        }
        if (firstSignificant < 0) {
            return " ".repeat(digits.length());
        }
        return " ".repeat(firstSignificant) + digits.substring(firstSignificant);
    }

    private static String groupedInteger(String digits) {
        String grouped = digits.substring(0, 3) + ","
                + digits.substring(3, 6) + ","
                + digits.substring(6);
        char[] result = grouped.toCharArray();
        if (allSpaces(digits.substring(0, 3))) {
            result[3] = ' ';
        }
        if (allSpaces(digits.substring(0, 6))) {
            result[7] = ' ';
        }
        return new String(result);
    }

    private static boolean allSpaces(String value) {
        return value.chars().allMatch(character -> character == ' ');
    }
}
