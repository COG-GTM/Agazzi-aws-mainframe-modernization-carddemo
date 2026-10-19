package com.carddemo.online.card.repository;

import com.carddemo.domain.entity.Card;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** VSAM browse of CARDDAT by card number (STARTBR GTEQ + READNEXT / READPREV). */
@Component
@Transactional(readOnly = true)
public class CardBrowser {

  @PersistenceContext private EntityManager em;

  /** READNEXT from {@code key} (inclusive, {@code null} = start of file). */
  public List<Card> readNext(String key, int max) {
    return em.createQuery(
            "select c from Card c where c.cardNum >= :k order by c.cardNum asc", Card.class)
        .setParameter("k", key == null ? "" : key)
        .setMaxResults(max)
        .getResultList();
  }

  /** READPREV from {@code key} (inclusive). */
  public List<Card> readPrev(String key, int max) {
    return em.createQuery(
            "select c from Card c where c.cardNum <= :k order by c.cardNum desc", Card.class)
        .setParameter("k", key == null ? "" : key)
        .setMaxResults(max)
        .getResultList();
  }

  /**
   * Account path (CARDAIX alternate index, {@code card_acct_id}): same card-number order as {@link
   * #readNext(String, int)} restricted to one account, so CCLI with an account filter or a USER
   * owned account reads only that account's cards (Fase 5A, finding 5B F2).
   */
  public List<Card> readNext(String key, int max, Long acctId) {
    if (acctId == null) {
      return readNext(key, max);
    }
    return em.createQuery(
            "select c from Card c where c.cardAcctId = :a and c.cardNum >= :k"
                + " order by c.cardNum asc",
            Card.class)
        .setParameter("a", acctId)
        .setParameter("k", key == null ? "" : key)
        .setMaxResults(max)
        .getResultList();
  }

  /** Account path of {@link #readPrev(String, int)}. */
  public List<Card> readPrev(String key, int max, Long acctId) {
    if (acctId == null) {
      return readPrev(key, max);
    }
    return em.createQuery(
            "select c from Card c where c.cardAcctId = :a and c.cardNum <= :k"
                + " order by c.cardNum desc",
            Card.class)
        .setParameter("a", acctId)
        .setParameter("k", key == null ? "" : key)
        .setMaxResults(max)
        .getResultList();
  }
}
