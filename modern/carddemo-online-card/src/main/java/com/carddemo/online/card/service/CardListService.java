package com.carddemo.online.card.service;

import static com.carddemo.online.card.mapper.CardScreenSupport.isBlank;
import static com.carddemo.online.card.mapper.CardScreenSupport.isNumeric;
import static com.carddemo.online.card.mapper.CardScreenSupport.isZeros;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.FieldError;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenMessage;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.CocrdliScreen;
import com.carddemo.domain.entity.Card;
import com.carddemo.online.card.mapper.CardScreenSupport;
import com.carddemo.online.card.mapper.CardScreenSupport.Header;
import com.carddemo.online.card.repository.CardBrowser;
import com.carddemo.online.card.session.CardListState;
import com.carddemo.online.card.session.CardListState.Row;
import com.carddemo.online.card.session.ContextEdit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** COCRDLIC (CCLI): card list, 7 rows per page, account/card filters, S/U row selection. */
@Service
public class CardListService {

  public static final String TRANID = "CCLI";
  public static final String PGM = "COCRDLIC";
  public static final String MAP = "CCRDLIA";
  public static final String MAPSET = "COCRDLI";
  static final int MAX_SCREEN_LINES = 7;
  private static final int BROWSE_CHUNK = 64;

  public static final String MSG_ACCT_FILTER =
      "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER";
  public static final String MSG_CARD_FILTER =
      "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER";
  public static final String MSG_MORE_THAN_ONE = "PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE";
  public static final String MSG_INVALID_ACTION = "INVALID ACTION CODE";
  public static final String MSG_NO_MORE_RECORDS = "NO MORE RECORDS TO SHOW";
  public static final String MSG_NO_RECORDS = "NO RECORDS FOUND FOR THIS SEARCH CONDITION.";
  public static final String MSG_NO_PREVIOUS = "NO PREVIOUS PAGES TO DISPLAY";
  public static final String MSG_NO_MORE_PAGES = "NO MORE PAGES TO DISPLAY";
  public static final String MSG_INFORM_ACTIONS = "TYPE S FOR DETAIL, U TO UPDATE ANY RECORD";

  private enum Filter {
    BLANK,
    VALID,
    NOT_OK
  }

  private final CardBrowser browser;
  private final BusinessClock clock;

  public CardListService(CardBrowser browser, BusinessClock clock) {
    this.browser = browser;
    this.clock = clock;
  }

  /** Working storage of one CCLI execution. */
  private final class Run {
    ConversationContext ctx;
    CardListState st;
    Optional<Long> owned;
    String errorMsg;
    boolean noRecordsFound;
    Filter acctFlag = Filter.BLANK;
    Filter cardFlag = Filter.BLANK;
    String acctIn;
    String cardIn;
    long acctFilter;
    String cardFilter;
    boolean inputError;
    List<String> select = Arrays.asList(new String[MAX_SCREEN_LINES]);
    boolean[] selectError = new boolean[MAX_SCREEN_LINES];
    int selected = -1;
    boolean commarea;

    boolean include(Card c) {
      if (owned.isPresent() && c.getCardAcctId().longValue() != owned.get()) {
        return false;
      }
      if (acctFlag == Filter.VALID && c.getCardAcctId() != acctFilter) {
        return false;
      }
      return cardFlag != Filter.VALID || c.getCardNum().equals(cardFilter);
    }
  }

