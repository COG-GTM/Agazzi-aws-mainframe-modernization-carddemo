package com.carddemo.batch;

import com.carddemo.domain.Account;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.DisclosureGroup;
import com.carddemo.domain.DisclosureGroupId;
import com.carddemo.domain.TranCatBalance;
import com.carddemo.domain.Transaction;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.DisclosureGroupRepository;
import com.carddemo.domain.repository.TranCatBalanceRepository;
import com.carddemo.domain.repository.TransactionRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class InterestCalculationJobConfiguration {

    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public Tasklet interestCalculationTasklet(
            TranCatBalanceRepository balances,
            AccountRepository accounts,
            CardXrefRepository xrefs,
            DisclosureGroupRepository disclosures,
            TransactionRepository transactions,
            @Value("#{jobParameters['interest-date']}") String interestDate) {
        return (contribution, chunkContext) -> {
            String date = interestDate == null ? "2022071800" : interestDate;
            Map<Long, CardXref> xrefByAccount = xrefs.findAll().stream()
                    .collect(Collectors.toMap(
                            CardXref::getAccountId,
                            Function.identity(),
                            (first, ignored) -> first));
            long suffix = 0;
            Long currentAccountId = null;
            BigDecimal totalInterest = BigDecimal.ZERO.setScale(2);
            Account currentAccount = null;
            for (TranCatBalance balance : balances.findAll().stream()
                    .sorted(Comparator.comparing((TranCatBalance value) ->
                                    value.getId().getAccountId())
                            .thenComparing(value -> value.getId().getTypeCode())
                            .thenComparing(value -> value.getId().getCategoryCode()))
                    .toList()) {
                Long accountId = balance.getId().getAccountId();
                if (!accountId.equals(currentAccountId)) {
                    if (currentAccount != null) {
                        updateAccount(accounts, currentAccount, totalInterest);
                    }
                    currentAccountId = accountId;
                    currentAccount = accounts.findById(accountId).orElseThrow();
                    totalInterest = BigDecimal.ZERO.setScale(2);
                }
                CardXref xref = xrefByAccount.get(accountId);
                if (xref == null) {
                    throw new IllegalStateException("No card xref for account " + accountId);
                }
                DisclosureGroup disclosure = disclosures.findById(new DisclosureGroupId(
                                currentAccount.getGroupId(),
                                balance.getId().getTypeCode(),
                                balance.getId().getCategoryCode()))
                        .orElseGet(() -> disclosures.findById(new DisclosureGroupId(
                                "DEFAULT",
                                balance.getId().getTypeCode(),
                                balance.getId().getCategoryCode())).orElseThrow());
                if (disclosure.getInterestRate().signum() == 0) {
                    continue;
                }
                BigDecimal interest = balance.getBalance()
                        .multiply(disclosure.getInterestRate())
                        .divide(BigDecimal.valueOf(1200), 2, RoundingMode.DOWN);
                totalInterest = totalInterest.add(interest).setScale(2);
                suffix++;
                Transaction transaction = new Transaction();
                transaction.setId(String.format("%s%06d", date, suffix));
                transaction.setTypeCode("01");
                transaction.setCategoryCode(5);
                transaction.setSource("System");
                transaction.setDescription(String.format("Int. for a/c %d", accountId));
                transaction.setAmount(interest);
                transaction.setMerchantId(0L);
                transaction.setMerchantName("");
                transaction.setMerchantCity("");
                transaction.setMerchantZip("");
                transaction.setCardNumber(xref.getCardNumber());
                String timestamp = currentTimestamp();
                transaction.setOriginalTimestamp(timestamp);
                transaction.setProcessingTimestamp(timestamp);
                transactions.save(transaction);
                computeFeesNoOp();
            }
            if (currentAccount != null) {
                updateAccount(accounts, currentAccount, totalInterest);
            }
            return org.springframework.batch.repeat.RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Step interestCalculationStep(
            JobRepository repository,
            PlatformTransactionManager transactionManager,
            @Qualifier("interestCalculationTasklet") Tasklet interestCalculationTasklet) {
        return new StepBuilder("interestCalculationStep", repository)
                .tasklet(interestCalculationTasklet, transactionManager)
                .build();
    }

    @Bean
    public Job interestCalculationJob(
            JobRepository repository,
            @Qualifier("interestCalculationStep") Step interestCalculationStep) {
        return new JobBuilder("interestCalculationJob", repository)
                .start(interestCalculationStep)
                .build();
    }

    private static void updateAccount(
            AccountRepository accounts,
            Account account,
            BigDecimal totalInterest) {
        account.setCurrentBalance(
                account.getCurrentBalance().add(totalInterest).setScale(2));
        account.setCurrentCycleCredit(BigDecimal.ZERO.setScale(2));
        account.setCurrentCycleDebit(BigDecimal.ZERO.setScale(2));
        accounts.save(account);
    }

    private static void computeFeesNoOp() {
        // COBOL 1400-COMPUTE-FEES is intentionally an empty stub.
    }

    private static String currentTimestamp() {
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
