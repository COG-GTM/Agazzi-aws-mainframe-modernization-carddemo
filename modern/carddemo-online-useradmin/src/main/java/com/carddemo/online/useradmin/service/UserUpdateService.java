package com.carddemo.online.useradmin.service;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.UserCredentialService;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.Cousr02Screen;
import com.carddemo.online.useradmin.session.UseradminState;
import com.carddemo.online.useradmin.store.UserRow;
import com.carddemo.online.useradmin.store.UserStore;
import com.carddemo.online.useradmin.support.Fields;
import com.carddemo.online.useradmin.support.Messages;
import com.carddemo.online.useradmin.support.ScreenHeader;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * COUSR02C (CU02): look up and update a user. REWRITE is guarded by the {@code @Version} read in
 * the same task; a concurrent change makes the REWRITE fail ("Unable to Update User...").
 */
@Service
public class UserUpdateService {

  static final ScreenId ME = ScreenId.COUSR02;

  private final UserStore store;
  private final UserCredentialService credentials;
  private final PasswordMatcher passwords;
  private final BusinessClock clock;

  public UserUpdateService(
      UserStore store,
      UserCredentialService credentials,
      PasswordMatcher passwords,
      BusinessClock clock) {
    this.store = store;
    this.credentials = credentials;
    this.passwords = passwords;
    this.clock = clock;
  }

  public ScreenResponse<Cousr02Screen.Output> send(ConversationContext ctx, UseradminState state) {
    Map m = new Map(null);
    if (!Fields.isBlank(state.selected())) {
      m.usridin = Fields.received(state.selected(), 8);
      processEnterKey(m);
    }
    return m.reply.toResponse(ME, m.output(), ctx, state);
  }

  public ScreenResponse<Cousr02Screen.Output> receive(
      Action action, Cousr02Screen.Input input, ConversationContext ctx, UseradminState state) {
    Map m = new Map(input);
    switch (action) {
      case ENTER -> processEnterKey(m);
      case PF3 -> {
        updateUserInfo(m);
        m.reply.returnToCaller(ctx);
      }
      case PF4 -> m.initializeAllFields();
      case PF5 -> updateUserInfo(m);
      case PF12 -> m.reply.transferTo(ScreenId.COADM01);
      default -> m.reply.error(Messages.INVALID_KEY, null);
    }
    return m.reply.toResponse(ME, m.output(), ctx, state);
  }

  private void processEnterKey(Map m) {
    if (Fields.isBlank(m.usridin)) {
      m.reply.error(Messages.USER_ID_EMPTY, "USRIDIN");
      return;
    }
    m.fname = "";
    m.lname = "";
    m.usrtype = "";
    Optional<UserRow> row = read(m);
    row.ifPresent(
        r -> {
          m.fname = r.firstName();
          m.lname = r.lastName();
          m.usrtype = r.type();
        });
  }

  private void updateUserInfo(Map m) {
    if (Fields.isBlank(m.usridin)) {
      m.reply.error(Messages.USER_ID_EMPTY, "USRIDIN");
    } else if (Fields.isBlank(m.fname)) {
      m.reply.error(Messages.FIRST_NAME_EMPTY, "FNAME");
    } else if (Fields.isBlank(m.lname)) {
      m.reply.error(Messages.LAST_NAME_EMPTY, "LNAME");
    } else if (Fields.isBlank(m.passwd)) {
      m.reply.error(Messages.PASSWORD_EMPTY, "PASSWD");
    } else if (Fields.isBlank(m.usrtype)) {
      m.reply.error(Messages.USER_TYPE_EMPTY, "USRTYPE");
    } else {
      Optional<UserRow> found = read(m);
      if (found.isEmpty()) {
        // READ UPDATE failed: the REWRITE that follows has no record to rewrite (RESP OTHER).
        m.reply.error(Messages.UNABLE_TO_UPDATE, "FNAME");
        return;
      }
      UserRow current = found.get();
      String fname = Fields.pad(m.fname, 20);
      String lname = Fields.pad(m.lname, 20);
      String type = Fields.pad(m.usrtype, 1);
      boolean samePassword = passwords.matches(m.passwd, current.password());
      boolean modified =
          !fname.equals(current.firstName())
              || !lname.equals(current.lastName())
              || !samePassword
              || !type.equals(current.type());
      if (!modified) {
        m.reply.warning(Messages.PLEASE_MODIFY);
        return;
      }
      updateUserSecFile(m, current, fname, lname, type, samePassword);
    }
  }

  private void updateUserSecFile(
      Map m, UserRow current, String fname, String lname, String type, boolean samePassword) {
    UserStore.WriteOutcome outcome;
    try {
      String pwd = samePassword ? current.password() : credentials.encode(Fields.rtrim(m.passwd));
      outcome =
          store.update(
              new UserRow(
                  current.id(), fname, lname, pwd, type, current.filler(), current.version()));
    } catch (RuntimeException e) {
      m.reply.error(Messages.UNABLE_TO_UPDATE, "FNAME");
      return;
    }
    switch (outcome) {
      case OK -> m.reply.info(Messages.updated(current.id()));
      case NOT_FOUND -> m.reply.error(Messages.USER_NOT_FOUND, "USRIDIN");
      default -> m.reply.error(Messages.UNABLE_TO_UPDATE, "FNAME");
    }
  }

  /** READ-USER-SEC-FILE. */
  private Optional<UserRow> read(Map m) {
    Optional<UserRow> row;
    try {
      row = store.findById(Fields.pad(m.usridin, 8));
    } catch (RuntimeException e) {
      m.reply.error(Messages.UNABLE_TO_LOOKUP, "FNAME");
      return Optional.empty();
    }
    if (row.isPresent()) {
      m.reply.info(Messages.PRESS_PF5_UPDATE);
    } else {
      m.reply.error(Messages.USER_NOT_FOUND, "USRIDIN");
    }
    return row;
  }

  /** COUSR2AI / COUSR2AO. PASSWD is never sent back to the terminal. */
  private final class Map {
    final Reply reply = new Reply();
    String usridin;
    String fname;
    String lname;
    String passwd;
    String usrtype;

    Map(Cousr02Screen.Input in) {
      usridin = Fields.received(in == null ? null : in.usridin(), 8);
      fname = Fields.received(in == null ? null : in.fname(), 20);
      lname = Fields.received(in == null ? null : in.lname(), 20);
      passwd = Fields.received(in == null ? null : in.passwd(), 8);
      usrtype = Fields.received(in == null ? null : in.usrtype(), 1);
    }

    void initializeAllFields() {
      usridin = "";
      fname = "";
      lname = "";
      passwd = "";
      usrtype = "";
      reply.clear();
    }

    Cousr02Screen.Output output() {
      ScreenHeader h = ScreenHeader.of(ME.transactionId(), ME.program(), clock);
      return new Cousr02Screen.Output(
          h.trnname(),
          Fields.rtrim(h.title01()),
          h.curdate(),
          h.pgmname(),
          Fields.rtrim(h.title02()),
          h.curtime(),
          Fields.rtrim(usridin),
          Fields.rtrim(fname),
          Fields.rtrim(lname),
          "",
          Fields.rtrim(usrtype),
          reply.message());
    }
  }
}
