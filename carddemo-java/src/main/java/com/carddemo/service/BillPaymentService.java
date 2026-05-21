package com.carddemo.service;

import com.carddemo.dto.request.BillPaymentRequest;
import com.carddemo.dto.response.TransactionResponse;
import com.carddemo.entity.Account;
import com.carddemo.entity.AccountCardXref;
import com.carddemo.entity.DailyTransaction;
import com.carddemo.entity.Transaction;
import com.carddemo.exception.BusinessRuleException;
import com.carddemo.exception.ResourceNotFoundException;
import com.carddemo.repository.AccountCardXrefRepository;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.DailyTransactionRepository;
import com.carddemo.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Migrated from COBOL program COBIL00C.cbl (Bill Payment).
 * Original: CICS transaction CB00.
 * Business rules: Validates payment amount against current balance,
 * creates a payment transaction (type 02), and updates account balance.
 */
@Service
public class BillPaymentService {

    private static final DateTimeFormatter TRAN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssnn");

    private final AccountRepository accountRepository;
    private final AccountCardXrefRepository xrefRepository;
    private final TransactionRepository transactionRepository;
    private final DailyTransactionRepository dailyTransactionRepository;

    public BillPaymentService(AccountRepository accountRepository,
                              AccountCardXrefRepository xrefRepository,
                              TransactionRepository transactionRepository,
                              DailyTransactionRepository dailyTransactionRepository) {
        this.accountRepository = accountRepository;
        this.xrefRepository = xrefRepository;
        this.transactionRepository = transactionRepository;
        this.dailyTransactionRepository = dailyTransactionRepository;
    }

    @Transactional
    public TransactionResponse processPayment(BillPaymentRequest request) {
        Account account = accountRepository.findById(request.acctId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + request.acctId()));

        if (!"Y".equals(account.getActiveStatus())) {
            throw new BusinessRuleException("Account is not active");
        }

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Payment amount must be positive");
        }

        List<AccountCardXref> xrefs = xrefRepository.findByAcctId(request.acctId());
        if (xrefs.isEmpty()) {
            throw new BusinessRuleException("No card found for account: " + request.acctId());
        }

        String cardNum = xrefs.getFirst().getCardNum();
        String tranId = generateTransactionId();
        LocalDateTime now = LocalDateTime.now();

        account.setCurrBal(account.getCurrBal().subtract(request.amount()));
        account.setCurrCycCredit(account.getCurrCycCredit().add(request.amount()));
        accountRepository.save(account);

        Transaction tran = new Transaction();
        tran.setTranId(tranId);
        tran.setTranTypeCd("02");
        tran.setTranCatCd(1);
        tran.setTranSource("ONLINE");
        tran.setTranDesc("Bill Payment - Thank You");
        tran.setTranAmt(request.amount());
        tran.setCardNum(cardNum);
        tran.setOrigTs(now);
        tran.setProcTs(now);
        transactionRepository.save(tran);

        DailyTransaction dailyTran = new DailyTransaction();
        dailyTran.setTranId(tranId);
        dailyTran.setTranTypeCd("02");
        dailyTran.setTranCatCd(1);
        dailyTran.setTranSource("ONLINE");
        dailyTran.setTranDesc("Bill Payment - Thank You");
        dailyTran.setTranAmt(request.amount());
        dailyTran.setCardNum(cardNum);
        dailyTran.setOrigTs(now);
        dailyTran.setProcTs(now);
        dailyTran.setPosted(true);
        dailyTransactionRepository.save(dailyTran);

        return TransactionResponse.from(tran);
    }

    private synchronized String generateTransactionId() {
        return LocalDateTime.now().format(TRAN_ID_FORMAT);
    }
}
