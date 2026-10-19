package com.carddemo.copybook;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.contracts.record.AccountRecord;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CoreCodecsRoundTripTest {

  static Path appData() {
    return Path.of(System.getProperty("modern.root", ".."))
        .resolve("../app/data/ASCII")
        .normalize();
  }

  static Stream<Arguments> sampleFiles() {
    return Stream.of(
        Arguments.of("acctdata.txt", CoreCodecs.ACCOUNT, 50),
        Arguments.of("carddata.txt", CoreCodecs.CARD, 50),
        Arguments.of("cardxref.txt", CoreCodecs.CARD_XREF, 50),
        Arguments.of("custdata.txt", CoreCodecs.CUSTOMER, 50),
        Arguments.of("tcatbal.txt", CoreCodecs.TRAN_CAT_BALANCE, 50),
        Arguments.of("discgrp.txt", CoreCodecs.DISCLOSURE_GROUP, 51),
        Arguments.of("trantype.txt", CoreCodecs.TRANSACTION_TYPE, 7),
        Arguments.of("trancatg.txt", CoreCodecs.TRANSACTION_CATEGORY, 18),
        Arguments.of("dailytran.txt", CoreCodecs.DAILY_TRANSACTION, 300));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("sampleFiles")
  <T> void decodeEncodeIsByteIdentical(String file, RecordCodec<T> codec, int expected)
      throws IOException {
    List<String> lines =
        Files.readAllLines(appData().resolve(file), StandardCharsets.US_ASCII).stream()
            .map(l -> l.replace("\r", ""))
            .filter(l -> !l.isBlank())
            .toList();
    assertThat(lines).hasSize(expected);
    for (String line : lines) {
      String padded = line + " ".repeat(codec.recordLength() - line.length());
      assertThat(codec.encodeLine(codec.decodeLine(line))).isEqualTo(padded);
    }
  }

  @Test
  void overpunchSignsAndScale() {
    assertThat(ZonedDecimal.decode("0000000194{", 2, true)).isEqualByComparingTo("19.40");
    assertThat(ZonedDecimal.decode("0000000194}", 2, true)).isEqualByComparingTo("-19.40");
    assertThat(ZonedDecimal.decode("0000000002R", 2, true)).isEqualByComparingTo("-0.29");
    assertThat(ZonedDecimal.encode(new BigDecimal("-0.29"), 11, 2, true)).isEqualTo("0000000002R");
    assertThat(ZonedDecimal.encode(new BigDecimal("12345.678"), 5, 2, true))
        .as("MOVE truncates high-order and low-order digits")
        .isEqualTo("3456G");
  }

  @Test
  void accountFieldsAreDecoded() throws IOException {
    String first = Files.readAllLines(appData().resolve("acctdata.txt")).get(0);
    AccountRecord a = CoreCodecs.ACCOUNT.decodeLine(first);
    assertThat(a.acctId()).isPositive();
    assertThat(a.acctCurrBal().scale()).isEqualTo(2);
    assertThat(a.acctOpenDate()).hasSize(10);
  }
}
