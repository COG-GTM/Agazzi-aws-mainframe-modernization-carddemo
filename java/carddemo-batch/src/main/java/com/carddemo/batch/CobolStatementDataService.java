package com.carddemo.batch;

import com.carddemo.domain.Account;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.Customer;
import com.carddemo.domain.Transaction;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXrefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import com.carddemo.domain.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class CobolStatementDataService implements StatementDataService {

    private final AccountRepository accounts;
    private final CardXrefRepository xrefs;
    private final CustomerRepository customers;
    private final TransactionRepository transactions;

    public CobolStatementDataService(
            AccountRepository accounts,
            CardXrefRepository xrefs,
            CustomerRepository customers,
            TransactionRepository transactions) {
        this.accounts = accounts;
        this.xrefs = xrefs;
        this.customers = customers;
        this.transactions = transactions;
    }

    @Override
    public void open() {
        // Repository access has no explicit file handle; this is CBSTM03B's open boundary.
    }

    @Override
    public List<CardXref> readXrefs() {
        return xrefs.findAll().stream()
                .sorted(Comparator.comparing(CardXref::getCardNumber))
                .toList();
    }

    @Override
    public Optional<Customer> readCustomer(long customerId) {
        return customers.findById(customerId);
    }

    @Override
    public Optional<Account> readAccount(long accountId) {
        return accounts.findById(accountId);
    }

    @Override
    public List<Transaction> readTransactions(String cardNumber) {
        return transactions.findAll().stream()
                .filter(transaction -> cardNumber.equals(transaction.getCardNumber()))
                .sorted(Comparator.comparing(Transaction::getId))
                .toList();
    }

    @Override
    public void close() {
        // Repository access has no explicit file handle; this is CBSTM03B's close boundary.
    }
}
