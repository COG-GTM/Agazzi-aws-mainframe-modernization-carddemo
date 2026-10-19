package com.carddemo.ext.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.ext.authorization.messaging.AuthorizationListener;
import com.carddemo.ext.authorization.service.AuthorizationDecisionService;
import com.carddemo.ext.authorization.web.AuthorizationScreenController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = "carddemo.extensions.authorization.enabled=false")
class FeatureFlagOffTest {

  @Autowired ApplicationContext ctx;

  @Test
  void noX1BeansWhenFlagIsOff() {
    assertThat(ctx.getBeanNamesForType(AuthorizationDecisionService.class)).isEmpty();
    assertThat(ctx.getBeanNamesForType(AuthorizationScreenController.class)).isEmpty();
    assertThat(ctx.getBeanNamesForType(AuthorizationListener.class)).isEmpty();
    assertThat(ctx.containsBean("cbpaup0jJob")).isFalse();
  }
}
