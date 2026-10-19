package com.carddemo.online.transaction.file;

import com.carddemo.domain.entity.Transaction;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Keyset queries on {@code transaction} mirroring the VSAM browse of TRANSACT. */
public interface TransactRepository extends Repository<Transaction, String> {

  Optional<Transaction> findById(String tranId);

  Optional<Transaction> findFirstByTranIdGreaterThanEqualOrderByTranIdAsc(String key);

  Optional<Transaction> findFirstByTranIdGreaterThanOrderByTranIdAsc(String key);

  Optional<Transaction> findFirstByTranIdLessThanOrderByTranIdDesc(String key);

  Optional<Transaction> findFirstByOrderByTranIdDesc();
}
