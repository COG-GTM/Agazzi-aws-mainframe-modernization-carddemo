package com.carddemo.batch;

import com.carddemo.domain.Account;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.DailyTransaction;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyTransactionValidationProcessorTest {

    @Mock
    private CardXrefRepository cardXrefRepository;

    @Mock
    private AccountRepository accountRepository;

    @Test
    void rejectsMissingCardXrefWithReason100() {
        DailyTransaction transaction = transaction("CARD-100", "2025-01-01");
        when(cardXrefRepository.findById("CARD-100")).thenReturn(Optional.empty());

        ValidatedDailyTransaction result = processor().process(transaction);

        assertThat(result.rejectReasonCode()).isEqualTo(100);
        assertThat(result.rejectReasonDescription())
                .isEqualTo("INVALID CARD NUMBER FOUND");
    }

    @Test
    void rejectsMissingAccountWithReason101() {
        DailyTransaction transaction = transaction("CARD-101", "2025-01-01");
        CardXref xref = xref("CARD-101", 101L);
        when(cardXrefRepository.findById("CARD-101")).thenReturn(Optional.of(xref));
        when(accountRepository.findById(101L)).thenReturn(Optional.empty());

        ValidatedDailyTransaction result = processor().process(transaction);

        assertThat(result.rejectReasonCode()).isEqualTo(101);
        assertThat(result.rejectReasonDescription())
                .isEqualTo("ACCOUNT RECORD NOT FOUND");
    }

    @Test
    void acceptsExactOverlimitBoundary() {
        DailyTransaction transaction = transaction("CARD-102", "2025-01-01");
        CardXref xref = xref("CARD-102", 102L);
        Account account = account(102L, "2025-12-31", "90.00");
        when(cardXrefRepository.findById("CARD-102")).thenReturn(Optional.of(xref));
        when(accountRepository.findById(102L)).thenReturn(Optional.of(account));

        ValidatedDailyTransaction result = processor().process(transaction);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void rejectsLimitJustBelowTemporaryBalanceWithReason102() {
        DailyTransaction transaction = transaction("CARD-102", "2025-01-01");
        CardXref xref = xref("CARD-102", 102L);
        Account account = account(102L, "2025-12-31", "89.99");
        when(cardXrefRepository.findById("CARD-102")).thenReturn(Optional.of(xref));
        when(accountRepository.findById(102L)).thenReturn(Optional.of(account));

        ValidatedDailyTransaction result = processor().process(transaction);

        assertThat(result.rejectReasonCode()).isEqualTo(102);
        assertThat(result.rejectReasonDescription()).isEqualTo("OVERLIMIT TRANSACTION");
    }

    @Test
    void rejectsExpiredTransactionWithReason103() {
        DailyTransaction transaction = transaction("CARD-103", "2026-01-01");
        CardXref xref = xref("CARD-103", 103L);
        Account account = account(103L, "2025-12-31", "1000.00");
        when(cardXrefRepository.findById("CARD-103")).thenReturn(Optional.of(xref));
        when(accountRepository.findById(103L)).thenReturn(Optional.of(account));

        ValidatedDailyTransaction result = processor().process(transaction);

        assertThat(result.rejectReasonCode()).isEqualTo(103);
        assertThat(result.rejectReasonDescription())
                .isEqualTo("TRANSACTION RECEIVED AFTER ACCT EXPIRATION");
    }

    private DailyTransactionValidationProcessor processor() {
        return new DailyTransactionValidationProcessor(cardXrefRepository, accountRepository);
    }

    private static DailyTransaction transaction(String cardNumber, String date) {
        DailyTransaction transaction = new DailyTransaction();
        transaction.setId("TRANSACTION-01");
        transaction.setCardNumber(cardNumber);
        transaction.setOriginalTimestamp(date + "-00.00.00.0000000");
        transaction.setAmount(new BigDecimal("10.00"));
        return transaction;
    }

    private static CardXref xref(String cardNumber, long accountId) {
        CardXref xref = new CardXref();
        xref.setCardNumber(cardNumber);
        xref.setAccountId(accountId);
        return xref;
    }

    private static Account account(long accountId, String expirationDate, String creditLimit) {
        Account account = new Account();
        account.setAcctId(accountId);
        account.setCurrentCycleCredit(new BigDecimal("100.00"));
        account.setCurrentCycleDebit(new BigDecimal("20.00"));
        account.setCurrentBalance(new BigDecimal("100.00"));
        account.setCreditLimit(new BigDecimal(creditLimit));
        account.setExpirationDate(expirationDate);
        return account;
    }
}
