package com.carddemo.online.useradmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenMessage;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.Cousr00Screen;
import com.carddemo.online.useradmin.session.UseradminState;
import com.carddemo.online.useradmin.store.UserStoreException;
import com.carddemo.online.useradmin.support.InMemoryUserStore;
import com.carddemo.online.useradmin.support.Messages;
import com.carddemo.platform.time.FixedBusinessClock;
import org.junit.jupiter.api.Test;

class UserListServiceTest {

  private final ConversationContext ctx = ConversationContext.signedOn("ADMIN001", UserType.ADMIN);

  private static Cousr00Screen.Input in(String usridin, int selRow, String sel) {
    String[] s = new String[10];
    if (selRow > 0) {
      s[selRow - 1] = sel;
    }
    return new Cousr00Screen.Input(
        usridin, s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7], s[8], s[9]);
  }

  private static InMemoryUserStore store(int users) {
    InMemoryUserStore s = new InMemoryUserStore();
    for (int i = 1; i <= users; i++) {
      s.put(String.format("U%07d", i), "F" + i, "L" + i, "PASSWORD", "U");
    }
    return s;
  }

  private static String msg(ScreenResponse<?> r) {
    return r.messages().isEmpty() ? "" : r.messages().get(0).text();
  }

  @Test
  void firstSendWithSeedShowsTenUsersAndBottomNotice() {
    UserListService svc =
        new UserListService(InMemoryUserStore.seeded(), FixedBusinessClock.golden());
    UseradminState st = new UseradminState();
    ScreenResponse<Cousr00Screen.Output> r = svc.send(ctx, st);
    Cousr00Screen.Output o = r.data();
    assertThat(r.screen()).isEqualTo(ScreenId.COUSR00);
    assertThat(o.usrid01()).isEqualTo("ADMIN001");
    assertThat(o.usrid10()).isEqualTo("USER0005");
    assertThat(o.fname01()).isEqualTo("ADMIN");
    assertThat(o.utype06()).isEqualTo("U");
    assertThat(o.pagenum()).isEqualTo("00000001");
    assertThat(o.trnname()).isEqualTo("CU00");
    assertThat(o.pgmname()).isEqualTo("COUSR00C");
    assertThat(o.curdate()).isEqualTo("07/18/22");
    assertThat(o.curtime()).isEqualTo("12:00:00");
    assertThat(msg(r)).isEqualTo(Messages.REACHED_BOTTOM);
    assertThat(r.messages().get(0).severity()).isEqualTo(ScreenMessage.Severity.INFO);
    assertThat(st.nextPage()).isFalse();
    assertThat(r.context().programContext()).isEqualTo(ConversationContext.ProgramContext.REENTER);
  }

  @Test
  void pagesForwardAndBackwardOverTwentyFiveUsers() {
    UserListService svc = new UserListService(store(25), FixedBusinessClock.golden());
    UseradminState st = new UseradminState();
    ScreenResponse<Cousr00Screen.Output> r = svc.send(ctx, st);
    assertThat(msg(r)).isEmpty();
    assertThat(r.data().usrid01()).isEqualTo("U0000001");
    assertThat(st.nextPage()).isTrue();

    r = svc.receive(Action.PF8, in("", 0, null), ctx, st);
    assertThat(r.data().usrid01()).isEqualTo("U0000011");
    assertThat(r.data().usrid10()).isEqualTo("U0000020");
    assertThat(r.data().pagenum()).isEqualTo("00000002");

    r = svc.receive(Action.PF8, in("", 0, null), ctx, st);
    assertThat(r.data().usrid01()).isEqualTo("U0000021");
    assertThat(r.data().usrid05()).isEqualTo("U0000025");
    assertThat(r.data().usrid06()).isEmpty();
    assertThat(r.data().pagenum()).isEqualTo("00000003");
    assertThat(msg(r)).isEqualTo(Messages.REACHED_BOTTOM);

    r = svc.receive(Action.PF8, in("", 0, null), ctx, st);
    assertThat(msg(r)).isEqualTo(Messages.ALREADY_BOTTOM);
    assertThat(r.data().usrid01()).isEqualTo("U0000021");

    r = svc.receive(Action.PF7, in("", 0, null), ctx, st);
    assertThat(r.data().usrid01()).isEqualTo("U0000011");
    assertThat(r.data().usrid10()).isEqualTo("U0000020");
    assertThat(r.data().pagenum()).isEqualTo("00000002");
    assertThat(msg(r)).isEmpty();

    r = svc.receive(Action.PF7, in("", 0, null), ctx, st);
    assertThat(r.data().usrid01()).isEqualTo("U0000001");
    assertThat(r.data().pagenum()).isEqualTo("00000001");
    assertThat(msg(r)).isEqualTo(Messages.REACHED_TOP);

    r = svc.receive(Action.PF7, in("", 0, null), ctx, st);
    assertThat(msg(r)).isEqualTo(Messages.ALREADY_TOP);
  }

  @Test
  void enterPositionsOnUserIdAndTopNoticeWhenPastLastKey() {
    UserListService svc = new UserListService(store(25), FixedBusinessClock.golden());
    UseradminState st = new UseradminState();
    svc.send(ctx, st);
    ScreenResponse<Cousr00Screen.Output> r =
        svc.receive(Action.ENTER, in("U0000015", 0, null), ctx, st);
    assertThat(r.data().usrid01()).isEqualTo("U0000015");
    assertThat(r.data().usridin()).isEmpty();
    assertThat(r.data().pagenum()).isEqualTo("00000001");

    r = svc.receive(Action.ENTER, in("ZZZZZZZZ", 0, null), ctx, st);
    assertThat(msg(r)).isEqualTo(Messages.AT_TOP);
    assertThat(r.data().usrid01()).isEqualTo("U0000015");
  }

  @Test
  void pf8AfterNotFoundBrowseIsUnableToLookup() {
    UserListService svc = new UserListService(new InMemoryUserStore(), FixedBusinessClock.golden());
    UseradminState st = new UseradminState();
    st.nextPage(true);
    st.usridLast("ZZZZZZZZ");
    ScreenResponse<Cousr00Screen.Output> r = svc.receive(Action.PF8, in("", 0, null), ctx, st);
    assertThat(msg(r)).isEqualTo(Messages.UNABLE_TO_LOOKUP);
    assertThat(r.fieldErrors()).extracting(e -> e.field()).containsExactly("USRIDIN");
  }

  @Test
  void selectionTransfersToUpdateOrDelete() {
    UserListService svc =
        new UserListService(InMemoryUserStore.seeded(), FixedBusinessClock.golden());
    UseradminState st = new UseradminState();
    svc.send(ctx, st);
    ScreenResponse<Cousr00Screen.Output> r = svc.receive(Action.ENTER, in("", 3, "u"), ctx, st);
    assertThat(r.nextScreen()).isEqualTo(ScreenId.COUSR02);
    assertThat(st.selected()).isEqualTo("ADMIN003");
    assertThat(r.context().fromProgram()).isEqualTo("COUSR00C");
    assertThat(r.context().toProgram()).isEqualTo("COUSR02C");

    svc.send(ctx, st);
    r = svc.receive(Action.ENTER, in("", 7, "D"), ctx, st);
    assertThat(r.nextScreen()).isEqualTo(ScreenId.COUSR03);
    assertThat(st.selected()).isEqualTo("USER0002");
  }

  @Test
  void invalidSelectionMessageIsKeptWhenMorePagesFollow() {
    UserListService svc = new UserListService(store(25), FixedBusinessClock.golden());
    UseradminState st = new UseradminState();
    svc.send(ctx, st);
    ScreenResponse<Cousr00Screen.Output> r = svc.receive(Action.ENTER, in("", 1, "X"), ctx, st);
    assertThat(r.nextScreen()).isNull();
    assertThat(msg(r)).isEqualTo(Messages.INVALID_SELECTION);
    assertThat(r.messages().get(0).severity()).isEqualTo(ScreenMessage.Severity.ERROR);
    assertThat(r.data().sel0001()).isEqualTo("X");
  }

  @Test
  void pf3ReturnsToAdminMenuAndOtherKeysAreInvalid() {
    UserListService svc =
        new UserListService(InMemoryUserStore.seeded(), FixedBusinessClock.golden());
    UseradminState st = new UseradminState();
    svc.send(ctx, st);
    ScreenResponse<Cousr00Screen.Output> r = svc.receive(Action.PF3, null, ctx, st);
    assertThat(r.nextScreen()).isEqualTo(ScreenId.COADM01);
    assertThat(st.usridFirst()).isEmpty();

    r = svc.receive(Action.PF5, in("", 0, null), ctx, st);
    assertThat(msg(r)).isEqualTo(Messages.INVALID_KEY);
    assertThat(r.fieldErrors()).hasSize(1);
  }

  @Test
  void storeFailureIsUnableToLookup() {
    InMemoryUserStore s = InMemoryUserStore.seeded();
    s.failWith(new UserStoreException("boom", null));
    UserListService svc = new UserListService(s, FixedBusinessClock.golden());
    UseradminState st = new UseradminState();
    ScreenResponse<Cousr00Screen.Output> r = svc.send(ctx, st);
    assertThat(msg(r)).isEqualTo(Messages.UNABLE_TO_LOOKUP);
    st.pageNum(2);
    st.usridFirst("ADMIN001");
    r = svc.receive(Action.PF7, in("", 0, null), ctx, st);
    assertThat(msg(r)).isEqualTo(Messages.UNABLE_TO_LOOKUP);
  }

  @Test
  void backwardFromVanishedFirstKeyHitsTop() {
    InMemoryUserStore s = store(3);
    UserListService svc = new UserListService(s, FixedBusinessClock.golden());
    UseradminState st = new UseradminState();
    st.pageNum(2);
    st.usridFirst("ZZZZZZZZ");
    ScreenResponse<Cousr00Screen.Output> r = svc.receive(Action.PF7, in("", 0, null), ctx, st);
    assertThat(msg(r)).isEqualTo(Messages.UNABLE_TO_LOOKUP);
  }
}
