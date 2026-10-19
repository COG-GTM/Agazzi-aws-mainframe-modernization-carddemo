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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.repeat.RepeatStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final class InterestCalculationTasklet implements Tasklet {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(InterestCalculationTasklet.class);

    private final TranCatBalanceRepository balances;
    private final AccountRepository accounts;
    private final CardXrefRepository xrefs;
    private final DisclosureGroupRepository disclosures;
    private final TransactionRepository transactions;
    private final FlatFileItemWriter<String> writer;
    private final String interestDate;

    InterestCalculationTasklet(
            TranCatBalanceRepository balances,
            AccountRepository accounts,
            CardXrefRepository xrefs,
            DisclosureGroupRepository disclosures,
            TransactionRepository transactions,
            FlatFileItemWriter<String> writer,
            String interestDate) {
        this.balances = balances;
        this.accounts = accounts;
        this.xrefs = xrefs;
        this.disclosures = disclosures;
        this.transactions = transactions;
        this.writer = writer;
        this.interestDate = interestDate == null ? "2022071800" : interestDate;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext context)
            throws Exception {
        writer.open(context.getStepContext().getStepExecution().getExecutionContext());
        List<String> output = new ArrayList<>();
        output.add("START OF EXECUTION OF PROGRAM CBACT04C");

        // The lowest card number is intentional: it makes generated interest deterministic.
        Map<Long, CardXref> xrefByAccount = xrefs.findAll().stream()
                .sorted(Comparator.comparing(CardXref::getCardNumber))
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
            output.add(balanceLine(balance));
            if (!accountId.equals(currentAccountId)) {
                if (currentAccount != null) {
                    updateAccount(currentAccount, totalInterest);
                }
                currentAccountId = accountId;
                totalInterest = BigDecimal.ZERO.setScale(2);
                currentAccount = accounts.findById(accountId).orElse(null);
                if (currentAccount == null) {
                    LOGGER.warn("ACCOUNT NOT FOUND: {}", accountId);
                    // COBOL continues with prior record contents; skipping is safer in Java.
                    continue;
                }
            }
            if (currentAccount == null) {
                continue;
            }
            CardXref xref = xrefByAccount.get(accountId);
            if (xref == null) {
                LOGGER.warn("ACCOUNT NOT FOUND: {}", accountId);
                // COBOL continues with prior record contents; skipping is safer in Java.
                continue;
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
            transaction.setId(String.format("%s%06d", interestDate, suffix));
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
            updateAccount(currentAccount, totalInterest);
        }
        output.add("END OF EXECUTION OF PROGRAM CBACT04C");
        writer.write(new Chunk<>(output));
        writer.close();
        contribution.incrementWriteCount(output.size());
        return RepeatStatus.FINISHED;
    }

    private String balanceLine(TranCatBalance balance) {
        return String.format(
                "TRAN-CAT-BAL-RECORD ACCOUNT=%d TYPE=%s CATEGORY=%04d BALANCE=%s",
                balance.getId().getAccountId(),
                balance.getId().getTypeCode(),
                balance.getId().getCategoryCode(),
                balance.getBalance().setScale(2).toPlainString());
    }

    private void updateAccount(Account account, BigDecimal totalInterest) {
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
