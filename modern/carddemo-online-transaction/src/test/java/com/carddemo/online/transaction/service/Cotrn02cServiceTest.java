package com.carddemo.online.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.online.transaction.file.Resp;
import com.carddemo.platform.time.FixedBusinessClock;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class Cotrn02cServiceTest {

  private final Fakes.Transact file = new Fakes.Transact().with(2);
  private final Fakes.Xref xref =
      new Fakes.Xref().with("4111111111111111", 1).with("4222222222222222", 2);
  private final Cotrn02cService svc = new Cotrn02cService(file, xref, FixedBusinessClock.golden());

  static Map<String, String> valid() {
    Map<String, String> m = new HashMap<>();
    m.put("ACTIDIN", "00000000001");
    m.put("CARDNIN", "");
    m.put("TTYPCD", "01");
    m.put("TCATCD", "0001");
    m.put("TRNSRC", "POS TERM");
    m.put("TDESC", "COFFEE");
    m.put("TRNAMT", "+00000012.34");
    m.put("TORIGDT", "2022-07-18");
    m.put("TPROCDT", "2022-07-18");
    m.put("MID", "000000001");
    m.put("MNAME", "BAR");
    m.put("MCITY", "MILANO");
    m.put("MZIP", "20100");
    m.put("CONFIRM", "Y");
    return m;
  }

  private ProgramResult<?> post(Action aid, Map<String, String> fields) {
    var p = svc.run(Ctx.fromMenu("CT02", "COTRN02C"), CommareaExtension.initial(), null, Map.of());
    return svc.run(p.response().context(), p.extension(), aid, fields);
  }

  @Test
  void addsTransactionWithLastIdPlusOne() {
    var r = post(Action.ENTER, valid());
    assertThat(Ctx.message(r.response()))
        .isEqualTo("Transaction added successfully.  Your Tran ID is 0000000000000003.");
    assertThat(Ctx.green(r.response())).isTrue();
    var t = file.rows.get(Fakes.id(3));
    assertThat(t.getTranCardNum()).isEqualTo("4111111111111111");
    assertThat(t.getTranAmt()).isEqualByComparingTo(new BigDecimal("12.34"));
    assertThat(t.getTranOrigTs()).isEqualTo(CobolFmt.pad("2022-07-18", 26));
    assertThat(file.locks).isEqualTo(1);
  }

  @Test
  void cardNumberPathAndEmptyFile() {
    file.rows.clear();
    var m = valid();
    m.put("ACTIDIN", "");
    m.put("CARDNIN", "4222222222222222");
    var r = post(Action.ENTER, m);
    assertThat(Ctx.message(r.response())).endsWith("Your Tran ID is 0000000000000001.");
    assertThat(file.rows.get(Fakes.id(1)).getTranCardNum()).isEqualTo("4222222222222222");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "ACTIDIN|1|Account ID must be Numeric...|ACTIDIN",
        "ACTIDIN|00000000009|Account ID NOT found...|ACTIDIN",
        "TTYPCD||Type CD can NOT be empty...|TTYPCD",
        "TCATCD||Category CD can NOT be empty...|TCATCD",
        "TRNSRC||Source can NOT be empty...|TRNSRC",
        "TDESC||Description can NOT be empty...|TDESC",
        "TRNAMT||Amount can NOT be empty...|TRNAMT",
        "TORIGDT||Orig Date can NOT be empty...|TORIGDT",
        "TPROCDT||Proc Date can NOT be empty...|TPROCDT",
        "MID||Merchant ID can NOT be empty...|MID",
        "MNAME||Merchant Name can NOT be empty...|MNAME",
        "MCITY||Merchant City can NOT be empty...|MCITY",
        "MZIP||Merchant Zip can NOT be empty...|MZIP",
        "TTYPCD|A1|Type CD must be Numeric...|TTYPCD",
        "TCATCD|00X1|Category CD must be Numeric...|TCATCD",
        "TRNAMT|12.34|Amount should be in format -99999999.99|TRNAMT",
        "TORIGDT|18/07/2022|Orig Date should be in format YYYY-MM-DD|TORIGDT",
        "TPROCDT|2022/07/18|Proc Date should be in format YYYY-MM-DD|TPROCDT",
        "TORIGDT|2022-02-30|Orig Date - Not a valid date...|TORIGDT",
        "TPROCDT|2022-13-01|Proc Date - Not a valid date...|TPROCDT",
        "MID|12AB|Merchant ID must be Numeric...|MID",
        "CONFIRM||Confirm to add this transaction...|CONFIRM",
        "CONFIRM|N|Confirm to add this transaction...|CONFIRM",
        "CONFIRM|X|Invalid value. Valid values are (Y/N)...|CONFIRM"
      })
  void validationsInCobolOrder(String field, String value, String message, String cursor) {
    var m = valid();
    m.put(field, value == null ? "" : value);
    var r = post(Action.ENTER, m);
    assertThat(Ctx.message(r.response())).isEqualTo(message);
    assertThat(r.response().fieldErrors()).extracting("field").containsExactly(cursor);
    assertThat(file.rows).hasSize(2);
  }

  @Test
  void keyErrors() {
    var m = valid();
    m.put("ACTIDIN", "");
    assertThat(Ctx.message(post(Action.ENTER, m).response()))
        .isEqualTo("Account or Card Number must be entered...");
    m.put("CARDNIN", "41X");
    assertThat(Ctx.message(post(Action.ENTER, m).response()))
        .isEqualTo("Card Number must be Numeric...");
    m.put("CARDNIN", "4999999999999999");
    assertThat(Ctx.message(post(Action.ENTER, m).response())).isEqualTo("Card Number NOT found...");
    xref.forced = Resp.ERROR;
    assertThat(Ctx.message(post(Action.ENTER, m).response()))
        .isEqualTo("Unable to lookup Card # in XREF file...");
    assertThat(Ctx.message(post(Action.ENTER, valid()).response()))
        .isEqualTo("Unable to lookup Acct in XREF AIX file...");
  }

  @Test
  void writeFailures() {
    file.writeResp = Resp.DUPREC;
    assertThat(Ctx.message(post(Action.ENTER, valid()).response()))
        .isEqualTo("Tran ID already exist...");
    file.writeResp = Resp.ERROR;
    assertThat(Ctx.message(post(Action.ENTER, valid()).response()))
        .isEqualTo("Unable to Add Transaction...");
  }

  @Test
  void pf5CopiesLastTransactionThenAsksConfirmation() {
    var m = new HashMap<String, String>();
    m.put("ACTIDIN", "00000000001");
    var r = post(Action.PF5, m);
    assertThat(Ctx.message(r.response())).isEqualTo("Confirm to add this transaction...");
    var d = (com.carddemo.contracts.screen.map.Cotrn02Screen.Output) r.response().data();
    assertThat(d.tdesc()).isEqualTo("DESC 2");
    assertThat(d.trnamt()).isEqualTo("+00000002.50");
    file.rows.clear();
    assertThat(Ctx.message(post(Action.PF5, m).response()))
        .isEqualTo("Type CD can NOT be empty...");
  }

  @Test
  void otherKeys() {
    assertThat(post(Action.PF3, Map.of()).response().nextScreen()).isEqualTo(ScreenId.COMEN01);
    var clr = post(Action.PF4, valid());
    assertThat(clr.response().messages()).isEmpty();
    assertThat(Ctx.message(post(Action.PF9, Map.of()).response()))
        .isEqualTo("Invalid key pressed. Please see below...");
    var ext = new CommareaExtension("", "", 0, false, "", "4111111111111111", null, Map.of());
    var first = svc.run(Ctx.fromMenu("CT02", "COTRN02C"), ext, null, Map.of());
    assertThat(Ctx.message(first.response())).isEqualTo("Type CD can NOT be empty...");
  }
}
