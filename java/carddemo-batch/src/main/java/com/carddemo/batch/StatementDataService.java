package com.carddemo.batch;

import com.carddemo.domain.Account;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.Customer;
import com.carddemo.domain.Transaction;

import java.util.List;
import java.util.Optional;

public interface StatementDataService extends AutoCloseable {

    void open();

    List<CardXref> readXrefs();

    Optional<Customer> readCustomer(long customerId);

    Optional<Account> readAccount(long accountId);

    List<Transaction> readTransactions(String cardNumber);

    @Override
    void close();
}
