package com.carddemo.online.transaction.file;

import com.carddemo.domain.entity.Transaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/** JPA implementation of {@link TransactFile} on table {@code transaction}. */
@Component
public class JpaTransactFile implements TransactFile {

  private final TransactRepository repository;

  @PersistenceContext private EntityManager em;

  public JpaTransactFile(TransactRepository repository) {
    this.repository = repository;
  }

  @Override
  public ReadResult<Transaction> read(String tranId) {
    return repository
        .findById(tranId)
        .map(ReadResult::normal)
        .orElseGet(() -> ReadResult.of(Resp.NOTFND));
  }

  @Override
  public Optional<Transaction> firstAtOrAfter(String key) {
    return repository.findFirstByTranIdGreaterThanEqualOrderByTranIdAsc(key);
  }

  @Override
  public Optional<Transaction> firstAfter(String key) {
    return repository.findFirstByTranIdGreaterThanOrderByTranIdAsc(key);
  }

  @Override
  public Optional<Transaction> lastBefore(String key) {
    return repository.findFirstByTranIdLessThanOrderByTranIdDesc(key);
  }

  @Override
  public Optional<Transaction> last() {
    return repository.findFirstByOrderByTranIdDesc();
  }

  @Override
  public void lockForAppend() {
    em.createNativeQuery("SELECT lock_id FROM o4_tran_id_lock WHERE lock_id = 1 FOR UPDATE")
        .getSingleResult();
  }

  @Override
  public Resp write(Transaction transaction) {
    if (em.find(Transaction.class, transaction.getTranId()) != null) {
      return Resp.DUPREC;
    }
    try {
      em.persist(transaction);
      em.flush();
      return Resp.NORMAL;
    } catch (RuntimeException e) {
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
      return Resp.ERROR;
    }
  }
}
