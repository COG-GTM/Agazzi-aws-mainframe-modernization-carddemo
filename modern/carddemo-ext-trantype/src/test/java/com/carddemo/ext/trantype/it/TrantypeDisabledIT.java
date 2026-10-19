package com.carddemo.ext.trantype.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.domain.repository.TransactionTypeRepository;
import com.carddemo.ext.trantype.menu.TrantypeAdminMenu;
import com.carddemo.ext.trantype.online.TrantypeScreenController;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Flag OFF (default): the module adds nothing; shared tables and repositories still work. */
@SpringBootTest(
    classes = TrantypeItApplication.class,
    properties = {
      "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
      "spring.batch.job.enabled=false",
      "spring.batch.jdbc.initialize-schema=never"
    })
@AutoConfigureMockMvc
class TrantypeDisabledIT extends PostgresSupport {

  @Autowired MockMvc mvc;
  @Autowired ApplicationContext ctx;
  @Autowired TransactionTypeRepository types;

  @Test
  void endpointsJobsAndMenuOptionsAreAbsent() throws Exception {
    assertThat(ctx.getBeanNamesForType(TrantypeScreenController.class)).isEmpty();
    assertThat(ctx.getBeanNamesForType(TrantypeAdminMenu.class)).isEmpty();
    assertThat(ctx.getBeanNamesForType(Job.class)).isEmpty();
    mvc.perform(get("/api/v1/screens/cotrtli").with(user("ADMIN001")))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/screens/cotrtup")
                .with(user("ADMIN001"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"ENTER\",\"fields\":{}}"))
        .andExpect(status().isNotFound());
    assertThat(types.count()).isEqualTo(7);
  }
}
