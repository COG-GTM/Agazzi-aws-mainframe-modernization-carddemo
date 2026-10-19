package com.carddemo.online.card.service;

import static com.carddemo.online.card.mapper.CardScreenSupport.isBlank;
import static com.carddemo.online.card.mapper.CardScreenSupport.isNumeric;
import static com.carddemo.online.card.mapper.CardScreenSupport.isZeros;
import static com.carddemo.online.card.mapper.CardScreenSupport.padRight;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.ProgramContext;
import com.carddemo.contracts.screen.FieldError;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenMessage;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.CocrdupScreen;
import com.carddemo.domain.entity.Card;
import com.carddemo.domain.repository.CardRepository;
import com.carddemo.online.card.mapper.CardScreenSupport;
import com.carddemo.online.card.mapper.CardScreenSupport.Header;
import com.carddemo.online.card.repository.CardRewriter;
import com.carddemo.online.card.session.CardUpdateState;
import com.carddemo.online.card.session.CardUpdateState.CardDetails;
import com.carddemo.online.card.session.CardUpdateState.Stage;
import com.carddemo.online.card.session.ContextEdit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/** COCRDUPC (CCUP): card update with validate (ENTER) / confirm (F5) / cancel (F12). */
@Service
public class CardUpdateService {

  public static final String TRANID = "CCUP";
  public static final String PGM = "COCRDUPC";
  public static final String MAP = "CCRDUPA";
  public static final String MAPSET = "COCRDUP";

  public static final String INFO_FOUND = "Details of selected card shown above";
  public static final String INFO_SEARCH = "Please enter Account and Card Number";
  public static final String INFO_CHANGES = "Update card details presented above.";
  public static final String INFO_CONFIRM = "Changes validated.Press F5 to save";
  public static final String INFO_DONE = "Changes committed to database";
  public static final String INFO_FAILURE = "Changes unsuccessful. Please try again";

  public static final String MSG_ACCT_NOT_PROVIDED = "Account number not provided";
  public static final String MSG_CARD_NOT_PROVIDED = "Card number not provided";
  public static final String MSG_NAME_NOT_PROVIDED = "Card name not provided";
  public static final String MSG_NAME_ALPHA = "Card name can only contain alphabets and spaces";
  public static final String MSG_NO_INPUT = "No input received";
  public static final String MSG_NO_CHANGE = "No change detected with respect to values fetched.";
  public static final String MSG_STATUS = "Card Active Status must be Y or N";
  public static final String MSG_MONTH = "Card expiry month must be between 1 and 12";
  public static final String MSG_YEAR = "Invalid card expiry year";
  public static final String MSG_NOT_FOUND = "Did not find cards for this search condition";
  public static final String MSG_LOCK = "Could not lock record for update";
  public static final String MSG_CHANGED = "Record changed by some one else. Please review";
  public static final String MSG_UPDATE_FAILED = "Update of record failed";
  public static final String MSG_READ_ERROR = "Error reading Card Data File";
  static final String FKEYS = "ENTER=Process F3=Exit";
  static final String FKEYSC = "F5=Save F12=Cancel";

  private enum Flag {
    BLANK,
    VALID,
    NOT_OK
  }

  private final CardRepository cards;
  private final CardRewriter rewriter;
  private final BusinessClock clock;

  public CardUpdateService(CardRepository cards, CardRewriter rewriter, BusinessClock clock) {
    this.cards = cards;
    this.rewriter = rewriter;
    this.clock = clock;
  }

  /** Working storage of one CCUP execution. */
  private static final class Run {
    ConversationContext ctx;
    Stage stage;
    CardDetails old;
    CardDetails neu;
    Optional<Long> owned;
    Action aid;
    String errorMsg;
    boolean inputError;
    boolean noChanges;
    String ccAcct;
    String ccCard;
    Flag acct = Flag.BLANK;
    Flag card = Flag.BLANK;
    Flag name = Flag.VALID;
    Flag status = Flag.VALID;
    Flag month = Flag.VALID;
    Flag year = Flag.VALID;
    boolean found;
  }

