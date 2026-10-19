package com.carddemo.copybook.layout;

import java.util.Locale;

/**
 * Parsed {@code PIC} clause.
 *
 * @param source normalized picture string (upper case, as written in the copybook)
 * @param category alphanumeric, numeric or numeric-edited
 * @param digits number of digit positions (numeric only)
 * @param scale digits after the implied decimal point {@code V}
 * @param signed whether the picture has an operational sign {@code S}
 * @param displayLength length in bytes when {@code USAGE DISPLAY}
 */
public record Picture(
    String source, Category category, int digits, int scale, boolean signed, int displayLength) {

  /** Picture category. */
  public enum Category {
    ALPHANUMERIC,
    NUMERIC,
    NUMERIC_EDITED
  }

  public boolean numeric() {
    return category == Category.NUMERIC;
  }

  /** Storage length in bytes for the given usage. */
  public int storageLength(Usage usage) {
    if (!numeric()) {
      return displayLength;
    }
    return switch (usage) {
      case DISPLAY -> displayLength;
      case PACKED -> digits / 2 + 1;
      case BINARY, NATIVE_BINARY -> digits <= 4 ? 2 : digits <= 9 ? 4 : 8;
    };
  }

  /**
   * Parses a COBOL picture string such as {@code S9(10)V99}, {@code X(25)} or {@code +9(10).99}.
   */
  public static Picture parse(String pic) {
    String p = pic.toUpperCase(Locale.ROOT);
    StringBuilder expanded = new StringBuilder();
    for (int i = 0; i < p.length(); i++) {
      char c = p.charAt(i);
      if (c == '(') {
        int close = p.indexOf(')', i);
        if (close < 0 || expanded.isEmpty()) {
          throw new IllegalArgumentException("Malformed PIC " + pic);
        }
        int n = Integer.parseInt(p.substring(i + 1, close).trim());
        char last = expanded.charAt(expanded.length() - 1);
        expanded.append(String.valueOf(last).repeat(n - 1));
        i = close;
      } else {
        expanded.append(c);
      }
    }
    String e = expanded.toString();
    boolean alpha = false;
    boolean edited = false;
    boolean signed = false;
    boolean afterPoint = false;
    int digits = 0;
    int scale = 0;
    int length = 0;
    for (int i = 0; i < e.length(); i++) {
      char c = e.charAt(i);
      switch (c) {
        case 'X', 'A' -> {
          alpha = true;
          length++;
        }
        case '9' -> {
          digits++;
          length++;
          if (afterPoint) {
            scale++;
          }
        }
        case 'S' -> signed = true;
        case 'V' -> afterPoint = true;
        case 'P' -> digits++;
        case 'Z', '*', '+', '-', '$', '.', ',', 'B', '0', '/' -> {
          edited = true;
          length++;
          if (c == '.') {
            afterPoint = true;
          } else if ((c == 'Z' || c == '*') && afterPoint) {
            scale++;
          }
        }
        case 'C', 'D', 'R' -> {
          edited = true;
          length++;
        }
        default ->
            throw new IllegalArgumentException("Unsupported PIC symbol '" + c + "' in " + pic);
      }
    }
    Category category =
        alpha ? Category.ALPHANUMERIC : edited ? Category.NUMERIC_EDITED : Category.NUMERIC;
    if (digits > 18 && category == Category.NUMERIC) {
      throw new IllegalArgumentException("PIC " + pic + " exceeds 18 digits");
    }
    return new Picture(p, category, digits, scale, signed, length);
  }
}
