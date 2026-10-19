package com.carddemo.online.card.session;

import java.io.Serializable;

/** {@code WS-THIS-PROGCOMMAREA} of COCRDUPC: change action plus old/new card details. */
public record CardUpdateState(Stage stage, CardDetails old, CardDetails changed)
    implements Serializable {

  /** {@code CCUP-CHANGE-ACTION}. */
  public enum Stage {
    NOT_FETCHED,
    SHOW_DETAILS,
    CHANGES_NOT_OK,
    CHANGES_OK_NOT_CONFIRMED,
    CHANGES_DONE,
    LOCK_ERROR,
    UPDATE_FAILED;

    public boolean changesFailed() {
      return this == LOCK_ERROR || this == UPDATE_FAILED;
    }

    public boolean changesMade() {
      return this == CHANGES_NOT_OK
          || this == CHANGES_OK_NOT_CONFIRMED
          || this == CHANGES_DONE
          || changesFailed();
    }
  }

  /** CCUP-OLD-DETAILS / CCUP-NEW-DETAILS (padded PIC X values, {@code null} = low-values). */
  public record CardDetails(
      String acctId,
      String cardId,
      Integer cvv,
      String name,
      String expYear,
      String expMon,
      String expDay,
      String status)
      implements Serializable {

    public static CardDetails empty() {
      return new CardDetails(null, null, null, null, null, null, null, null);
    }

    /** CCUP-xxx-CARDDATA compared with {@code FUNCTION UPPER-CASE}. */
    public String cardData() {
      return (nz(name, 50) + nz(expYear, 4) + nz(expMon, 2) + nz(expDay, 2) + nz(status, 1))
          .toUpperCase(java.util.Locale.ROOT);
    }

    private static String nz(String s, int len) {
      return com.carddemo.online.card.mapper.CardScreenSupport.padRight(s, len);
    }
  }

  public static CardUpdateState initial() {
    return new CardUpdateState(Stage.NOT_FETCHED, CardDetails.empty(), CardDetails.empty());
  }

  public CardUpdateState withStage(Stage s) {
    return new CardUpdateState(s, old, changed);
  }
}
