package com.carddemo.datamigration.security;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.datamigration.store.RelationalStore;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reversible USRSEC password migration (PARITY_EXCEPTIONS PE-001): plaintext SEC-USR-PWD in {@code
 * app_user} becomes a DelegatingPasswordEncoder hash {@code {bcrypt}$2a$...}; the legacy value is
 * kept in {@code dm_usrsec_pwd_backup} until {@link #purgeBackup()}. The hashed text is the PIC
 * X(8) value without trailing spaces, upper-cased as COSGN00C compares it. ESDS/RRDS copies keep
 * the legacy 8-byte layout and are not touched.
 */
public class UsrsecPasswordMigration {

  public static final String PREFIX = "{bcrypt}";

  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final BusinessClock clock;
  private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

  public UsrsecPasswordMigration(JdbcTemplate jdbc, TransactionTemplate tx, BusinessClock clock) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.clock = clock;
  }

  /** Outcome of a run. */
  public record Result(int migrated, int alreadyHashed, int restored) {}

  /**
   * Already encoded by a {@code {id}} password encoder: a legacy SEC-USR-PWD is PIC X(8), so any
   * longer brace-prefixed value is a hash and is never re-hashed.
   */
  public static boolean isEncoded(String pwd) {
    return pwd.startsWith("{") && pwd.stripTrailing().length() > 8;
  }

  public static String legacyText(String pwd) {
    return pwd.stripTrailing().toUpperCase(Locale.ROOT);
  }

  public Result migrate() {
    return tx.execute(
        s -> {
          jdbc.queryForObject(
              "SELECT pg_advisory_xact_lock(" + RelationalStore.USRSEC_LOCK + ")::text",
              String.class);
          List<Map<String, Object>> rows =
              jdbc.queryForList("SELECT sec_usr_id, sec_usr_pwd FROM app_user ORDER BY sec_usr_id");
          int migrated = 0;
          int hashed = 0;
          Timestamp now = Timestamp.valueOf(clock.now());
          for (Map<String, Object> r : rows) {
            String id = (String) r.get("sec_usr_id");
            String pwd = (String) r.get("sec_usr_pwd");
            // any brace-prefixed value is already treated as encoded by the credential service:
            // left as is, so the cutover never changes who can sign in
            if (pwd.startsWith("{")) {
              hashed++;
              continue;
            }
            String hash = PREFIX + bcrypt.encode(legacyText(pwd));
            if (!bcrypt.matches(legacyText(pwd), hash.substring(PREFIX.length()))) {
              throw new IllegalStateException("BCrypt self-check failed for " + id.trim());
            }
            jdbc.update(
                "INSERT INTO dm_usrsec_pwd_backup (sec_usr_id, legacy_pwd, bcrypt_pwd, migrated_at)"
                    + " VALUES (?, ?, ?, ?) ON CONFLICT (sec_usr_id) DO UPDATE SET"
                    + " legacy_pwd = EXCLUDED.legacy_pwd, bcrypt_pwd = EXCLUDED.bcrypt_pwd,"
                    + " migrated_at = EXCLUDED.migrated_at",
                id,
                pwd,
                hash,
                now);
            jdbc.update(
                "UPDATE app_user SET sec_usr_pwd = ?, version = version + 1 WHERE sec_usr_id = ?",
                hash,
                id);
            migrated++;
          }
          return new Result(migrated, hashed, 0);
        });
  }

  /**
   * Restores the legacy plaintext of every user still holding the hash written by {@link
   * #migrate()}; a password changed after the cutover is kept.
   */
  public Result rollback() {
    return tx.execute(
        s -> {
          jdbc.queryForObject(
              "SELECT pg_advisory_xact_lock(" + RelationalStore.USRSEC_LOCK + ")::text",
              String.class);
          int restored =
              jdbc.update(
                  "UPDATE app_user u SET sec_usr_pwd = b.legacy_pwd, version = u.version + 1"
                      + " FROM dm_usrsec_pwd_backup b WHERE u.sec_usr_id = b.sec_usr_id"
                      + " AND u.sec_usr_pwd = b.bcrypt_pwd");
          jdbc.update("DELETE FROM dm_usrsec_pwd_backup");
          return new Result(0, 0, restored);
        });
  }

  /** Drops the plaintext backup once the cutover is confirmed (migration no longer reversible). */
  public int purgeBackup() {
    return jdbc.update("DELETE FROM dm_usrsec_pwd_backup");
  }
}
