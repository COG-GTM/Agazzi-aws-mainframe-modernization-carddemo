package com.carddemo.domain.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ZonedDecimalCodec {

    private ZonedDecimalCodec() {
    }

    public static BigDecimal decode(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO.setScale(2);
        }

        String encoded = value.trim();
        int sign = 1;
        if (encoded.startsWith("+") || encoded.startsWith("-")) {
            if (encoded.charAt(0) == '-') {
                sign = -1;
            }
            encoded = encoded.substring(1);
        }

        char last = encoded.charAt(encoded.length() - 1);
        if (last == '{') {
            last = '0';
        } else if (last == '}') {
            last = '0';
            sign = -1;
        } else if (last >= 'A' && last <= 'I') {
            last = (char) ('0' + last - 'A' + 1);
        } else if (last >= 'J' && last <= 'R') {
            last = (char) ('0' + last - 'J' + 1);
            sign = -1;
        }
        if (!Character.isDigit(last)) {
            throw new IllegalArgumentException("Invalid zoned decimal: " + value);
        }

        String digits = encoded.substring(0, encoded.length() - 1) + last;
        if (!digits.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Invalid zoned decimal: " + value);
        }
        return BigDecimal.valueOf(sign)
                .multiply(new BigDecimal(digits))
                .movePointLeft(2)
                .setScale(2);
    }

    public static String encode(BigDecimal number, int length) {
        BigDecimal value = number == null ? BigDecimal.ZERO : number;
        String digits = value.setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .abs()
                .toBigIntegerExact()
                .toString();
        if (digits.length() > length) {
            throw new IllegalArgumentException("Value does not fit " + length);
        }

        digits = "0".repeat(length - digits.length()) + digits;
        int lastIndex = digits.length() - 1;
        int lastDigit = digits.charAt(lastIndex) - '0';
        char overpunch = value.signum() < 0
                ? (lastDigit == 0 ? '}' : (char) ('J' + lastDigit - 1))
                : (lastDigit == 0 ? '{' : (char) ('A' + lastDigit - 1));
        return digits.substring(0, lastIndex) + overpunch;
    }
}
