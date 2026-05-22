package com.carddemo.service;

import com.carddemo.dto.request.AccountUpdateRequest;
import com.carddemo.dto.response.AccountResponse;
import com.carddemo.entity.Account;
import com.carddemo.exception.BusinessRuleException;
import com.carddemo.exception.ResourceNotFoundException;
import com.carddemo.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Migrated from COBOL programs COACTVWC.cbl (Account View) and COACTUPC.cbl (Account Update).
 * Original: CICS transactions CAVW/CAUP with VSAM KSDS ACCTFILE.
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AccountResponse getAccount(Long acctId) {
        Account account = accountRepository.findById(acctId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + acctId));
        return AccountResponse.from(account);
    }

    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional
    public AccountResponse updateAccount(Long acctId, AccountUpdateRequest request) {
        Account account = accountRepository.findById(acctId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + acctId));

        if (request.activeStatus() != null) {
            if (!"Y".equals(request.activeStatus()) && !"N".equals(request.activeStatus())) {
                throw new BusinessRuleException("Active status must be 'Y' or 'N'");
            }
            account.setActiveStatus(request.activeStatus());
        }

        if (request.creditLimit() != null) {
            if (request.creditLimit().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessRuleException("Credit limit cannot be negative");
            }
            account.setCreditLimit(request.creditLimit());
        }

        if (request.cashCreditLimit() != null) {
            if (request.cashCreditLimit().compareTo(account.getCreditLimit()) > 0) {
                throw new BusinessRuleException("Cash credit limit cannot exceed credit limit");
            }
            account.setCashCreditLimit(request.cashCreditLimit());
        }

        accountRepository.save(account);
        return AccountResponse.from(account);
    }
}
