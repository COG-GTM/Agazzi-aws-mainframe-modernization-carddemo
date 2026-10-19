package com.carddemo.batch.datalifecycle.gdg;

import com.carddemo.contracts.port.BusinessClock;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Local adapter for IDCAMS DEFINE GENERATIONDATAGROUP (table {@code dl_gdg_base}, V500). */
public class GdgCatalog {

  /** IDCAMS condition code for a duplicate catalog entry. */
  public static final int DUPLICATE = 12;

  private final JdbcTemplate jdbc;
  private final BusinessClock clock;

  public GdgCatalog(JdbcTemplate jdbc, BusinessClock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** Returns 0 when defined, {@link #DUPLICATE} when the base already exists. */
  public int define(String base, int limit, boolean scratch) {
    try {
      jdbc.update(
          "INSERT INTO dl_gdg_base (base_name, gen_limit, scratch, defined_at) VALUES (?, ?, ?, ?)",
          base,
          limit,
          scratch,
          clock.cobolTimestamp());
      return 0;
    } catch (DuplicateKeyException e) {
      return DUPLICATE;
    }
  }

  public Optional<Integer> limit(String base) {
    return jdbc
        .query(
            "SELECT gen_limit FROM dl_gdg_base WHERE base_name = ?", (rs, n) -> rs.getInt(1), base)
        .stream()
        .findFirst();
  }
}
