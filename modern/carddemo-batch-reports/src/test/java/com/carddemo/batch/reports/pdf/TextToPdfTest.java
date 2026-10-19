package com.carddemo.batch.reports.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextToPdfTest {

  @Test
  void rendersOneLinePerRecordAcrossPages() {
    List<String> lines = new ArrayList<>();
    for (int i = 0; i < 130; i++) {
      lines.add(i % 7 == 0 ? "" : "Line " + i + "   with   spaces   ");
    }
    byte[] pdf = TextToPdf.render(lines);
    assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1))
        .isEqualTo("%PDF-");
    assertThat(TextToPdf.extractLines(pdf).stream().map(TextToPdfTest::norm).toList())
        .containsExactlyElementsOf(
            lines.stream().filter(l -> !l.isBlank()).map(TextToPdfTest::norm).toList());
  }

  @Test
  void emptyInputGivesBlankDocument() {
    assertThat(TextToPdf.extractLines(TextToPdf.render(List.of()))).isEmpty();
  }

  private static String norm(String s) {
    return s.strip().replaceAll("\\s+", " ");
  }
}
