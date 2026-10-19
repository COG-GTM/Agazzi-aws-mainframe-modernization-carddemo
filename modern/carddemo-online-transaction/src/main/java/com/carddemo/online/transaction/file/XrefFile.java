package com.carddemo.online.transaction.file;

import com.carddemo.domain.entity.CardXref;

/** KSDS CCXREF (CVACT03Y) and its non-unique alternate index CXACAIX on XREF-ACCT-ID. */
public interface XrefFile {

  /** READ CCXREF by card number. */
  ReadResult<CardXref> readByCard(String cardNum);

  /**
   * READ CXACAIX by account id: NOTFND, NORMAL, or DUPKEY (with the first record) when more cards
   * share the account (NONUNIQUEKEY path).
   */
  ReadResult<CardXref> readByAcct(long acctId);
}
