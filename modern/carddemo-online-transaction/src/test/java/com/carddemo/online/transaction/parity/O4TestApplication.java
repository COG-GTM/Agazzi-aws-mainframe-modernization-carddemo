package com.carddemo.online.transaction.parity;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.ReportJobLauncherPort;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import com.carddemo.online.transaction.web.ConversationStore;
import com.carddemo.platform.time.FixedBusinessClock;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only host for O4 (carddemo-app cannot be a dependency: it depends on this module). Stands in
 * for O1 (HTTP Basic users of DUSRSECJ + a sign-on endpoint that writes the COMMAREA the menus
 * would pass) and for B3 (in-memory {@link ReportJobLauncherPort}).
 */
@SpringBootApplication(scanBasePackages = "com.carddemo.online.transaction")
@EntityScan(basePackages = "com.carddemo.domain")
@EnableJpaRepositories(basePackages = {"com.carddemo.domain", "com.carddemo.online.transaction"})
public class O4TestApplication {

  @Bean
  BusinessClock businessClock() {
    return FixedBusinessClock.golden();
  }

  @Bean
  ReportJobLauncherPort reportJobLauncherPort() {
    return new FakeReportJobLauncher();
  }

  @Bean
  InMemoryUserDetailsManager users() {
    return new InMemoryUserDetailsManager(
        User.withUsername("ADMIN001").password("{noop}PASSWORD").roles("ADMIN").build(),
        User.withUsername("USER0001").password("{noop}PASSWORD").roles("USER").build(),
        User.withUsername("USER0002").password("{noop}PASSWORD").roles("USER").build());
  }

  @Bean
  SecurityFilterChain o4TestSecurity(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .httpBasic(b -> {})
        .csrf(c -> c.disable());
    return http.build();
  }

  /** Records launches; status = SUBMITTED for known ids. */
  public static final class FakeReportJobLauncher implements ReportJobLauncherPort {
    private final AtomicLong ids = new AtomicLong(1000);
    private final Map<Long, ReportJobHandle> jobs = new ConcurrentHashMap<>();

    @Override
    public ReportJobHandle launch(ReportRequest request) {
      ReportJobHandle h = new ReportJobHandle(ids.incrementAndGet(), "TRANREPT", "STARTING");
      jobs.put(h.executionId(), h);
      return h;
    }

    @Override
    public Optional<ReportJobHandle> status(long executionId) {
      return Optional.ofNullable(jobs.get(executionId));
    }
  }

  /** Sign-on stand-in: COMMAREA after COSGN00C + menu option XCTL to {@code program}. */
  @RestController
  public static class TestSignOnController {

    /** Body: {@code {"program": "COTRN00C"}}; with no program only the sign-on is done. */
    @PostMapping("/test/signon")
    public ConversationContext signOn(
        HttpServletRequest request, Principal principal, @RequestBody Map<String, String> body) {
      boolean admin = principal.getName().startsWith("ADMIN");
      ConversationContext ctx =
          ConversationContext.signedOn(principal.getName(), admin ? UserType.ADMIN : UserType.USER);
      String program = body.get("program");
      if (program != null) {
        String menuTran = admin ? "CA00" : "CM00";
        String menuPgm = admin ? "COADM01C" : "COMEN01C";
        ctx = ctx.transferTo(menuTran, menuPgm, body.getOrDefault("tranid", ""), program);
      }
      request.getSession(true).setAttribute(ConversationStore.CONTEXT_ATTRIBUTE, ctx);
      return ctx;
    }
  }
}
