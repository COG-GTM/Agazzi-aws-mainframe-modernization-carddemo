package com.carddemo.online.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.screen.ScreenId;
import org.junit.jupiter.api.Test;

class MenuCatalogTest {

  @Test
  void countsAndTargetsMatchCopybooks() {
    assertThat(MenuCatalog.MAIN_MENU).hasSize(11);
    assertThat(MenuCatalog.ADMIN_MENU).hasSize(6);
    assertThat(MenuCatalog.MAIN_MENU)
        .extracting(o -> o.screen().orElseThrow())
        .containsExactly(
            ScreenId.COACTVW,
            ScreenId.COACTUP,
            ScreenId.COCRDLI,
            ScreenId.COCRDSL,
            ScreenId.COCRDUP,
            ScreenId.COTRN00,
            ScreenId.COTRN01,
            ScreenId.COTRN02,
            ScreenId.CORPT00,
            ScreenId.COBIL00,
            ScreenId.COPAU00);
    assertThat(MenuCatalog.ADMIN_MENU)
        .extracting(o -> o.screen().orElseThrow())
        .containsExactly(
            ScreenId.COUSR00,
            ScreenId.COUSR01,
            ScreenId.COUSR02,
            ScreenId.COUSR03,
            ScreenId.COTRTLI,
            ScreenId.COTRTUP);
  }

  @Test
  void menuTextIsStringDelimitedBySize() {
    MenuOption o = MenuCatalog.MAIN_MENU.get(0);
    assertThat(o.menuText()).hasSize(40).startsWith("01. Account View ");
    assertThat(MenuCatalog.MAIN_MENU.get(9).menuText()).startsWith("10. Bill Payment");
    assertThat(o.dummy()).isFalse();
    assertThat(new MenuOption(1, "Soon", "DUMMY001", 'U').dummy()).isTrue();
    assertThat(new MenuOption(1, "X", "NOSUCHPG", 'U').screen()).isEmpty();
  }

  @Test
  void stringDelimiters() {
    MenuOption pau = MenuCatalog.MAIN_MENU.get(10);
    assertThat(Comen01cService.notInstalled(pau))
        .isEqualTo("This option Pending Authorization View is not installed...");
    assertThat(Comen01cService.comingSoon(pau)).isEqualTo("This option Pendingis coming soon ...");
  }
}
