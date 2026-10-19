package com.carddemo.online.useradmin.service;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.Cousr03Screen;
import com.carddemo.online.useradmin.session.UseradminState;
import com.carddemo.online.useradmin.store.UserRow;
import com.carddemo.online.useradmin.store.UserStore;
import com.carddemo.online.useradmin.support.Fields;
import com.carddemo.online.useradmin.support.Messages;
import com.carddemo.online.useradmin.support.ScreenHeader;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** COUSR03C (CU03): look up and delete a user (DELETE guarded by {@code @Version}). */
@Service
public class UserDeleteService {

  static final ScreenId ME = ScreenId.COUSR03;

  private final UserStore store;
  private final BusinessClock clock;

  public UserDeleteService(UserStore store, BusinessClock clock) {
    this.store = store;
    this.clock = clock;
  }

  public ScreenResponse<Cousr03Screen.Output> send(ConversationContext ctx, UseradminState state) {
    Map m = new Map(null, state);
    state.clearDisplayed();
    if (!Fields.isBlank(state.selected())) {
      m.usridin = Fields.received(state.selected(), 8);
      processEnterKey(m);
    }
    return m.reply.toResponse(ME, m.output(), ctx, state);
  }

  public ScreenResponse<Cousr03Screen.Output> receive(
      Action action, Cousr03Screen.Input input, ConversationContext ctx, UseradminState state) {
    Map m = new Map(input, state);
    switch (action) {
      case ENTER -> processEnterKey(m);
      case PF3 -> m.reply.returnToCaller(ctx);
      case PF4 -> {
        m.initializeAllFields();
        state.clearDisplayed();
      }
      case PF5 -> deleteUserInfo(m);
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
    m.st.clearDisplayed();
    read(m)
        .ifPresent(
            r -> {
              m.st.displayed(r);
              m.fname = r.firstName();
              m.lname = r.lastName();
              m.usrtype = r.type();
            });
  }

  private void deleteUserInfo(Map m) {
    if (Fields.isBlank(m.usridin)) {
      m.reply.error(Messages.USER_ID_EMPTY, "USRIDIN");
      return;
    }
    Optional<UserRow> found = read(m);
    if (found.isEmpty()) {
      // READ UPDATE failed: the DELETE that follows has no record to delete (RESP OTHER).
      m.reply.error(Messages.UNABLE_TO_UPDATE, "FNAME");
      return;
    }
    UserRow current = found.get();
    if (m.st.isStale(current)) {
      // changed by another terminal since this screen was displayed
      m.reply.error(Messages.UNABLE_TO_UPDATE, "FNAME");
      return;
    }
    UserStore.WriteOutcome outcome;
    try {
      outcome = store.delete(current);
    } catch (RuntimeException e) {
      m.reply.error(Messages.UNABLE_TO_UPDATE, "FNAME");
      return;
    }
    switch (outcome) {
      case OK -> {
        String id = found.get().id();
        m.initializeAllFields();
        m.st.clearDisplayed();
        m.reply.info(Messages.deleted(id));
      }
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
      m.reply.info(Messages.PRESS_PF5_DELETE);
    } else {
      m.reply.error(Messages.USER_NOT_FOUND, "USRIDIN");
    }
    return row;
  }

  /** COUSR3AI / COUSR3AO. */
  private final class Map {
    final Reply reply = new Reply();
    final UseradminState st;
    String usridin;
    String fname = "";
    String lname = "";
    String usrtype = "";

    Map(Cousr03Screen.Input in, UseradminState st) {
      this.st = st;
      usridin = Fields.received(in == null ? null : in.usridin(), 8);
    }

    void initializeAllFields() {
      usridin = "";
      fname = "";
      lname = "";
      usrtype = "";
      reply.clear();
    }

    Cousr03Screen.Output output() {
      ScreenHeader h = ScreenHeader.of(ME.transactionId(), ME.program(), clock);
      return new Cousr03Screen.Output(
          h.trnname(),
          Fields.rtrim(h.title01()),
          h.curdate(),
          h.pgmname(),
          Fields.rtrim(h.title02()),
          h.curtime(),
          Fields.rtrim(usridin),
          Fields.rtrim(fname),
          Fields.rtrim(lname),
          Fields.rtrim(usrtype),
          reply.message());
    }
  }
}
