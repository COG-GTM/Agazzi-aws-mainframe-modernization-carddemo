package com.carddemo.ext.trantype.online;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenMessage;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.CotrtupScreen;
import com.carddemo.ext.trantype.online.CttuState.Mode;
import com.carddemo.ext.trantype.store.TrantypeSqlException;
import com.carddemo.ext.trantype.support.InMemoryTransactionTypeStore;
import com.carddemo.platform.time.FixedBusinessClock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CttuServiceTest {

  private InMemoryTransactionTypeStore store;
  private CttuService service;
  private CttuState state;

  @BeforeEach
  void setUp() {
    store = InMemoryTransactionTypeStore.seeded();
    service = new CttuService(store, FixedBusinessClock.golden());
    state = new CttuState();
    service.firstSend(state, "ADMIN001");
  }

  private ScreenResponse<CotrtupScreen.Output> key(Action a, String type, String desc) {
    return service.receive(a, new CotrtupScreen.Input(type, desc), state, "ADMIN001");
  }

  private static List<String> texts(ScreenResponse<?> r) {
    return r.messages().stream().map(ScreenMessage::text).toList();
  }

  @Test
  void showsFetchedRecord() {
    ScreenResponse<CotrtupScreen.Output> r = key(Action.ENTER, "1", null);
    assertThat(state.mode()).isEqualTo(Mode.SHOW_DETAILS);
    assertThat(r.data().trtypcd()).isEqualTo("01");
    assertThat(r.data().trtydsc()).isEqualTo("Purchase");
    assertThat(r.data().pgmname()).isEqualTo("COTRTUPC");
  }

  @Test
  void lockAndUpdateFailures() {
    key(Action.ENTER, "01", null);
    key(Action.ENTER, "01", "New one");
    store.failWith = new TrantypeSqlException(TrantypeSqlException.DEADLOCK, "LOCK");
    ScreenResponse<CotrtupScreen.Output> r = key(Action.PF5, "01", "New one");
    assertThat(state.mode()).isEqualTo(Mode.CHANGES_OKAYED_LOCK_ERROR);
    assertThat(texts(r)).containsExactly(CttuService.INFORM_FAILURE, CttuService.MSG_LOCK);
    store.failWith = null;
    r = key(Action.ENTER, "", null);
    assertThat(state.mode()).isEqualTo(Mode.NOT_FETCHED);
    key(Action.ENTER, "01", null);
    key(Action.ENTER, "01", "New one");
    store.failWith = new TrantypeSqlException(TrantypeSqlException.GENERIC, "X");
    r = key(Action.PF5, "01", "New one");
    assertThat(texts(r)).contains("Error updating: TRANSACTION_TYPE Table. SQLCODE: -999:X");
  }

  @Test
  void insertFailureAndReadFailure() {
    key(Action.ENTER, "09", null);
    key(Action.PF5, "09", null);
    key(Action.ENTER, "", "Interest");
    assertThat(state.mode()).isEqualTo(Mode.CHANGES_OK_NOT_CONFIRMED);
    store.types.put("09", "x");
    store.types.remove("09");
    InMemoryTransactionTypeStore failing =
        new InMemoryTransactionTypeStore() {
          @Override
          public void updateDescription(String t, String d) {
            throw new TrantypeSqlException(TrantypeSqlException.NOT_FOUND, "NF");
          }

          @Override
          public void insert(String t, String d) {
            throw new TrantypeSqlException(TrantypeSqlException.DUPLICATE_KEY, "DUP");
          }
        };
    ScreenResponse<CotrtupScreen.Output> r =
        new CttuService(failing, FixedBusinessClock.golden())
            .receive(Action.PF5, new CotrtupScreen.Input("09", "Interest"), state, "U");
    assertThat(texts(r))
        .contains("Error inserting record into: TRANSACTION_TYPE Table. SQLCODE: -803:DUP");
    state.reset();
    store.failWith = new TrantypeSqlException(TrantypeSqlException.GENERIC, "DOWN");
    r = key(Action.ENTER, "01", null);
    assertThat(texts(r)).contains("Error accessing: TRANSACTION_TYPE table. SQLCODE: -999:DOWN");
  }

  @Test
  void genericDeleteFailure() {
    store.types.put("09", "Interest");
    key(Action.ENTER, "09", null);
    key(Action.PF4, "09", null);
    store.failWith = new TrantypeSqlException(TrantypeSqlException.GENERIC, "E");
    ScreenResponse<CotrtupScreen.Output> r = key(Action.PF4, "09", null);
    assertThat(state.mode()).isEqualTo(Mode.DELETE_FAILED);
    assertThat(texts(r)).contains("Delete failed with message:SQLCODE : -999:E");
  }

  @Test
  void pf3ReturnsToCallerList() {
    state.enteredFrom(CtliService.TRANID, CtliService.PGM);
    service.firstSend(state, "U");
    ScreenResponse<CotrtupScreen.Output> r = key(Action.PF3, null, null);
    assertThat(r.nextScreen()).isEqualTo(ScreenId.COTRTLI);
    assertThat(r.context().toTransactionId()).isEqualTo("CTLI");
  }

  @Test
  void backedOutThenEnterRevalidates() {
    key(Action.ENTER, "01", null);
    key(Action.ENTER, "01", "Other");
    ScreenResponse<CotrtupScreen.Output> r = key(Action.PF12, "01", "Other");
    assertThat(state.mode()).isEqualTo(Mode.CHANGES_BACKED_OUT);
    assertThat(r.data().trtydsc()).isEqualTo("Purchase");
    assertThat(texts(r)).contains(CttuService.PROMPT_CHANGES);
    key(Action.ENTER, "01", "Third");
    assertThat(state.mode()).isEqualTo(Mode.CHANGES_OK_NOT_CONFIRMED);
  }

  @Test
  void notFoundSameKeyEnterRefetches() {
    key(Action.ENTER, "09", null);
    ScreenResponse<CotrtupScreen.Output> r = key(Action.ENTER, "09", null);
    assertThat(state.mode()).isEqualTo(Mode.DETAILS_NOT_FOUND);
    assertThat(texts(r)).contains(CttuService.MSG_NOT_FOUND);
  }
}
