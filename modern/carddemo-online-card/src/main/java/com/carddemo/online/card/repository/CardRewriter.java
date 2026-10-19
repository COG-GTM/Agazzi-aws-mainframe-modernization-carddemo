package com.carddemo.online.card.repository;

import com.carddemo.domain.entity.Card;
import com.carddemo.domain.repository.CardRepository;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 9200-WRITE-PROCESSING: READ UPDATE, 9300-CHECK-CHANGE-IN-REC, REWRITE. The {@code @Version}
 * column of {@link Card} turns a concurrent rewrite into an optimistic-lock failure on flush.
 */
@Component
public class CardRewriter {

  /** Outcome of the rewrite; {@code current} is the record read for update. */
  public record Result(Status status, Card current) {}

  public enum Status {
    NOT_FOUND,
    CHANGED,
    REWRITTEN
  }

  private final CardRepository cards;

  public CardRewriter(CardRepository cards) {
    this.cards = cards;
  }

  @Transactional
  public Result rewrite(String cardNum, Predicate<Card> unchanged, Card values) {
    Optional<Card> found = cards.findById(cardNum);
    if (found.isEmpty()) {
      return new Result(Status.NOT_FOUND, null);
    }
    Card c = found.get();
    if (!unchanged.test(c)) {
      return new Result(Status.CHANGED, c);
    }
    c.setCardAcctId(values.getCardAcctId());
    c.setCardEmbossedName(values.getCardEmbossedName());
    c.setCardExpirationDate(values.getCardExpirationDate());
    c.setCardActiveStatus(values.getCardActiveStatus());
    cards.saveAndFlush(c);
    return new Result(Status.REWRITTEN, c);
  }
}
