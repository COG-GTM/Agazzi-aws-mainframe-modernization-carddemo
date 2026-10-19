package com.carddemo.online.card.service;

import static com.carddemo.online.card.service.CardFixtures.acctText;
import static com.carddemo.online.card.service.CardFixtures.num;
import static com.carddemo.online.card.service.CardFixtures.texts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.FieldError;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.map.CocrdslScreen;
import com.carddemo.online.card.session.ContextEdit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class CardDetailServiceTest {

  final CardFixtures fx = new CardFixtures(10);
  final CardDetailService svc = new CardDetailService(fx.repo, fx.clock);
  ConversationContext ctx = CardFixtures.admin();
  Optional<Long> owned = Optional.empty();

  CardOutcome<CocrdslScreen.Output, Void> run(Action a, String acct, String card, boolean first) {
    var o = svc.process(ctx, owned, a, new CocrdslScreen.Input(acct, card), first);
    ctx = o.response().context();
    return o;
  }

  @Test
  void firstSendPrompts() {
    var r = run(Action.ENTER, null, null, true).response();
    assertThat(r.screen()).isEqualTo(ScreenId.COCRDSL);
    assertThat(texts(r)).containsExactly(CardDetailService.MSG_PROMPT);
    assertThat(r.data().fkeys()).isEqualTo("ENTER=Search Cards  F3=Exit");
    assertThat(r.context().programContext()).isEqualTo(ConversationContext.ProgramContext.REENTER);
  }

  @Test
  void searchFound() {
    run(Action.ENTER, null, null, true);
    var r = run(Action.ENTER, acctText(3), num(3), false).response();
    assertThat(texts(r)).containsExactly(CardDetailService.MSG_FOUND);
    assertThat(r.data().crdname()).isEqualTo("Holder Name");
    assertThat(r.data().expyear()).isEqualTo("2025");
    assertThat(r.data().expmon()).isEqualTo("04");
    assertThat(r.data().crdstcd()).isEqualTo("Y");
    assertThat(r.data().cardsid()).isEqualTo(num(3));
  }

  @Test
  void editMessages() {
    run(Action.ENTER, null, null, true);
    assertThat(texts(run(Action.ENTER, "*", " ", false).response()))
        .contains(CardDetailService.MSG_NO_INPUT);
    assertThat(texts(run(Action.ENTER, null, num(1), false).response()))
        .contains(CardDetailService.MSG_ACCT_NOT_PROVIDED);
    assertThat(texts(run(Action.ENTER, acctText(1), null, false).response()))
        .contains(CardDetailService.MSG_CARD_NOT_PROVIDED);
    var a = run(Action.ENTER, "ABC", num(1), false).response();
    assertThat(texts(a)).contains(CardListService.MSG_ACCT_FILTER);
    assertThat(a.fieldErrors()).extracting(FieldError::field).containsExactly("ACCTSID");
    assertThat(texts(run(Action.ENTER, acctText(1), "12", false).response()))
        .contains(CardListService.MSG_CARD_FILTER);
    var nf = run(Action.ENTER, acctText(1), num(99), false).response();
    assertThat(texts(nf)).contains(CardDetailService.MSG_NOT_FOUND);
    assertThat(nf.fieldErrors())
        .extracting(FieldError::field)
        .containsExactly("ACCTSID", "CARDSID");
  }

  @Test
  void fromListShowsSelectedCard() {
    ctx =
        ContextEdit.of(ctx)
            .from("CCLI", "COCRDLIC")
            .to("CCDL", "COCRDSLC")
            .enter()
            .account(CardFixtures.acct(2))
            .card(Long.parseLong(num(2)))
            .lastMap("CCRDLIA", "COCRDLI")
            .build();
    var r = run(Action.ENTER, null, null, true).response();
    assertThat(texts(r)).containsExactly(CardDetailService.MSG_FOUND);
    assertThat(r.data().acctsid()).isEqualTo(acctText(2));
    var back = run(Action.PF3, null, null, false).response();
    assertThat(back.nextScreen()).isEqualTo(ScreenId.COCRDLI);
    assertThat(back.context().fromProgram()).isEqualTo("COCRDSLC");
  }

  @Test
  void pf3FromMenuGoesToMenu() {
    ctx = ContextEdit.of(ctx).from("CM00", "COMEN01C").build();
    run(Action.ENTER, null, null, false);
    var r = run(Action.PF3, null, null, false).response();
    assertThat(r.nextScreen()).isEqualTo(ScreenId.COMEN01);
  }

  @Test
  void pf3WithoutCallerGoesToMenu() {
    run(Action.ENTER, null, null, true);
    var r = run(Action.PF3, null, null, false).response();
    assertThat(r.nextScreen()).isEqualTo(ScreenId.COMEN01);
    assertThat(r.context().toProgram()).isEqualTo("COMEN01C");
  }

  @Test
  void nonAdminCannotSeeOtherAccountsCard() {
    ctx = CardFixtures.user();
    owned = Optional.of(CardFixtures.acct(1));
    run(Action.ENTER, null, null, true);
    assertThat(texts(run(Action.ENTER, acctText(2), num(2), false).response()))
        .contains(CardDetailService.MSG_NOT_FOUND);
    assertThat(texts(run(Action.ENTER, acctText(1), num(1), false).response()))
        .contains(CardDetailService.MSG_FOUND);
  }

  @Test
  void readErrorIsReported() {
    when(fx.repo.findById(anyString())).thenThrow(new DataAccessResourceFailureException("down"));
    run(Action.ENTER, null, null, true);
    assertThat(texts(run(Action.ENTER, acctText(1), num(1), false).response()))
        .contains(CardDetailService.MSG_READ_ERROR);
  }
}
