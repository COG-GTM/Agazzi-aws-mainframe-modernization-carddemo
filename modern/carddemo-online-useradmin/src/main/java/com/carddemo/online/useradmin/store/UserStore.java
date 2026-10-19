package com.carddemo.online.useradmin.store;

import java.util.List;
import java.util.Optional;

/**
 * USRSEC VSAM KSDS access used by COUSR00C-COUSR03C. Keys are compared in byte (EBCDIC-free ASCII)
 * order as the VSAM cluster does. All methods throw {@link UserStoreException} on any failure other
 * than the outcomes they model (CICS {@code RESP OTHER}).
 */
public interface UserStore {

  enum WriteOutcome {
    OK,
    DUPLICATE,
    NOT_FOUND,
    CONFLICT
  }

  /** {@code READ DATASET(USRSEC) RIDFLD(id)}. */
  Optional<UserRow> findById(String id);

  /** Records with key {@code >= fromKey} in ascending key order (STARTBR GTEQ + READNEXT). */
  List<UserRow> browseForward(String fromKey, int limit);

  /** Records with key {@code <= fromKey} in descending key order (STARTBR + READPREV). */
  List<UserRow> browseBackward(String fromKey, int limit);

  /** {@code WRITE}: {@link WriteOutcome#DUPLICATE} on DUPKEY/DUPREC. */
  WriteOutcome insert(UserRow row);

  /** {@code REWRITE} guarded by the row version read in the same unit of work. */
  WriteOutcome update(UserRow row);

  /** {@code DELETE} guarded by the row version read in the same unit of work. */
  WriteOutcome delete(UserRow row);
}
