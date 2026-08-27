package com.carddemo.migration;

import com.carddemo.domain.util.ZonedDecimalCodec;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Declarative fixed-width field layout.
 *
 * <p>Missing trailing source columns are read as blank. Formatting writes
 * the configured record length and leaves unspecified fields blank.</p>
 */
public record FixedWidthLayout(int recordLength, List<FieldSpec> fields) {

    public record FieldSpec(String name, int offset, int length, Kind kind) {
    }

    public enum Kind {
        TEXT,
        INTEGER,
        LONG,
        MONEY
    }

    public String read(String line, FieldSpec field) {
        int start = Math.min(field.offset(), line.length());
        int end = Math.min(start + field.length(), line.length());
        return line.substring(start, end);
    }

    public String read(String line, String name) {
        return read(line, field(name));
    }

    public FieldSpec field(String name) {
        return fields.stream()
                .filter(field -> field.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown field: " + name));
    }

    public String format(Map<String, ?> values) {
        char[] record = new char[recordLength];
        Arrays.fill(record, ' ');
        for (FieldSpec field : fields) {
            Object value = values.get(field.name());
            if (value == null) {
                continue;
            }
            String encoded = encode(field, value);
            if (encoded.length() > field.length()) {
                throw new IllegalArgumentException("Value does not fit field: " + field.name());
            }
            int start = field.kind() == Kind.TEXT
                    ? field.offset()
                    : field.offset() + field.length() - encoded.length();
            encoded.getChars(0, encoded.length(), record, start);
        }
        return new String(record);
    }

    private String encode(FieldSpec field, Object value) {
        return switch (field.kind()) {
            case TEXT -> value.toString();
            case INTEGER, LONG -> String.format(
                    Locale.ROOT,
                    "%0" + field.length() + "d",
                    ((Number) value).longValue());
            case MONEY -> ZonedDecimalCodec.encode((BigDecimal) value, field.length());
        };
    }

    public static FieldSpec text(String name, int offset, int length) {
        return new FieldSpec(name, offset, length, Kind.TEXT);
    }

    public static FieldSpec integer(String name, int offset, int length) {
        return new FieldSpec(name, offset, length, Kind.INTEGER);
    }

    public static FieldSpec longField(String name, int offset, int length) {
        return new FieldSpec(name, offset, length, Kind.LONG);
    }

    public static FieldSpec money(String name, int offset, int length) {
        return new FieldSpec(name, offset, length, Kind.MONEY);
    }

    public static FixedWidthLayout of(FieldSpec... fields) {
        int recordLength = Arrays.stream(fields)
                .mapToInt(field -> field.offset() + field.length())
                .max()
                .orElse(0);
        return of(recordLength, fields);
    }

    public static FixedWidthLayout of(int recordLength, FieldSpec... fields) {
        return new FixedWidthLayout(recordLength, List.of(fields));
    }
}
