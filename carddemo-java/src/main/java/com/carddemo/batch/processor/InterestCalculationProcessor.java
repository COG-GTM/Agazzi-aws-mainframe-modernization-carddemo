package com.carddemo.batch.processor;

import com.carddemo.entity.Account;
import com.carddemo.entity.DisclosureGroup;
import com.carddemo.entity.DisclosureGroupId;
import com.carddemo.entity.Transaction;
import com.carddemo.entity.TransactionCategoryBalance;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.DisclosureGroupRepository;
import com.carddemo.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Migrated from COBOL batch program CBACT04C.cbl (Interest Calculation).
 * Original: INTCALC.jcl
 * Business logic:
 * 1. For each transaction category balance, look up the disclosure group interest rate
 * 2. Calculate monthly interest: balance * (annual_rate / 1200)
 * 3. Add interest to account current balance
 * 4. Create interest transaction record
 */
@Component
public class InterestCalculationProcessor implements ItemProcessor<TransactionCategoryBalance, TransactionCategoryBalance> {

    private static final Logger log = LoggerFactory.getLogger(InterestCalculationProcessor.class);
    private static final BigDecimal MONTHS_IN_YEAR = new BigDecimal("1200");
    private static final DateTimeFormatter TRAN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssnn");

    private final AccountRepository accountRepository;
    private final DisclosureGroupRepository disclosureGroupRepository;
    private final TransactionRepository transactionRepository;

    public InterestCalculationProcessor(AccountRepository accountRepository,
                                        DisclosureGroupRepository disclosureGroupRepository,
                                        TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.disclosureGroupRepository = disclosureGroupRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public TransactionCategoryBalance process(TransactionCategoryBalance tcb) {
        if (tcb.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            return tcb;
        }

        Optional<Account> accountOpt = accountRepository.findById(tcb.getAcctId());
        if (accountOpt.isEmpty()) {
            log.warn("Skipping interest calc: account {} not found", tcb.getAcctId());
            return tcb;
        }

        Account account = accountOpt.get();
        String groupId = account.getGroupId();
        if (groupId == null || groupId.isBlank()) {
            groupId = account.getAddrZip();
        }

        if (groupId == null) {
            log.warn("Skipping interest calc: no group ID for account {}", tcb.getAcctId());
            return tcb;
        }

        DisclosureGroupId dgId = new DisclosureGroupId(groupId, tcb.getTranTypeCd(), tcb.getTranCatCd());
        Optional<DisclosureGroup> dgOpt = disclosureGroupRepository.findById(dgId);
        if (dgOpt.isEmpty()) {
            log.debug("No disclosure group for {}/{}/{}", groupId, tcb.getTranTypeCd(), tcb.getTranCatCd());
            return tcb;
        }

        BigDecimal annualRate = dgOpt.get().getIntRate();
        if (annualRate.compareTo(BigDecimal.ZERO) <= 0) {
            return tcb;
        }

        BigDecimal monthlyInterest = tcb.getBalance()
                .multiply(annualRate)
                .divide(MONTHS_IN_YEAR, 2, RoundingMode.HALF_UP);

        account.setCurrBal(account.getCurrBal().add(monthlyInterest));
        accountRepository.save(account);

        tcb.setBalance(tcb.getBalance().add(monthlyInterest));

        Transaction interestTran = new Transaction();
        interestTran.setTranId(LocalDateTime.now().format(TRAN_ID_FORMAT));
        interestTran.setTranTypeCd("01");
        interestTran.setTranCatCd(5);
        interestTran.setTranSource("INTCALC");
        interestTran.setTranDesc("Monthly Interest Charge");
        interestTran.setTranAmt(monthlyInterest);
        interestTran.setOrigTs(LocalDateTime.now());
        interestTran.setProcTs(LocalDateTime.now());
        transactionRepository.save(interestTran);

        log.info("Interest calculated for account {}: rate={}%, balance={}, interest={}",
                tcb.getAcctId(), annualRate, tcb.getBalance(), monthlyInterest);
        return tcb;
    }
}
