package com.carddemo.online.useradmin.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.online.useradmin.store.UserStore.WriteOutcome;
import com.carddemo.online.useradmin.support.Fields;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** JpaUserStore on PostgreSQL 16 with V1 + V2 seed: VSAM key order, WRITE/REWRITE/DELETE. */
@Tag("it")
@Testcontainers
@SpringBootTest(
    classes = JpaUserStoreIT.Config.class,
    properties = {
      "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
      "spring.jpa.open-in-view=false"
    })
@DirtiesContext
class JpaUserStoreIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan("com.carddemo.domain.entity")
  @Import(JpaUserStore.class)
  static class Config {}

  @Autowired UserStore store;

  private static UserRow row(String id, String fname, String pwd, long version) {
    return new UserRow(
        Fields.pad(id, 8),
        Fields.pad(fname, 20),
        Fields.pad("IT", 20),
        pwd,
        "U",
        Fields.pad("", 23),
        version);
  }

  @Test
  void browsesInByteOrderLikeTheKsds() {
    assertThat(store.insert(row("U0000001", "Keyset", "{bcrypt}x", 0))).isEqualTo(WriteOutcome.OK);
    List<String> fwd = store.browseForward("", 12).stream().map(UserRow::id).toList();
    assertThat(fwd.subList(0, 7))
        .containsExactly(
            "ADMIN001", "ADMIN002", "ADMIN003", "ADMIN004", "ADMIN005", "U0000001", "USER0001");
    assertThat(store.browseForward("USER0004", 10))
        .extracting(UserRow::id)
        .containsExactly("USER0004", "USER0005");
    assertThat(store.browseBackward("ADMIN002", 10))
        .extracting(UserRow::id)
        .containsExactly("ADMIN002", "ADMIN001");
    assertThat(store.browseForward("ZZZZZZZZ", 10)).isEmpty();
    assertThat(store.delete(store.findById("U0000001").orElseThrow())).isEqualTo(WriteOutcome.OK);
  }

  @Test
  void writeRewriteDeleteWithOptimisticLock() {
    assertThat(store.insert(row("ITUSER01", "First", "{bcrypt}h", 0))).isEqualTo(WriteOutcome.OK);
    assertThat(store.insert(row("ITUSER01", "Again", "{bcrypt}h", 0)))
        .isEqualTo(WriteOutcome.DUPLICATE);

    UserRow terminalA = store.findById("ITUSER01").orElseThrow();
    UserRow terminalB = store.findById("ITUSER01").orElseThrow();
    assertThat(terminalA.version()).isZero();
    assertThat(terminalA.firstName()).isEqualTo(Fields.pad("First", 20));

    assertThat(store.update(row("ITUSER01", "FromA", terminalA.password(), terminalA.version())))
        .isEqualTo(WriteOutcome.OK);
    assertThat(store.update(row("ITUSER01", "FromB", terminalB.password(), terminalB.version())))
        .isEqualTo(WriteOutcome.CONFLICT);
    assertThat(store.delete(terminalB)).isEqualTo(WriteOutcome.CONFLICT);

    UserRow now = store.findById("ITUSER01").orElseThrow();
    assertThat(now.firstName().trim()).isEqualTo("FromA");
    assertThat(now.version()).isEqualTo(1L);
    assertThat(store.delete(now)).isEqualTo(WriteOutcome.OK);
    assertThat(store.findById("ITUSER01")).isEmpty();
    assertThat(store.update(now)).isEqualTo(WriteOutcome.NOT_FOUND);
    assertThat(store.delete(now)).isEqualTo(WriteOutcome.NOT_FOUND);
  }

  @Test
  void seedUsersKeepTheirLegacyPassword() {
    UserRow admin = store.findById("ADMIN001").orElseThrow();
    assertThat(admin.type()).isEqualTo("A");
    assertThat(admin.toString()).doesNotContain(admin.password());
  }
}
