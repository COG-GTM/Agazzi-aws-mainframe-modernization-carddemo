package com.carddemo.ext.trantype.store;

import java.util.List;
import java.util.Optional;

/**
 * DB2 access of COTRTLIC/COTRTUPC/COBTUPDT on {@code transaction_type}/{@code
 * transaction_category}.
 */
public interface TransactionTypeStore {

  /**
   * Cursor C-TR-TYPE-FORWARD: {@code TR_TYPE >= startKey ORDER BY TR_TYPE}, at most {@code limit}.
   */
  List<TypeRow> forward(TypeFilter filter, String startKey, int limit);

  /**
   * Cursor C-TR-TYPE-BACKWARD: {@code TR_TYPE < startKey ORDER BY TR_TYPE DESC}, at most {@code
   * limit}.
   */
  List<TypeRow> backward(TypeFilter filter, String startKey, int limit);

  /** 9100-CHECK-FILTERS: {@code SELECT COUNT(1)} with the same predicates. */
  long count(TypeFilter filter);

  Optional<TypeRow> find(String type);

  /**
   * @throws TrantypeSqlException {@code -803} when the key exists
   */
  void insert(String type, String description);

  /**
   * @throws TrantypeSqlException {@code +100} when the key does not exist
   */
  void updateDescription(String type, String description);

  /**
   * @throws TrantypeSqlException {@code +100} when the key does not exist, {@code -532} when
   *     categories still reference it (DB2 FK {@code ON DELETE RESTRICT})
   */
  void delete(String type);

  List<TypeRow> allTypes();

  List<CategoryRow> allCategories();
}
