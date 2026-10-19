package com.carddemo.online.transaction.service;

import java.io.Serializable;
import java.util.Map;

/**
 * Program area appended to COCOM01Y by COTRN00C/01C/02C and COBIL00C ({@code CDEMO-CTnn-INFO} /
 * {@code CDEMO-CB00-INFO}, identical layout, hence shared): first/last key and number of the page,
 * next-page flag, selection. It only survives XCTLs between O4 programs (COMEN01C/COADM01C pass a
 * bare COCOM01Y), so it is reset whenever an O4 screen is entered from another program.
 *
 * @param xctlAid AID key of the task that XCTLed into the next O4 program (EIBAID survives XCTL)
 * @param lastScreen fields currently shown by COTRN0A (needed for SEND without ERASE and for the
 *     protected TRNIDnn fields received with the selection)
 */
public record CommareaExtension(
    String trnidFirst,
    String trnidLast,
    long pageNum,
    boolean nextPage,
    String selFlg,
    String selected,
    String xctlAid,
    Map<String, String> lastScreen)
    implements Serializable {

  public CommareaExtension {
    lastScreen = lastScreen == null ? Map.of() : Map.copyOf(lastScreen);
  }

  public static CommareaExtension initial() {
    return new CommareaExtension("", "", 0, false, "", "", null, Map.of());
  }
}
