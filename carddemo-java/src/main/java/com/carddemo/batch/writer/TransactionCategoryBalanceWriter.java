package com.carddemo.batch.writer;

import com.carddemo.entity.TransactionCategoryBalance;
import com.carddemo.repository.TransactionCategoryBalanceRepository;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
public class TransactionCategoryBalanceWriter implements ItemWriter<TransactionCategoryBalance> {

    private final TransactionCategoryBalanceRepository repository;

    public TransactionCategoryBalanceWriter(TransactionCategoryBalanceRepository repository) {
        this.repository = repository;
    }

    @Override
    public void write(Chunk<? extends TransactionCategoryBalance> items) {
        repository.saveAll(items.getItems());
    }
}
