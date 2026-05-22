package com.carddemo.batch.reader;

import com.carddemo.entity.TransactionCategoryBalance;
import com.carddemo.repository.TransactionCategoryBalanceRepository;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.Iterator;

@Component
public class TransactionCategoryBalanceReader implements ItemReader<TransactionCategoryBalance> {

    private final TransactionCategoryBalanceRepository repository;
    private Iterator<TransactionCategoryBalance> iterator;

    public TransactionCategoryBalanceReader(TransactionCategoryBalanceRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionCategoryBalance read() {
        if (iterator == null) {
            iterator = repository.findAll().iterator();
        }
        if (iterator.hasNext()) {
            return iterator.next();
        }
        iterator = null;
        return null;
    }
}