  public CardOutcome<CocrdupScreen.Output, CardUpdateState> process(
      ConversationContext ctx,
      CardUpdateState state,
      Optional<Long> owned,
      Action action,
      CardUpdateFields fields,
      boolean firstSend) {
    Run r = new Run();
    r.owned = owned;
    boolean commarea = !firstSend || PGM.equals(ctx.toProgram());
    r.ctx = ctx;
    r.stage = state.stage();
    r.old = state.old();
    r.neu = state.changed();
    if (!commarea
        || (CardScreenSupport.MENU_PGM.equals(ctx.fromProgram())
            && ctx.programContext() != ProgramContext.REENTER)) {
      ContextEdit ce = ContextEdit.of(ctx).enter();
      if (!commarea) {
        ce.from(null, null).account(null).card(null).lastMap(null, null);
      }
      r.ctx = ce.build();
      r.stage = Stage.NOT_FETCHED;
      r.old = CardDetails.empty();
      r.neu = CardDetails.empty();
    }
    boolean valid =
        action == Action.ENTER
            || action == Action.PF3
            || (action == Action.PF5 && r.stage == Stage.CHANGES_OK_NOT_CONFIRMED)
            || (action == Action.PF12 && r.stage != Stage.NOT_FETCHED);
    r.aid = valid ? action : Action.ENTER;
    boolean fromList = CardListService.PGM.equals(r.ctx.fromProgram());
    boolean lastWasList = CardListService.MAPSET.equals(r.ctx.lastMapset());

    if (r.aid == Action.PF3
        || ((r.stage == Stage.CHANGES_DONE || r.stage.changesFailed()) && lastWasList)) {
      return exit(r, lastWasList);
    }
    if ((r.ctx.programContext() == ProgramContext.ENTER && fromList)
        || (r.aid == Action.PF12 && fromList)) {
      r.ctx = ContextEdit.of(r.ctx).reenter().build();
      r.acct = Flag.VALID;
      r.card = Flag.VALID;
      r.ccAcct = CardScreenSupport.accountText(r.ctx.accountId());
      r.ccCard = CardScreenSupport.cardText(r.ctx.cardNumber());
      readData(r);
      r.stage = Stage.SHOW_DETAILS;
      return send(r);
    }
    if ((r.stage == Stage.NOT_FETCHED && r.ctx.programContext() == ProgramContext.ENTER)
        || (CardScreenSupport.MENU_PGM.equals(r.ctx.fromProgram())
            && r.ctx.programContext() != ProgramContext.REENTER)) {
      r.old = CardDetails.empty();
      r.neu = CardDetails.empty();
      r.stage = Stage.NOT_FETCHED;
      CardOutcome<CocrdupScreen.Output, CardUpdateState> out = send(r);
      return reenterAfterSend(out, Stage.NOT_FETCHED);
    }
    if (r.stage == Stage.CHANGES_DONE || r.stage.changesFailed()) {
      r.old = CardDetails.empty();
      r.neu = CardDetails.empty();
      r.ctx = ContextEdit.of(r.ctx).account(0L).card(0L).enter().build();
      r.stage = Stage.NOT_FETCHED;
      CardOutcome<CocrdupScreen.Output, CardUpdateState> out = send(r);
      return reenterAfterSend(out, Stage.NOT_FETCHED);
    }
    receive(r, fields == null ? CardUpdateFields.empty() : fields);
    edit(r);
    decide(r);
    return send(r);
  }

  private CardOutcome<CocrdupScreen.Output, CardUpdateState> exit(Run r, boolean lastWasList) {
    String toTran =
        isBlank(r.ctx.fromTransactionId())
            ? CardScreenSupport.MENU_TRANID
            : r.ctx.fromTransactionId();
    String toPgm = isBlank(r.ctx.fromProgram()) ? CardScreenSupport.MENU_PGM : r.ctx.fromProgram();
    ContextEdit ce =
        ContextEdit.of(r.ctx).to(toTran, toPgm).from(TRANID, PGM).enter().lastMap(MAP, MAPSET);
    if (lastWasList) {
      ce.account(0L).card(0L);
    }
    ScreenId next = CardScreenSupport.screenOfProgram(toPgm).orElse(ScreenId.COMEN01);
    return new CardOutcome<>(
        new ScreenResponse<>(ScreenId.COCRDUP, null, List.of(), next, List.of(), ce.build()),
        new CardUpdateState(r.stage, r.old, r.neu));
  }

  private static CardOutcome<CocrdupScreen.Output, CardUpdateState> reenterAfterSend(
      CardOutcome<CocrdupScreen.Output, CardUpdateState> out, Stage stage) {
    ScreenResponse<CocrdupScreen.Output> resp = out.response();
    ConversationContext ctx = ContextEdit.of(resp.context()).reenter().build();
    return new CardOutcome<>(
        new ScreenResponse<>(
            resp.screen(), resp.data(), resp.messages(), null, resp.fieldErrors(), ctx),
        out.state().withStage(stage));
  }

