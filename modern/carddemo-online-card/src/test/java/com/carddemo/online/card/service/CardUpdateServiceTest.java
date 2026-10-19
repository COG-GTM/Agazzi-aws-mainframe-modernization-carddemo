package com.carddemo.online.card.service;

import static com.carddemo.online.card.service.CardFixtures.acctText;
import static com.carddemo.online.card.service.CardFixtures.num;
import static com.carddemo.online.card.service.CardFixtures.texts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.map.CocrdupScreen;
import com.carddemo.domain.entity.Card;
import com.carddemo.online.card.repository.CardRewriter;
import com.carddemo.online.card.session.CardUpdateState;
import com.carddemo.online.card.session.CardUpdateState.Stage;
import com.carddemo.online.card.session.ContextEdit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class CardUpdateServiceTest {

  final CardFixtures fx = new CardFixtures(10);
  final CardUpdateService svc = new CardUpdateService(fx.repo, new CardRewriter(fx.repo), fx.clock);
  ConversationContext ctx = CardFixtures.admin();
  CardUpdateState st = CardUpdateState.initial();
  Optional<Long> owned = Optional.empty();

  CardOutcome<CocrdupScreen.Output, CardUpdateState> run(
      Action a, CardUpdateFields f, boolean first) {
    var o = svc.process(ctx, st, owned, a, f, first);
    ctx = o.response().context();
    st = o.state();
    return o;
  }

  static CardUpdateFields edit(String name, String status, String mon, String year) {
    return new CardUpdateFields(null, null, name, status, mon, year);
  }

  static CardUpdateFields search(String acct, String card) {
    return new CardUpdateFields(acct, card, null, null, null, null);
  }

  void fromList(int i) {
    ctx =
        ContextEdit.of(ctx)
            .from("CCLI", "COCRDLIC")
            .to("CCUP", "COCRDUPC")
            .enter()
            .account(CardFixtures.acct(i))
            .card(Long.parseLong(num(i)))
            .lastMap("CCRDLIA", "COCRDLI")
            .build();
  }

  @Test
  void firstSendFromMenuPrompts() {
    ctx = ContextEdit.of(ctx).from("CM00", "COMEN01C").build();
    var r = run(Action.ENTER, null, true).response();
    assertThat(r.screen()).isEqualTo(ScreenId.COCRDUP);
    assertThat(texts(r)).contains(CardUpdateService.INFO_SEARCH);
    assertThat(st.stage()).isEqualTo(Stage.NOT_FETCHED);
    assertThat(r.context().programContext()).isEqualTo(ConversationContext.ProgramContext.REENTER);
  }

  @Test
  void searchThenUpdateThenSave() {
    run(Action.ENTER, null, true);
    var found = run(Action.ENTER, search(acctText(4), num(4)), false).response();
    assertThat(texts(found)).contains(CardUpdateService.INFO_FOUND);
    assertThat(found.data().crdname()).isEqualTo("HOLDER NAME");
    assertThat(found.data().expday()).isEqualTo("15");
    assertThat(st.stage()).isEqualTo(Stage.SHOW_DETAILS);

    var ok = run(Action.ENTER, edit("NEW NAME", "N", "12", "2030"), false).response();
    assertThat(texts(ok)).contains(CardUpdateService.INFO_CONFIRM);
    assertThat(ok.data().fkeysc()).isNotBlank();
    assertThat(st.stage()).isEqualTo(Stage.CHANGES_OK_NOT_CONFIRMED);

    var done = run(Action.PF5, edit("NEW NAME", "N", "12", "2030"), false).response();
    assertThat(texts(done)).contains(CardUpdateService.INFO_DONE);
    Card saved = fx.file.get(num(4));
    assertThat(saved.getCardEmbossedName().strip()).isEqualTo("NEW NAME");
    assertThat(saved.getCardActiveStatus()).isEqualTo("N");
    assertThat(saved.getCardExpirationDate()).isEqualTo("2030-12-15");
    assertThat(saved.getCardEmbossedName()).hasSize(50);

    var restart = run(Action.ENTER, null, false).response();
    assertThat(texts(restart)).contains(CardUpdateService.INFO_SEARCH);
  }

  @Test
  void fromListShowsDetailsAndPf3ReturnsToList() {
    fromList(2);
    var r = run(Action.ENTER, null, true).response();
    assertThat(texts(r)).contains(CardUpdateService.INFO_FOUND);
    assertThat(r.data().acctsid()).isEqualTo(acctText(2));
    var back = run(Action.PF3, null, false).response();
    assertThat(back.nextScreen()).isEqualTo(ScreenId.COCRDLI);
  }

  @Test
  void saveFromListReturnsToList() {
    fromList(2);
    run(Action.ENTER, null, true);
    run(Action.ENTER, edit("ANOTHER", "Y", "3", "2031"), false);
    run(Action.PF5, edit("ANOTHER", "Y", "3", "2031"), false);
    assertThat(st.stage()).isEqualTo(Stage.CHANGES_DONE);
    var back = run(Action.ENTER, null, false).response();
    assertThat(back.nextScreen()).isEqualTo(ScreenId.COCRDLI);
  }

  @Test
  void pf12RestoresFetchedValues() {
    fromList(2);
    run(Action.ENTER, null, true);
    run(Action.ENTER, edit("ANOTHER", "Y", "3", "2031"), false);
    var r = run(Action.PF12, null, false).response();
    assertThat(r.data().crdname()).isEqualTo("HOLDER NAME");
    assertThat(st.stage()).isEqualTo(Stage.SHOW_DETAILS);
  }

  @Test
  void pf3FromMenuReturnsToMenu() {
    ctx = ContextEdit.of(ctx).from("CM00", "COMEN01C").build();
    run(Action.ENTER, null, true);
    var r = run(Action.PF3, null, false).response();
    assertThat(r.nextScreen()).isEqualTo(ScreenId.COMEN01);
  }

  @Test
  void searchErrors() {
    run(Action.ENTER, null, true);
    assertThat(texts(run(Action.ENTER, search(null, null), false).response()))
        .contains(CardUpdateService.MSG_NO_INPUT);
    assertThat(texts(run(Action.ENTER, search(null, num(1)), false).response()))
        .contains(CardUpdateService.MSG_ACCT_NOT_PROVIDED);
    assertThat(texts(run(Action.ENTER, search("A1", num(1)), false).response()))
        .contains(CardListService.MSG_ACCT_FILTER);
    assertThat(texts(run(Action.ENTER, search(acctText(1), null), false).response()))
        .contains(CardUpdateService.MSG_CARD_NOT_PROVIDED);
    assertThat(texts(run(Action.ENTER, search(acctText(1), "9x"), false).response()))
        .contains(CardListService.MSG_CARD_FILTER);
    assertThat(texts(run(Action.ENTER, search(acctText(1), num(77)), false).response()))
        .contains(CardUpdateService.MSG_NOT_FOUND);
  }

  @Test
  void otherAccountCardIsNotFoundForUser() {
    ctx = CardFixtures.user();
    owned = Optional.of(CardFixtures.acct(1));
    run(Action.ENTER, null, true);
    assertThat(texts(run(Action.ENTER, search(acctText(2), num(2)), false).response()))
        .contains(CardUpdateService.MSG_NOT_FOUND);
  }

  @Test
  void cardIsReadByCardNumberOnlyLikeCobol() {
    run(Action.ENTER, null, true);
    assertThat(texts(run(Action.ENTER, search(acctText(2), num(1)), false).response()))
        .contains(CardUpdateService.INFO_FOUND);
  }

  @Test
  void editErrorsInCobolOrder() {
    fromList(3);
    run(Action.ENTER, null, true);
    assertThat(texts(run(Action.ENTER, edit("Holder Name", "Y", "04", "2025"), false).response()))
        .contains(CardUpdateService.MSG_NO_CHANGE);
    assertThat(texts(run(Action.ENTER, edit(null, "Y", "1", "2025"), false).response()))
        .contains(CardUpdateService.MSG_NAME_NOT_PROVIDED);
    assertThat(texts(run(Action.ENTER, edit("B0B", "Q", "13", "1900"), false).response()))
        .contains(CardUpdateService.MSG_NAME_ALPHA);
    assertThat(st.stage()).isEqualTo(Stage.CHANGES_NOT_OK);
    assertThat(texts(run(Action.ENTER, edit("BOB", null, "1", "2025"), false).response()))
        .contains(CardUpdateService.MSG_STATUS);
    assertThat(texts(run(Action.ENTER, edit("BOB", "Q", "1", "2025"), false).response()))
        .contains(CardUpdateService.MSG_STATUS);
    assertThat(texts(run(Action.ENTER, edit("BOB", "Y", null, "2025"), false).response()))
        .contains(CardUpdateService.MSG_MONTH);
    assertThat(texts(run(Action.ENTER, edit("BOB", "Y", "13", "2025"), false).response()))
        .contains(CardUpdateService.MSG_MONTH);
    assertThat(texts(run(Action.ENTER, edit("BOB", "Y", "1", null), false).response()))
        .contains(CardUpdateService.MSG_YEAR);
    var y = run(Action.ENTER, edit("BOB", "Y", "1", "2100"), false).response();
    assertThat(texts(y)).contains(CardUpdateService.MSG_YEAR);
    assertThat(texts(y)).contains(CardUpdateService.INFO_CHANGES);
  }

  @Test
  void concurrentChangeIsDetected() {
    fromList(5);
    run(Action.ENTER, null, true);
    run(Action.ENTER, edit("MINE", "Y", "1", "2030"), false);
    Card other = CardFixtures.card(5);
    other.setCardEmbossedName(CardFixtures.CardScreenSupportPad.pad("THEIRS", 50));
    fx.put(other);
    var r = run(Action.PF5, edit("MINE", "Y", "1", "2030"), false).response();
    assertThat(texts(r)).contains(CardUpdateService.MSG_CHANGED);
    assertThat(r.data().crdname()).isEqualTo("THEIRS");
    assertThat(fx.file.get(num(5)).getCardEmbossedName().strip()).isEqualTo("THEIRS");
  }

  @Test
  void cardMovedToAnotherAccountIsReportedAsChanged() {
    fromList(5);
    run(Action.ENTER, null, true);
    run(Action.ENTER, edit("MINE", "Y", "1", "2030"), false);
    Card moved = CardFixtures.card(5);
    moved.setCardAcctId(999L);
    fx.put(moved);
    var r = run(Action.PF5, edit("MINE", "Y", "1", "2030"), false).response();
    assertThat(texts(r)).contains(CardUpdateService.MSG_CHANGED);
    assertThat(fx.file.get(num(5)).getCardEmbossedName().strip()).isNotEqualTo("MINE");
  }

  @Test
  void optimisticLockFailure() {
    fromList(5);
    run(Action.ENTER, null, true);
    run(Action.ENTER, edit("MINE", "Y", "1", "2030"), false);
    when(fx.repo.saveAndFlush(any(Card.class)))
        .thenThrow(new ObjectOptimisticLockingFailureException(Card.class, num(5)));
    var r = run(Action.PF5, edit("MINE", "Y", "1", "2030"), false).response();
    assertThat(texts(r)).contains(CardUpdateService.MSG_LOCK, CardUpdateService.INFO_FAILURE);
  }

  @Test
  void updateFailure() {
    fromList(5);
    run(Action.ENTER, null, true);
    run(Action.ENTER, edit("MINE", "Y", "1", "2030"), false);
    when(fx.repo.saveAndFlush(any(Card.class))).thenThrow(new DataIntegrityViolationException("x"));
    var r = run(Action.PF5, edit("MINE", "Y", "1", "2030"), false).response();
    assertThat(texts(r)).contains(CardUpdateService.MSG_UPDATE_FAILED);
  }

  @Test
  void recordDeletedBeforeSave() {
    fromList(5);
    run(Action.ENTER, null, true);
    run(Action.ENTER, edit("MINE", "Y", "1", "2030"), false);
    fx.file.remove(num(5));
    var r = run(Action.PF5, edit("MINE", "Y", "1", "2030"), false).response();
    assertThat(texts(r)).contains(CardUpdateService.MSG_LOCK);
  }
}
