package com.carddemo.service;

import com.carddemo.dto.request.TransactionAddRequest;
import com.carddemo.dto.response.TransactionResponse;
import com.carddemo.entity.AccountCardXref;
import com.carddemo.entity.DailyTransaction;
import com.carddemo.entity.Transaction;
import com.carddemo.exception.BusinessRuleException;
import com.carddemo.exception.ResourceNotFoundException;
import com.carddemo.repository.AccountCardXrefRepository;
import com.carddemo.repository.CardRepository;
import com.carddemo.repository.DailyTransactionRepository;
import com.carddemo.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Migrated from COBOL programs COTRN00C.cbl (Transaction List),
 * COTRN01C.cbl (Transaction View), COTRN02C.cbl (Transaction Add).
 * Original: CICS transactions CT00/CT01/CT02 with VSAM KSDS TRANSACT.
 */
@Service
public class TransactionService {

    private static final DateTimeFormatter TRAN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssnn");

    private final TransactionRepository transactionRepository;
    private final DailyTransactionRepository dailyTransactionRepository;
    private final CardRepository cardRepository;
    private final AccountCardXrefRepository xrefRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              DailyTransactionRepository dailyTransactionRepository,
                              CardRepository cardRepository,
                              AccountCardXrefRepository xrefRepository) {
        this.transactionRepository = transactionRepository;
        this.dailyTransactionRepository = dailyTransactionRepository;
        this.cardRepository = cardRepository;
        this.xrefRepository = xrefRepository;
    }

    public Page<TransactionResponse> getTransactionsByCard(String cardNum, Pageable pageable) {
        return transactionRepository.findByCardNum(cardNum, pageable)
                .map(TransactionResponse::from);
    }

    public TransactionResponse getTransaction(String tranId) {
        Transaction tran = transactionRepository.findById(tranId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + tranId));
        return TransactionResponse.from(tran);
    }

    public Page<TransactionResponse> getTransactionsByAccount(Long acctId, Pageable pageable) {
        List<String> cardNums = xrefRepository.findByAcctId(acctId).stream()
                .map(AccountCardXref::getCardNum)
                .toList();

        if (cardNums.isEmpty()) {
            return Page.empty(pageable);
        }

        return transactionRepository.findByCardNumIn(cardNums, pageable)
                .map(TransactionResponse::from);
    }

    @Transactional
    public TransactionResponse addTransaction(TransactionAddRequest request) {
        cardRepository.findById(request.cardNum())
                .orElseThrow(() -> new BusinessRuleException("Card not found: " + request.cardNum()));

        xrefRepository.findById(request.cardNum())
                .orElseThrow(() -> new BusinessRuleException(
                        "Card not registered in cross-reference: " + request.cardNum()));

        String tranId = generateTransactionId();
        LocalDateTime now = LocalDateTime.now();

        DailyTransaction dailyTran = new DailyTransaction();
        dailyTran.setTranId(tranId);
        dailyTran.setTranTypeCd(request.tranTypeCd());
        dailyTran.setTranCatCd(request.tranCatCd());
        dailyTran.setTranSource(request.tranSource() != null ? request.tranSource() : "ONLINE");
        dailyTran.setTranDesc(request.tranDesc());
        dailyTran.setTranAmt(request.tranAmt());
        dailyTran.setMerchantId(request.merchantId());
        dailyTran.setMerchantName(request.merchantName());
        dailyTran.setMerchantCity(request.merchantCity());
        dailyTran.setMerchantZip(request.merchantZip());
        dailyTran.setCardNum(request.cardNum());
        dailyTran.setOrigTs(now);
        dailyTran.setPosted(false);

        dailyTransactionRepository.save(dailyTran);

        Transaction tran = new Transaction();
        tran.setTranId(tranId);
        tran.setTranTypeCd(request.tranTypeCd());
        tran.setTranCatCd(request.tranCatCd());
        tran.setTranSource(dailyTran.getTranSource());
        tran.setTranDesc(request.tranDesc());
        tran.setTranAmt(request.tranAmt());
        tran.setMerchantId(request.merchantId());
        tran.setMerchantName(request.merchantName());
        tran.setMerchantCity(request.merchantCity());
        tran.setMerchantZip(request.merchantZip());
        tran.setCardNum(request.cardNum());
        tran.setOrigTs(now);

        transactionRepository.save(tran);

        return TransactionResponse.from(tran);
    }

    private synchronized String generateTransactionId() {
        return LocalDateTime.now().format(TRAN_ID_FORMAT);
    }
}
