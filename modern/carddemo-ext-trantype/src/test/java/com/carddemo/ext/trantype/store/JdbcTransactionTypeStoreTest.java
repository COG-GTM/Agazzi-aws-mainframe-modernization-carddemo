package com.carddemo.ext.trantype.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.contracts.record.TransactionTypeRecord;
import com.carddemo.domain.entity.TransactionType;
import com.carddemo.domain.repository.TransactionTypeRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class JdbcTransactionTypeStoreTest {

  private NamedParameterJdbcTemplate jdbc;
  private TransactionTypeRepository repo;
  private JdbcTransactionTypeStore store;

  @BeforeEach
  void setUp() {
    jdbc = mock(NamedParameterJdbcTemplate.class);
    repo = mock(TransactionTypeRepository.class);
    store = new JdbcTransactionTypeStore(jdbc, repo);
  }

  @Test
  @SuppressWarnings("unchecked")
  void keysetQueriesCarryPredicates() {
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<SqlParameterSource> p = ArgumentCaptor.forClass(SqlParameterSource.class);
    when(jdbc.query(sql.capture(), p.capture(), any(RowMapper.class))).thenReturn(List.of());
    store.forward(new TypeFilter("01", "Pay"), "01", 8);
    assertThat(sql.getValue())
        .contains("tran_type >= :start", "tran_type = :type", "LIKE :desc", "ASC", "LIMIT :limit");
    assertThat(((MapSqlParameterSource) p.getValue()).getValue("desc")).isEqualTo("%Pay%");
    store.backward(TypeFilter.NONE, "05", 7);
    assertThat(sql.getValue()).contains("tran_type < :start", "DESC").doesNotContain("LIKE");
    store.allTypes();
    store.allCategories();
    when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
        .thenReturn(3L);
    assertThat(store.count(TypeFilter.NONE)).isEqualTo(3);
    when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
        .thenThrow(new DataIntegrityViolationException("x"));
    assertThatThrownBy(() -> store.count(TypeFilter.NONE))
        .isInstanceOfSatisfying(
            TrantypeSqlException.class, e -> assertThat(e.sqlcode()).isEqualTo(-999));
  }

  @Test
  void writesGoThroughRepository() {
    TransactionType t =
        TransactionType.fromRecord(new TransactionTypeRecord("01", "Purchase", "00000000"));
    when(repo.findById("01")).thenReturn(Optional.of(t));
    assertThat(store.find("01")).contains(new TypeRow("01", "Purchase"));
    store.updateDescription("01", "New");
    assertThat(t.getTranTypeDesc()).hasSize(50).startsWith("New ");
    when(repo.existsById("01")).thenReturn(true);
    assertThatThrownBy(() -> store.insert("01", "x"))
        .isInstanceOfSatisfying(
            TrantypeSqlException.class, e -> assertThat(e.sqlcode()).isEqualTo(-803));
    store.insert("08", "Fee");
    verify(repo, times(2)).saveAndFlush(any(TransactionType.class));
    assertThatThrownBy(() -> store.updateDescription("77", "x"))
        .isInstanceOfSatisfying(
            TrantypeSqlException.class, e -> assertThat(e.sqlcode()).isEqualTo(100));
  }

  @Test
  void deleteChecksChildrenAndMapsLockErrors() {
    assertThatThrownBy(() -> store.delete("77"))
        .isInstanceOfSatisfying(
            TrantypeSqlException.class, e -> assertThat(e.sqlcode()).isEqualTo(100));
    when(repo.existsById("01")).thenReturn(true);
    when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
        .thenReturn(2L, 0L, 0L);
    assertThatThrownBy(() -> store.delete("01"))
        .isInstanceOfSatisfying(
            TrantypeSqlException.class, e -> assertThat(e.sqlcode()).isEqualTo(-532));
    store.delete("01");
    verify(repo).deleteById("01");
    doThrow(new ObjectOptimisticLockingFailureException(TransactionType.class, "01"))
        .when(repo)
        .flush();
    assertThatThrownBy(() -> store.delete("01"))
        .isInstanceOfSatisfying(
            TrantypeSqlException.class, e -> assertThat(e.sqlcode()).isEqualTo(-911));
  }
}
