package com.carddemo.online.card.session;

import java.io.Serializable;
import java.util.List;

/** {@code WS-THIS-PROGCOMMAREA} of COCRDLIC: browse keys, page number and the rows on screen. */
public record CardListState(
    String firstCardNum,
    String lastCardNum,
    int screenNum,
    boolean nextPageExists,
    boolean lastPageShown,
    List<Row> rows)
    implements Serializable {

  /**
   * One screen row ({@code null} entries are empty rows) (WS-ROW-ACCTNO / WS-ROW-CARD-NUM /
   * WS-ROW-CARD-STATUS).
   */
  public record Row(long accountId, String cardNum, String status) implements Serializable {}

  public CardListState {
    rows =
        rows == null
            ? List.of()
            : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(rows));
  }

  public static CardListState initial() {
    return new CardListState(null, null, 1, false, false, List.of());
  }

  public boolean firstPage() {
    return screenNum <= 1;
  }
}