  /**
   * One CCLI interaction.
   *
   * @param firstSend {@code GET}: no map is received (EIBCALEN = 0 unless an XCTL brought us here)
   * @param owned account a non-admin user is confined to (PARITY-EXCEPTION, empty = legacy)
   */
  public CardOutcome<CocrdliScreen.Output, CardListState> process(
      ConversationContext ctx,
      CardListState state,
      Optional<Long> owned,
      Action action,
      CardListFields fields,
      boolean firstSend) {
    Run r = new Run();
    r.owned = owned;
    r.st = state;
    r.commarea = !firstSend || PGM.equals(ctx.toProgram());
    r.ctx = ctx;
    if (!r.commarea) {
      r.ctx = ContextEdit.of(ctx).from(TRANID, PGM).enter().lastMap(MAP, MAPSET).build();
      r.st = CardListState.initial();
    }
    if (r.ctx.programContext() == ConversationContext.ProgramContext.ENTER
        && !PGM.equals(r.ctx.fromProgram())) {
      r.st = CardListState.initial();
      r.ctx = ContextEdit.of(r.ctx).enter().lastMap(MAP, r.ctx.lastMapset()).build();
    }
    if (r.commarea && PGM.equals(r.ctx.fromProgram())) {
      receive(r, fields == null ? CardListFields.empty() : fields);
    }
    Action aid =
        switch (action) {
          case ENTER, PF3, PF7, PF8 -> action;
          default -> Action.ENTER;
        };
    if (aid == Action.PF3 && PGM.equals(r.ctx.fromProgram())) {
      ConversationContext out =
          ContextEdit.of(r.ctx)
              .from(TRANID, PGM)
              .to(CardScreenSupport.MENU_TRANID, CardScreenSupport.MENU_PGM)
              .enter()
              .lastMap(MAP, MAPSET)
              .build();
      return new CardOutcome<>(
          new ScreenResponse<>(ScreenId.COCRDLI, null, List.of(), ScreenId.COMEN01, List.of(), out),
          r.st);
    }
    boolean lastPageShown = aid == Action.PF8 && r.st.lastPageShown();
    r.st = withLastPageShown(r.st, lastPageShown);

    boolean fromThis = PGM.equals(r.ctx.fromProgram());
    if (r.inputError) {
      if (r.acctFlag != Filter.NOT_OK && r.cardFlag != Filter.NOT_OK) {
        readForward(r, r.st.firstCardNum());
      }
    } else if (aid == Action.PF7 && r.st.firstPage()) {
      readForward(r, r.st.firstCardNum());
    } else if (aid == Action.PF3
        || (r.ctx.programContext() == ConversationContext.ProgramContext.REENTER && !fromThis)) {
      r.ctx = ContextEdit.of(r.ctx).from(TRANID, PGM).enter().lastMap(MAP, MAPSET).build();
      r.st = CardListState.initial();
      readForward(r, null);
    } else if (aid == Action.PF8 && r.st.nextPageExists()) {
      r.st = withScreen(r.st, r.st.screenNum() + 1);
      readForward(r, r.st.lastCardNum());
    } else if (aid == Action.PF7) {
      r.st = withScreen(r.st, r.st.screenNum() - 1);
      readBackwards(r);
    } else if (aid == Action.ENTER && r.selected >= 0 && fromThis) {
      return transfer(r);
    } else {
      readForward(r, r.st.firstCardNum());
    }
    return send(r, aid);
  }

  private void receive(Run r, CardListFields f) {
    r.acctIn = f.acctsid();
    r.cardIn = f.cardsid();
    List<String> sel = f.selections();
    for (int i = 0; i < MAX_SCREEN_LINES; i++) {
      r.select.set(i, sel.get(i));
    }
    // 2210-EDIT-ACCOUNT
    ContextEdit ce = ContextEdit.of(r.ctx);
    if (isBlank(r.acctIn) || isZeros(r.acctIn)) {
      r.acctFlag = Filter.BLANK;
      ce.account(0L);
    } else if (!isNumeric(r.acctIn, 11)) {
      r.inputError = true;
      r.acctFlag = Filter.NOT_OK;
      r.errorMsg = MSG_ACCT_FILTER;
      ce.account(0L);
    } else {
      r.acctFilter = Long.parseLong(r.acctIn);
      r.acctFlag = Filter.VALID;
      ce.account(r.acctFilter);
    }
    // 2220-EDIT-CARD
    if (isBlank(r.cardIn) || isZeros(r.cardIn)) {
      r.cardFlag = Filter.BLANK;
      ce.card(0L);
    } else if (!isNumeric(r.cardIn, 16)) {
      r.inputError = true;
      r.cardFlag = Filter.NOT_OK;
      if (r.errorMsg == null) {
        r.errorMsg = MSG_CARD_FILTER;
      }
      ce.card(0L);
    } else {
      r.cardFilter = r.cardIn;
      r.cardFlag = Filter.VALID;
      ce.card(Long.parseLong(r.cardIn));
    }
    r.ctx = ce.build();
    // 2250-EDIT-ARRAY
    if (r.inputError) {
      return;
    }
    long actions = r.select.stream().filter(s -> "S".equals(s) || "U".equals(s)).count();
    boolean moreThanOne = actions > 1;
    if (moreThanOne) {
      r.inputError = true;
      r.errorMsg = MSG_MORE_THAN_ONE;
    }
    for (int i = 0; i < MAX_SCREEN_LINES; i++) {
      String s = r.select.get(i);
      if ("S".equals(s) || "U".equals(s)) {
        r.selected = i;
        if (moreThanOne) {
          r.selectError[i] = true;
        }
      } else if (!isBlank(s)) {
        r.inputError = true;
        r.selectError[i] = true;
        if (r.errorMsg == null) {
          r.errorMsg = MSG_INVALID_ACTION;
        }
      }
    }
    if (r.inputError) {
      r.selected = -1;
    }
  }

