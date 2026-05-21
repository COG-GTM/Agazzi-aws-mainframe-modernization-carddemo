package com.carddemo.batch.writer;

import com.carddemo.entity.DailyTransaction;
import com.carddemo.repository.DailyTransactionRepository;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
public class DailyTransactionWriter implements ItemWriter<DailyTransaction> {

    private final DailyTransactionRepository repository;

    public DailyTransactionWriter(DailyTransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void write(Chunk<? extends DailyTransaction> items) {
        repository.saveAll(items.getItems());
    }
}
