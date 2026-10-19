package com.carddemo.online.auth.service;

import com.carddemo.contracts.screen.ScreenId;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * One row of CDEMO-MENU-OPT (COMEN02Y) or CDEMO-ADMIN-OPT (COADM02Y). {@code name} keeps the PIC
 * X(35) padding; {@code userType} is CDEMO-MENU-OPT-USRTYPE ('U'/'A'), blank for admin options.
 */
public record MenuOption(int number, String name, String program, char userType) {

  public MenuOption {
    name = ScreenText.picX(name, 35);
    program = ScreenText.picX(program, 8);
  }

  /** {@code STRING OPT-NUM '. ' OPT-NAME DELIMITED BY SIZE INTO WS-MENU-OPT-TXT} (PIC X(40)). */
  public String menuText() {
    return ScreenText.picX(String.format(Locale.ROOT, "%02d", number) + ". " + name, 40);
  }

  /** The XCTL target screen of the option program, if it is an online screen. */
  public Optional<ScreenId> screen() {
    String pgm = program.stripTrailing();
    return Arrays.stream(ScreenId.values()).filter(s -> s.program().equals(pgm)).findFirst();
  }

  /** {@code CDEMO-MENU-OPT-PGMNAME(WS-OPTION)(1:5) = 'DUMMY'}. */
  public boolean dummy() {
    return program.startsWith("DUMMY");
  }

  /** {@code CDEMO-MENU-OPT-NAME DELIMITED BY <delimiter>} in a STRING statement. */
  public String nameDelimitedBy(String delimiter) {
    int i = name.indexOf(delimiter);
    return i < 0 ? name : name.substring(0, i);
  }
}