  /** 9000-READ-FORWARD. */
  private void readForward(Run r, String key) {
    List<Row> rows = new ArrayList<>();
    String first = r.st.firstCardNum();
    String last = r.st.lastCardNum();
    int screen = r.st.screenNum();
    boolean next = true;
    String cursor = key;
    boolean skipCursor = false;
    boolean done = false;
    while (!done) {
      List<Card> chunk = browser.readNext(cursor, BROWSE_CHUNK + (skipCursor ? 1 : 0));
      if (skipCursor && !chunk.isEmpty() && chunk.get(0).getCardNum().equals(cursor)) {
        chunk = chunk.subList(1, chunk.size());
      }
      if (chunk.isEmpty()) {
        next = false;
        if (r.errorMsg == null) {
          r.errorMsg = MSG_NO_MORE_RECORDS;
        }
        if (screen == 1 && rows.isEmpty()) {
          r.noRecordsFound = true;
        }
        break;
      }
      for (int i = 0; i < chunk.size(); i++) {
        Card c = chunk.get(i);
        last = c.getCardNum();
        if (r.include(c)) {
          rows.add(new Row(c.getCardAcctId(), c.getCardNum(), c.getCardActiveStatus()));
          if (rows.size() == 1) {
            first = c.getCardNum();
            if (screen == 0) {
              screen = 1;
            }
          }
        }
        if (rows.size() == MAX_SCREEN_LINES) {
          Card ahead = i + 1 < chunk.size() ? chunk.get(i + 1) : peekAfter(c.getCardNum());
          if (ahead != null) {
            next = true;
            last = ahead.getCardNum();
          } else {
            next = false;
            if (r.errorMsg == null) {
              r.errorMsg = MSG_NO_MORE_RECORDS;
            }
          }
          done = true;
          break;
        }
      }
      cursor = last;
      skipCursor = true;
    }
    r.st = new CardListState(first, last, screen, next, r.st.lastPageShown(), rows);
  }

  private Card peekAfter(String key) {
    return browser.readNext(key, 2).stream()
        .filter(c -> !c.getCardNum().equals(key))
        .findFirst()
        .orElse(null);
  }

  /** 9100-READ-BACKWARDS: rows are filled from the bottom, the first READPREV is the old top. */
  private void readBackwards(Run r) {
    Row[] rows = new Row[MAX_SCREEN_LINES];
    String oldFirst = r.st.firstCardNum();
    String first = oldFirst;
    int counter = MAX_SCREEN_LINES + 1;
    String cursor = oldFirst;
    boolean done = false;
    while (!done) {
      List<Card> chunk = browser.readPrev(cursor, BROWSE_CHUNK);
      if (counter == MAX_SCREEN_LINES + 1 && !chunk.isEmpty()) {
        counter--;
      }
      if (!chunk.isEmpty() && chunk.get(0).getCardNum().equals(cursor)) {
        chunk = chunk.subList(1, chunk.size());
      }
      if (chunk.isEmpty()) {
        break;
      }
      for (Card c : chunk) {
        cursor = c.getCardNum();
        if (r.include(c)) {
          rows[counter - 1] = new Row(c.getCardAcctId(), c.getCardNum(), c.getCardActiveStatus());
          counter--;
          if (counter == 0) {
            first = c.getCardNum();
            done = true;
            break;
          }
        }
      }
    }
    r.st =
        new CardListState(
            first, oldFirst, r.st.screenNum(), true, r.st.lastPageShown(), Arrays.asList(rows));
  }

  private CardOutcome<CocrdliScreen.Output, CardListState> transfer(Run r) {
    Row row = r.selected < r.st.rows().size() ? r.st.rows().get(r.selected) : null;
    boolean view = "S".equals(r.select.get(r.selected));
    ScreenId target = view ? ScreenId.COCRDSL : ScreenId.COCRDUP;
    ConversationContext out =
        ContextEdit.of(r.ctx)
            .from(TRANID, PGM)
            .to(target.transactionId(), target.program())
            .enter()
            .lastMap(MAP, MAPSET)
            .account(row == null ? 0L : row.accountId())
            .card(row == null ? 0L : Long.parseLong(row.cardNum()))
            .build();
    return new CardOutcome<>(
        new ScreenResponse<>(ScreenId.COCRDLI, null, List.of(), target, List.of(), out), r.st);
  }

