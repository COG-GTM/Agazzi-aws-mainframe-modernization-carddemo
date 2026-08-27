package com.carddemo.batch;

import com.carddemo.domain.Account;
import com.carddemo.domain.DailyTransaction;
import com.carddemo.domain.DailyTransactionReject;
import com.carddemo.domain.TranCatBalance;
import com.carddemo.domain.TranCatBalanceId;
import com.carddemo.domain.Transaction;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.DailyTransactionRejectRepository;
import com.carddemo.domain.repository.TransactionRepository;
import com.carddemo.domain.repository.TranCatBalanceRepository;
import com.carddemo.migration.SampleParsers;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.AfterStep;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@StepScope
public class PostTransactionsItemWriter implements ItemWriter<ValidatedDailyTransaction> {

    private final TransactionRepository transactionRepository;
    private final TranCatBalanceRepository tranCatBalanceRepository;
    private final AccountRepository accountRepository;
    private final DailyTransactionRejectRepository rejectRepository;
    private int postedCount;
    private int rejectCount;

    public PostTransactionsItemWriter(
            TransactionRepository transactionRepository,
            TranCatBalanceRepository tranCatBalanceRepository,
            AccountRepository accountRepository,
            DailyTransactionRejectRepository rejectRepository) {
        this.transactionRepository = transactionRepository;
        this.tranCatBalanceRepository = tranCatBalanceRepository;
        this.accountRepository = accountRepository;
        this.rejectRepository = rejectRepository;
    }

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        this.postedCount = 0;
        this.rejectCount = 0;
    }

    @Override
    public void write(Chunk<? extends ValidatedDailyTransaction> chunk) {
        for (ValidatedDailyTransaction item : chunk.getItems()) {
            if (item.isValid()) {
                post(item);
                postedCount++;
            } else {
                reject(item);
                rejectCount++;
            }
        }
    }

    @AfterStep
    public ExitStatus afterStep(StepExecution execution) {
        execution.getExecutionContext().putInt("postedCount", postedCount);
        execution.getExecutionContext().putInt("rejectCount", rejectCount);
        return ExitStatus.COMPLETED;
    }

    private void post(ValidatedDailyTransaction validated) {
        DailyTransaction daily = validated.dailyTransaction();
        Account account = accountRepository.findById(validated.cardXref().getAccountId())
                .orElseThrow(() -> new IllegalStateException(
                        "Account disappeared during transaction posting"));

        Transaction transaction = new Transaction();
        transaction.setId(daily.getId());
        transaction.setTypeCode(daily.getTypeCode());
        transaction.setCategoryCode(daily.getCategoryCode());
        transaction.setSource(daily.getSource());
        transaction.setDescription(daily.getDescription());
        transaction.setAmount(daily.getAmount());
        transaction.setMerchantId(daily.getMerchantId());
        transaction.setMerchantName(daily.getMerchantName());
        transaction.setMerchantCity(daily.getMerchantCity());
        transaction.setMerchantZip(daily.getMerchantZip());
        transaction.setCardNumber(daily.getCardNumber());
        transaction.setOriginalTimestamp(daily.getOriginalTimestamp());
        transaction.setProcessingTimestamp(CobolTimestamp.current());
        transactionRepository.save(transaction);

        TranCatBalanceId balanceId = new TranCatBalanceId(
                validated.cardXref().getAccountId(),
                daily.getTypeCode(),
                daily.getCategoryCode());
        TranCatBalance balance = tranCatBalanceRepository.findById(balanceId)
                .orElseGet(() -> {
                    TranCatBalance created = new TranCatBalance();
                    created.setId(balanceId);
                    created.setBalance(BigDecimal.ZERO.setScale(2));
                    return created;
                });
        balance.setBalance(balance.getBalance().add(daily.getAmount()).setScale(2));
        tranCatBalanceRepository.save(balance);

        account.setCurrentBalance(account.getCurrentBalance().add(daily.getAmount()).setScale(2));
        if (daily.getAmount().signum() >= 0) {
            account.setCurrentCycleCredit(
                    account.getCurrentCycleCredit().add(daily.getAmount()).setScale(2));
        } else {
            account.setCurrentCycleDebit(
                    account.getCurrentCycleDebit().add(daily.getAmount()).setScale(2));
        }
        accountRepository.save(account);
    }

    private void reject(ValidatedDailyTransaction item) {
        DailyTransactionReject reject = new DailyTransactionReject();
        reject.setRawRecord(SampleParsers.serializeDailyTransaction(item.dailyTransaction()));
        reject.setReasonCode(item.rejectReasonCode());
        reject.setReasonDescription(item.rejectReasonDescription());
        reject.setRejectedAt(LocalDateTime.now());
        rejectRepository.save(reject);
    }

    public int getPostedCount() {
        return postedCount;
    }

    public int getRejectCount() {
        return rejectCount;
    }

    private static final class CobolTimestamp {

        private CobolTimestamp() {
        }

        private static String current() {
            LocalDateTime now = LocalDateTime.now();
            return String.format(
                    "%04d-%02d-%02d-%02d.%02d.%02d.%02d0000",
                    now.getYear(),
                    now.getMonthValue(),
                    now.getDayOfMonth(),
                    now.getHour(),
                    now.getMinute(),
                    now.getSecond(),
                    now.getNano() / 10_000_000);
        }
    }
}
