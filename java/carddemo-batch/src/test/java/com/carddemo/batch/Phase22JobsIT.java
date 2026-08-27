package com.carddemo.batch;

import com.carddemo.domain.Account;
import com.carddemo.domain.Card;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.Customer;
import com.carddemo.domain.DailyTransaction;
import com.carddemo.domain.DisclosureGroup;
import com.carddemo.domain.DisclosureGroupId;
import com.carddemo.domain.TranCatBalance;
import com.carddemo.domain.Transaction;
import com.carddemo.domain.TransactionCategory;
import com.carddemo.domain.TransactionCategoryId;
import com.carddemo.domain.TransactionType;
import com.carddemo.domain.util.CobolPicture;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.domain.repository.DailyTransactionRepository;
import com.carddemo.domain.repository.DailyTransactionRejectRepository;
import com.carddemo.domain.repository.DisclosureGroupRepository;
import com.carddemo.domain.repository.TranCatBalanceRepository;
import com.carddemo.domain.repository.TransactionRepository;
import com.carddemo.domain.repository.TransactionCategoryRepository;
import com.carddemo.domain.repository.TransactionTypeRepository;
import com.carddemo.migration.CardDemoLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CardDemoBatchApplication.class,
        properties = {
                "loader.input-dir=../../app/data/ASCII",
                "spring.batch.job.enabled=false",
                "spring.batch.jdbc.initialize-schema=always"
        })
@Import(CardDemoLoader.class)
class Phase22JobsIT {

    private static final Path DATA = Path.of("..", "..", "app", "data", "ASCII");
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job printAccountsJob;

    @Autowired
    private Job printCardsJob;

    @Autowired
    private Job printCardXrefJob;

    @Autowired
    private Job printCustomersJob;

    @Autowired
    private Job readDailyTransactionsJob;

    @Autowired
    private Job postTransactionsJob;

    @Autowired
    private Job interestCalculationJob;

    @Autowired
    private Job transactionReportJob;

    @Autowired
    private Job createStatementsJob;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private CardRepository cards;

    @Autowired
    private CardXrefRepository xrefs;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private DailyTransactionRepository dailyTransactions;

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private TransactionTypeRepository transactionTypes;

    @Autowired
    private TransactionCategoryRepository transactionCategories;

    @Autowired
    private DailyTransactionRejectRepository rejects;

    @Autowired
    private TranCatBalanceRepository balances;

    @Autowired
    private DisclosureGroupRepository disclosures;

