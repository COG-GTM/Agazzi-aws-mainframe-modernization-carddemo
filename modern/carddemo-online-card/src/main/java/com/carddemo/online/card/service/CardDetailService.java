package com.carddemo.online.card.service;

import static com.carddemo.online.card.mapper.CardScreenSupport.isBlank;
import static com.carddemo.online.card.mapper.CardScreenSupport.isNumeric;
import static com.carddemo.online.card.mapper.CardScreenSupport.isZeros;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.ProgramContext;
import com.carddemo.contracts.screen.FieldError;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.ScreenMessage;
import com.carddemo.contracts.screen.ScreenResponse;
import com.carddemo.contracts.screen.map.CocrdslScreen;
import com.carddemo.domain.entity.Card;
import com.carddemo.domain.repository.CardRepository;
import com.carddemo.online.card.mapper.CardScreenSupport;
import com.carddemo.online.card.mapper.CardScreenSupport.Header;
import com.carddemo.online.card.session.ContextEdit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** COCRDSLC (CCDL): card detail by account and card number. */
@Service
public class CardDetailService {

  public static final String TRANID = "CCDL";
  public static final String PGM = "COCRDSLC";
  public static final String MAP = "CCRDSLA";
  public static final String MAPSET = "COCRDSL";

  public static final String MSG_FOUND = "   Displaying requested details";
  public static final String MSG_PROMPT = "Please enter Account and Card Number";
  public static final String MSG_ACCT_NOT_PROVIDED = "Account number not provided";
  public static final String MSG_CARD_NOT_PROVIDED = "Card number not provided";
  public static final String MSG_NO_INPUT = "No input received";
  public static final String MSG_NOT_FOUND = "Did not find cards for this search condition";
  public static final String MSG_READ_ERROR = "Error reading Card Data File";
  static final String FKEYS = "ENTER=Search Cards  F3=Exit";

  private final CardRepository cards;
  private final BusinessClock clock;

  public CardDetailService(CardRepository cards, BusinessClock clock) {
    this.cards = cards;
    this.clock = clock;
  }

  private enum Flag {
    BLANK,
    VALID,
    NOT_OK
  }

  /** Working storage of one CCDL execution. */
  private static final class Run {
    ConversationContext ctx;
    String errorMsg;
    String infoMsg;
    boolean inputError;
    Flag acct = Flag.VALID;
    Flag card = Flag.VALID;
    String acctIn;
    String cardIn;
    Card found;
  }

  public CardOutcome<CocrdslScreen.Output, Void> process(
      ConversationContext ctx,
      Optional<Long> owned,
      Action action,
      CocrdslScreen.Input fields,
      boolean firstSend) {
    Run r = new Run();
    boolean commarea = !firstSend || PGM.equals(ctx.toProgram());
    r.ctx = ctx;
    if (!commarea
        || (CardScreenSupport.MENU_PGM.equals(ctx.fromProgram())
            && ctx.programContext() != ProgramContext.REENTER)) {
      r.ctx = ContextEdit.of(ctx).enter().account(null).card(null).lastMap(null, null).build();
      if (!commarea) {
        r.ctx = ContextEdit.of(r.ctx).from(null, null).build();
      }
    }
    Action aid = action == Action.PF3 ? Action.PF3 : Action.ENTER;
    if (aid == Action.PF3) {
      String toTran =
          isBlank(r.ctx.fromTransactionId())
              ? CardScreenSupport.MENU_TRANID
              : r.ctx.fromTransactionId();
      String toPgm =
          isBlank(r.ctx.fromProgram()) ? CardScreenSupport.MENU_PGM : r.ctx.fromProgram();
      ConversationContext out =
          ContextEdit.of(r.ctx)
              .from(TRANID, PGM)
              .to(toTran, toPgm)
              .enter()
              .lastMap(MAP, MAPSET)
              .build();
      ScreenId next = CardScreenSupport.screenOfProgram(toPgm).orElse(ScreenId.COMEN01);
      return new CardOutcome<>(
          new ScreenResponse<>(ScreenId.COCRDSL, null, List.of(), next, List.of(), out), null);
    }
    boolean showKeys = commarea;
    if (r.ctx.programContext() == ProgramContext.ENTER
        && CardListService.PGM.equals(r.ctx.fromProgram())) {
      r.acctIn = CardScreenSupport.accountText(r.ctx.accountId());
      r.cardIn = CardScreenSupport.cardText(r.ctx.cardNumber());
      read(r, owned);
    } else if (r.ctx.programContext() == ProgramContext.ENTER || !commarea) {
      showKeys = commarea && r.ctx.accountId() != null;
    } else {
      edit(r, fields == null ? new CocrdslScreen.Input(null, null) : fields);
      if (!r.inputError) {
        read(r, owned);
      }
    }
    return send(r, commarea && showKeys);
  }

