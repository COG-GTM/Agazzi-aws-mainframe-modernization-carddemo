package com.carddemo.online.auth.service;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.map.Comen01Screen;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** CM00 COMEN01C - main menu for regular users (map COMEN1A, options COMEN02Y). */
@Service
public class Comen01cService extends MenuProgram<Comen01Screen.Output> {

  @Autowired
  public Comen01cService(BusinessClock clock, ExtensionFlags extensions) {
    this(MenuCatalog.MAIN_MENU, clock, extensions);
  }

  Comen01cService(List<MenuOption> options, BusinessClock clock, ExtensionFlags extensions) {
    super(ScreenId.COMEN01, options, clock, extensions);
  }

  @Override
  protected ScreenResult<Comen01Screen.Output> selected(
      ConversationContext ctx, MenuOption option, Task task) {
    // IF CDEMO-USRTYP-USER AND CDEMO-MENU-OPT-USRTYPE(WS-OPTION) = 'A'
    if (!ctx.isAdmin() && option.userType() == 'A') {
      task.error(CicsMessages.ADMIN_ONLY);
      return ScreenResult.keep(send(ctx, task));
    }
    if (option.dummy()) {
      // STRING 'This option ' NAME DELIMITED BY SPACE 'is coming soon ...'
      task.info(comingSoon(option));
      return ScreenResult.keep(send(ctx, task));
    }
    boolean optional = option.screen().map(ScreenId::optional).orElse(true);
    if (optional && !extensions.installed(option)) {
      // EXEC CICS INQUIRE PROGRAM not NORMAL:
      // MOVE DFHRED TO ERRMSGC, STRING 'This option ' NAME DELIMITED BY '  ' ' is not installed...'
      task.error(notInstalled(option));
      return ScreenResult.keep(send(ctx, task));
    }
    return xctl(ctx, option);
  }

  /** {@code STRING 'This option ' NAME DELIMITED BY ' ' ' is not installed...'}. */
  public static String notInstalled(MenuOption option) {
    return "This option " + option.nameDelimitedBy("  ") + " is not installed...";
  }

  /** {@code STRING 'This option ' NAME DELIMITED BY SPACE 'is coming soon ...'} (sic). */
  public static String comingSoon(MenuOption option) {
    return "This option " + option.nameDelimitedBy(" ") + "is coming soon ...";
  }

  @Override
  protected Comen01Screen.Output output(
      ScreenHeader h, List<String> o, String optiono, String errmsg) {
    return new Comen01Screen.Output(
        h.trnname(),
        h.title01(),
        h.curdate(),
        h.pgmname(),
        h.title02(),
        h.curtime(),
        slot(o, 0),
        slot(o, 1),
        slot(o, 2),
        slot(o, 3),
        slot(o, 4),
        slot(o, 5),
        slot(o, 6),
        slot(o, 7),
        slot(o, 8),
        slot(o, 9),
        slot(o, 10),
        slot(o, 11),
        optiono,
        errmsg);
  }
}
