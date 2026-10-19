package com.carddemo.ext.trantype.menu;

import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.ext.trantype.TrantypeEnabled;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * CA00 options 5 and 6 of COADM02Y, published only when the extension is enabled. O1 builds the
 * admin menu from the beans of this shape (local adapter until the CONTRACT-CHANGE lands).
 */
@Component
@TrantypeEnabled
public class TrantypeAdminMenu {

  public static final List<AdminMenuOption> OPTIONS =
      List.of(
          new AdminMenuOption(
              5, "Transaction Type List/Update (Db2) ", "COTRTLIC", ScreenId.COTRTLI),
          new AdminMenuOption(
              6, "Transaction Type Maintenance (Db2) ", "COTRTUPC", ScreenId.COTRTUP));

  public List<AdminMenuOption> options() {
    return OPTIONS;
  }
}
