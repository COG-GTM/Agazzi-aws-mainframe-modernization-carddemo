package com.carddemo.ext.trantype.store;

import com.carddemo.contracts.record.TransactionTypeRecord;
import com.carddemo.domain.entity.TransactionType;
import com.carddemo.domain.repository.TransactionTypeRepository;
import com.carddemo.ext.trantype.TrantypeEnabled;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keyset queries via JDBC, writes through the shared {@link TransactionTypeRepository}. Each write
 * is its own unit of work, so an expected SQLCODE (+100, -803, -532) never dooms the caller's
 * transaction (the MNTTRDB2 step keeps going, like COBTUPDT).
 */
@Repository
@TrantypeEnabled
public class JdbcTransactionTypeStore implements TransactionTypeStore {

  private final NamedParameterJdbcTemplate jdbc;
  private final TransactionTypeRepository types;

  public JdbcTransactionTypeStore(
      NamedParameterJdbcTemplate jdbc, TransactionTypeRepository types) {
    this.jdbc = jdbc;
    this.types = types;
  }

  private static String where(TypeFilter f, MapSqlParameterSource p) {
    StringBuilder sb = new StringBuilder();
    if (f.typeCode() != null) {
      sb.append(" AND tran_type = :type");
      p.addValue("type", f.typeCode());
    }
    if (f.descriptionContains() != null) {
      sb.append(" AND tran_type_desc LIKE :desc");
      p.addValue("desc", "%" + f.descriptionContains() + "%");
    }
    return sb.toString();
  }

  private List<TypeRow> page(TypeFilter f, String startKey, int limit, boolean forward) {
    MapSqlParameterSource p = new MapSqlParameterSource("start", startKey == null ? "" : startKey);
    p.addValue("limit", limit);
    String sql =
        "SELECT tran_type, tran_type_desc FROM transaction_type WHERE tran_type "
            + (forward ? ">=" : "<")
            + " :start"
            + where(f, p)
            + " ORDER BY tran_type "
            + (forward ? "ASC" : "DESC")
            + " LIMIT :limit";
    return read(() -> jdbc.query(sql, p, (rs, i) -> new TypeRow(rs.getString(1), rs.getString(2))));
  }

  @Override
  public List<TypeRow> forward(TypeFilter filter, String startKey, int limit) {
    return page(filter, startKey, limit, true);
  }

  @Override
  public List<TypeRow> backward(TypeFilter filter, String startKey, int limit) {
    return page(filter, startKey, limit, false);
  }

  @Override
  public long count(TypeFilter filter) {
    MapSqlParameterSource p = new MapSqlParameterSource();
    String sql = "SELECT COUNT(1) FROM transaction_type WHERE 1 = 1" + where(filter, p);
    Long n = read(() -> jdbc.queryForObject(sql, p, Long.class));
    return n == null ? 0 : n;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TypeRow> find(String type) {
    return types.findById(type).map(t -> new TypeRow(t.getTranType(), t.getTranTypeDesc()));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = TrantypeSqlException.class)
  public void insert(String type, String description) {
    if (types.existsById(type)) {
      throw new TrantypeSqlException(
          TrantypeSqlException.DUPLICATE_KEY, TrantypeSqlException.DUPLICATE_KEY_TEXT);
    }
    guard(
        () ->
            types.saveAndFlush(
                TransactionType.fromRecord(
                    new TransactionTypeRecord(
                        type, Pic.pad(description, Pic.DESC_LEN), Pic.TYPE_FILLER))));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = TrantypeSqlException.class)
  public void updateDescription(String type, String description) {
    TransactionType t =
        types
            .findById(type)
            .orElseThrow(
                () ->
                    new TrantypeSqlException(
                        TrantypeSqlException.NOT_FOUND, TrantypeSqlException.NOT_FOUND_TEXT));
    t.setTranTypeDesc(Pic.pad(description, Pic.DESC_LEN));
    guard(() -> types.saveAndFlush(t));
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = TrantypeSqlException.class)
  public void delete(String type) {
    if (!types.existsById(type)) {
      throw new TrantypeSqlException(
          TrantypeSqlException.NOT_FOUND, TrantypeSqlException.NOT_FOUND_TEXT);
    }
    Long children =
        jdbc.queryForObject(
            "SELECT COUNT(1) FROM transaction_category WHERE tran_type_cd = :t",
            new MapSqlParameterSource("t", type),
            Long.class);
    if (children != null && children > 0) {
      throw new TrantypeSqlException(
          TrantypeSqlException.CHILD_ROWS_EXIST, TrantypeSqlException.CHILD_ROWS_TEXT);
    }
    guard(
        () -> {
          types.deleteById(type);
          types.flush();
        });
  }

  @Override
  public List<TypeRow> allTypes() {
    return jdbc.query(
        "SELECT tran_type, tran_type_desc FROM transaction_type ORDER BY tran_type",
        (rs, i) -> new TypeRow(rs.getString(1), rs.getString(2)));
  }

  @Override
  public List<CategoryRow> allCategories() {
    return jdbc.query(
        "SELECT tran_type_cd, tran_cat_cd, tran_cat_type_desc, filler FROM transaction_category"
            + " ORDER BY tran_type_cd, tran_cat_cd",
        (rs, i) ->
            new CategoryRow(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4)));
  }

  private static <T> T read(java.util.function.Supplier<T> q) {
    try {
      return q.get();
    } catch (DataAccessException e) {
      throw new TrantypeSqlException(TrantypeSqlException.GENERIC, e.getMessage());
    }
  }

  private static void guard(Runnable r) {
    try {
      r.run();
    } catch (ObjectOptimisticLockingFailureException | PessimisticLockingFailureException e) {
      throw new TrantypeSqlException(TrantypeSqlException.DEADLOCK, e.getMessage());
    } catch (DataAccessException e) {
      throw new TrantypeSqlException(TrantypeSqlException.GENERIC, e.getMessage());
    }
  }
}
