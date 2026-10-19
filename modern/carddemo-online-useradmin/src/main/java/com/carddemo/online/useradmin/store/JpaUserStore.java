package com.carddemo.online.useradmin.store;

import com.carddemo.domain.entity.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link UserStore} over the {@code app_user} table (entity {@link AppUser}). Browses use {@code
 * COLLATE "C"} so that the key order is the VSAM byte order whatever the database collation.
 */
@Repository
public class JpaUserStore implements UserStore {

  private static final String FORWARD =
      "SELECT * FROM app_user WHERE sec_usr_id COLLATE \"C\" >= :key"
          + " ORDER BY sec_usr_id COLLATE \"C\" ASC";
  private static final String BACKWARD =
      "SELECT * FROM app_user WHERE sec_usr_id COLLATE \"C\" <= :key"
          + " ORDER BY sec_usr_id COLLATE \"C\" DESC";

  @PersistenceContext private EntityManager em;

  @Override
  @Transactional(readOnly = true)
  public Optional<UserRow> findById(String id) {
    try {
      return Optional.ofNullable(em.find(AppUser.class, id)).map(JpaUserStore::toRow);
    } catch (PersistenceException | DataAccessException e) {
      throw new UserStoreException("READ USRSEC failed", e);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserRow> browseForward(String fromKey, int limit) {
    return browse(FORWARD, fromKey, limit);
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserRow> browseBackward(String fromKey, int limit) {
    return browse(BACKWARD, fromKey, limit);
  }

  @SuppressWarnings("unchecked")
  private List<UserRow> browse(String sql, String fromKey, int limit) {
    try {
      List<AppUser> rows =
          em.createNativeQuery(sql, AppUser.class)
              .setParameter("key", fromKey)
              .setMaxResults(limit)
              .getResultList();
      return rows.stream().map(JpaUserStore::toRow).toList();
    } catch (PersistenceException | DataAccessException e) {
      throw new UserStoreException("Browse USRSEC failed", e);
    }
  }

  @Override
  @Transactional
  public WriteOutcome insert(UserRow row) {
    if (em.find(AppUser.class, row.id()) != null) {
      return WriteOutcome.DUPLICATE;
    }
    AppUser u = new AppUser();
    u.setSecUsrId(row.id());
    copy(row, u);
    try {
      em.persist(u);
      em.flush();
      return WriteOutcome.OK;
    } catch (DataIntegrityViolationException e) {
      return WriteOutcome.DUPLICATE;
    } catch (PersistenceException e) {
      if (e.getCause() instanceof org.hibernate.exception.ConstraintViolationException) {
        return WriteOutcome.DUPLICATE;
      }
      throw new UserStoreException("WRITE USRSEC failed", e);
    }
  }

  @Override
  @Transactional
  public WriteOutcome update(UserRow row) {
    try {
      AppUser u = em.find(AppUser.class, row.id());
      if (u == null) {
        return WriteOutcome.NOT_FOUND;
      }
      if (u.getVersion() != row.version()) {
        return WriteOutcome.CONFLICT;
      }
      copy(row, u);
      em.flush();
      return WriteOutcome.OK;
    } catch (OptimisticLockException | OptimisticLockingFailureException e) {
      return WriteOutcome.CONFLICT;
    } catch (PersistenceException | DataAccessException e) {
      throw new UserStoreException("REWRITE USRSEC failed", e);
    }
  }

  @Override
  @Transactional
  public WriteOutcome delete(UserRow row) {
    try {
      AppUser u = em.find(AppUser.class, row.id());
      if (u == null) {
        return WriteOutcome.NOT_FOUND;
      }
      if (u.getVersion() != row.version()) {
        return WriteOutcome.CONFLICT;
      }
      em.remove(u);
      em.flush();
      return WriteOutcome.OK;
    } catch (OptimisticLockException | OptimisticLockingFailureException e) {
      return WriteOutcome.CONFLICT;
    } catch (PersistenceException | DataAccessException e) {
      throw new UserStoreException("DELETE USRSEC failed", e);
    }
  }

  private static void copy(UserRow row, AppUser u) {
    u.setSecUsrFname(row.firstName());
    u.setSecUsrLname(row.lastName());
    u.setSecUsrPwd(row.password());
    u.setSecUsrType(row.type());
    u.setSecUsrFiller(row.filler());
  }

  static UserRow toRow(AppUser u) {
    return new UserRow(
        u.getSecUsrId(),
        u.getSecUsrFname(),
        u.getSecUsrLname(),
        u.getSecUsrPwd(),
        u.getSecUsrType(),
        u.getSecUsrFiller(),
        u.getVersion());
  }
}
