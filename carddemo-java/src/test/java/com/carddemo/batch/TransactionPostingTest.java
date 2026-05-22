package com.carddemo.batch;

import com.carddemo.entity.Account;
import com.carddemo.entity.AccountCardXref;
import com.carddemo.entity.DailyTransaction;
import com.carddemo.batch.processor.TransactionPostingProcessor;
import com.carddemo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionPostingTest {

    @Mock
    private AccountCardXrefRepository xrefRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionCategoryBalanceRepository tcbRepository;

    @InjectMocks
    private TransactionPostingProcessor processor;

    private DailyTransaction dailyTran;
    private AccountCardXref xref;
    private Account account;

    @BeforeEach
    void setUp() {
        dailyTran = new DailyTransaction();
        dailyTran.setTranId("0000000000000001");
        dailyTran.setCardNum("0500024453765740");
        dailyTran.setTranTypeCd("01");
        dailyTran.setTranCatCd(1);
        dailyTran.setTranAmt(new BigDecimal("100.00"));
        dailyTran.setOrigTs(LocalDateTime.now());
        dailyTran.setPosted(false);

        xref = new AccountCardXref();
        xref.setCardNum("0500024453765740");
        xref.setAcctId(50L);
        xref.setCustId(5L);

        account = new Account();
        account.setAcctId(50L);
        account.setCurrBal(new BigDecimal("25000.00"));
        account.setCurrCycDebit(BigDecimal.ZERO);
        account.setCurrCycCredit(BigDecimal.ZERO);
    }

    @Test
    void process_purchaseTransaction_updatesBalance() throws Exception {
        when(xrefRepository.findById("0500024453765740")).thenReturn(Optional.of(xref));
        when(accountRepository.findById(50L)).thenReturn(Optional.of(account));
        when(transactionRepository.existsById("0000000000000001")).thenReturn(false);
        when(tcbRepository.findById(any())).thenReturn(Optional.empty());

        DailyTransaction result = processor.process(dailyTran);

        assertNotNull(result);
        assertTrue(result.getPosted());
        assertEquals(new BigDecimal("25100.00"), account.getCurrBal());
        assertEquals(new BigDecimal("100.00"), account.getCurrCycDebit());
    }

    @Test
    void process_paymentTransaction_reducesBalance() throws Exception {
        dailyTran.setTranTypeCd("02");
        when(xrefRepository.findById("0500024453765740")).thenReturn(Optional.of(xref));
        when(accountRepository.findById(50L)).thenReturn(Optional.of(account));
        when(transactionRepository.existsById("0000000000000001")).thenReturn(false);
        when(tcbRepository.findById(any())).thenReturn(Optional.empty());

        DailyTransaction result = processor.process(dailyTran);

        assertNotNull(result);
        assertEquals(new BigDecimal("24900.00"), account.getCurrBal());
        assertEquals(new BigDecimal("100.00"), account.getCurrCycCredit());
    }

    @Test
    void process_unknownCard_returnsNull() throws Exception {
        when(xrefRepository.findById("0500024453765740")).thenReturn(Optional.empty());

        DailyTransaction result = processor.process(dailyTran);

        assertNull(result);
    }
}
