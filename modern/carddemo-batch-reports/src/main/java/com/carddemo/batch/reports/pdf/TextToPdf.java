package com.carddemo.batch.reports.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * TXT2PDF ({@code %TXT2PDF BROWSE Y IN DD:INDD OUT ...}): fixed-pitch text rendered one record per
 * line in Courier, {@value #LINES_PER_PAGE} lines per page. Parity is checked on the extracted
 * text, not on the PDF bytes.
 */
public final class TextToPdf {

  static final int LINES_PER_PAGE = 60;

  private TextToPdf() {}

  public static byte[] render(List<String> lines) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Document doc = new Document(PageSize.LETTER, 36, 36, 36, 36);
    try {
      PdfWriter.getInstance(doc, out);
      doc.addCreator("CardDemo TXT2PDF1");
      doc.open();
      Font font = FontFactory.getFont(FontFactory.COURIER, 8);
      if (lines.isEmpty()) {
        doc.add(new Paragraph(" ", font));
      }
      for (int i = 0; i < lines.size(); i++) {
        if (i > 0 && i % LINES_PER_PAGE == 0) {
          doc.newPage();
        }
        String l = stripTrailing(lines.get(i));
        Paragraph p = new Paragraph(9f, l.isEmpty() ? " " : l, font);
        doc.add(p);
      }
    } catch (DocumentException e) {
      throw new IllegalStateException("TXT2PDF failed", e);
    } finally {
      doc.close();
    }
    return out.toByteArray();
  }

  /** Text of every page, one entry per non-blank line. */
  public static List<String> extractLines(byte[] pdf) {
    List<String> lines = new ArrayList<>();
    try (PdfReader reader = new PdfReader(pdf)) {
      PdfTextExtractor ex = new PdfTextExtractor(reader);
      for (int p = 1; p <= reader.getNumberOfPages(); p++) {
        for (String l : ex.getTextFromPage(p).split("\\R")) {
          if (!l.isBlank()) {
            lines.add(l);
          }
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return lines;
  }

  static String stripTrailing(String s) {
    return s.stripTrailing();
  }
}