    @BeforeAll
    static void dockerAvailable() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable());
        POSTGRES.start();
    }

    @AfterAll
    static void stopContainer() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void characterizesPhase22Jobs() throws Exception {
        Path accountReport = Path.of("target/phase22-accounts.txt");
        Path cardReport = Path.of("target/phase22-cards.txt");
        Path xrefReport = Path.of("target/phase22-xrefs.txt");
        Path customerReport = Path.of("target/phase22-customers.txt");
        Path dailyReport = Path.of("target/phase22-daily.txt");
        Path transactionReport = Path.of("target/phase22-transactions.txt");
        Path statementText = Path.of("target/phase22-statements.txt");
        Path statementHtml = Path.of("target/phase22-statements.html");

        run(printAccountsJob, "output", accountReport.toString());
        run(printCardsJob, "output", cardReport.toString());
        run(printCardXrefJob, "output", xrefReport.toString());
        run(printCustomersJob, "output", customerReport.toString());
        assertOrderedReport(
                accountReport,
                "ACCT-ID",
                accounts.findAll().stream()
                        .map(Account::getAcctId)
                        .sorted()
                        .map(String::valueOf)
                        .toList());
        assertOrderedReport(
                cardReport,
                null,
                cards.findAll().stream()
                        .map(Card::getCardNumber)
                        .sorted()
                        .toList());
        assertOrderedReport(
                xrefReport,
                null,
                xrefs.findAll().stream()
                        .map(CardXref::getCardNumber)
                        .sorted()
                        .toList());
        assertOrderedReport(
                customerReport,
                null,
                customers.findAll().stream()
                        .map(Customer::getCustomerId)
                        .sorted()
                        .map(String::valueOf)
                        .toList());

        run(readDailyTransactionsJob, "output", dailyReport.toString());
        List<String> dailyLines = Files.readAllLines(dailyReport);
        List<String> dailyIds = dailyLines.stream()
                .filter(line -> line.startsWith("DALYTRAN-ID"))
                .map(line -> line.substring(line.indexOf(':') + 1).trim())
                .toList();
        assertThat(dailyIds).hasSize(300)
                .isSortedAccordingTo(Comparator.comparing(Long::parseLong));
        assertThat(dailyLines).anyMatch(line -> line.contains("NOT FOUND")
                || line.contains("INVALID CARD NUMBER"));

        JobExecution postExecution = run(postTransactionsJob);
        assertThat(transactions.count()).isGreaterThan(0);

        List<Transaction> posted = transactions.findAll().stream()
                .sorted(Comparator.comparing(Transaction::getId))
                .toList();
        assertThat(postExecution.getExitStatus().getExitCode())
                .isEqualTo("COMPLETED_WITH_REJECTS");
        assertThat(postExecution.getStatus())
                .isEqualTo(org.springframework.batch.core.BatchStatus.COMPLETED);
        assertThat(posted).hasSize(262);
        assertThat(rejects.count()).isEqualTo(38);
        BigDecimal expectedGrandTotal = posted.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        run(transactionReportJob,
                "start-date", "0000-01-01",
                "end-date", "9999-12-31",
                "output", transactionReport.toString());
        List<String> transactionReportLines = Files.readAllLines(transactionReport);
        List<String> reportDetails = detailLines(transactionReportLines);
        assertThat(transactionReportLines.get(0))
                .isEqualTo("START OF EXECUTION OF PROGRAM CBTRN03C");
        assertThat(transactionReportLines.get(1)).isEqualTo(
                fixed("DALYREPT", 38)
                        + fixed("Daily Transaction Report", 41)
                        + fixed("Date Range: ", 12)
                        + fixed("0000-01-01", 10)
                        + fixed(" to ", 4)
                        + fixed("9999-12-31", 10)
                        + " ".repeat(18));
        assertThat(transactionReportLines.get(2)).isEqualTo(" ".repeat(133));
        assertThat(transactionReportLines.get(3)).isEqualTo(
                fixed("Transaction ID", 17)
                        + fixed("Account ID", 12)
                        + fixed("Transaction Type", 19)
                        + fixed("Tran Category", 35)
                        + fixed("Tran Source", 14)
                        + " "
                        + fixed("        Amount", 16)
                        + " ".repeat(19));
        assertThat(transactionReportLines.get(4)).isEqualTo("-".repeat(133));
        Transaction knownReportTransaction = posted.stream()
                .sorted(Comparator.comparing(Transaction::getCardNumber)
                        .thenComparing(Transaction::getId))
                .findFirst()
                .orElseThrow();
        CardXref knownReportXref = xrefs.findById(
                knownReportTransaction.getCardNumber()).orElseThrow();
        TransactionType knownType = transactionTypes.findById(
                knownReportTransaction.getTypeCode()).orElseThrow();
        TransactionCategory knownCategory = transactionCategories.findById(
                new TransactionCategoryId(
                        knownReportTransaction.getTypeCode(),
                        knownReportTransaction.getCategoryCode())).orElseThrow();
        assertThat(reportDetails.get(0)).isEqualTo(
                fixed(knownReportTransaction.getId(), 16)
                        + " "
                        + fixed(String.valueOf(knownReportXref.getAccountId()), 11)
                        + " "
                        + fixed(knownReportTransaction.getTypeCode(), 2)
                        + "-"
                        + fixed(knownType.getDescription(), 15)
                        + " "
                        + String.format("%04d", knownReportTransaction.getCategoryCode())
                        + "-"
                        + fixed(knownCategory.getDescription(), 29)
                        + " "
                        + fixed(knownReportTransaction.getSource(), 10)
                        + "    "
                        + CobolPicture.signedLeading(knownReportTransaction.getAmount())
                        + "  "
                        + " ".repeat(19));
        assertThat(reportDetails).allSatisfy(line -> assertThat(line).hasSize(133));
        List<String> expectedTransactionIds = posted.stream()
                .sorted(Comparator.comparing(Transaction::getCardNumber)
                        .thenComparing(Transaction::getId))
                .map(Transaction::getId)
                .toList();
        assertThat(reportDetails).hasSize(expectedTransactionIds.size());
        assertThat(reportDetails.stream()
                .map(line -> line.substring(0, 16))
                .toList()).containsExactlyElementsOf(expectedTransactionIds);
        assertThat(transactionReportLines)
                .anyMatch(line -> line.startsWith("Grand Total")
                        && line.contains(
                        CobolPicture.signedLeadingAlways(expectedGrandTotal)));
        Map<String, BigDecimal> expectedAccountTotals = new LinkedHashMap<>();
        for (Transaction transaction : posted.stream()
                .sorted(Comparator.comparing(Transaction::getCardNumber)
                        .thenComparing(Transaction::getId))
                .toList()) {
            expectedAccountTotals.merge(
                    transaction.getCardNumber(),
                    transaction.getAmount(),
                    BigDecimal::add);
        }
        List<BigDecimal> actualAccountTotals = transactionReportLines.stream()
                .filter(line -> line.startsWith("Account Total"))
                .map(Phase22JobsIT::markerAmount)
                .toList();
        assertThat(actualAccountTotals)
                .containsExactlyElementsOf(expectedAccountTotals.values().stream().toList());
        List<BigDecimal> expectedPageTotals = new ArrayList<>();
        for (int offset = 0; offset < posted.size(); offset += 20) {
            expectedPageTotals.add(posted.stream()
                    .sorted(Comparator.comparing(Transaction::getCardNumber)
                            .thenComparing(Transaction::getId))
                    .skip(offset)
                    .limit(20)
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        List<BigDecimal> actualPageTotals = transactionReportLines.stream()
                .filter(line -> line.startsWith("Page Total"))
                .map(Phase22JobsIT::markerAmount)
                .toList();
        assertThat(actualPageTotals)
                .containsExactlyElementsOf(expectedPageTotals);

        String boundaryDate = posted.get(0).getProcessingTimestamp().substring(0, 10);
        Path boundaryReport = Path.of("target/phase22-boundary.txt");
        run(transactionReportJob,
                "start-date", boundaryDate,
                "end-date", boundaryDate,
                "output", boundaryReport.toString());
        List<Transaction> boundaryTransactions = posted.stream()
                .filter(transaction -> transaction.getProcessingTimestamp()
                        .substring(0, 10).compareTo(boundaryDate) == 0)
                .toList();
        assertThat(detailLines(Files.readAllLines(boundaryReport)))
                .hasSize(boundaryTransactions.size());
        assertThat(Files.readAllLines(boundaryReport))
                .anyMatch(line -> line.startsWith("Grand Total")
                        && line.contains(CobolPicture.signedLeadingAlways(
                        boundaryTransactions.stream()
                                .map(Transaction::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))));

        Map<Long, Account> accountsBeforeInterest = accounts.findAll().stream()
                .collect(Collectors.toMap(Account::getAcctId, Function.identity()));
        Map<Long, BigDecimal> expectedInterest = new HashMap<>();
        int expectedGeneratedCount = 0;
        boolean foundDefaultFallback = false;
        for (TranCatBalance balance : balances.findAll()) {
            Account account = accountsBeforeInterest.get(balance.getId().getAccountId());
            DisclosureGroupId groupId = new DisclosureGroupId(
                    account.getGroupId(),
                    balance.getId().getTypeCode(),
                    balance.getId().getCategoryCode());
            DisclosureGroup disclosure = disclosures.findById(groupId).orElse(null);
            if (disclosure == null) {
                foundDefaultFallback = true;
                disclosure = disclosures.findById(new DisclosureGroupId(
                        "DEFAULT",
                        balance.getId().getTypeCode(),
                        balance.getId().getCategoryCode())).orElseThrow();
            }
            if (disclosure.getInterestRate().signum() != 0) {
                BigDecimal interest = balance.getBalance()
                        .multiply(disclosure.getInterestRate())
                        .divide(BigDecimal.valueOf(1200), 2, RoundingMode.DOWN);
                expectedInterest.merge(
                        balance.getId().getAccountId(),
                        interest,
                        BigDecimal::add);
                expectedGeneratedCount++;
            }
        }
        assertThat(foundDefaultFallback).isTrue();
        Map<Long, BigDecimal> balancesBeforeInterestByAccount = accountsBeforeInterest.values().stream()
                .collect(Collectors.toMap(Account::getAcctId, Account::getCurrentBalance));
        long generatedBeforeInterest = transactions.findAll().stream()
                .filter(transaction -> "2022071800".equals(
                        transaction.getId().substring(0, 10)))
                .count();
        run(interestCalculationJob, "interest-date", "2022071800");
        List<Transaction> generated = transactions.findAll().stream()
                .filter(transaction -> transaction.getId().startsWith("2022071800")
                        && "01".equals(transaction.getTypeCode())
                        && transaction.getCategoryCode() == 5)
                .toList();
        assertThat(generated).hasSize(expectedGeneratedCount);
        assertThat(generated.size() - generatedBeforeInterest).isEqualTo(expectedGeneratedCount);
        assertThat(generated).allSatisfy(transaction -> {
            assertThat(transaction.getTypeCode()).isEqualTo("01");
            assertThat(transaction.getCategoryCode()).isEqualTo(5);
            Long accountId = Long.valueOf(
                    transaction.getDescription().substring("Int. for a/c ".length()));
            assertThat(transaction.getAmount())
                    .isEqualByComparingTo(expectedInterest.get(accountId)
                            .divide(BigDecimal.ONE, 2, RoundingMode.DOWN));
        });
        assertThat(generated).hasSizeGreaterThanOrEqualTo(2);
        assertThat(accounts.findAll()).allSatisfy(account -> {
            assertThat(account.getCurrentBalance()).isEqualByComparingTo(
                    balancesBeforeInterestByAccount.get(account.getAcctId())
                            .add(expectedInterest.getOrDefault(
                                    account.getAcctId(), BigDecimal.ZERO)));
            assertThat(account.getCurrentCycleCredit()).isEqualByComparingTo("0.00");
            assertThat(account.getCurrentCycleDebit()).isEqualByComparingTo("0.00");
        });

        run(createStatementsJob,
                "statement-text", statementText.toString(),
                "statement-html", statementHtml.toString());
        String textStatement = Files.readString(statementText);
        String htmlStatement = Files.readString(statementHtml);
        CardXref knownXref = xrefs.findAll().stream()
                .sorted(Comparator.comparing(CardXref::getCardNumber))
                .findFirst()
                .orElseThrow();
        Account knownAccount = accounts.findById(knownXref.getAccountId()).orElseThrow();
        List<Transaction> knownTransactions = transactions.findAll().stream()
                .filter(transaction -> knownXref.getCardNumber()
                        .equals(transaction.getCardNumber()))
                .sorted(Comparator.comparing(Transaction::getId))
                .toList();
        BigDecimal knownTotal = knownTransactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String knownAccountLine = "Account ID         :"
                + knownAccount.getAcctId();
        List<String> textLines = textStatement.lines().toList();
        int accountLineIndex = -1;
        for (int index = 0; index < textLines.size(); index++) {
            if (textLines.get(index).startsWith(knownAccountLine)) {
                accountLineIndex = index;
                break;
            }
        }
        int statementStart = -1;
        for (int index = accountLineIndex; index >= 0; index--) {
            if (textLines.get(index).contains("START OF STATEMENT")) {
                statementStart = index;
                break;
            }
        }
        int statementEnd = textLines.size();
        for (int index = statementStart + 1; index < textLines.size(); index++) {
            if (textLines.get(index).contains("START OF STATEMENT")) {
                statementEnd = index;
                break;
            }
        }
        List<String> knownStatement = textLines.subList(
                statementStart, statementEnd);
        String expectedAccountLine = knownAccountLine
                + " ".repeat(20 - String.valueOf(knownAccount.getAcctId()).length())
                + " ".repeat(40);
        String expectedBalanceLine = "Current Balance    :"
                + CobolPicture.unsignedTrailingSign(
                knownAccount.getCurrentBalance())
                + " ".repeat(7 + 40);
        assertThat(knownStatement).contains(
                "*******************************START OF STATEMENT*******************************",
                expectedAccountLine,
                expectedBalanceLine);
        assertThat(knownStatement)
                .anyMatch(line -> line.startsWith("Total EXP:")
                        && line.equals("Total EXP:" + " ".repeat(56)
                        + "$" + CobolPicture.signedTrailing(knownTotal)));
        if (!knownTransactions.isEmpty()) {
            assertThat(knownStatement).anyMatch(line -> line.startsWith(
                    knownTransactions.get(0).getId()));
        }
        assertThat(htmlStatement).contains(
                "<!DOCTYPE html>",
                "<html lang=\"en\">",
                "<meta charset=\"utf-8\">",
                "<title>HTML Table Layout</title>",
                "Statement for Account Number: " + knownAccount.getAcctId());
        assertThat(Files.exists(statementText)).isTrue();
        assertThat(Files.exists(statementHtml)).isTrue();
    }

    private JobExecution run(Job job, String... options) throws Exception {
        JobParametersBuilder parameters = new JobParametersBuilder()
                .addLong("run.id", System.nanoTime());
        for (int index = 0; index < options.length; index += 2) {
            parameters.addString(options[index], options[index + 1]);
        }
        return jobLauncher.run(job, parameters.toJobParameters());
    }

    private void assertOrderedReport(
            Path path,
            String marker,
            List<String> expectedIds)
            throws Exception {
        List<String> lines = Files.readAllLines(path);
        List<String> records;
        List<String> actualIds;
        if (marker == null) {
            int width = expectedIds == null || expectedIds.isEmpty()
                    ? 0
                    : switch (path.getFileName().toString()) {
                        case "phase22-cards.txt" -> 150;
                        case "phase22-xrefs.txt" -> 50;
                        case "phase22-customers.txt" -> 500;
                        default -> 0;
                    };
            records = lines.stream().filter(line -> line.length() == width).toList();
            actualIds = records.stream()
                    .map(line -> switch (width) {
                        case 150 -> line.substring(0, 16).trim();
                        case 50 -> line.substring(0, 16).trim();
                        case 500 -> line.substring(0, 9).trim();
                        default -> "";
                    })
                    .toList();
        } else {
            records = lines.stream()
                    .filter(line -> line.startsWith(marker))
                    .toList();
            actualIds = records.stream()
                    .map(line -> line.substring(line.indexOf(':') + 1).trim())
                    .toList();
        }
        assertThat(records).hasSize(expectedIds.size());
        assertThat(actualIds).containsExactlyElementsOf(expectedIds);
    }

    private static List<String> detailLines(List<String> lines) {
        return lines.stream()
                .filter(line -> line.matches("\\d{16} .*"))
                .toList();
    }

    private static BigDecimal markerAmount(String line) {
        return new BigDecimal(line.replaceFirst(
                        "^.*?([+-]\\s*[\\d,]+\\.\\d{2})\\s*$", "$1")
                .replace(" ", "")
                .replace(",", ""));
    }

    private static String fixed(String value, int width) {
        String normalized = value == null ? "" : value;
        if (normalized.length() > width) {
            return normalized.substring(0, width);
        }
        return normalized + " ".repeat(width - normalized.length());
    }
}
