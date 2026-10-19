package com.carddemo.online.useradmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.Cousr01Screen;
import com.carddemo.contracts.screen.map.Cousr02Screen;
import com.carddemo.contracts.screen.map.Cousr03Screen;
import com.carddemo.online.useradmin.session.UseradminState;
import com.carddemo.online.useradmin.store.UserRow;
import com.carddemo.online.useradmin.store.UserStoreException;
import com.carddemo.online.useradmin.support.InMemoryUserStore;
import com.carddemo.online.useradmin.support.Messages;
import com.carddemo.online.useradmin.support.TestCredentials;
import com.carddemo.platform.time.FixedBusinessClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UserMaintenanceServicesTest {

  private final ConversationContext ctx = ConversationContext.signedOn("ADMIN001", UserType.ADMIN);
  private final ConversationContext fromList =
      ctx.transferTo("CU00", "COUSR00C", "CU02", "COUSR02C");
  private InMemoryUserStore store;
  private UserAddService add;
  private UserUpdateService update;
  private UserDeleteService delete;
  private UseradminState st;

  @BeforeEach
  void setUp() {
    store = InMemoryUserStore.seeded();
    FixedBusinessClock clock = FixedBusinessClock.golden();
    add = new UserAddService(store, new TestCredentials(), clock);
    update = new UserUpdateService(store, new TestCredentials(), new PasswordMatcher(), clock);
    delete = new UserDeleteService(store, clock);
    st = new UseradminState();
  }

  private static String msg(ScreenResponse<?> r) {
    return r.messages().isEmpty() ? "" : r.messages().get(0).text();
  }

  private static String field(ScreenResponse<?> r) {
    return r.fieldErrors().isEmpty() ? null : r.fieldErrors().get(0).field();
  }

  // ---- CU01 ----

  @ParameterizedTest
  @CsvSource(
      nullValues = "null",
      value = {
        "null,L,ID,PW,U,First Name can NOT be empty...,FNAME",
        "F,'  ',ID,PW,U,Last Name can NOT be empty...,LNAME",
        "F,L,null,PW,U,User ID can NOT be empty...,USERID",
        "F,L,ID,'',U,Password can NOT be empty...,PASSWD",
        "F,L,ID,PW,null,User Type can NOT be empty...,USRTYPE",
        "null,null,null,null,null,First Name can NOT be empty...,FNAME"
      })
  void addValidatesInCobolOrder(
      String f, String l, String id, String pw, String t, String expected, String fld) {
    ScreenResponse<Cousr01Screen.Output> r =
        add.receive(Action.ENTER, new Cousr01Screen.Input(f, l, id, pw, t), ctx, st);
    assertThat(msg(r)).isEqualTo(expected);
    assertThat(field(r)).isEqualTo(fld);
    assertThat(r.data().passwd()).isEmpty();
    assertThat(store.snapshot()).hasSize(10);
  }

  @Test
  void addStoresBcryptHashAndClearsScreen() {
    ScreenResponse<Cousr01Screen.Output> r =
        add.receive(
            Action.ENTER, new Cousr01Screen.Input("Jane", "Doe", "NEWU01", "Secr3t", "U"), ctx, st);
    assertThat(msg(r)).isEqualTo("User NEWU01 has been added ...");
    assertThat(r.data().fname()).isEmpty();
    assertThat(r.data().passwd()).isEmpty();
    UserRow row = store.snapshot().get("NEWU01  ");
    assertThat(row.password()).startsWith("{bcrypt}$2");
    assertThat(new PasswordMatcher().matches("Secr3t", row.password())).isTrue();
    assertThat(row.firstName()).hasSize(20);
    assertThat(row.type()).isEqualTo("U");
  }

  @Test
  void addDuplicateAndFailure() {
    ScreenResponse<Cousr01Screen.Output> r =
        add.receive(Action.ENTER, new Cousr01Screen.Input("A", "B", "ADMIN001", "X", "A"), ctx, st);
    assertThat(msg(r)).isEqualTo(Messages.USER_ALREADY_EXISTS);
    assertThat(field(r)).isEqualTo("USERID");
    assertThat(r.data().userid()).isEqualTo("ADMIN001");
    store.failWith(new UserStoreException("down", null));
    r = add.receive(Action.ENTER, new Cousr01Screen.Input("A", "B", "NEW", "X", "A"), ctx, st);
    assertThat(msg(r)).isEqualTo(Messages.UNABLE_TO_ADD);
    assertThat(field(r)).isEqualTo("FNAME");
  }

  @Test
  void addKeys() {
    assertThat(add.send(ctx, st).data().trnname()).isEqualTo("CU01");
    Cousr01Screen.Input in = new Cousr01Screen.Input("A", "B", "C", "D", "U");
    assertThat(add.receive(Action.PF3, in, ctx, st).nextScreen()).isEqualTo(ScreenId.COADM01);
    ScreenResponse<Cousr01Screen.Output> r = add.receive(Action.PF4, in, ctx, st);
    assertThat(r.data().fname()).isEmpty();
    assertThat(r.messages()).isEmpty();
    assertThat(msg(add.receive(Action.PF7, in, ctx, st))).isEqualTo(Messages.INVALID_KEY);
  }

  // ---- CU02 ----

  @Test
  void updateFirstSendLoadsSelectedUserWithoutPassword() {
    st.selected("USER0001");
    ScreenResponse<Cousr02Screen.Output> r = update.send(fromList, st);
    assertThat(r.data().usridin()).isEqualTo("USER0001");
    assertThat(r.data().fname()).isEqualTo("USER");
    assertThat(r.data().passwd()).isEmpty();
    assertThat(msg(r)).isEqualTo(Messages.PRESS_PF5_UPDATE);
    assertThat(r.messages().get(0).severity().name()).isEqualTo("INFO");
    assertThat(update.send(fromList, new UseradminState()).messages()).isEmpty();
  }

  @Test
  void updateEnterErrors() {
    ScreenResponse<Cousr02Screen.Output> r =
        update.receive(Action.ENTER, new Cousr02Screen.Input(" ", null, null, null, null), ctx, st);
    assertThat(msg(r)).isEqualTo(Messages.USER_ID_EMPTY);
    r =
        update.receive(
            Action.ENTER, new Cousr02Screen.Input("NOPE", "x", null, null, null), ctx, st);
    assertThat(msg(r)).isEqualTo(Messages.USER_NOT_FOUND);
    assertThat(field(r)).isEqualTo("USRIDIN");
    assertThat(r.data().fname()).isEmpty();
    store.failWith(new UserStoreException("down", null));
    r =
        update.receive(
            Action.ENTER, new Cousr02Screen.Input("USER0001", null, null, null, null), ctx, st);
    assertThat(msg(r)).isEqualTo(Messages.UNABLE_TO_LOOKUP);
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "null",
      value = {
        "null,F,L,P,U,User ID can NOT be empty...,USRIDIN",
        "USER0001,null,L,P,U,First Name can NOT be empty...,FNAME",
        "USER0001,F,null,P,U,Last Name can NOT be empty...,LNAME",
        "USER0001,F,L,null,U,Password can NOT be empty...,PASSWD",
        "USER0001,F,L,P,null,User Type can NOT be empty...,USRTYPE",
        "NOPE,F,L,P,U,Unable to Update User...,FNAME"
      })
  void updateValidatesInCobolOrder(
      String id, String f, String l, String p, String t, String expected, String fld) {
    ScreenResponse<Cousr02Screen.Output> r =
        update.receive(Action.PF5, new Cousr02Screen.Input(id, f, l, p, t), ctx, st);
    assertThat(msg(r)).isEqualTo(expected);
    assertThat(field(r)).isEqualTo(fld);
  }

  @Test
  void updateDetectsModificationsAndRehashesPassword() {
    ScreenResponse<Cousr02Screen.Output> r =
        update.receive(
            Action.PF5,
            new Cousr02Screen.Input("USER0001", "USER", "0001", "PASSWORD", "U"),
            ctx,
            st);
    assertThat(msg(r)).isEqualTo(Messages.PLEASE_MODIFY);
    assertThat(r.fieldErrors()).isEmpty();
    assertThat(r.messages().get(0).severity().name()).isEqualTo("ERROR");

    r =
        update.receive(
            Action.PF5,
            new Cousr02Screen.Input("USER0001", "Mario", "0001", "PASSWORD", "U"),
            ctx,
            st);
    assertThat(msg(r)).isEqualTo("User USER0001 has been updated ...");
    UserRow row = store.snapshot().get("USER0001");
    assertThat(row.firstName().trim()).isEqualTo("Mario");
    assertThat(row.password()).isEqualTo("PASSWORD");
    assertThat(row.version()).isEqualTo(1L);

    r =
        update.receive(
            Action.PF5,
            new Cousr02Screen.Input("USER0001", "Mario", "0001", "NEWPASS", "A"),
            ctx,
            st);
    assertThat(msg(r)).isEqualTo("User USER0001 has been updated ...");
    row = store.snapshot().get("USER0001");
    assertThat(row.password()).startsWith("{bcrypt}");
    assertThat(row.type()).isEqualTo("A");
    assertThat(r.data().passwd()).isEmpty();

    r =
        update.receive(
            Action.PF5,
            new Cousr02Screen.Input("USER0001", "Mario", "0001", "NEWPASS", "A"),
            ctx,
            st);
    assertThat(msg(r)).isEqualTo(Messages.PLEASE_MODIFY);
  }

  @Test
  void concurrentRewriteFailsWithUnableToUpdate() {
    InMemoryUserStore racing =
        new InMemoryUserStore() {
          @Override
          public synchronized java.util.Optional<UserRow> findById(String id) {
            java.util.Optional<UserRow> r = store.findById(id);
            // another terminal rewrites the record between READ UPDATE and REWRITE
            r.ifPresent(
                u ->
                    store.update(
                        new UserRow(
                            u.id(),
                            u.firstName(),
                            "OTHER",
                            u.password(),
                            u.type(),
                            u.filler(),
                            u.version())));
            return r;
          }

          @Override
          public synchronized WriteOutcome update(UserRow row) {
            return store.update(row);
          }
        };
    UserUpdateService svc =
        new UserUpdateService(
            racing, new TestCredentials(), new PasswordMatcher(), FixedBusinessClock.golden());
    ScreenResponse<Cousr02Screen.Output> r =
        svc.receive(
            Action.PF5,
            new Cousr02Screen.Input("USER0002", "Luigi", "0002", "PASSWORD", "U"),
            ctx,
            st);
    assertThat(msg(r)).isEqualTo(Messages.UNABLE_TO_UPDATE);
    assertThat(field(r)).isEqualTo("FNAME");
    assertThat(store.snapshot().get("USER0002").lastName().trim()).isEqualTo("OTHER");
  }

  @Test
  void updateKeysAndNavigation() {
    Cousr02Screen.Input in = new Cousr02Screen.Input("USER0001", "Z", "0001", "PASSWORD", "U");
    ScreenResponse<Cousr02Screen.Output> r = update.receive(Action.PF3, in, fromList, st);
    assertThat(r.nextScreen()).isEqualTo(ScreenId.COUSR00);
    assertThat(msg(r)).isEqualTo("User USER0001 has been updated ...");
    assertThat(update.receive(Action.PF3, in, ctx, st).nextScreen()).isEqualTo(ScreenId.COSGN00);
    assertThat(update.receive(Action.PF12, in, ctx, st).nextScreen()).isEqualTo(ScreenId.COADM01);
    r = update.receive(Action.PF4, in, ctx, st);
    assertThat(r.data().usridin()).isEmpty();
    assertThat(r.messages()).isEmpty();
    r = update.receive(Action.PF8, in, ctx, st);
    assertThat(msg(r)).isEqualTo(Messages.INVALID_KEY);
    assertThat(r.fieldErrors()).isEmpty();
    store.failWith(new UserStoreException("down", null));
    assertThat(msg(update.receive(Action.PF5, in, ctx, st))).isEqualTo(Messages.UNABLE_TO_UPDATE);
  }

  // ---- CU03 ----

  @Test
  void deleteFlow() {
    st.selected("USER0003");
    ScreenResponse<Cousr03Screen.Output> r = delete.send(fromList, st);
    assertThat(msg(r)).isEqualTo(Messages.PRESS_PF5_DELETE);
    assertThat(r.data().lname()).isEqualTo("0003");
    r = delete.receive(Action.PF5, new Cousr03Screen.Input("USER0003"), ctx, st);
    assertThat(msg(r)).isEqualTo("User USER0003 has been deleted ...");
    assertThat(r.data().usridin()).isEmpty();
    assertThat(store.snapshot()).doesNotContainKey("USER0003");
    r = delete.receive(Action.PF5, new Cousr03Screen.Input("USER0003"), ctx, st);
    assertThat(msg(r)).isEqualTo(Messages.UNABLE_TO_UPDATE);
    r = delete.receive(Action.ENTER, new Cousr03Screen.Input("USER0003"), ctx, st);
    assertThat(msg(r)).isEqualTo(Messages.USER_NOT_FOUND);
    r = delete.receive(Action.PF5, new Cousr03Screen.Input(""), ctx, st);
    assertThat(msg(r)).isEqualTo(Messages.USER_ID_EMPTY);
    r = delete.receive(Action.ENTER, new Cousr03Screen.Input(null), ctx, st);
    assertThat(msg(r)).isEqualTo(Messages.USER_ID_EMPTY);
  }

  @Test
  void deleteKeysAndFailures() {
    Cousr03Screen.Input in = new Cousr03Screen.Input("USER0004");
    assertThat(delete.receive(Action.PF3, in, fromList, st).nextScreen())
        .isEqualTo(ScreenId.COUSR00);
    assertThat(delete.receive(Action.PF12, in, ctx, st).nextScreen()).isEqualTo(ScreenId.COADM01);
    assertThat(delete.receive(Action.PF4, in, ctx, st).data().usridin()).isEmpty();
    assertThat(msg(delete.receive(Action.PA1, in, ctx, st))).isEqualTo(Messages.INVALID_KEY);
    assertThat(delete.send(ctx, new UseradminState()).messages()).isEmpty();
    store.failWith(new UserStoreException("down", null));
    assertThat(msg(delete.receive(Action.ENTER, in, ctx, st))).isEqualTo(Messages.UNABLE_TO_LOOKUP);
    assertThat(msg(delete.receive(Action.PF5, in, ctx, st))).isEqualTo(Messages.UNABLE_TO_UPDATE);
  }

  @Test
  void concurrentDeleteFails() {
    InMemoryUserStore racing =
        new InMemoryUserStore() {
          @Override
          public synchronized java.util.Optional<UserRow> findById(String id) {
            java.util.Optional<UserRow> r = store.findById(id);
            r.ifPresent(u -> store.update(u));
            return r;
          }

          @Override
          public synchronized WriteOutcome delete(UserRow row) {
            return store.delete(row);
          }
        };
    UserDeleteService svc = new UserDeleteService(racing, FixedBusinessClock.golden());
    ScreenResponse<Cousr03Screen.Output> r =
        svc.receive(Action.PF5, new Cousr03Screen.Input("USER0005"), ctx, st);
    assertThat(msg(r)).isEqualTo(Messages.UNABLE_TO_UPDATE);
    assertThat(store.snapshot()).containsKey("USER0005");
  }
}
