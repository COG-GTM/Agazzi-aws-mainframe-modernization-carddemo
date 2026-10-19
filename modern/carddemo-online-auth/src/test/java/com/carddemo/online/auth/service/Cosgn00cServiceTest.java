package com.carddemo.online.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carddemo.contracts.port.UserCredentialService;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import com.carddemo.contracts.screen.FieldError;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenMessage;
import com.carddemo.contracts.screen.map.Cosgn00Screen;
import com.carddemo.domain.repository.AppUserRepository;
import com.carddemo.online.auth.AuthTestApplication;
import com.carddemo.online.auth.SeedUsers;
import com.carddemo.online.auth.security.AppUserCredentialService;
import com.carddemo.platform.time.FixedBusinessClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class Cosgn00cServiceTest {

  private Cosgn00cService service;

  @BeforeEach
  void setUp() {
    AppUserRepository repo = mock(AppUserRepository.class);
    SeedUsers.stub(repo);
    service =
        new Cosgn00cService(
            new AppUserCredentialService(repo),
            new FixedBusinessClock(AuthTestApplication.BUSINESS_TIME),
            "CARDDEMO",
            "CDEM");
  }

  private ScreenResult<Cosgn00Screen.Output> enter(String user, String pwd) {
    return service.receive(Action.ENTER, new Cosgn00Screen.Input(user, pwd));
  }

  @Test
  void firstSendHasHeaderAndNoMessage() {
    var r = service.firstSend();
    assertThat(r.effect()).isEqualTo(ScreenResult.Effect.KEEP);
    Cosgn00Screen.Output o = r.response().data();
    assertThat(o.trnname()).isEqualTo("CC00");
    assertThat(o.pgmname()).isEqualTo("COSGN00C");
    assertThat(o.title01()).isEqualTo("      AWS Mainframe Modernization");
    assertThat(o.title02()).isEqualTo("              CardDemo");
    assertThat(o.curdate()).isEqualTo("07/18/22");
    assertThat(o.curtime()).isEqualTo("12:00:00");
    assertThat(o.applid()).isEqualTo("CARDDEMO");
    assertThat(o.sysid()).isEqualTo("CDEM");
    assertThat(o.errmsg()).isEmpty();
    assertThat(r.response().messages()).isEmpty();
    assertThat(r.response().screen()).isEqualTo(ScreenId.COSGN00);
  }

  @Test
  void validationOrderOfProcessEnterKey() {
    var r = enter("", "");
    assertThat(r.response().messages())
        .containsExactly(ScreenMessage.error("Please enter User ID ..."));
    assertThat(r.response().fieldErrors())
        .containsExactly(new FieldError("USERID", "Please enter User ID ..."));
    assertThat(r.response().data().errmsg()).isEqualTo("Please enter User ID ...");
    assertThat(enter("  \u0000", "PASSWORD").response().messages().get(0).text())
        .as("mixed spaces/low-values is a value: USRSEC lookup")
        .isEqualTo("User not found. Try again ...");
    var p = enter("admin001", "\u0000\u0000");
    assertThat(p.response().messages().get(0).text()).isEqualTo("Please enter Password ...");
    assertThat(p.response().fieldErrors()).extracting(FieldError::field).containsExactly("PASSWD");
    assertThat(p.response().data().userid()).isEqualTo("admin001");
    assertThat(p.response().data().passwd()).isNull();
    assertThat(service.receive(Action.ENTER, null).response().fieldErrors())
        .extracting(FieldError::field)
        .containsExactly("USERID");
  }

  @Test
  void readUserSecFileOutcomes() {
    var nf = enter("NOBODY01", "PASSWORD");
    assertThat(nf.response().messages().get(0).text()).isEqualTo("User not found. Try again ...");
    assertThat(nf.response().fieldErrors()).extracting(FieldError::field).containsExactly("USERID");
    var wp = enter("ADMIN001", "WRONGPWD");
    assertThat(wp.response().messages().get(0).text()).isEqualTo("Wrong Password. Try again ...");
    assertThat(wp.response().fieldErrors()).extracting(FieldError::field).containsExactly("PASSWD");
    assertThat(wp.response().data().passwd()).isNull();
    assertThat(wp.effect()).isEqualTo(ScreenResult.Effect.KEEP);
  }

  @Test
  void adminGoesToCoadm01AndUserToComen01() {
    var a = enter("admin001", "password");
    assertThat(a.effect()).isEqualTo(ScreenResult.Effect.SIGN_ON);
    assertThat(a.response().nextScreen()).isEqualTo(ScreenId.COADM01);
    assertThat(a.response().context().userId()).isEqualTo("ADMIN001");
    assertThat(a.response().context().userType()).isEqualTo(UserType.ADMIN);
    assertThat(a.response().context().fromTransactionId()).isEqualTo("CC00");
    assertThat(a.response().context().fromProgram()).isEqualTo("COSGN00C");
    assertThat(a.user().userId()).isEqualTo("ADMIN001");
    var u = enter("USER0001", "PASSWORD");
    assertThat(u.response().nextScreen()).isEqualTo(ScreenId.COMEN01);
    assertThat(u.response().messages()).isEmpty();
    assertThat(u.response().fieldErrors()).isEmpty();
  }

  @Test
  void pf3ThanksAndOtherKeysAreInvalid() {
    var pf3 = service.receive(Action.PF3, null);
    assertThat(pf3.effect()).isEqualTo(ScreenResult.Effect.SIGN_OFF);
    assertThat(pf3.response().messages())
        .containsExactly(ScreenMessage.info("Thank you for using CardDemo application..."));
    assertThat(pf3.response().nextScreen()).isNull();
    for (Action a : new Action[] {Action.PF1, Action.CLEAR, Action.PA1, Action.PF12}) {
      var r = service.receive(a, new Cosgn00Screen.Input("ADMIN001", "PASSWORD"));
      assertThat(r.response().messages())
          .containsExactly(ScreenMessage.error("Invalid key pressed. Please see below..."));
      assertThat(r.response().nextScreen()).isNull();
    }
  }

  @Test
  void unexpectedResponseIsUnableToVerify() {
    UserCredentialService broken = mock(UserCredentialService.class);
    when(broken.authenticate(any(), any()))
        .thenThrow(new DataAccessResourceFailureException("USRSEC closed"));
    var s =
        new Cosgn00cService(
            broken, new FixedBusinessClock(AuthTestApplication.BUSINESS_TIME), "A", "B");
    var r = s.receive(Action.ENTER, new Cosgn00Screen.Input("ADMIN001", "PASSWORD"));
    assertThat(r.response().messages().get(0).text()).isEqualTo("Unable to verify the User ...");
    assertThat(r.response().fieldErrors()).extracting(FieldError::field).containsExactly("USERID");
  }
}
