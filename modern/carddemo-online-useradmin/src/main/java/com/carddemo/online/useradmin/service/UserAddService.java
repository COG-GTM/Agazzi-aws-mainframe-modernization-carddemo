package com.carddemo.online.useradmin.service;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.UserCredentialService;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.Cousr01Screen;
import com.carddemo.online.useradmin.session.UseradminState;
import com.carddemo.online.useradmin.store.UserRow;
import com.carddemo.online.useradmin.store.UserStore;
import com.carddemo.online.useradmin.support.Fields;
import com.carddemo.online.useradmin.support.Messages;
import com.carddemo.online.useradmin.support.ScreenHeader;
import org.springframework.stereotype.Service;

/** COUSR01C (CU01): add a regular/admin user. The password is stored as a BCrypt hash. */
@Service
public class UserAddService {

  static final ScreenId ME = ScreenId.COUSR01;

  private final UserStore store;
  private final UserCredentialService credentials;
  private final BusinessClock clock;

  public UserAddService(UserStore store, UserCredentialService credentials, BusinessClock clock) {
    this.store = store;
    this.credentials = credentials;
    this.clock = clock;
  }

  public ScreenResponse<Cousr01Screen.Output> send(ConversationContext ctx, UseradminState state) {
    Map map = new Map(null);
    return map.reply.toResponse(ME, map.output(), ctx, state);
  }

  public ScreenResponse<Cousr01Screen.Output> receive(
      Action action, Cousr01Screen.Input input, ConversationContext ctx, UseradminState state) {
    Map map = new Map(input);
    switch (action) {
      case ENTER -> processEnterKey(map);
      case PF3 -> map.reply.transferTo(ScreenId.COADM01);
      case PF4 -> map.initializeAllFields();
      default -> map.reply.error(Messages.INVALID_KEY, "FNAME");
    }
    return map.reply.toResponse(ME, map.output(), ctx, state);
  }

  private void processEnterKey(Map m) {
    if (Fields.isBlank(m.fname)) {
      m.reply.error(Messages.FIRST_NAME_EMPTY, "FNAME");
    } else if (Fields.isBlank(m.lname)) {
      m.reply.error(Messages.LAST_NAME_EMPTY, "LNAME");
    } else if (Fields.isBlank(m.userid)) {
      m.reply.error(Messages.USER_ID_EMPTY, "USERID");
    } else if (Fields.isBlank(m.passwd)) {
      m.reply.error(Messages.PASSWORD_EMPTY, "PASSWD");
    } else if (Fields.isBlank(m.usrtype)) {
      m.reply.error(Messages.USER_TYPE_EMPTY, "USRTYPE");
    } else {
      writeUserSecFile(m);
    }
  }

  private void writeUserSecFile(Map m) {
    String id = Fields.pad(m.userid, 8);
    UserStore.WriteOutcome outcome;
    try {
      UserRow row =
          new UserRow(
              id,
              Fields.pad(m.fname, 20),
              Fields.pad(m.lname, 20),
              credentials.encode(Fields.rtrim(m.passwd)),
              Fields.pad(m.usrtype, 1),
              Fields.pad("", 23),
              0L);
      outcome = store.insert(row);
    } catch (RuntimeException e) {
      m.reply.error(Messages.UNABLE_TO_ADD, "FNAME");
      return;
    }
    switch (outcome) {
      case OK -> {
        m.initializeAllFields();
        m.reply.info(Messages.added(id));
      }
      case DUPLICATE -> m.reply.error(Messages.USER_ALREADY_EXISTS, "USERID");
      default -> m.reply.error(Messages.UNABLE_TO_ADD, "FNAME");
    }
  }

  /** COUSR1AI / COUSR1AO. */
  private final class Map {
    final Reply reply = new Reply();
    String fname;
    String lname;
    String userid;
    String passwd;
    String usrtype;

    Map(Cousr01Screen.Input in) {
      fname = Fields.received(in == null ? null : in.fname(), 20);
      lname = Fields.received(in == null ? null : in.lname(), 20);
      userid = Fields.received(in == null ? null : in.userid(), 8);
      passwd = Fields.received(in == null ? null : in.passwd(), 8);
      usrtype = Fields.received(in == null ? null : in.usrtype(), 1);
    }

    void initializeAllFields() {
      fname = "";
      lname = "";
      userid = "";
      passwd = "";
      usrtype = "";
      reply.clear();
    }

    Cousr01Screen.Output output() {
      ScreenHeader h = ScreenHeader.of(ME.transactionId(), ME.program(), clock);
      return new Cousr01Screen.Output(
          h.trnname(),
          Fields.rtrim(h.title01()),
          h.curdate(),
          h.pgmname(),
          Fields.rtrim(h.title02()),
          h.curtime(),
          Fields.rtrim(fname),
          Fields.rtrim(lname),
          Fields.rtrim(userid),
          "",
          Fields.rtrim(usrtype),
          reply.message());
    }
  }
}
