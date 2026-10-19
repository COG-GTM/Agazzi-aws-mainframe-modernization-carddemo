package com.carddemo.online.card.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import com.carddemo.domain.entity.Card;
import com.carddemo.domain.repository.CardRepository;
import com.carddemo.online.card.repository.CardBrowser;
import com.carddemo.platform.time.FixedBusinessClock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/** In-memory CARDDAT: card {@code 40000000000000NN} belongs to account {@code 100 + NN % 3}. */
public final class CardFixtures {

  public final TreeMap<String, Card> file = new TreeMap<>();
  public final CardBrowser browser = mock(CardBrowser.class);
  public final CardRepository repo = mock(CardRepository.class);
  public final BusinessClock clock = FixedBusinessClock.golden();

  public CardFixtures(int count) {
    for (int i = 1; i <= count; i++) {
      put(card(i));
    }
    when(browser.readNext(nullable(String.class), anyInt()))
        .thenAnswer(
            a -> {
              String k = a.getArgument(0);
              int max = a.getArgument(1);
              return (k == null ? file : file.tailMap(k, true))
                  .values().stream().limit(max).toList();
            });
    when(browser.readPrev(anyString(), anyInt()))
        .thenAnswer(
            a -> {
              String k = a.getArgument(0);
              int max = a.getArgument(1);
              return file.headMap(k, true).values().stream()
                  .sorted(Comparator.comparing(Card::getCardNum).reversed())
                  .limit(max)
                  .toList();
            });
    when(browser.readNext(nullable(String.class), anyInt(), nullable(Long.class)))
        .thenAnswer(
            a -> {
              String k = a.getArgument(0);
              int max = a.getArgument(1);
              Long acct = a.getArgument(2);
              return (k == null ? file : file.tailMap(k, true))
                  .values().stream()
                      .filter(c -> acct == null || c.getCardAcctId().equals(acct))
                      .limit(max)
                      .toList();
            });
    when(browser.readPrev(anyString(), anyInt(), nullable(Long.class)))
        .thenAnswer(
            a -> {
              String k = a.getArgument(0);
              int max = a.getArgument(1);
              Long acct = a.getArgument(2);
              return file.headMap(k, true).values().stream()
                  .filter(c -> acct == null || c.getCardAcctId().equals(acct))
                  .sorted(Comparator.comparing(Card::getCardNum).reversed())
                  .limit(max)
                  .toList();
            });
    when(repo.findById(anyString())).thenAnswer(a -> Optional.ofNullable(copy(a.getArgument(0))));
    when(repo.saveAndFlush(any(Card.class))).thenAnswer(a -> put(a.getArgument(0)));
  }

  public static String num(int i) {
    return "%016d".formatted(4000000000000000L + i);
  }

  public static long acct(int i) {
    return 100 + i % 3;
  }

  public static String acctText(int i) {
    return "%011d".formatted(acct(i));
  }

  public static Card card(int i) {
    Card c = new Card();
    c.setCardNum(num(i));
    c.setCardAcctId(acct(i));
    c.setCardCvvCd(100 + i);
    c.setCardEmbossedName(CardScreenSupportPad.pad("Holder Name", 50));
    c.setCardExpirationDate("2025-0%d-15".formatted(1 + i % 9));
    c.setCardActiveStatus("Y");
    return c;
  }

  public Card put(Card c) {
    file.put(c.getCardNum(), c);
    return c;
  }

  private Card copy(String key) {
    Card s = file.get(key);
    if (s == null) {
      return null;
    }
    Card c = new Card();
    c.setCardNum(s.getCardNum());
    c.setCardAcctId(s.getCardAcctId());
    c.setCardCvvCd(s.getCardCvvCd());
    c.setCardEmbossedName(s.getCardEmbossedName());
    c.setCardExpirationDate(s.getCardExpirationDate());
    c.setCardActiveStatus(s.getCardActiveStatus());
    return c;
  }

  public static ConversationContext admin() {
    return ConversationContext.signedOn("ADMIN001", UserType.ADMIN);
  }

  public static ConversationContext user() {
    return ConversationContext.signedOn("USER0001", UserType.USER);
  }

  public static List<String> texts(com.carddemo.contracts.screen.ScreenResponse<?> r) {
    return r.messages().stream().map(m -> m.text()).toList();
  }

  /** Avoids depending on main-code padding in fixtures. */
  public static final class CardScreenSupportPad {
    public static String pad(String s, int n) {
      return String.format("%-" + n + "s", s);
    }
  }
}
