package com.carddemo.perf.datagen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Maps a sample key to its copy {@code k} (0 = the sample itself, unchanged). The mapping is a pure
 * function, so every FK (card → account, xref → card/customer/account, transaction → card, tcatbal
 * → account) stays consistent across files without lookups.
 *
 * <ul>
 *   <li>ACCT-ID 9(11): {@code id + k * 1_000_000}
 *   <li>CUST-ID 9(09): {@code id + k * 10_000}
 *   <li>CARD-NUM X(16): the first 4 digits are replaced by the {@code k}-th 4-digit prefix that no
 *       sample card uses; the last 12 digits (unique in the sample) are kept.
 * </ul>
 */
public final class KeyMapper {

  static final long ACCT_STRIDE = 1_000_000L;
  static final int CUST_STRIDE = 10_000;
  static final int MAX_SCALE = 9_000;

  private final List<String> freePrefixes;

  public KeyMapper(List<String> sampleCardNums, int scale) {
    if (scale < 1 || scale > MAX_SCALE) {
      throw new IllegalArgumentException("scale must be in 1.." + MAX_SCALE + ": " + scale);
    }
    Set<String> used = new TreeSet<>();
    Set<String> tails = new TreeSet<>();
    for (String c : sampleCardNums) {
      used.add(c.substring(0, 4));
      if (!tails.add(c.substring(4))) {
        throw new IllegalStateException("Sample card numbers share the last 12 digits: " + c);
      }
    }
    freePrefixes = new ArrayList<>();
    for (int p = 1000; p <= 9999 && freePrefixes.size() < scale; p++) {
      String s = Integer.toString(p);
      if (!used.contains(s)) {
        freePrefixes.add(s);
      }
    }
    if (scale - 1 > freePrefixes.size()) {
      throw new IllegalArgumentException(
          "scale %d needs %d free card prefixes, the sample leaves %d"
              .formatted(scale, scale - 1, freePrefixes.size()));
    }
  }

  public long acctId(int k, long sampleAcctId) {
    return sampleAcctId + k * ACCT_STRIDE;
  }

  public int custId(int k, int sampleCustId) {
    return sampleCustId + k * CUST_STRIDE;
  }

  public String cardNum(int k, String sampleCardNum) {
    if (k == 0 || sampleCardNum.isBlank()) {
      return sampleCardNum;
    }
    return freePrefixes.get(k - 1) + sampleCardNum.substring(4);
  }
}
