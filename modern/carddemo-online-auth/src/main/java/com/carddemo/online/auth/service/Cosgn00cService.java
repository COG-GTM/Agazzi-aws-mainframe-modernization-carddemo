package com.carddemo.online.auth.service;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.UserCredentialService;
import com.carddemo.contracts.port.UserCredentialService.AuthenticatedUser;
import com.carddemo.contracts.port.UserCredentialService.AuthenticationResult;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import com.carddemo.contracts.screen.FieldError;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenMessage;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.Cosgn00Screen;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** CC00 COSGN00C - sign-on screen (map COSGN0A of mapset COSGN00). */
@Service
public class Cosgn00cService {

  static final String TRANID = "CC00";
  static final String PGMNAME = "COSGN00C";
  static final String USERID = "USERID";
  static final String PASSWD = "PASSWD";

  private final UserCredentialService credentials;
  private final BusinessClock clock;
  private final String applid;
  private final String sysid;

  public Cosgn00cService(
      UserCredentialService credentials,
      BusinessClock clock,
      @Value("${carddemo.cics.applid:CARDDEMO}") String applid,
      @Value("${carddemo.cics.sysid:CDEM}") String sysid) {
    this.credentials = credentials;
    this.clock = clock;
    this.applid = applid;
    this.sysid = sysid;
  }

  /** MAIN-PARA, EIBCALEN = 0: LOW-VALUES to COSGN0AO, cursor on USERID, SEND-SIGNON-SCREEN. */
  public ScreenResult<Cosgn00Screen.Output> firstSend() {
    return ScreenResult.keep(ScreenResponse.of(ScreenId.COSGN00, map(null, null), null));
  }

  /** MAIN-PARA, EIBCALEN > 0: EVALUATE EIBAID. */
  public ScreenResult<Cosgn00Screen.Output> receive(Action action, Cosgn00Screen.Input in) {
    return switch (action) {
      case ENTER -> processEnterKey(in == null ? new Cosgn00Screen.Input(null, null) : in);
      case PF3 ->
          // MOVE CCDA-MSG-THANK-YOU TO WS-MESSAGE, SEND-PLAIN-TEXT, RETURN (no TRANSID)
          ScreenResult.signOff(
              ScreenResponse.<Cosgn00Screen.Output>of(ScreenId.COSGN00, null, null)
                  .withMessage(ScreenMessage.info(CicsMessages.THANK_YOU)));
      default ->
          ScreenResult.keep(
              ScreenResponse.of(ScreenId.COSGN00, map(null, CicsMessages.INVALID_KEY), null)
                  .withMessage(ScreenMessage.error(CicsMessages.INVALID_KEY)));
    };
  }

  // COSGN00C PROCESS-ENTER-KEY
  private ScreenResult<Cosgn00Screen.Output> processEnterKey(Cosgn00Screen.Input in) {
    String userid = in.userid() == null ? null : ScreenText.picX(in.userid(), 8);
    if (ScreenText.isBlank(in.userid())) {
      return error(userid, CicsMessages.ENTER_USER_ID, USERID);
    }
    if (ScreenText.isBlank(in.passwd())) {
      return error(userid, CicsMessages.ENTER_PASSWORD, PASSWD);
    }
    return readUserSecFile(userid.toUpperCase(Locale.ROOT), ScreenText.picX(in.passwd(), 8));
  }

  // COSGN00C READ-USER-SEC-FILE
  private ScreenResult<Cosgn00Screen.Output> readUserSecFile(String wsUserId, String wsUserPwd) {
    AuthenticationResult r;
    try {
      r = credentials.authenticate(wsUserId, wsUserPwd);
    } catch (RuntimeException e) {
      // WHEN OTHER (RESP not NORMAL/NOTFND)
      return error(wsUserId, CicsMessages.UNABLE_TO_VERIFY, USERID);
    }
    return switch (r.outcome()) {
      case OK -> signedOn(r.user().orElseThrow());
      case WRONG_PASSWORD -> error(wsUserId, CicsMessages.WRONG_PASSWORD, PASSWD);
      case USER_NOT_FOUND -> error(wsUserId, CicsMessages.USER_NOT_FOUND, USERID);
    };
  }

  private ScreenResult<Cosgn00Screen.Output> signedOn(AuthenticatedUser user) {
    // MOVE WS-TRANID/WS-PGMNAME/WS-USER-ID/SEC-USR-TYPE, ZEROS TO CDEMO-PGM-CONTEXT, then
    // XCTL COADM01C (CDEMO-USRTYP-ADMIN) or COMEN01C
    ConversationContext ctx = ConversationContext.signedOn(user.userId(), user.userType());
    ScreenId next = user.userType() == UserType.ADMIN ? ScreenId.COADM01 : ScreenId.COMEN01;
    return ScreenResult.signOn(
        ScreenResponse.<Cosgn00Screen.Output>of(ScreenId.COSGN00, null, ctx).withNextScreen(next),
        user);
  }

  private ScreenResult<Cosgn00Screen.Output> error(String userid, String message, String cursor) {
    return ScreenResult.keep(
        new ScreenResponse<>(
            ScreenId.COSGN00,
            map(userid, message),
            List.of(ScreenMessage.error(message)),
            null,
            List.of(new FieldError(cursor, message)),
            null));
  }

  // SEND-SIGNON-SCREEN + POPULATE-HEADER-INFO; the password is never sent back (PARITY-EXCEPTION
  // O1-PE-1).
  Cosgn00Screen.Output map(String userid, String message) {
    ScreenHeader h = ScreenHeader.of(TRANID, PGMNAME, clock);
    return new Cosgn00Screen.Output(
        h.trnname(),
        h.title01(),
        h.curdate(),
        h.pgmname(),
        h.title02(),
        h.curtime(),
        ScreenText.out(ScreenText.picX(applid, 8)),
        ScreenText.out(ScreenText.picX(sysid, 8)),
        ScreenText.out(userid),
        null,
        ScreenText.errmsg(message));
  }
}
