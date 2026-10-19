package com.carddemo.online.card.service;

import static com.carddemo.online.card.service.CardFixtures.acctText;
import static com.carddemo.online.card.service.CardFixtures.num;
import static com.carddemo.online.card.service.CardFixtures.texts;
import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.FieldError;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.map.CocrdliScreen;
import com.carddemo.domain.entity.Card;
import com.carddemo.online.card.session.CardListState;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CardListServiceTest {

  final CardFixtures fx = new CardFixtures(17);
  final CardListService svc = new CardListService(fx.browser, fx.clock);
  ConversationContext ctx = CardFixtures.admin();
  CardListState st = CardListState.initial();
  Optional<Long> owned = Optional.empty();

  CardOutcome<CocrdliScreen.Output, CardListState> first() {
    return keep(svc.process(ctx, st, owned, Action.ENTER, null, true));
  }

  CardOutcome<CocrdliScreen.Output, CardListState> post(Action a, CardListFields f) {
    return keep(svc.process(ctx, st, owned, a, f, false));
  }

  CardOutcome<CocrdliScreen.Output, CardListState> keep(
      CardOutcome<CocrdliScreen.Output, CardListState> o) {
    ctx = o.response().context();
    st = o.state();
    return o;
  }

  static CardListFields filters(String acct, String card) {
    return new CardListFields(acct, card, null, null, null, null, null, null, null);
  }

  static CardListFields select(int row, String code) {
    String[] s = new String[7];
    s[row - 1] = code;
    return new CardListFields(null, null, s[0], s[1], s[2], s[3], s[4], s[5], s[6]);
  }

  @Test
  void firstSendShowsSevenRowsAndHeader() {
    var r = first().response();
    assertThat(r.screen()).isEqualTo(ScreenId.COCRDLI);
    assertThat(r.data().pageno()).isEqualTo("1");
    assertThat(r.data().crdnum1()).isEqualTo(num(1));
    assertThat(r.data().crdnum7()).isEqualTo(num(7));
    assertThat(r.data().acctno1()).isEqualTo(acctText(1));
    assertThat(r.data().curdate()).isEqualTo("07/18/22");
    assertThat(r.data().curtime()).isEqualTo("12:00:00");
    assertThat(r.data().infomsg()).isEqualTo(CardListService.MSG_INFORM_ACTIONS);
    assertThat(r.data().errmsg()).isNull();
    assertThat(r.context().fromProgram()).isEqualTo("COCRDLIC");
    assertThat(st.nextPageExists()).isTrue();
  }

  @Test
  void pf8PagesForwardAndPf7Back() {
    first();
    var p2 = post(Action.PF8, filters(null, null)).response();
    assertThat(p2.data().pageno()).isEqualTo("2");
    assertThat(p2.data().crdnum1()).isEqualTo(num(8));
    var p3 = post(Action.PF8, filters(null, null)).response();
    assertThat(p3.data().pageno()).isEqualTo("3");
    assertThat(p3.data().crdnum1()).isEqualTo(num(15));
    assertThat(p3.data().crdnum3()).isEqualTo(num(17));
    assertThat(p3.data().crdnum4()).isNull();
    assertThat(texts(p3)).contains(CardListService.MSG_NO_MORE_RECORDS);
    var again = post(Action.PF8, filters(null, null)).response();
    assertThat(texts(again)).contains(CardListService.MSG_INFORM_ACTIONS);
    var end = post(Action.PF8, filters(null, null)).response();
    assertThat(texts(end)).contains(CardListService.MSG_NO_MORE_PAGES);
    var back = post(Action.PF7, filters(null, null)).response();
    assertThat(back.data().pageno()).isEqualTo("2");
    assertThat(back.data().crdnum1()).isEqualTo(num(8));
    assertThat(back.data().crdnum7()).isEqualTo(num(14));
    var top = post(Action.PF7, filters(null, null)).response();
    assertThat(top.data().pageno()).isEqualTo("1");
    var none = post(Action.PF7, filters(null, null)).response();
    assertThat(none.data().pageno()).isEqualTo("1");
    assertThat(texts(none)).contains(CardListService.MSG_NO_PREVIOUS);
  }

  @Test
  void invalidKeyIsTreatedAsEnter() {
    first();
    var r = post(Action.PF10, filters(null, null)).response();
    assertThat(r.data().crdnum1()).isEqualTo(num(1));
  }

  @Test
  void accountFilter() {
    first();
    var r = post(Action.ENTER, filters(acctText(1), null)).response();
    assertThat(r.data().acctsid()).isEqualTo(acctText(1));
    assertThat(r.data().crdnum1()).isEqualTo(num(1));
    assertThat(r.data().crdnum2()).isEqualTo(num(4));
    assertThat(r.context().accountId()).isEqualTo(CardFixtures.acct(1));
  }

  @Test
  void cardFilter() {
    first();
    var r = post(Action.ENTER, filters(null, num(5))).response();
    assertThat(r.data().crdnum1()).isEqualTo(num(5));
    assertThat(r.data().crdnum2()).isNull();
    assertThat(r.data().cardsid()).isEqualTo(num(5));
  }

  @Test
  void invalidFilters() {
    first();
    var a = post(Action.ENTER, filters("12AB", null)).response();
    assertThat(texts(a)).containsExactly(CardListService.MSG_ACCT_FILTER);
    assertThat(a.fieldErrors()).extracting(FieldError::field).containsExactly("ACCTSID");
    var c = post(Action.ENTER, filters(null, "123")).response();
    assertThat(texts(c)).containsExactly(CardListService.MSG_CARD_FILTER);
    assertThat(c.fieldErrors()).extracting(FieldError::field).containsExactly("CARDSID");
    var both = post(Action.ENTER, filters("x", "y")).response();
    assertThat(texts(both)).containsExactly(CardListService.MSG_ACCT_FILTER);
    assertThat(both.fieldErrors()).hasSize(2);
  }

  @Test
  void noRecordsFound() {
    first();
    var r = post(Action.ENTER, filters("99999999999", null)).response();
    assertThat(texts(r)).containsExactly(CardListService.MSG_NO_RECORDS);
  }

  @Test
  void selectSTransfersToDetail() {
    first();
    var r = post(Action.ENTER, select(3, "S")).response();
    assertThat(r.nextScreen()).isEqualTo(ScreenId.COCRDSL);
    assertThat(r.context().toProgram()).isEqualTo("COCRDSLC");
    assertThat(r.context().cardNumber()).isEqualTo(Long.parseLong(num(3)));
    assertThat(r.context().accountId()).isEqualTo(CardFixtures.acct(3));
    assertThat(r.data()).isNull();
  }

  @Test
  void selectUTransfersToUpdate() {
    first();
    var r = post(Action.ENTER, select(1, "U")).response();
    assertThat(r.nextScreen()).isEqualTo(ScreenId.COCRDUP);
    assertThat(r.context().toProgram()).isEqualTo("COCRDUPC");
  }

  @Test
  void moreThanOneSelection() {
    first();
    var f = new CardListFields(null, null, "S", "U", null, null, null, null, null);
    var r = post(Action.ENTER, f).response();
    assertThat(texts(r)).contains(CardListService.MSG_MORE_THAN_ONE);
    assertThat(r.fieldErrors()).extracting(FieldError::field).containsExactly("CRDSEL1", "CRDSEL2");
  }

  @Test
  void invalidActionCode() {
    first();
    var r = post(Action.ENTER, select(2, "X")).response();
    assertThat(texts(r)).contains(CardListService.MSG_INVALID_ACTION);
    assertThat(r.fieldErrors()).extracting(FieldError::field).containsExactly("CRDSEL2");
    assertThat(r.data().crdsel2()).isEqualTo("X");
  }

  @Test
  void pf3ReturnsToMenu() {
    first();
    var r = post(Action.PF3, filters(null, null)).response();
    assertThat(r.nextScreen()).isEqualTo(ScreenId.COMEN01);
    assertThat(r.context().toProgram()).isEqualTo("COMEN01C");
    assertThat(r.context().programContext()).isEqualTo(ConversationContext.ProgramContext.ENTER);
  }

  @Test
  void returnFromDetailRestartsList() {
    first();
    post(Action.PF8, filters(null, null));
    ctx = ContextEdit_from(ctx, "CCDL", "COCRDSLC");
    var r = post(Action.ENTER, null).response();
    assertThat(r.data().pageno()).isEqualTo("1");
    assertThat(r.data().crdnum1()).isEqualTo(num(1));
  }

  @Test
  void nonAdminSeesOnlyOwnedAccount() {
    ctx = CardFixtures.user();
    owned = Optional.of(CardFixtures.acct(2));
    var r = first().response();
    assertThat(r.data().crdnum1()).isEqualTo(num(2));
    assertThat(r.data().crdnum2()).isEqualTo(num(5));
    var other = post(Action.ENTER, filters(acctText(1), null)).response();
    assertThat(texts(other)).containsExactly(CardListService.MSG_NO_RECORDS);
  }

  @Test
  void backwardsBrowseReadsBoundedChunksAcrossSparseMatches() {
    var f = new CardFixtures(300);
    for (int i : new int[] {1, 2, 3, 4, 5, 6, 7, 290}) {
      Card c = CardFixtures.card(i);
      c.setCardAcctId(999L);
      f.put(c);
    }
    var big = new CardListService(f.browser, f.clock);
    var flt = filters("%011d".formatted(999), null);
    var o = big.process(ctx, CardListState.initial(), Optional.empty(), Action.ENTER, flt, true);
    o = big.process(o.response().context(), o.state(), Optional.empty(), Action.ENTER, flt, false);
    o = big.process(o.response().context(), o.state(), Optional.empty(), Action.PF8, flt, false);
    assertThat(o.response().data().crdnum1()).isEqualTo(num(290));
    o = big.process(o.response().context(), o.state(), Optional.empty(), Action.PF7, flt, false);
    assertThat(o.response().data().pageno()).isEqualTo("1");
    assertThat(o.response().data().crdnum1()).isEqualTo(num(1));
    assertThat(o.response().data().crdnum7()).isEqualTo(num(7));
    org.mockito.Mockito.verify(f.browser, org.mockito.Mockito.never())
        .readPrev(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq(Integer.MAX_VALUE));
  }

  @Test
  void backwardsBrowseKeepsCardsWhenTheCursorCardIsDeleted() {
    var f = new CardFixtures(300);
    for (int i : new int[] {1, 2, 3, 4, 5, 6, 7, 290}) {
      Card c = CardFixtures.card(i);
      c.setCardAcctId(999L);
      f.put(c);
    }
    var big = new CardListService(f.browser, f.clock);
    var flt = filters("%011d".formatted(999), null);
    var o = big.process(ctx, CardListState.initial(), Optional.empty(), Action.ENTER, flt, true);
    o = big.process(o.response().context(), o.state(), Optional.empty(), Action.ENTER, flt, false);
    o = big.process(o.response().context(), o.state(), Optional.empty(), Action.PF8, flt, false);
    Card late = CardFixtures.card(226);
    late.setCardAcctId(999L);
    f.put(late);
    // the first chunk read backwards from card 290 ends on card 227; another session deletes it
    String chunkEnd = num(290 - 63);
    org.mockito.Mockito.when(
            f.browser.readPrev(
                org.mockito.ArgumentMatchers.eq(chunkEnd), org.mockito.ArgumentMatchers.anyInt()))
        .thenAnswer(
            a -> {
              f.file.remove(chunkEnd);
              return f.file.headMap(chunkEnd, true).descendingMap().values().stream()
                  .limit(a.<Integer>getArgument(1))
                  .toList();
            });
    o = big.process(o.response().context(), o.state(), Optional.empty(), Action.PF7, flt, false);
    assertThat(o.response().data().crdnum7()).isEqualTo(num(226));
    assertThat(o.response().data().crdnum6()).isEqualTo(num(7));
    assertThat(o.response().data().crdnum1()).isEqualTo(num(2));
  }

  @Test
  void backwardsFromLastPageWithFilter() {
    first();
    post(Action.ENTER, filters(acctText(1), null));
    var p2 = post(Action.PF8, filters(acctText(1), null)).response();
    assertThat(p2.data().pageno()).isEqualTo("1");
    var f = new CardFixtures(40);
    var big = new CardListService(f.browser, f.clock);
    var o = big.process(ctx, CardListState.initial(), Optional.empty(), Action.ENTER, null, true);
    var c2 = o.response().context();
    o = big.process(c2, o.state(), Optional.empty(), Action.PF8, filters(acctText(1), null), false);
    assertThat(o.response().data().pageno()).isEqualTo("2");
    o =
        big.process(
            o.response().context(),
            o.state(),
            Optional.empty(),
            Action.PF7,
            filters(acctText(1), null),
            false);
    // 9100-READ-BACKWARDS fills from the bottom: fewer matches leave the top rows blank.
    assertThat(o.response().data().pageno()).isEqualTo("1");
    assertThat(o.response().data().crdnum1()).isNull();
    assertThat(o.response().data().crdnum5()).isEqualTo(num(1));
    assertThat(o.response().data().crdnum7()).isEqualTo(num(7));
  }

  private static ConversationContext ContextEdit_from(ConversationContext c, String t, String p) {
    return com.carddemo.online.card.session.ContextEdit.of(c).from(t, p).enter().build();
  }
}
