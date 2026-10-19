package com.carddemo.online.auth.service;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.map.Coadm01Screen;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** CA00 COADM01C - admin menu (map COADM1A, options COADM02Y). */
@Service
public class Coadm01cService extends MenuProgram<Coadm01Screen.Output> {

  @Autowired
  public Coadm01cService(BusinessClock clock, ExtensionFlags extensions) {
    this(MenuCatalog.ADMIN_MENU, clock, extensions);
  }

  Coadm01cService(List<MenuOption> options, BusinessClock clock, ExtensionFlags extensions) {
    super(ScreenId.COADM01, options, clock, extensions);
  }

  @Override
  protected ScreenResult<Coadm01Screen.Output> selected(
      ConversationContext ctx, MenuOption option, Task task) {
    // IF CDEMO-ADMIN-OPT-PGMNAME(WS-OPTION)(1:5) NOT = 'DUMMY' -> XCTL (PGMIDERR when missing)
    if (!option.dummy() && option.screen().isPresent() && extensions.installed(option)) {
      return xctl(ctx, option);
    }
    task.info(CicsMessages.ADMIN_NOT_INSTALLED);
    return ScreenResult.keep(send(ctx, task));
  }

  @Override
  protected Coadm01Screen.Output output(
      ScreenHeader h, List<String> o, String optiono, String errmsg) {
    // BUILD-MENU-OPTIONS of COADM01C fills OPTN001-OPTN010 only (WHEN OTHER CONTINUE)
    return new Coadm01Screen.Output(
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
        null,
        null,
        optiono,
        errmsg);
  }
}