  /** 1000-SEND-MAP (1100..1400) and COMMON-RETURN. */
  private CardOutcome<CocrdliScreen.Output, CardListState> send(Run r, Action aid) {
    boolean info = true;
    if (r.acctFlag == Filter.NOT_OK || r.cardFlag == Filter.NOT_OK) {
      info = false;
    } else if (aid == Action.PF7 && r.st.firstPage()) {
      r.errorMsg = MSG_NO_PREVIOUS;
    } else if (aid == Action.PF8 && !r.st.nextPageExists() && r.st.lastPageShown()) {
      r.errorMsg = MSG_NO_MORE_PAGES;
    } else if (aid == Action.PF8 && !r.st.nextPageExists()) {
      r.st = withLastPageShown(r.st, true);
    }
    if (r.noRecordsFound) {
      r.errorMsg = MSG_NO_RECORDS;
    }
    String infoMsg = info && !r.noRecordsFound ? MSG_INFORM_ACTIONS : null;

    boolean showFilters =
        r.commarea
            && !(r.ctx.programContext() == ConversationContext.ProgramContext.ENTER
                && CardScreenSupport.MENU_PGM.equals(r.ctx.fromProgram()));
    String acctOut = null;
    String cardOut = null;
    if (showFilters) {
      acctOut =
          r.acctFlag != Filter.BLANK ? r.acctIn : CardScreenSupport.accountText(r.ctx.accountId());
      cardOut =
          r.cardFlag != Filter.BLANK ? r.cardIn : CardScreenSupport.cardText(r.ctx.cardNumber());
    }
    String[] sel = new String[MAX_SCREEN_LINES];
    String[] acct = new String[MAX_SCREEN_LINES];
    String[] num = new String[MAX_SCREEN_LINES];
    String[] sts = new String[MAX_SCREEN_LINES];
    for (int i = 0; i < MAX_SCREEN_LINES; i++) {
      Row row = i < r.st.rows().size() ? r.st.rows().get(i) : null;
      if (row != null) {
        sel[i] = r.select.get(i);
        acct[i] = "%011d".formatted(row.accountId());
        num[i] = row.cardNum();
        sts[i] = row.status();
      }
    }
    Header h = Header.of(clock);
    CocrdliScreen.Output out =
        new CocrdliScreen.Output(
            TRANID,
            CardScreenSupport.TITLE01,
            h.curdate(),
            PGM,
            CardScreenSupport.TITLE02,
            h.curtime(),
            String.valueOf(r.st.screenNum()),
            acctOut,
            cardOut,
            sel[0],
            acct[0],
            num[0],
            sts[0],
            sel[1],
            null,
            acct[1],
            num[1],
            sts[1],
            sel[2],
            null,
            acct[2],
            num[2],
            sts[2],
            sel[3],
            null,
            acct[3],
            num[3],
            sts[3],
            sel[4],
            null,
            acct[4],
            num[4],
            sts[4],
            sel[5],
            null,
            acct[5],
            num[5],
            sts[5],
            sel[6],
            null,
            acct[6],
            num[6],
            sts[6],
            infoMsg,
            r.errorMsg);
    List<ScreenMessage> messages = new ArrayList<>();
    if (r.errorMsg != null) {
      messages.add(ScreenMessage.error(r.errorMsg));
    }
    if (infoMsg != null) {
      messages.add(ScreenMessage.info(infoMsg));
    }
    List<FieldError> errors = new ArrayList<>();
    if (r.acctFlag == Filter.NOT_OK) {
      errors.add(new FieldError("ACCTSID", MSG_ACCT_FILTER));
    }
    if (r.cardFlag == Filter.NOT_OK) {
      errors.add(new FieldError("CARDSID", MSG_CARD_FILTER));
    }
    for (int i = 0; i < MAX_SCREEN_LINES; i++) {
      if (r.selectError[i]) {
        errors.add(new FieldError("CRDSEL" + (i + 1), r.errorMsg));
      }
    }
    ConversationContext ctx = ContextEdit.of(r.ctx).from(TRANID, PGM).lastMap(MAP, MAPSET).build();
    return new CardOutcome<>(
        new ScreenResponse<>(ScreenId.COCRDLI, out, messages, null, errors, ctx), r.st);
  }

  private static CardListState withScreen(CardListState s, int screen) {
    return new CardListState(
        s.firstCardNum(), s.lastCardNum(), screen, s.nextPageExists(), s.lastPageShown(), s.rows());
  }

  private static CardListState withLastPageShown(CardListState s, boolean shown) {
    return new CardListState(
        s.firstCardNum(), s.lastCardNum(), s.screenNum(), s.nextPageExists(), shown, s.rows());
  }
}
