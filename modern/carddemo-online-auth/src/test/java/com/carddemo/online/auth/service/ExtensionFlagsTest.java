package com.carddemo.online.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.screen.ScreenId;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ExtensionFlagsTest {

  @Test
  void optionalScreensNeedTheirFlag() {
    MockEnvironment env = new MockEnvironment();
    ExtensionFlags flags = new ExtensionFlags(env);
    assertThat(flags.installed(ScreenId.COACTVW)).isTrue();
    assertThat(flags.installed(ScreenId.COPAU00)).isFalse();
    assertThat(flags.installed(ScreenId.COTRTLI)).isFalse();
    env.setProperty("carddemo.extensions.authorization.enabled", "true");
    assertThat(flags.installed(ScreenId.COPAU00)).isTrue();
    assertThat(flags.installed(ScreenId.COTRTUP)).isFalse();
    env.setProperty("carddemo.extensions.trantype.enabled", "true");
    assertThat(flags.installed(MenuCatalog.ADMIN_MENU.get(4))).isTrue();
    assertThat(flags.installed(new MenuOption(1, "X", "NOSUCHPG", 'U'))).isFalse();
    assertThat(ExtensionFlags.extensionName(ScreenId.COPAU01)).isEqualTo("authorization");
  }
}
