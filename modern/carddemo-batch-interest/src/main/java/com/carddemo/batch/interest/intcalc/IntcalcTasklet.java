package com.carddemo.batch.interest.intcalc;

import static com.carddemo.batch.interest.intcalc.InterestCalculator.pad;

import com.carddemo.batch.interest.support.Abend;
import com.carddemo.batch.interest.support.GenerationWriter;
import com.carddemo.batch.interest.support.Sysout;
import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.contracts.record.TranCatBalanceRecord;
import com.carddemo.copybook.CoreCodecs;
import com.carddemo.domain.entity.Account;
import com.carddemo.domain.entity.CardXref;
import com.carddemo.domain.entity.DisclosureGroup;
import com.carddemo.domain.entity.DisclosureGroupId;
import com.carddemo.domain.entity.TranCatBalance;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.DisclosureGroupRepository;
import com.carddemo.domain.repository.TranCatBalanceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * INTCALC STEP15 = CBACT04C. Reads TCATBALF in key order, applies the disclosure-group rate per
 * category (fallback to {@code DEFAULT}), writes one SYSTRAN record per non-zero rate and rewrites
 * each account with the accumulated interest. The whole step is one transaction: a restart re-runs
 * it from the beginning on unchanged data. Changes are flushed to the database and the persistence
 * context is cleared every {@link #CHUNK_ACCOUNTS} accounts (Fase 5A, finding 5B F3), so the cost
 * per record stays constant on large TCATBALF files.
 */
@Component
public class IntcalcTasklet implements Tasklet {

  /** {@code PARM='2022071800'} of the JCL EXEC. */
  public static final String PARM = "PARM";

  public static final String SYSTRAN = "AWS.M2.CARDDEMO.SYSTRAN";
  static final String DEFAULT_GROUP = pad("DEFAULT", 10);
  static final String STATUS_NOT_FOUND = "FILE STATUS IS: NNNN0023";

  private static final Comparator<TranCatBalance> KEY_ORDER =
      Comparator.comparing((TranCatBalance t) -> t.getId().trancatAcctId())
          .thenComparing(t -> t.getId().trancatTypeCd())
          .thenComparing(t -> t.getId().trancatCd());

  /** Accounts per flush/clear chunk. */
  static final int CHUNK_ACCOUNTS = 500;

  @PersistenceContext private EntityManager em;
  private PlatformTransactionManager chunkTx;
  private ChunkTransaction chunks;

  private final TranCatBalanceRepository balances;
  private final AccountRepository accounts;
  private final CardXrefRepository xrefs;
  private final DisclosureGroupRepository groups;
  private final GenerationStore generations;
  private final BusinessClock clock;
  private final Sysout sysout;

  public IntcalcTasklet(
      TranCatBalanceRepository balances,
      AccountRepository accounts,
      CardXrefRepository xrefs,
      DisclosureGroupRepository groups,
      GenerationStore generations,
      BusinessClock clock,
      Sysout sysout) {
    this.balances = balances;
    this.accounts = accounts;
    this.xrefs = xrefs;
    this.groups = groups;
    this.generations = generations;
    this.clock = clock;
    this.sysout = sysout;
  }

  /**
   * The step then runs without a transaction and every {@link #CHUNK_ACCOUNTS} accounts are
   * committed (finding 5B F3).
   */
  public IntcalcTasklet chunkTransactions(PlatformTransactionManager tm) {
    this.chunkTx = tm;
    return this;
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    if (chunkTx == null) {
      return calculate(contribution, chunkContext);
    }
    chunks = new ChunkTransaction(chunkTx);
    try {
      RepeatStatus status = calculate(contribution, chunkContext);
      chunks.commit();
      return status;
    } catch (RuntimeException e) {
      chunks.rollback();
      throw e;
    } finally {
      chunks = null;
    }
  }

  private RepeatStatus calculate(StepContribution contribution, ChunkContext chunkContext) {
    String parmDate =
        pad(
            chunkContext.getStepContext().getStepExecution().getJobParameters().getString(PARM),
            10);
    sysout.display("START OF EXECUTION OF PROGRAM CBACT04C");
    List<TranCatBalance> records = balances.findAll().stream().sorted(KEY_ORDER).toList();
    try (GenerationWriter systran = GenerationWriter.open(generations, SYSTRAN)) {
      Long lastAcct = null;
      Account account = null;
      CardXref xref = null;
      BigDecimal totalInt = BigDecimal.ZERO.setScale(2);
      int suffix = 0;
      int accountsInChunk = 0;
      for (TranCatBalance tcat : records) {
        contribution.incrementReadCount();
        long acctId = tcat.getId().trancatAcctId();
        sysout.display(CoreCodecs.TRAN_CAT_BALANCE.encodeLine(toRecord(tcat)));
        if (lastAcct == null || lastAcct != acctId) {
          if (account != null) {
            updateAccount(account, totalInt);
            contribution.incrementWriteCount(1);
            if (++accountsInChunk == CHUNK_ACCOUNTS) {
              flushChunk();
              accountsInChunk = 0;
            }
          }
          totalInt = BigDecimal.ZERO.setScale(2);
          lastAcct = acctId;
          account = readAccount(acctId);
          xref = readXref(acctId);
        }
        BigDecimal rate = interestRate(account.getAcctGroupId(), tcat);
        if (rate.signum() != 0) {
          BigDecimal monthly = InterestCalculator.monthlyInterest(tcat.getTranCatBal(), rate);
          totalInt = InterestCalculator.addToTotal(totalInt, monthly);
          suffix++;
          systran.write(
              CoreCodecs.TRANSACTION.encodeLine(
                  InterestCalculator.interestTransaction(
                      InterestCalculator.tranId(parmDate, suffix),
                      account.getAcctId(),
                      monthly,
                      xref.getXrefCardNum(),
                      InterestCalculator.db2Timestamp(clock.now()))));
        }
      }
      // Legacy behaviour kept for parity: the ELSE branch of the CBACT04C main loop that would
      // rewrite the last account is unreachable, so the last account of TCATBALF is never updated.
    }
    sysout.display("END OF EXECUTION OF PROGRAM CBACT04C");
    return RepeatStatus.FINISHED;
  }

  private void flushChunk() {
    if (em != null) {
      em.flush();
      if (chunks != null) {
        chunks.run();
      }
      em.clear();
    }
  }

  private static TranCatBalanceRecord toRecord(TranCatBalance t) {
    return new TranCatBalanceRecord(
        t.getId().trancatAcctId(),
        t.getId().trancatTypeCd(),
        t.getId().trancatCd(),
        t.getTranCatBal(),
        t.getFiller());
  }

  /** 1050-UPDATE-ACCOUNT. */
  private void updateAccount(Account account, BigDecimal totalInt) {
    account.setAcctCurrBal(InterestCalculator.newBalance(account.getAcctCurrBal(), totalInt));
    account.setAcctCurrCycCredit(BigDecimal.ZERO.setScale(2));
    account.setAcctCurrCycDebit(BigDecimal.ZERO.setScale(2));
    accounts.save(account);
  }

  /** 1100-GET-ACCT-DATA. */
  private Account readAccount(long acctId) {
    Optional<Account> found = accounts.findById(acctId);
    if (found.isEmpty()) {
      sysout.display("ACCOUNT NOT FOUND: %011d".formatted(acctId));
      throw abend("ERROR READING ACCOUNT FILE");
    }
    return found.get();
  }

  /** 1110-GET-XREF-DATA: READ XREF-FILE KEY IS FD-XREF-ACCT-ID (first card of the AIX). */
  private CardXref readXref(long acctId) {
    Optional<CardXref> found =
        xrefs.findByXrefAcctId(acctId).stream().min(Comparator.comparing(CardXref::getXrefCardNum));
    if (found.isEmpty()) {
      sysout.display("ACCOUNT NOT FOUND: %011d".formatted(acctId));
      throw abend("ERROR READING XREF FILE");
    }
    return found.get();
  }

  /** 1200-GET-INTEREST-RATE with 1200-A-GET-DEFAULT-INT-RATE fallback on status 23. */
  private BigDecimal interestRate(String acctGroupId, TranCatBalance tcat) {
    String type = tcat.getId().trancatTypeCd();
    Integer cat = tcat.getId().trancatCd();
    Optional<DisclosureGroup> group =
        groups.findById(new DisclosureGroupId(pad(acctGroupId, 10), type, cat));
    if (group.isPresent()) {
      return group.get().getDisIntRate();
    }
    sysout.display("DISCLOSURE GROUP RECORD MISSING");
    sysout.display("TRY WITH DEFAULT GROUP CODE");
    return groups
        .findById(new DisclosureGroupId(DEFAULT_GROUP, type, cat))
        .map(DisclosureGroup::getDisIntRate)
        .orElseThrow(() -> abend("ERROR READING DEFAULT DISCLOSURE GROUP"));
  }

  /** 9910-DISPLAY-IO-STATUS + 9999-ABEND-PROGRAM (record not found = status 23). */
  private Abend abend(String error) {
    sysout.display(error);
    sysout.display(STATUS_NOT_FOUND);
    sysout.display("ABENDING PROGRAM");
    sysout.display("CEE3ABD ABEND U999");
    return Abend.user999(error);
  }
}
