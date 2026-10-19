package com.carddemo.online.auth.service;

import com.carddemo.contracts.screen.ScreenId;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Optional modules (X1/X2): a screen with {@link ScreenId#optional()} is "installed" only when
 * {@code carddemo.extensions.<name>.enabled=true} (name = workstream without the {@code xN-}
 * prefix, e.g. {@code authorization}, {@code trantype}). Replaces {@code EXEC CICS INQUIRE PROGRAM}
 * / PGMIDERR of the menus.
 */
@Component
public class ExtensionFlags {

  private final Environment env;

  public ExtensionFlags(Environment env) {
    this.env = env;
  }

  public static String extensionName(ScreenId screen) {
    String ws = screen.workstream();
    int dash = ws.indexOf('-');
    return dash < 0 ? ws : ws.substring(dash + 1);
  }

  public boolean installed(ScreenId screen) {
    return !screen.optional()
        || env.getProperty(
            "carddemo.extensions." + extensionName(screen) + ".enabled", Boolean.class, false);
  }

  public boolean installed(MenuOption option) {
    return option.screen().map(this::installed).orElse(false);
  }
}
