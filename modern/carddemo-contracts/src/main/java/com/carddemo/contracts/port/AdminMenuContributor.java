package com.carddemo.contracts.port;

import com.carddemo.contracts.screen.ScreenId;
import java.util.List;

/**
 * Extra CA00 admin-menu options (COADM02Y {@code CDEMO-ADMIN-OPT}) published by optional
 * extensions, so that O1 builds the menu from beans without depending on extension modules
 * (contracts-v1.1).
 */
public interface AdminMenuContributor {

  /** One admin menu entry: option number, display name, target program and screen. */
  record Option(int number, String name, String program, ScreenId screen) {}

  List<Option> options();
}
