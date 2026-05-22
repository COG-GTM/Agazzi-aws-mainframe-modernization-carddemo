package com.carddemo.batch.reader;

import com.carddemo.entity.DailyTransaction;
import com.carddemo.repository.DailyTransactionRepository;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;

@Component
public class DailyTransactionReader implements ItemReader<DailyTransaction> {

    private final DailyTransactionRepository repository;
    private Iterator<DailyTransaction> iterator;

    public DailyTransactionReader(DailyTransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    public DailyTransaction read() {
        if (iterator == null) {
            List<DailyTransaction> unposted = repository.findByPostedFalse();
            iterator = unposted.iterator();
        }
        if (iterator.hasNext()) {
            return iterator.next();
        }
        iterator = null;
        return null;
    }
}