  /** 1100-RECEIVE-MAP. */
  private void receive(Run r, CardUpdateFields f) {
    String acct = CardScreenSupport.receive(f.acctsid());
    String card = CardScreenSupport.receive(f.cardsid());
    if (r.stage != Stage.NOT_FETCHED) {
      acct = r.old.acctId();
      card = r.old.cardId();
    }
    r.ccAcct = acct;
    r.ccCard = card;
    r.neu =
        new CardDetails(
            acct,
            card,
            null,
            CardScreenSupport.receive(f.crdname()),
            CardScreenSupport.receive(f.expyear()),
            CardScreenSupport.receive(f.expmon()),
            r.old.expDay(),
            CardScreenSupport.receive(f.crdstcd()));
  }

  /** 1200-EDIT-MAP-INPUTS. */
  private void edit(Run r) {
    if (r.stage == Stage.NOT_FETCHED) {
      editAccount(r);
      editCard(r);
      r.neu = new CardDetails(r.neu.acctId(), r.neu.cardId(), null, null, null, null, null, null);
      if (r.acct == Flag.BLANK && r.card == Flag.BLANK) {
        r.errorMsg = MSG_NO_INPUT;
      }
      return;
    }
    r.found = true;
    r.acct = Flag.VALID;
    r.card = Flag.VALID;
    r.ctx =
        ContextEdit.of(r.ctx)
            .account(parseOrZero(r.old.acctId()))
            .card(parseOrZero(r.old.cardId()))
            .build();
    if (r.neu.cardData().equals(r.old.cardData())) {
      r.noChanges = true;
      setError(r, MSG_NO_CHANGE);
    }
    if (r.noChanges || r.stage == Stage.CHANGES_OK_NOT_CONFIRMED || r.stage == Stage.CHANGES_DONE) {
      return;
    }
    r.stage = Stage.CHANGES_NOT_OK;
    editName(r);
    editStatus(r);
    editMonth(r);
    editYear(r);
    if (!r.inputError) {
      r.stage = Stage.CHANGES_OK_NOT_CONFIRMED;
    }
  }

  private void editAccount(Run r) {
    String v = r.ccAcct;
    if (v == null || isZeros(v)) {
      r.inputError = true;
      r.acct = Flag.BLANK;
      setError(r, MSG_ACCT_NOT_PROVIDED);
      r.ctx = ContextEdit.of(r.ctx).account(0L).build();
    } else if (!isNumeric(v, 11)) {
      r.inputError = true;
      r.acct = Flag.NOT_OK;
      setError(r, CardListService.MSG_ACCT_FILTER);
      r.ctx = ContextEdit.of(r.ctx).account(0L).build();
    } else {
      r.acct = Flag.VALID;
      r.ctx = ContextEdit.of(r.ctx).account(Long.parseLong(v)).build();
    }
  }

  private void editCard(Run r) {
    String v = r.ccCard;
    if (v == null || isZeros(v)) {
      r.inputError = true;
      r.card = Flag.BLANK;
      setError(r, MSG_CARD_NOT_PROVIDED);
      r.ctx = ContextEdit.of(r.ctx).card(0L).build();
    } else if (!isNumeric(v, 16)) {
      r.inputError = true;
      r.card = Flag.NOT_OK;
      setError(r, CardListService.MSG_CARD_FILTER);
      r.ctx = ContextEdit.of(r.ctx).card(0L).build();
    } else {
      r.card = Flag.VALID;
      r.ctx = ContextEdit.of(r.ctx).card(Long.parseLong(v)).build();
    }
  }

  private void editName(Run r) {
    String v = r.neu.name();
    if (v == null || isZeros(v.strip())) {
      r.inputError = true;
      r.name = Flag.BLANK;
      setError(r, MSG_NAME_NOT_PROVIDED);
    } else if (!v.chars().allMatch(c -> c == ' ' || (c < 128 && Character.isLetter(c)))) {
      r.inputError = true;
      r.name = Flag.NOT_OK;
      setError(r, MSG_NAME_ALPHA);
    }
  }

  private void editStatus(Run r) {
    String v = r.neu.status();
    if (v == null || isZeros(v)) {
      r.inputError = true;
      r.status = Flag.BLANK;
      setError(r, MSG_STATUS);
    } else if (!"Y".equals(v) && !"N".equals(v)) {
      r.inputError = true;
      r.status = Flag.NOT_OK;
      setError(r, MSG_STATUS);
    }
  }

