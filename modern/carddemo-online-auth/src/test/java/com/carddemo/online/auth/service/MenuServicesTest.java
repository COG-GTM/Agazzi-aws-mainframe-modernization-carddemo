package com.carddemo.online.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.ProgramContext;
import com.carddemo.contracts.screen.ConversationContext.UserType;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenMessage;
import com.carddemo.contracts.screen.ScreenMessage.Severity;
import com.carddemo.online.auth.AuthTestApplication;
import com.carddemo.platform.time.FixedBusinessClock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class MenuServicesTest {

  private final FixedBusinessClock clock =
      new FixedBusinessClock(AuthTestApplication.BUSINESS_TIME);
  private final MockEnvironment env = new MockEnvironment();
  private final ExtensionFlags flags = new ExtensionFlags(env);
  private final Comen01cService menu = new Comen01cService(clock, flags);
  private final Coadm01cService admin = new Coadm01cService(clock, flags);

  private static ConversationContext signedOn(String user, UserType type) {
    return ConversationContext.signedOn(user, type);
  }

  private static ConversationContext reenter(String user, UserType type) {
    return signedOn(user, type).reenter();
  }

  @Test
  void firstSendBuildsMenuAndSetsReenter() {
    var r = menu.firstSend(signedOn("USER0001", UserType.USER));
    var o = r.response().data();
    assertThat(r.response().context().programContext()).isEqualTo(ProgramContext.REENTER);
    assertThat(o.trnname()).isEqualTo("CM00");
    assertThat(o.pgmname()).isEqualTo("COMEN01C");
    assertThat(o.curdate()).isEqualTo("07/18/22");
    assertThat(o.optn001()).isEqualTo("01. Account View");
    assertThat(o.optn010()).isEqualTo("10. Bill Payment");
    assertThat(o.optn011()).as("X1 off: option hidden").isNull();
    assertThat(o.optn012()).isNull();
    assertThat(o.errmsg()).isEmpty();
    env.setProperty("carddemo.extensions.authorization.enabled", "true");
    assertThat(menu.firstSend(signedOn("USER0001", UserType.USER)).response().data().optn011())
        .isEqualTo("11. Pending Authorization View");
  }

  @Test
  void receiveWithoutReenterIsAFirstSend() {
    var r = menu.receive(signedOn("USER0001", UserType.USER), Action.ENTER, "1");
    assertThat(r.response().nextScreen()).isNull();
    assertThat(r.response().messages()).isEmpty();
    assertThat(r.response().context().programContext()).isEqualTo(ProgramContext.REENTER);
  }

  @Test
  void enterXctlsToTheOptionProgram() {
    var r = menu.receive(reenter("USER0001", UserType.USER), Action.ENTER, "3");
    assertThat(r.response().nextScreen()).isEqualTo(ScreenId.COCRDLI);
    ConversationContext c = r.response().context();
    assertThat(c.fromTransactionId()).isEqualTo("CM00");
    assertThat(c.fromProgram()).isEqualTo("COMEN01C");
    assertThat(c.toProgram()).isEqualTo("COCRDLIC");
    assertThat(c.programContext()).isEqualTo(ProgramContext.ENTER);
    assertThat(c.userId()).isEqualTo("USER0001");
  }

  @Test
  void invalidOptionAndKeys() {
    var r = menu.receive(reenter("USER0001", UserType.USER), Action.ENTER, "ab");
    assertThat(r.response().messages())
        .containsExactly(ScreenMessage.error("Please enter a valid option number..."));
    assertThat(r.response().data().option()).isEqualTo("ab");
    assertThat(
            menu.receive(reenter("U", UserType.USER), Action.ENTER, "12")
                .response()
                .data()
                .option())
        .isEqualTo("12");
    var k = menu.receive(reenter("USER0001", UserType.USER), Action.PF8, "1");
    assertThat(k.response().messages().get(0).text())
        .isEqualTo("Invalid key pressed. Please see below...");
    var pf3 = menu.receive(reenter("USER0001", UserType.USER), Action.PF3, null);
    assertThat(pf3.effect()).isEqualTo(ScreenResult.Effect.SIGN_OFF);
    assertThat(pf3.response().nextScreen()).isEqualTo(ScreenId.COSGN00);
    assertThat(pf3.response().context()).isNull();
  }

  @Test
  void optionalModuleNotInstalledThenInstalled() {
    var r = menu.receive(reenter("USER0001", UserType.USER), Action.ENTER, "11");
    assertThat(r.response().messages())
        .containsExactly(
            ScreenMessage.error("This option Pending Authorization View is not installed..."));
    env.setProperty("carddemo.extensions.authorization.enabled", "true");
    assertThat(
            menu.receive(reenter("USER0001", UserType.USER), Action.ENTER, "11")
                .response()
                .nextScreen())
        .isEqualTo(ScreenId.COPAU00);
  }

  @Test
  void adminOnlyAndDummyOptions() {
    var custom =
        new Comen01cService(
            List.of(
                new MenuOption(1, "Secret", "COUSR00C", 'A'),
                new MenuOption(2, "Coming Feature", "DUMMY002", 'U')),
            clock,
            flags);
    var denied = custom.receive(reenter("USER0001", UserType.USER), Action.ENTER, "1");
    assertThat(denied.response().messages())
        .containsExactly(ScreenMessage.error("No access - Admin Only option..."));
    assertThat(denied.response().nextScreen()).isNull();
    var allowed = custom.receive(reenter("ADMIN001", UserType.ADMIN), Action.ENTER, "1");
    assertThat(allowed.response().nextScreen()).isEqualTo(ScreenId.COUSR00);
    var soon = custom.receive(reenter("USER0001", UserType.USER), Action.ENTER, "2");
    assertThat(soon.response().messages())
        .containsExactly(ScreenMessage.info("This option Comingis coming soon ..."));
    assertThat(soon.response().messages().get(0).severity()).isEqualTo(Severity.INFO);
  }

  @Test
  void adminMenu() {
    var first = admin.firstSend(signedOn("ADMIN001", UserType.ADMIN));
    var o = first.response().data();
    assertThat(o.trnname()).isEqualTo("CA00");
    assertThat(o.pgmname()).isEqualTo("COADM01C");
    assertThat(o.optn001()).isEqualTo("01. User List (Security)");
    assertThat(o.optn004()).isEqualTo("04. User Delete (Security)");
    assertThat(o.optn005()).as("X2 off").isNull();
    assertThat(o.optn011()).isNull();
    var x = admin.receive(reenter("ADMIN001", UserType.ADMIN), Action.ENTER, "2");
    assertThat(x.response().nextScreen()).isEqualTo(ScreenId.COUSR01);
    assertThat(x.response().context().fromProgram()).isEqualTo("COADM01C");
    var ni = admin.receive(reenter("ADMIN001", UserType.ADMIN), Action.ENTER, "5");
    assertThat(ni.response().messages())
        .containsExactly(ScreenMessage.info("This option is not installed ..."));
    env.setProperty("carddemo.extensions.trantype.enabled", "true");
    assertThat(admin.firstSend(signedOn("A", UserType.ADMIN)).response().data().optn006())
        .isEqualTo("06. Transaction Type Maintenance (Db2)");
    assertThat(
            admin
                .receive(reenter("ADMIN001", UserType.ADMIN), Action.ENTER, "6")
                .response()
                .nextScreen())
        .isEqualTo(ScreenId.COTRTUP);
    var bad = admin.receive(reenter("ADMIN001", UserType.ADMIN), Action.ENTER, "7");
    assertThat(bad.response().messages().get(0).text())
        .isEqualTo("Please enter a valid option number...");
    var dummy =
        new Coadm01cService(List.of(new MenuOption(1, "Soon", "DUMMY001", ' ')), clock, flags);
    assertThat(
            dummy
                .receive(reenter("ADMIN001", UserType.ADMIN), Action.ENTER, "1")
                .response()
                .messages()
                .get(0)
                .text())
        .isEqualTo("This option is not installed ...");
    var pf3 = admin.receive(reenter("ADMIN001", UserType.ADMIN), Action.PF3, null);
    assertThat(pf3.response().nextScreen()).isEqualTo(ScreenId.COSGN00);
  }
}
