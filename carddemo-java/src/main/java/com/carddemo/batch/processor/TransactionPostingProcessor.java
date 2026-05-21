package com.carddemo.batch.processor;

import com.carddemo.entity.Account;
import com.carddemo.entity.AccountCardXref;
import com.carddemo.entity.DailyTransaction;
import com.carddemo.entity.Transaction;
import com.carddemo.entity.TransactionCategoryBalance;
import com.carddemo.entity.TransactionCategoryBalanceId;
import com.carddemo.repository.AccountCardXrefRepository;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.TransactionCategoryBalanceRepository;
import com.carddemo.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Migrated from COBOL batch program CBTRN02C.cbl (Transaction Posting).
 * Original: POSTTRAN.jcl
 * Business logic:
 * 1. Validate card exists in cross-reference
 * 2. Update account balance (debit for purchases, credit for payments)
 * 3. Update transaction category balance
 * 4. Write to transaction master file
 * 5. Mark daily transaction as posted
 */
@Component
public class TransactionPostingProcessor implements ItemProcessor<DailyTransaction, DailyTransaction> {

    private static final Logger log = LoggerFactory.getLogger(TransactionPostingProcessor.class);

    private final AccountCardXrefRepository xrefRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionCategoryBalanceRepository tcbRepository;

    public TransactionPostingProcessor(AccountCardXrefRepository xrefRepository,
                                       AccountRepository accountRepository,
                                       TransactionRepository transactionRepository,
                                       TransactionCategoryBalanceRepository tcbRepository) {
        this.xrefRepository = xrefRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.tcbRepository = tcbRepository;
    }

    @Override
    public DailyTransaction process(DailyTransaction dailyTran) {
        Optional<AccountCardXref> xrefOpt = xrefRepository.findById(dailyTran.getCardNum());
        if (xrefOpt.isEmpty()) {
            log.warn("Rejected transaction {}: card {} not in cross-reference",
                    dailyTran.getTranId(), dailyTran.getCardNum());
            return null;
        }

        AccountCardXref xref = xrefOpt.get();
        Optional<Account> accountOpt = accountRepository.findById(xref.getAcctId());
        if (accountOpt.isEmpty()) {
            log.warn("Rejected transaction {}: account {} not found",
                    dailyTran.getTranId(), xref.getAcctId());
            return null;
        }

        Account account = accountOpt.get();
        BigDecimal amount = dailyTran.getTranAmt();

        if ("01".equals(dailyTran.getTranTypeCd())) {
            account.setCurrBal(account.getCurrBal().add(amount));
            account.setCurrCycDebit(account.getCurrCycDebit().add(amount));
        } else if ("02".equals(dailyTran.getTranTypeCd())) {
            account.setCurrBal(account.getCurrBal().subtract(amount));
            account.setCurrCycCredit(account.getCurrCycCredit().add(amount));
        } else if ("03".equals(dailyTran.getTranTypeCd()) || "05".equals(dailyTran.getTranTypeCd())) {
            account.setCurrBal(account.getCurrBal().subtract(amount));
            account.setCurrCycCredit(account.getCurrCycCredit().add(amount));
        }

        accountRepository.save(account);

        updateCategoryBalance(xref.getAcctId(), dailyTran.getTranTypeCd(),
                dailyTran.getTranCatCd(), amount);

        if (!transactionRepository.existsById(dailyTran.getTranId())) {
            Transaction tran = new Transaction();
            tran.setTranId(dailyTran.getTranId());
            tran.setTranTypeCd(dailyTran.getTranTypeCd());
            tran.setTranCatCd(dailyTran.getTranCatCd());
            tran.setTranSource(dailyTran.getTranSource());
            tran.setTranDesc(dailyTran.getTranDesc());
            tran.setTranAmt(dailyTran.getTranAmt());
            tran.setMerchantId(dailyTran.getMerchantId());
            tran.setMerchantName(dailyTran.getMerchantName());
            tran.setMerchantCity(dailyTran.getMerchantCity());
            tran.setMerchantZip(dailyTran.getMerchantZip());
            tran.setCardNum(dailyTran.getCardNum());
            tran.setOrigTs(dailyTran.getOrigTs());
            tran.setProcTs(LocalDateTime.now());
            transactionRepository.save(tran);
        }

        dailyTran.setPosted(true);
        dailyTran.setProcTs(LocalDateTime.now());

        log.info("Posted transaction {} for card {} amount {}",
                dailyTran.getTranId(), dailyTran.getCardNum(), dailyTran.getTranAmt());
        return dailyTran;
    }

    private void updateCategoryBalance(Long acctId, String tranTypeCd, Integer tranCatCd, BigDecimal amount) {
        if (tranTypeCd == null || tranCatCd == null) return;

        TransactionCategoryBalanceId id = new TransactionCategoryBalanceId(acctId, tranTypeCd, tranCatCd);
        TransactionCategoryBalance tcb = tcbRepository.findById(id)
                .orElseGet(() -> {
                    TransactionCategoryBalance newTcb = new TransactionCategoryBalance();
                    newTcb.setAcctId(acctId);
                    newTcb.setTranTypeCd(tranTypeCd);
                    newTcb.setTranCatCd(tranCatCd);
                    newTcb.setBalance(BigDecimal.ZERO);
                    return newTcb;
                });

        tcb.setBalance(tcb.getBalance().add(amount));
        tcbRepository.save(tcb);
    }
}