  private void editMonth(Run r) {
    String v = r.neu.expMon() == null ? null : r.neu.expMon().strip();
    if (v == null || isZeros(v)) {
      r.inputError = true;
      r.month = Flag.BLANK;
      setError(r, MSG_MONTH);
    } else if (!(v.length() <= 2
        && v.chars().allMatch(Character::isDigit)
        && Integer.parseInt(v) >= 1
        && Integer.parseInt(v) <= 12)) {
      r.inputError = true;
      r.month = Flag.NOT_OK;
      setError(r, MSG_MONTH);
    }
  }

  private void editYear(Run r) {
    String v = r.neu.expYear();
    if (v == null || isZeros(v)) {
      r.inputError = true;
      r.year = Flag.BLANK;
      setError(r, MSG_YEAR);
    } else if (!(isNumeric(v, 4) && Integer.parseInt(v) >= 1950 && Integer.parseInt(v) <= 2099)) {
      r.inputError = true;
      r.year = Flag.NOT_OK;
      setError(r, MSG_YEAR);
    }
  }

  /** 2000-DECIDE-ACTION. */
  private void decide(Run r) {
    if (r.stage == Stage.NOT_FETCHED || r.aid == Action.PF12) {
      if (r.acct == Flag.VALID && r.card == Flag.VALID) {
        readData(r);
        if (r.found) {
          r.stage = Stage.SHOW_DETAILS;
        }
      }
    } else if (r.stage == Stage.SHOW_DETAILS) {
      if (!r.inputError && !r.noChanges) {
        r.stage = Stage.CHANGES_OK_NOT_CONFIRMED;
      }
    } else if (r.stage == Stage.CHANGES_OK_NOT_CONFIRMED && r.aid == Action.PF5) {
      write(r);
    } else if (r.stage == Stage.CHANGES_DONE) {
      r.stage = Stage.SHOW_DETAILS;
      if (isBlank(r.ctx.fromTransactionId())) {
        r.ctx = ContextEdit.of(r.ctx).account(0L).card(0L).build();
      }
    }
  }

  /** 9000-READ-DATA / 9100-GETCARD-BYACCTCARD. */
  private void readData(Run r) {
    r.old = new CardDetails(r.ccAcct, r.ccCard, null, null, null, null, null, null);
    r.found = false;
    try {
      Optional<Card> c = r.ccCard == null ? Optional.empty() : cards.findById(r.ccCard);
      if (c.isPresent()
          && r.owned.isPresent()
          && c.get().getCardAcctId().longValue() != r.owned.get()) {
        c = Optional.empty();
      }
      if (c.isPresent()) {
        r.found = true;
        r.old = oldDetails(r.ccAcct, r.ccCard, c.get());
      } else {
        r.inputError = true;
        r.acct = Flag.NOT_OK;
        r.card = Flag.NOT_OK;
        setError(r, MSG_NOT_FOUND);
      }
    } catch (DataAccessException e) {
      r.inputError = true;
      r.acct = Flag.NOT_OK;
      setError(r, MSG_READ_ERROR);
    }
  }

  private static long valueOrZero(Long v) {
    return v == null ? 0L : v;
  }

  private static CardDetails oldDetails(String acct, String cardNum, Card c) {
    String exp = padRight(c.getCardExpirationDate(), 10);
    return new CardDetails(
        acct,
        cardNum,
        c.getCardCvvCd(),
        padRight(c.getCardEmbossedName(), 50).toUpperCase(Locale.ROOT),
        exp.substring(0, 4),
        exp.substring(5, 7),
        exp.substring(8, 10),
        c.getCardActiveStatus());
  }

