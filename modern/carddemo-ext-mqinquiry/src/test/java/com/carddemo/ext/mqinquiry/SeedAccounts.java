package com.carddemo.ext.mqinquiry;

import com.carddemo.copybook.CoreCodecs;
import com.carddemo.domain.entity.Account;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Test fixtures: the ACCTDAT seed (app/data/ASCII/acctdata.txt) and the x3 golden directory. */
final class SeedAccounts {

  private SeedAccounts() {}

  static Path modernRoot() {
    String root = System.getProperty("modern.root");
    return root == null ? Path.of("..").toAbsolutePath().normalize() : Path.of(root);
  }

  static Path goldenDir(String tranId) {
    return modernRoot()
        .resolve("carddemo-parity/src/test/resources/golden/x3-mqinquiry/online")
        .resolve(tranId);
  }

  static Map<Long, Account> load() {
    Map<Long, Account> accounts = new LinkedHashMap<>();
    try {
      for (String line :
          Files.readAllLines(
              modernRoot().resolveSibling("app/data/ASCII/acctdata.txt"),
              StandardCharsets.US_ASCII)) {
        Account a =
            Account.fromRecord(
                CoreCodecs.ACCOUNT.decode(
                    MqMessageLayout.pic(line, 300).getBytes(StandardCharsets.US_ASCII)));
        accounts.put(a.toRecord().acctId(), a);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return accounts;
  }
}