  private void edit(Run r, CocrdslScreen.Input in) {
    r.acctIn = CardScreenSupport.receive(in.acctsid());
    r.cardIn = CardScreenSupport.receive(in.cardsid());
    ContextEdit ce = ContextEdit.of(r.ctx);
    // 2210-EDIT-ACCOUNT
    if (r.acctIn == null || isZeros(r.acctIn)) {
      r.inputError = true;
      r.acct = Flag.BLANK;
      setError(r, MSG_ACCT_NOT_PROVIDED);
      ce.account(0L);
    } else if (!isNumeric(r.acctIn, 11)) {
      r.inputError = true;
      r.acct = Flag.NOT_OK;
      setError(r, CardListService.MSG_ACCT_FILTER);
      ce.account(0L);
    } else {
      ce.account(Long.parseLong(r.acctIn));
    }
    // 2220-EDIT-CARD
    if (r.cardIn == null || isZeros(r.cardIn)) {
      r.inputError = true;
      r.card = Flag.BLANK;
      setError(r, MSG_CARD_NOT_PROVIDED);
      ce.card(0L);
    } else if (!isNumeric(r.cardIn, 16)) {
      r.inputError = true;
      r.card = Flag.NOT_OK;
      setError(r, CardListService.MSG_CARD_FILTER);
      ce.card(0L);
    } else {
      ce.card(Long.parseLong(r.cardIn));
    }
    if (r.acct == Flag.BLANK && r.card == Flag.BLANK) {
      r.errorMsg = MSG_NO_INPUT;
    }
    r.ctx = ce.build();
  }

  /** 9100-GETCARD-BYACCTCARD: READ CARDDAT by card number. */
  private void read(Run r, Optional<Long> owned) {
    try {
      Optional<Card> c = r.cardIn == null ? Optional.empty() : cards.findById(r.cardIn);
      if (c.isPresent()
          && owned.isPresent()
          && c.get().getCardAcctId().longValue() != owned.get()) {
        c = Optional.empty();
      }
      if (c.isPresent()) {
        r.found = c.get();
        r.infoMsg = MSG_FOUND;
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

  private static void setError(Run r, String msg) {
    if (r.errorMsg == null) {
      r.errorMsg = msg;
    }
  }

  private CardOutcome<CocrdslScreen.Output, Void> send(Run r, boolean showKeys) {
    if (r.infoMsg == null) {
      r.infoMsg = MSG_PROMPT;
    }
    String acctOut = null;
    String cardOut = null;
    if (showKeys) {
      acctOut = r.ctx.accountId() == null || r.ctx.accountId() == 0 ? null : r.acctIn;
      cardOut = r.ctx.cardNumber() == null || r.ctx.cardNumber() == 0 ? null : r.cardIn;
    }
    Card c = r.found;
    String exp = c == null ? null : c.getCardExpirationDate();
    Header h = Header.of(clock);
    CocrdslScreen.Output out =
        new CocrdslScreen.Output(
            TRANID,
            CardScreenSupport.TITLE01,
            h.curdate(),
            PGM,
            CardScreenSupport.TITLE02,
            h.curtime(),
            acctOut,
            cardOut,
            c == null ? null : CardScreenSupport.trimToNull(c.getCardEmbossedName()),
            c == null ? null : c.getCardActiveStatus(),
            exp == null || exp.length() < 7 ? null : exp.substring(5, 7),
            exp == null || exp.length() < 4 ? null : exp.substring(0, 4),
            r.infoMsg,
            r.errorMsg,
            FKEYS);
    List<ScreenMessage> messages = new ArrayList<>();
    if (r.errorMsg != null) {
      messages.add(ScreenMessage.error(r.errorMsg));
    }
    messages.add(ScreenMessage.info(r.infoMsg));
    List<FieldError> errors = new ArrayList<>();
    if (r.inputError && r.acct != Flag.VALID) {
      errors.add(new FieldError("ACCTSID", r.errorMsg));
    }
    if (r.inputError && r.card != Flag.VALID) {
      errors.add(new FieldError("CARDSID", r.errorMsg));
    }
    ConversationContext ctx = ContextEdit.of(r.ctx).reenter().build();
    return new CardOutcome<>(
        new ScreenResponse<>(ScreenId.COCRDSL, out, messages, null, errors, ctx), null);
  }
}
