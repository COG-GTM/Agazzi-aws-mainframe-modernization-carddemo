package com.carddemo.copybook.layout;

import java.math.BigDecimal;
import java.util.List;

/**
 * A level-88 condition name attached to an item.
 *
 * @param name condition name, e.g. {@code PA-AUTH-APPROVED}
 * @param values the {@code VALUE}/{@code VALUES} entries, each a single literal or a {@code THRU}
 *     range
 */
public record Condition(String name, List<Range> values) {

  public Condition {
    values = List.copyOf(values);
  }

  /**
   * One {@code VALUE} entry: a literal ({@code from == to}) or an inclusive range. Literals are
   * kept as written: quoted alphanumerics ({@code 'A'}), numerics ({@code 20}) or figurative
   * constants ({@code SPACES}, {@code LOW-VALUES}, {@code HIGH-VALUES}, {@code ZEROS}).
   */
  public record Range(String from, String to) {
    public boolean single() {
      return from.equals(to);
    }
  }

  /** Whether every entry is a single quoted literal (eligible for an enum). */
  public boolean singleLiteral() {
    return values.size() == 1 && values.get(0).single() && values.get(0).from().startsWith("'");
  }

  /**
   * Tests a decoded field value against this condition, with COBOL comparison semantics:
   * alphanumerics are compared after padding the literal with spaces to {@code length}, numerics by
   * value.
   */
  public boolean test(Object value, int length) {
    for (Range r : values) {
      if (value instanceof String s) {
        String from = alphanumeric(r.from(), length);
        String to = alphanumeric(r.to(), length);
        if (s.compareTo(from) >= 0 && s.compareTo(to) <= 0) {
          return true;
        }
      } else if (value != null) {
        BigDecimal v = new BigDecimal(value.toString());
        if (v.compareTo(numeric(r.from())) >= 0 && v.compareTo(numeric(r.to())) <= 0) {
          return true;
        }
      }
    }
    return false;
  }

  /** Alphanumeric value of a literal, padded with spaces (or repeated, for figuratives). */
  public static String alphanumeric(String literal, int length) {
    String fill =
        switch (literal) {
          case "SPACE", "SPACES" -> " ";
          case "LOW-VALUE", "LOW-VALUES" -> "\u0000";
          case "HIGH-VALUE", "HIGH-VALUES" -> "\u00ff";
          case "ZERO", "ZEROS", "ZEROES" -> "0";
          default -> null;
        };
    if (fill != null) {
      return fill.repeat(length);
    }
    String text =
        literal.length() >= 2 && (literal.startsWith("'") || literal.startsWith("\""))
            ? literal.substring(1, literal.length() - 1)
            : literal;
    StringBuilder sb = new StringBuilder(text);
    while (sb.length() < length) {
      sb.append(' ');
    }
    return sb.toString();
  }

  private static BigDecimal numeric(String literal) {
    return switch (literal) {
      case "ZERO", "ZEROS", "ZEROES", "LOW-VALUE", "LOW-VALUES" -> BigDecimal.ZERO;
      default -> new BigDecimal(literal.replace("'", "").replace("+", "").trim());
    };
  }
}
