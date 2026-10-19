package com.carddemo.perf.datagen;

import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.contracts.record.AccountRecord;
import com.carddemo.contracts.record.CardRecord;
import com.carddemo.contracts.record.CardXrefRecord;
import com.carddemo.contracts.record.CustomerRecord;
import com.carddemo.contracts.record.DailyTransactionRecord;
import com.carddemo.contracts.record.TranCatBalanceRecord;
import com.carddemo.copybook.CoreCodecs;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The {@code app/data/ASCII} sample decoded with the Fase 0 codecs: the template for scaling. */
public record SampleData(
    Path dir,
    List<AccountRecord> accounts,
    List<CardRecord> cards,
    List<CardXrefRecord> xrefs,
    List<CustomerRecord> customers,
    List<DailyTransactionRecord> dailyTransactions,
    List<TranCatBalanceRecord> tranCatBalances) {

  /** Reference files copied unchanged (re-encoded at full LRECL): not scaled by 5B. */
  public static final List<String> REFERENCE_FILES =
      List.of("discgrp.txt", "trantype.txt", "trancatg.txt");

  public static SampleData load(Path dir) {
    return new SampleData(
        dir,
        read(dir, "acctdata.txt", CoreCodecs.ACCOUNT),
        read(dir, "carddata.txt", CoreCodecs.CARD),
        read(dir, "cardxref.txt", CoreCodecs.CARD_XREF),
        read(dir, "custdata.txt", CoreCodecs.CUSTOMER),
        read(dir, "dailytran.txt", CoreCodecs.DAILY_TRANSACTION),
        read(dir, "tcatbal.txt", CoreCodecs.TRAN_CAT_BALANCE));
  }

  /** Reads a sample file line by line ({@code \r} and over-length trailing blanks tolerated). */
  public static <T> List<T> read(Path dir, String file, RecordCodec<T> codec) {
    return lines(dir, file).stream().map(l -> codec.decodeLine(fit(l, codec))).toList();
  }

  static List<String> lines(Path dir, String file) {
    try {
      return Files.readAllLines(dir.resolve(file), StandardCharsets.US_ASCII).stream()
          .filter(l -> !l.isBlank())
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read sample " + dir.resolve(file), e);
    }
  }

  static String fit(String line, RecordCodec<?> codec) {
    String l = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    int len = codec.recordLength();
    if (l.length() > len && l.substring(len).isBlank()) {
      return l.substring(0, len);
    }
    return l;
  }
}
