package com.carddemo.batch;

import com.carddemo.domain.Account;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.DailyTransaction;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class DailyTransactionValidationProcessor
        implements ItemProcessor<DailyTransaction, ValidatedDailyTransaction> {

    public static final int INVALID_CARD_NUMBER = 100;
    public static final int ACCOUNT_NOT_FOUND = 101;
    public static final int OVERLIMIT_TRANSACTION = 102;
    public static final int AFTER_ACCOUNT_EXPIRATION = 103;

    public static final String INVALID_CARD_NUMBER_DESCRIPTION = "INVALID CARD NUMBER FOUND";
    public static final String ACCOUNT_NOT_FOUND_DESCRIPTION = "ACCOUNT RECORD NOT FOUND";
    public static final String OVERLIMIT_DESCRIPTION = "OVERLIMIT TRANSACTION";
    public static final String AFTER_ACCOUNT_EXPIRATION_DESCRIPTION =
            "TRANSACTION RECEIVED AFTER ACCT EXPIRATION";

    private final CardXrefRepository cardXrefRepository;
    private final AccountRepository accountRepository;
    private final Map<Long, CycleState> validationState = new HashMap<>();

    public DailyTransactionValidationProcessor(
            CardXrefRepository cardXrefRepository,
            AccountRepository accountRepository) {
        this.cardXrefRepository = cardXrefRepository;
        this.accountRepository = accountRepository;
    }

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        validationState.clear();
    }

    @Override
    public ValidatedDailyTransaction process(DailyTransaction item) {
        Optional<CardXref> xref = cardXrefRepository.findById(item.getCardNumber());
        if (xref.isEmpty()) {
            return rejected(item, INVALID_CARD_NUMBER, INVALID_CARD_NUMBER_DESCRIPTION);
        }

        Optional<Account> account = accountRepository.findById(xref.get().getAccountId());
        if (account.isEmpty()) {
            return rejected(item, ACCOUNT_NOT_FOUND, ACCOUNT_NOT_FOUND_DESCRIPTION);
        }

        Account accountValue = account.get();
        CycleState state = validationState.computeIfAbsent(
                accountValue.getAcctId(),
                ignored -> new CycleState(
                        accountValue.getCurrentCycleCredit(),
                        accountValue.getCurrentCycleDebit()));
        BigDecimal temporaryBalance = state.currentCycleCredit()
                .subtract(state.currentCycleDebit())
                .add(item.getAmount());
        if (accountValue.getCreditLimit().compareTo(temporaryBalance) < 0) {
            return rejected(item, OVERLIMIT_TRANSACTION, OVERLIMIT_DESCRIPTION);
        }

        String originalDate = item.getOriginalTimestamp().substring(0, 10);
        if (accountValue.getExpirationDate().compareTo(originalDate) < 0) {
            return rejected(item, AFTER_ACCOUNT_EXPIRATION, AFTER_ACCOUNT_EXPIRATION_DESCRIPTION);
        }

        if (item.getAmount().signum() >= 0) {
            state.addCredit(item.getAmount());
        } else {
            state.addDebit(item.getAmount());
        }
        return new ValidatedDailyTransaction(item, xref.get(), accountValue, 0, "");
    }

    private ValidatedDailyTransaction rejected(
            DailyTransaction item,
            int reasonCode,
            String reasonDescription) {
        return new ValidatedDailyTransaction(item, null, null, reasonCode, reasonDescription);
    }

    private static final class CycleState {

        private BigDecimal currentCycleCredit;
        private BigDecimal currentCycleDebit;

        private CycleState(BigDecimal currentCycleCredit, BigDecimal currentCycleDebit) {
            this.currentCycleCredit = currentCycleCredit;
            this.currentCycleDebit = currentCycleDebit;
        }

        private BigDecimal currentCycleCredit() {
            return currentCycleCredit;
        }

        private BigDecimal currentCycleDebit() {
            return currentCycleDebit;
        }

        private void addCredit(BigDecimal amount) {
            currentCycleCredit = currentCycleCredit.add(amount);
        }

        private void addDebit(BigDecimal amount) {
            currentCycleDebit = currentCycleDebit.add(amount);
        }
    }
}