  /** 9200-WRITE-PROCESSING. */
  private void write(Run r) {
    Card values = new Card();
    values.setCardAcctId(parseOrZero(r.ccAcct));
    String mon = r.neu.expMon().strip();
    values.setCardEmbossedName(padRight(r.neu.name(), 50));
    values.setCardExpirationDate(
        r.neu.expYear() + "-" + (mon.length() == 1 ? "0" + mon : mon) + "-" + r.neu.expDay());
    values.setCardActiveStatus(r.neu.status());
    CardDetails expected = r.old;
    try {
      CardRewriter.Result res =
          rewriter.rewrite(
              r.ccCard,
              cur ->
                  parseOrZero(expected.acctId()) == valueOrZero(cur.getCardAcctId())
                      && oldDetails(expected.acctId(), expected.cardId(), cur).equals(expected),
              values);
      switch (res.status()) {
        case NOT_FOUND -> {
          r.inputError = true;
          setError(r, MSG_LOCK);
          r.stage = Stage.LOCK_ERROR;
        }
        case CHANGED -> {
          setError(r, MSG_CHANGED);
          r.old = oldDetails(r.ccAcct, r.ccCard, res.current());
          r.stage = Stage.SHOW_DETAILS;
        }
        default -> r.stage = Stage.CHANGES_DONE;
      }
    } catch (OptimisticLockingFailureException e) {
      r.inputError = true;
      setError(r, MSG_LOCK);
      r.stage = Stage.LOCK_ERROR;
    } catch (DataAccessException e) {
      setError(r, MSG_UPDATE_FAILED);
      r.stage = Stage.UPDATE_FAILED;
    }
  }

  private static void setError(Run r, String msg) {
    if (r.errorMsg == null) {
      r.errorMsg = msg;
    }
  }

  private static long parseOrZero(String s) {
    return s == null || isBlank(s) ? 0L : Long.parseLong(s.strip());
  }

  private static String info(Run r) {
    if (r.ctx.programContext() == ProgramContext.ENTER) {
      return INFO_SEARCH;
    }
    return switch (r.stage) {
      case NOT_FETCHED -> INFO_SEARCH;
      case SHOW_DETAILS -> INFO_FOUND;
      case CHANGES_NOT_OK -> INFO_CHANGES;
      case CHANGES_OK_NOT_CONFIRMED -> INFO_CONFIRM;
      case CHANGES_DONE -> INFO_DONE;
      case LOCK_ERROR, UPDATE_FAILED -> INFO_FAILURE;
    };
  }

  /** 3000-SEND-MAP. */
  private CardOutcome<CocrdupScreen.Output, CardUpdateState> send(Run r) {
    String infoMsg = info(r);
    String acctOut = null;
    String cardOut = null;
    String name = null;
    String sts = null;
    String day = null;
    String mon = null;
    String yr = null;
    if (r.ctx.programContext() != ProgramContext.ENTER) {
      acctOut = r.ccAcct == null || isZeros(r.ccAcct) ? null : r.ccAcct;
      cardOut = r.ccCard == null || isZeros(r.ccCard) ? null : r.ccCard;
      CardDetails shown =
          r.stage == Stage.NOT_FETCHED ? null : r.stage.changesMade() ? r.neu : r.old;
      if (shown != null) {
        name = CardScreenSupport.trimToNull(shown.name());
        sts = shown.status();
        day = r.old.expDay();
        mon = shown.expMon();
        yr = shown.expYear();
      }
    }
    Header h = Header.of(clock);
    CocrdupScreen.Output out =
        new CocrdupScreen.Output(
            TRANID,
            CardScreenSupport.TITLE01,
            h.curdate(),
            PGM,
            CardScreenSupport.TITLE02,
            h.curtime(),
            acctOut,
            cardOut,
            name,
            sts,
            mon,
            yr,
            day,
            infoMsg,
            r.errorMsg,
            FKEYS,
            r.stage == Stage.CHANGES_OK_NOT_CONFIRMED ? FKEYSC : null);
    List<ScreenMessage> messages = new ArrayList<>();
    if (r.errorMsg != null) {
      messages.add(ScreenMessage.error(r.errorMsg));
    }
    messages.add(ScreenMessage.info(infoMsg));
    List<FieldError> errors = new ArrayList<>();
    if (r.inputError) {
      addError(errors, "ACCTSID", r.acct, r.errorMsg);
      addError(errors, "CARDSID", r.card, r.errorMsg);
      addError(errors, "CRDNAME", r.name, r.errorMsg);
      addError(errors, "CRDSTCD", r.status, r.errorMsg);
      addError(errors, "EXPMON", r.month, r.errorMsg);
      addError(errors, "EXPYEAR", r.year, r.errorMsg);
    }
    ConversationContext ctx = r.ctx;
    return new CardOutcome<>(
        new ScreenResponse<>(ScreenId.COCRDUP, out, messages, null, errors, ctx),
        new CardUpdateState(r.stage, r.old, r.neu));
  }

  private static void addError(List<FieldError> errors, String field, Flag f, String msg) {
    if (f != Flag.VALID) {
      errors.add(new FieldError(field, msg));
    }
  }
}
