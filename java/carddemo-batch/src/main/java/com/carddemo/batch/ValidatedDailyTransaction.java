package com.carddemo.batch;

import com.carddemo.domain.Account;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.DailyTransaction;

public record ValidatedDailyTransaction(
        DailyTransaction dailyTransaction,
        CardXref cardXref,
        Account account,
        int rejectReasonCode,
        String rejectReasonDescription) {

    public boolean isValid() {
        return rejectReasonCode == 0;
    }
}
