package com.carddemo.online.auth.service;

import java.util.List;

/** Menu tables of COMEN02Y (main menu, CM00) and COADM02Y (admin menu, CA00), same order. */
public final class MenuCatalog {

  private MenuCatalog() {}

  /** COMEN02Y CARDDEMO-MAIN-MENU-OPTIONS, CDEMO-MENU-OPT-COUNT = 11. */
  public static final List<MenuOption> MAIN_MENU =
      List.of(
          new MenuOption(1, "Account View", "COACTVWC", 'U'),
          new MenuOption(2, "Account Update", "COACTUPC", 'U'),
          new MenuOption(3, "Credit Card List", "COCRDLIC", 'U'),
          new MenuOption(4, "Credit Card View", "COCRDSLC", 'U'),
          new MenuOption(5, "Credit Card Update", "COCRDUPC", 'U'),
          new MenuOption(6, "Transaction List", "COTRN00C", 'U'),
          new MenuOption(7, "Transaction View", "COTRN01C", 'U'),
          new MenuOption(8, "Transaction Add", "COTRN02C", 'U'),
          new MenuOption(9, "Transaction Reports", "CORPT00C", 'U'),
          new MenuOption(10, "Bill Payment", "COBIL00C", 'U'),
          new MenuOption(11, "Pending Authorization View", "COPAUS0C", 'U'));

  /** COADM02Y CARDDEMO-ADMIN-MENU-OPTIONS, CDEMO-ADMIN-OPT-COUNT = 6. */
  public static final List<MenuOption> ADMIN_MENU =
      List.of(
          new MenuOption(1, "User List (Security)", "COUSR00C", ' '),
          new MenuOption(2, "User Add (Security)", "COUSR01C", ' '),
          new MenuOption(3, "User Update (Security)", "COUSR02C", ' '),
          new MenuOption(4, "User Delete (Security)", "COUSR03C", ' '),
          new MenuOption(5, "Transaction Type List/Update (Db2)", "COTRTLIC", ' '),
          new MenuOption(6, "Transaction Type Maintenance (Db2)", "COTRTUPC", ' '));
}
