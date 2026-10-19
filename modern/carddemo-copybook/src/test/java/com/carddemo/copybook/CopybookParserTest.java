package com.carddemo.copybook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.copybook.codec.CodecOptions;
import com.carddemo.copybook.codec.LayoutCodec;
import com.carddemo.copybook.layout.LayoutNode;
import com.carddemo.copybook.layout.RecordLayout;
import com.carddemo.copybook.layout.Usage;
import com.carddemo.copybook.parser.CopybookParser;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CopybookParserTest {

  static String cb(String... lines) {
    return Arrays.stream(lines).map(l -> "      " + " " + l).collect(Collectors.joining("\n"));
  }

  static LayoutNode node(RecordLayout l, String name) {
    return l.find(name).orElseThrow();
  }

  @Test
  void usagesAndOffsets() {
    RecordLayout l =
        CopybookParser.parse(
                "T",
                cb(
                    "01 REC.",
                    "   05 A PIC X(3).",
                    "   05 B PIC S9(4) COMP.",
                    "   05 C PIC S9(9) BINARY.",
                    "   05 D PIC S9(18) COMP.",
                    "   05 E PIC S9(7)V99 COMP-3.",
                    "   05 F PIC 9(4) USAGE IS COMP-5.",
                    "   05 G PIC -ZZZ,ZZ9.99.",
                    "   05 FILLER PIC X(2)."))
            .get(0);
    assertThat(l.length()).isEqualTo(3 + 2 + 4 + 8 + 5 + 2 + 11 + 2);
    assertThat(node(l, "B").offset()).isEqualTo(3);
    assertThat(node(l, "C").length()).isEqualTo(4);
    assertThat(node(l, "D").offset()).isEqualTo(9);
    assertThat(node(l, "E").usage()).isEqualTo(Usage.PACKED);
    assertThat(node(l, "E").picture().scale()).isEqualTo(2);
    assertThat(node(l, "F").usage()).isEqualTo(Usage.NATIVE_BINARY);
    assertThat(node(l, "G").length()).isEqualTo(11);
  }

  @Test
  void occursRedefinesAndConditions() {
    RecordLayout l =
        CopybookParser.parse(
                "T",
                cb(
                    "01 REC.",
                    "   05 KIND PIC X.",
                    "      88 IS-A VALUE 'A'.",
                    "      88 IS-LETTER VALUES 'B' THRU 'Z'.",
                    "   05 BODY PIC X(6).",
                    "   05 BODY-R REDEFINES BODY.",
                    "      10 PAIR OCCURS 3 TIMES.",
                    "         15 P1 PIC X.",
                    "         15 P2 PIC 9.",
                    "   05 TAIL PIC X(2)."))
            .get(0);
    assertThat(l.length()).isEqualTo(9);
    assertThat(node(l, "BODY-R").offset()).isEqualTo(1);
    assertThat(node(l, "PAIR").occurs()).isEqualTo(3);
    assertThat(node(l, "TAIL").offset()).isEqualTo(7);
    assertThat(l.condition("IS-A").test("A", 1)).isTrue();
    assertThat(l.condition("IS-LETTER").test("Q", 1)).isTrue();
    assertThat(l.condition("IS-LETTER").test("A", 1)).isFalse();
    RecordLayout v = l.variant("BODY", "BODY-R");
    assertThat(v.root().storageChildren())
        .extracting(LayoutNode::name)
        .containsExactly("KIND", "BODY-R", "TAIL");
  }

  record Item(String code) {}

  record Odo(int cnt, List<Item> items) {}

  @Test
  void occursDependingOnIsVariableLength() {
    RecordLayout l =
        CopybookParser.parse(
                "T",
                cb(
                    "01 ODO-REC.",
                    "   05 CNT PIC 9(2).",
                    "   05 ITEMS OCCURS 1 TO 5 TIMES DEPENDING ON CNT.",
                    "      10 CODE PIC X(3)."))
            .get(0);
    assertThat(l.variableLength()).isTrue();
    assertThat(l.length()).isEqualTo(17);
    LayoutCodec<Odo> codec = LayoutCodec.of(l, Odo.class, CodecOptions.ASCII);
    byte[] rec = "02AAABBB".getBytes(StandardCharsets.ISO_8859_1);
    Odo o = codec.decode(rec);
    assertThat(o).isEqualTo(new Odo(2, List.of(new Item("AAA"), new Item("BBB"))));
    assertThat(codec.encode(o)).isEqualTo(rec);
  }

  record Packed(BigDecimal amt, int cnt, String name) {}

  @Test
  void ebcdicRoundTripOfMixedUsages() {
    RecordLayout l =
        CopybookParser.parse(
                "T",
                cb(
                    "01 R.",
                    "   05 AMT PIC S9(5)V99 COMP-3.",
                    "   05 CNT PIC S9(4) COMP.",
                    "   05 NAME PIC X(5)."))
            .get(0);
    LayoutCodec<Packed> codec = LayoutCodec.of(l, Packed.class, CodecOptions.EBCDIC);
    Packed p = new Packed(new BigDecimal("-123.45"), -7, "Zoë");
    byte[] b = codec.encode(p);
    assertThat(b).hasSize(4 + 2 + 5);
    assertThat(Arrays.copyOfRange(b, 0, 4)).containsExactly(0x00, 0x12, 0x34, 0x5D);
    assertThat(Arrays.copyOfRange(b, 4, 6)).containsExactly(0xFF, 0xF9);
    assertThat(Arrays.copyOfRange(b, 6, 11)).containsExactly(0xE9, 0x96, 0x53, 0x40, 0x40);
    assertThat(codec.decode(b)).isEqualTo(new Packed(new BigDecimal("-123.45"), -7, "Zoë  "));
  }

  @Test
  void longerRedefinitionIsRejected() {
    assertThatThrownBy(
            () ->
                CopybookParser.parse(
                    "T",
                    cb(
                        "01 R.",
                        "   05 A PIC X(2).",
                        "   05 B REDEFINES A PIC X(4).",
                        "   05 C PIC X.")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("larger than A");
  }

  @Test
  void decodeRejectsWrongLengthsAndUncountedOdoPayload() {
    RecordLayout fixed = CopybookParser.parse("T", cb("01 R.", "   05 A PIC X(3).")).get(0);
    LayoutCodec<Item> codec = LayoutCodec.of(fixed, Item.class, CodecOptions.ASCII);
    assertThatThrownBy(() -> codec.decode("ABCD".getBytes(StandardCharsets.ISO_8859_1)))
        .isInstanceOf(IllegalArgumentException.class);
    RecordLayout odo =
        CopybookParser.parse(
                "T",
                cb(
                    "01 ODO-REC.",
                    "   05 CNT PIC 9(2).",
                    "   05 ITEMS OCCURS 1 TO 5 TIMES DEPENDING ON CNT.",
                    "      10 CODE PIC X(3)."))
            .get(0);
    LayoutCodec<Odo> oc = LayoutCodec.of(odo, Odo.class, CodecOptions.ASCII);
    assertThatThrownBy(() -> oc.decode("01AAABBB".getBytes(StandardCharsets.ISO_8859_1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void highValuesConditionMatchesInBothCharsets() {
    RecordLayout l =
        CopybookParser.parse(
                "T", cb("01 R.", "   05 F PIC X(2).", "      88 F-HIGH VALUE HIGH-VALUES."))
            .get(0);
    byte[] ff = {(byte) 0xFF, (byte) 0xFF};
    byte[] df = {(byte) 0xDF, (byte) 0xDF};
    byte[] x9f = {(byte) 0x9F, (byte) 0x9F};
    for (CodecOptions o : List.of(CodecOptions.ASCII, CodecOptions.EBCDIC)) {
      LayoutCodec<Item> codec = LayoutCodec.of(l, Item.class, o);
      assertThat(l.condition("F-HIGH").test(codec.decode(ff).code(), 2, o.charset()))
          .as(o.toString())
          .isTrue();
    }
    var ebcdic = LayoutCodec.of(l, Item.class, CodecOptions.EBCDIC);
    var ascii = LayoutCodec.of(l, Item.class, CodecOptions.ASCII);
    assertThat(
            l.condition("F-HIGH").test(ebcdic.decode(df).code(), 2, CodecOptions.EBCDIC.charset()))
        .isFalse();
    assertThat(l.condition("F-HIGH").test(ascii.decode(x9f).code(), 2)).isFalse();
    assertThat(l.condition("F-HIGH").test("  ", 2)).isFalse();
  }

  @Test
  void groupLevelConditionsAreGenerated() {
    var valid =
        new com.carddemo.copybook.generated.core.DateEditWorkArea.WsEditDateFlgs("\0", "\0", "\0");
    assertThat(valid.isWsEditDateIsValid()).isTrue();
    assertThat(valid.isWsEditDateIsInvalid()).isFalse();
    var invalid =
        new com.carddemo.copybook.generated.core.DateEditWorkArea.WsEditDateFlgs("0", "0", "0");
    assertThat(invalid.isWsEditDateIsInvalid()).isTrue();
    assertThat(invalid.isWsEditDateIsValid()).isFalse();
  }

  @Test
  void floatingPointUsageIsRejected() {
    assertThatThrownBy(() -> CopybookParser.parse("T", cb("01 R.", "   05 X COMP-2.")))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void contractsAnnotationsMatchTheCopybooks() throws Exception {
    Map<String, RecordCodec<?>> all = CoreCodecs.all();
    assertThat(all).hasSize(11);
    for (Map.Entry<String, RecordCodec<?>> e : all.entrySet()) {
      String text =
          Files.readString(
              TestPaths.app().resolve("cpy/" + e.getKey() + ".cpy"), StandardCharsets.ISO_8859_1);
      RecordLayout parsed = CopybookParser.parse(e.getKey(), text).get(0);
      ZonedRecordCodec<?> codec = (ZonedRecordCodec<?>) e.getValue();
      assertThat(codec.recordLength()).as(e.getKey()).isEqualTo(parsed.length());
      for (ZonedRecordCodec.FieldLayout f : codec.fields()) {
        LayoutNode n = parsed.find(f.name()).orElseThrow(() -> new AssertionError(f.name()));
        assertThat(n.offset()).as("%s.%s offset", e.getKey(), f.name()).isEqualTo(f.offset());
        assertThat(n.length()).as("%s.%s length", e.getKey(), f.name()).isEqualTo(f.length());
        assertThat(n.usage()).isEqualTo(Usage.DISPLAY);
      }
    }
  }

  @Test
  void ebcdicCoreCodecsRoundTrip() {
    byte[] all =
        TestPaths.bytes(TestPaths.app().resolve("data/EBCDIC/AWS.M2.CARDDEMO.ACCTDATA.PS"));
    var codec = ZonedRecordCodec.ebcdic(com.carddemo.contracts.record.AccountRecord.class);
    for (int off = 0; off < all.length; off += 300) {
      byte[] rec = Arrays.copyOfRange(all, off, off + 300);
      assertThat(codec.encode(codec.decode(rec))).isEqualTo(rec);
    }
  }
}
