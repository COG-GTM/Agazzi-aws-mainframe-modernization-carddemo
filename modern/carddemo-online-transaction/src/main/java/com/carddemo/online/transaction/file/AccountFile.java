package com.carddemo.online.transaction.file;

import com.carddemo.domain.entity.Account;

/** KSDS ACCTDAT (CVACT01Y): READ UPDATE + REWRITE. */
public interface AccountFile {

  /** READ ... UPDATE: the row stays locked until the end of the unit of work. */
  ReadResult<Account> readForUpdate(long acctId);

  /** REWRITE of a record obtained by {@link #readForUpdate}; ERROR on a stale {@code @Version}. */
  Resp rewrite(Account account);
}
