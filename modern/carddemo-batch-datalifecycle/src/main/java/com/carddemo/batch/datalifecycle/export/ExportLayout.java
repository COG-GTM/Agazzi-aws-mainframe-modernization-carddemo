package com.carddemo.batch.datalifecycle.export;

import static com.carddemo.batch.datalifecycle.cobol.CobolData.binary;
import static com.carddemo.batch.datalifecycle.cobol.CobolData.packed;
import static com.carddemo.batch.datalifecycle.cobol.CobolData.putBinary;
import static com.carddemo.batch.datalifecycle.cobol.CobolData.putPacked;
import static com.carddemo.batch.datalifecycle.cobol.CobolData.putZoned;
import static com.carddemo.batch.datalifecycle.cobol.CobolData.zoned;

import java.util.List;
import java.util.Optional;

/**
 * Minimal local codec for the CVEXPORT multi-record layout (500 bytes): a 40-byte header followed
 * by the REDEFINES payload of the record type. Stands in for the sealed {@code ExportRecord} of
 * workstream 1A until that type is merged.
 */
public enum ExportLayout {
  CUSTOMER(
      'C',
      500,
      List.of(
          Field.binary(0, 9, 0, 4, false),
          Field.copy(9, 4, 25),
          Field.copy(34, 29, 25),
          Field.copy(59, 54, 25),
          Field.copy(84, 79, 150),
          Field.copy(234, 229, 2),
          Field.copy(236, 231, 3),
          Field.copy(239, 234, 10),
          Field.copy(249, 244, 30),
          Field.copy(279, 274, 9),
          Field.copy(288, 283, 20),
          Field.copy(308, 303, 10),
          Field.copy(318, 313, 10),
          Field.copy(328, 323, 1),
          Field.packed(329, 3, 0, 324, false))),
  ACCOUNT(
      'A',
      300,
      List.of(
          Field.copy(0, 0, 11),
          Field.copy(11, 11, 1),
          Field.packed(12, 12, 2, 12, true),
          Field.copy(24, 19, 12),
          Field.packed(36, 12, 2, 31, true),
          Field.copy(48, 38, 30),
          Field.copy(78, 68, 12),
          Field.binary(90, 12, 2, 80, 8, true),
          Field.copy(102, 88, 20))),
  XREF(
      'X',
      50,
      List.of(Field.copy(0, 0, 16), Field.copy(16, 16, 9), Field.binary(25, 11, 0, 25, 8, false))),
  TRANSACTION(
      'T',
      350,
      List.of(
          Field.copy(0, 0, 132),
          Field.packed(132, 11, 2, 132, true),
          Field.binary(143, 9, 0, 138, 4, false),
          Field.copy(152, 142, 178))),
  CARD(
      'D',
      150,
      List.of(
          Field.copy(0, 0, 16),
          Field.binary(16, 11, 0, 16, 8, false),
          Field.binary(27, 3, 0, 24, 2, false),
          Field.copy(30, 26, 61)));

  public static final int RECORD_LENGTH = 500;
  public static final int PAYLOAD_OFFSET = 40;
  public static final int SEQUENCE_OFFSET = 27;

  private final char type;
  private final int legacyLength;
  private final List<Field> fields;

  ExportLayout(char type, int legacyLength, List<Field> fields) {
    this.type = type;
    this.legacyLength = legacyLength;
    this.fields = fields;
  }

  public char type() {
    return type;
  }

  public int legacyLength() {
    return legacyLength;
  }

  public static Optional<ExportLayout> of(byte recordType) {
    for (ExportLayout l : values()) {
      if (l.type == (char) (recordType & 0xFF)) {
        return Optional.of(l);
      }
    }
    return Optional.empty();
  }

  /** CBEXPORT 2200/3200/4200/5200/5600: legacy record into the export payload. */
  public void export(byte[] legacy, byte[] export) {
    for (Field f : fields) {
      int dst = PAYLOAD_OFFSET + f.exportOffset;
      switch (f.kind) {
        case COPY -> System.arraycopy(legacy, f.legacyOffset, export, dst, f.size);
        case PACKED ->
            putPacked(
                export,
                dst,
                zoned(legacy, f.legacyOffset, f.size, f.scale),
                f.size,
                f.scale,
                f.signed);
        case BINARY ->
            putBinary(
                export, dst, f.bytes, zoned(legacy, f.legacyOffset, f.size, f.scale), f.scale);
        default -> throw new IllegalStateException(f.kind.name());
      }
    }
  }

  /** CBIMPORT 2300-2650: export payload into the legacy record (numeric fields re-zoned). */
  public void restore(byte[] export, byte[] legacy) {
    for (Field f : fields) {
      int src = PAYLOAD_OFFSET + f.exportOffset;
      switch (f.kind) {
        case COPY -> System.arraycopy(export, src, legacy, f.legacyOffset, f.size);
        case PACKED ->
            putZoned(
                legacy,
                f.legacyOffset,
                packed(export, src, f.size / 2 + 1, f.scale),
                f.size,
                f.scale,
                f.signed);
        case BINARY ->
            putZoned(
                legacy,
                f.legacyOffset,
                binary(export, src, f.bytes, f.scale, f.signed),
                f.size,
                f.scale,
                f.signed);
        default -> throw new IllegalStateException(f.kind.name());
      }
    }
  }

  enum Kind {
    COPY,
    PACKED,
    BINARY
  }

  /** One mapped field; {@code size} is the byte length for COPY, the digit count otherwise. */
  record Field(
      Kind kind,
      int legacyOffset,
      int size,
      int scale,
      int exportOffset,
      int bytes,
      boolean signed) {

    static Field copy(int legacyOffset, int exportOffset, int length) {
      return new Field(Kind.COPY, legacyOffset, length, 0, exportOffset, length, false);
    }

    static Field packed(int legacyOffset, int digits, int scale, int exportOffset, boolean signed) {
      return new Field(
          Kind.PACKED, legacyOffset, digits, scale, exportOffset, digits / 2 + 1, signed);
    }

    static Field binary(int legacyOffset, int digits, int exportOffset, int bytes, boolean signed) {
      return new Field(Kind.BINARY, legacyOffset, digits, 0, exportOffset, bytes, signed);
    }

    static Field binary(
        int legacyOffset, int digits, int scale, int exportOffset, int bytes, boolean signed) {
      return new Field(Kind.BINARY, legacyOffset, digits, scale, exportOffset, bytes, signed);
    }
  }
}
