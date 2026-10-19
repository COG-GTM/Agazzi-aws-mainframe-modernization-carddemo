package com.carddemo.ext.trantype.menu;

import com.carddemo.contracts.port.AdminMenuContributor;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.ext.trantype.TrantypeEnabled;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * CA00 options 5 and 6 of COADM02Y, published only when the extension is enabled. O1 builds the
 * admin menu from {@link AdminMenuContributor} beans (contracts-v1.1).
 */
@Component
@TrantypeEnabled
public class TrantypeAdminMenu implements AdminMenuContributor {

  public static final List<Option> OPTIONS =
      List.of(
          new Option(5, "Transaction Type List/Update (Db2) ", "COTRTLIC", ScreenId.COTRTLI),
          new Option(6, "Transaction Type Maintenance (Db2) ", "COTRTUPC", ScreenId.COTRTUP));

  @Override
  public List<Option> options() {
    return OPTIONS;
  }
}
