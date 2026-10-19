package com.carddemo.ext.trantype.online;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenMessage;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.CotrtliScreen;
import com.carddemo.ext.trantype.store.TrantypeSqlException;
import com.carddemo.ext.trantype.support.InMemoryTransactionTypeStore;
import com.carddemo.platform.time.FixedBusinessClock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CtliServiceTest {

  private InMemoryTransactionTypeStore store;
  private CtliService service;
  private CtliState state;

  @BeforeEach
  void setUp() {
    store = InMemoryTransactionTypeStore.seeded();
    for (int i = 10; i < 26; i++) {
      store.types.put(String.valueOf(i), "Type " + i);
    }
    service = new CtliService(store, FixedBusinessClock.golden());
    state = new CtliState();
  }

  private static CtliInput sel(int row, String action, String desc) {
    String[] s = new String[7];
    String[] d = new String[7];
    s[row - 1] = action;
    d[row - 1] = desc;
    return new CtliInput(
        null, null, s[0], s[1], s[2], s[3], s[4], s[5], s[6], d[0], d[1], d[2], d[3], d[4], d[5],
        d[6]);
  }

  private static CtliInput filter(String type, String desc) {
    return CtliInput.fromContract(
        new CotrtliScreen.Input(type, desc, null, null, null, null, null, null, null));
  }

  private static List<String> texts(ScreenResponse<?> r) {
    return r.messages().stream().map(ScreenMessage::text).toList();
  }

  @Test
  void firstSendFillsHeaderAndFirstPage() {
    ScreenResponse<CotrtliScreen.Output> r = service.firstSend(state, "ADMIN001");
    CotrtliScreen.Output o = r.data();
    assertThat(o.trnname()).isEqualTo("CTLI");
    assertThat(o.pgmname()).isEqualTo("COTRTLIC");
    assertThat(o.curdate()).isEqualTo("07/18/22");
    assertThat(o.curtime()).isEqualTo("12:00:00");
    assertThat(o.pageno()).isEqualTo("1");
    assertThat(o.trttyp1()).isEqualTo("01");
    assertThat(o.trtypd1()).isEqualTo("Purchase");
    assertThat(o.trttyp7()).isEqualTo("07");
    assertThat(state.lastKey()).isEqualTo("10");
    assertThat(r.context().userId()).isEqualTo("ADMIN001");
    assertThat(texts(r)).containsExactly(CtliService.INFO_REC_ACTIONS);
  }

  @Test
  void keysetForwardAndBackward() {
    service.firstSend(state, "U");
    ScreenResponse<CotrtliScreen.Output> p2 = service.receive(Action.PF8, null, state, "U");
    assertThat(p2.data().trttyp1()).isEqualTo("10");
    assertThat(p2.data().pageno()).isEqualTo("2");
    service.receive(Action.PF8, null, state, "U");
    ScreenResponse<CotrtliScreen.Output> p4 = service.receive(Action.PF8, null, state, "U");
    assertThat(p4.data().trttyp1()).isEqualTo("24");
    assertThat(p4.data().trttyp3()).isEmpty();
    assertThat(texts(p4)).contains(CtliService.MSG_NO_MORE_RECORDS);
    ScreenResponse<CotrtliScreen.Output> p3 = service.receive(Action.PF7, null, state, "U");
    assertThat(p3.data().trttyp1()).isEqualTo("17");
    assertThat(p3.data().trttyp7()).isEqualTo("23");
    assertThat(p3.data().pageno()).isEqualTo("3");
  }

  @Test
  void screenNumberIsPic91() {
    state.screenNum = 9;
    state.nextScreen();
    assertThat(state.screenNum()).isZero();
    assertThat(state.firstKey()).isEmpty();
  }

  @Test
  void invalidKeyActsAsEnterAndUnenteredStateFirstSends() {
    ScreenResponse<CotrtliScreen.Output> r = service.receive(Action.PF5, null, state, "U");
    assertThat(r.data().trttyp1()).isEqualTo("01");
    r = service.receive(Action.PF5, null, state, "U");
    assertThat(texts(r)).containsExactly(CtliService.INFO_REC_ACTIONS);
  }

  @Test
  void pf10WithChangedSelectionIsEnter() {
    service.firstSend(state, "U");
    service.receive(Action.ENTER, sel(1, "D", null), state, "U");
    ScreenResponse<CotrtliScreen.Output> r =
        service.receive(Action.PF10, sel(2, "D", null), state, "U");
    assertThat(texts(r)).contains(CtliService.INFO_DELETE);
    assertThat(store.types).containsKey("02");
  }

  @Test
  void filterChangeResetsPagingAndDescFilterUsesLike() {
    service.firstSend(state, "U");
    service.receive(Action.PF8, null, state, "U");
    ScreenResponse<CotrtliScreen.Output> r =
        service.receive(Action.ENTER, filter(null, "Type 2"), state, "U");
    assertThat(r.data().trttyp1()).isEqualTo("20");
    assertThat(r.data().trdesc()).isEqualTo("Type 2");
    assertThat(r.data().pageno()).isEqualTo("1");
    r = service.receive(Action.ENTER, filter("02", null), state, "U");
    assertThat(r.data().trttyp1()).isEqualTo("02");
    assertThat(r.data().trttyp2()).isEmpty();
    r = service.receive(Action.ENTER, filter("0", null), state, "U");
    assertThat(r.data().trttyp1()).as("blank filter keeps WS-CA paging").isEqualTo("02");
  }

  @Test
  void updatePendingShowsNewDescriptionAndDbErrorsAreFormatted() {
    service.firstSend(state, "U");
    ScreenResponse<CotrtliScreen.Output> r =
        service.receive(Action.ENTER, sel(3, "U", "Credit Memo"), state, "U");
    assertThat(r.data().trtypd3()).isEqualTo("Credit Memo");
    assertThat(r.data().trtsel3()).isEqualTo("U");
    store.types.remove("03");
    r = service.receive(Action.PF10, sel(3, "U", "Credit Memo"), state, "U");
    assertThat(texts(r))
        .contains("Record not found. Deleted by others ? SQLCODE:  100 ROW NOT FOUND");
  }

  @Test
  void updateDeadlockAndGenericFailures() {
    service.firstSend(state, "U");
    service.receive(Action.ENTER, sel(3, "U", "Credit Memo"), state, "U");
    store.failWith = new TrantypeSqlException(TrantypeSqlException.DEADLOCK, "LOCK");
    CtliState copy = state;
    ScreenResponse<CotrtliScreen.Output> r =
        service.receive(Action.PF10, sel(3, "U", "Credit Memo"), copy, "U");
    assertThat(texts(r))
        .anyMatch(t -> t.startsWith("Deadlock. Someone else updating ? SQLCODE: -911"));
    store.failWith = new TrantypeSqlException(TrantypeSqlException.GENERIC, "BOOM");
    service.firstSend(state, "U");
    service.receive(Action.ENTER, sel(3, "U", "Credit Memo"), state, "U");
    store.failWith = null;
    service.receive(Action.ENTER, sel(3, "U", "Credit Memo"), state, "U");
    store.failWith = new TrantypeSqlException(TrantypeSqlException.GENERIC, "BOOM");
    r = service.receive(Action.PF10, sel(3, "U", "Credit Memo"), state, "U");
    assertThat(texts(r)).contains("Update failed with SQLCODE: -999 BOOM");
  }

  @Test
  void readErrorsAreReported() {
    store.failWith = new TrantypeSqlException(TrantypeSqlException.GENERIC, "DOWN");
    ScreenResponse<CotrtliScreen.Output> r = service.firstSend(state, "U");
    assertThat(texts(r)).contains("C-TR-TYPE-FORWARD fetch SQLCODE: -999 DOWN");
    store.failWith = null;
    service.firstSend(state, "U");
    service.receive(Action.PF8, null, state, "U");
    service.receive(Action.PF8, null, state, "U");
    store.failWith = new TrantypeSqlException(TrantypeSqlException.GENERIC, "DOWN");
    r = service.receive(Action.PF7, null, state, "U");
    assertThat(texts(r)).contains("Error on fetch Cursor C-TR-TYPE-BACKWARD SQLCODE: -999 DOWN");
  }

  @Test
  void deleteFailureOtherThanChildRows() {
    service.firstSend(state, "U");
    service.receive(Action.ENTER, sel(1, "D", null), state, "U");
    store.types.remove("01");
    ScreenResponse<CotrtliScreen.Output> r =
        service.receive(Action.PF10, sel(1, "D", null), state, "U");
    assertThat(texts(r)).contains("Delete failed with message: SQLCODE:  100 ROW NOT FOUND");
  }

  @Test
  void emptyTableShowsNoRows() {
    store.types.clear();
    ScreenResponse<CotrtliScreen.Output> r = service.firstSend(state, "U");
    assertThat(r.data().trttyp1()).isEmpty();
    assertThat(texts(r)).containsExactly(CtliService.MSG_NO_RECORDS);
  }

  @Test
  void exitResponsesCarryNextScreen() {
    service.firstSend(state, "U");
    ScreenResponse<CotrtliScreen.Output> r = service.receive(Action.PF3, null, state, "U");
    assertThat(r.nextScreen()).isEqualTo(ScreenId.COADM01);
    assertThat(r.context().toProgram()).isEqualTo("COADM01C");
  }

  @Test
  void contractInputRoundTrip() {
    CotrtliScreen.Input in = new CotrtliScreen.Input("01", "x", "a", "b", "c", "d", "e", "f", "g");
    assertThat(CtliInput.fromContract(in).toContract()).isEqualTo(in);
  }
}
