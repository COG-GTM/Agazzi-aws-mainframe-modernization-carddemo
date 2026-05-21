package com.carddemo.service;

import com.carddemo.entity.Account;
import com.carddemo.entity.AccountCardXref;
import com.carddemo.entity.Transaction;
import com.carddemo.exception.ResourceNotFoundException;
import com.carddemo.repository.AccountCardXrefRepository;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Migrated from COBOL program CORPT00C.cbl (Transaction Reports)
 * and batch programs CBSTM03A.CBL / CBSTM03B.CBL (Statement Generation).
 * Original: CICS transaction CR00.
 */
@Service
public class ReportService {

    private final TransactionRepository transactionRepository;
    private final AccountCardXrefRepository xrefRepository;
    private final AccountRepository accountRepository;

    public ReportService(TransactionRepository transactionRepository,
                         AccountCardXrefRepository xrefRepository,
                         AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.xrefRepository = xrefRepository;
        this.accountRepository = accountRepository;
    }

    public Map<String, Object> generateAccountStatement(Long acctId, LocalDate startDate, LocalDate endDate) {
        Account account = accountRepository.findById(acctId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + acctId));

        List<String> cardNums = xrefRepository.findByAcctId(acctId).stream()
                .map(AccountCardXref::getCardNum)
                .toList();

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Transaction> transactions = cardNums.stream()
                .flatMap(cn -> transactionRepository
                        .findByCardNumAndOrigTsBetween(cn, start, end).stream())
                .toList();

        BigDecimal totalDebits = transactions.stream()
                .filter(t -> "01".equals(t.getTranTypeCd()))
                .map(Transaction::getTranAmt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = transactions.stream()
                .filter(t -> "02".equals(t.getTranTypeCd()) || "03".equals(t.getTranTypeCd()))
                .map(Transaction::getTranAmt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> report = new HashMap<>();
        report.put("acctId", acctId);
        report.put("statementPeriod", Map.of("start", startDate, "end", endDate));
        report.put("currentBalance", account.getCurrBal());
        report.put("creditLimit", account.getCreditLimit());
        report.put("totalDebits", totalDebits);
        report.put("totalCredits", totalCredits);
        report.put("transactionCount", transactions.size());
        report.put("transactions", transactions.stream()
                .map(t -> Map.of(
                        "tranId", t.getTranId(),
                        "date", t.getOrigTs() != null ? t.getOrigTs().toString() : "",
                        "description", t.getTranDesc() != null ? t.getTranDesc() : "",
                        "amount", t.getTranAmt(),
                        "type", t.getTranTypeCd() != null ? t.getTranTypeCd() : ""
                ))
                .toList());
        return report;
    }
}
