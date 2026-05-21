package com.carddemo.service;

import com.carddemo.dto.request.AccountUpdateRequest;
import com.carddemo.dto.response.AccountResponse;
import com.carddemo.entity.Account;
import com.carddemo.exception.BusinessRuleException;
import com.carddemo.exception.ResourceNotFoundException;
import com.carddemo.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        testAccount = new Account();
        testAccount.setAcctId(1L);
        testAccount.setActiveStatus("Y");
        testAccount.setCurrBal(new BigDecimal("19400.00"));
        testAccount.setCreditLimit(new BigDecimal("202000.00"));
        testAccount.setCashCreditLimit(new BigDecimal("102000.00"));
    }

    @Test
    void getAccount_existing_returnsResponse() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        AccountResponse response = accountService.getAccount(1L);

        assertEquals(1L, response.acctId());
        assertEquals("Y", response.activeStatus());
        assertEquals(new BigDecimal("19400.00"), response.currBal());
    }

    @Test
    void getAccount_notFound_throwsException() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> accountService.getAccount(999L));
    }

    @Test
    void updateAccount_validUpdate_updatesFields() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any())).thenReturn(testAccount);

        AccountUpdateRequest request = new AccountUpdateRequest("N", null, null);
        AccountResponse response = accountService.updateAccount(1L, request);

        assertEquals("N", response.activeStatus());
    }

    @Test
    void updateAccount_invalidStatus_throwsException() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        AccountUpdateRequest request = new AccountUpdateRequest("X", null, null);

        assertThrows(BusinessRuleException.class, () -> accountService.updateAccount(1L, request));
    }

    @Test
    void updateAccount_cashLimitExceedsCreditLimit_throwsException() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        AccountUpdateRequest request = new AccountUpdateRequest(null, null, new BigDecimal("999999.00"));

        assertThrows(BusinessRuleException.class, () -> accountService.updateAccount(1L, request));
    }
}
