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
}
