package com.carddemo.online.transaction.file;

import com.carddemo.domain.entity.Account;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * JPA implementation of {@link AccountFile}: pessimistic row lock (READ UPDATE) + {@code @Version}.
 */
@Component
public class JpaAccountFile implements AccountFile {

  @PersistenceContext private EntityManager em;

  @Override
  public ReadResult<Account> readForUpdate(long acctId) {
    Account a = em.find(Account.class, acctId, LockModeType.PESSIMISTIC_WRITE);
    return a == null ? ReadResult.of(Resp.NOTFND) : ReadResult.normal(a);
  }

  @Override
  public Resp rewrite(Account account) {
    try {
      if (em.contains(account)) {
        em.flush();
      } else {
        em.merge(account);
        em.flush();
      }
      return Resp.NORMAL;
    } catch (RuntimeException e) {
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
      return Resp.ERROR;
    }
  }
}
