package com.carddemo.dto.response;

import com.carddemo.entity.Transaction;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        String tranId,
        String tranTypeCd,
        Integer tranCatCd,
        String tranSource,
        String tranDesc,
        BigDecimal tranAmt,
        Long merchantId,
        String merchantName,
        String merchantCity,
        String merchantZip,
        String cardNum,
        LocalDateTime origTs,
        LocalDateTime procTs
) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getTranId(), t.getTranTypeCd(), t.getTranCatCd(),
                t.getTranSource(), t.getTranDesc(), t.getTranAmt(),
                t.getMerchantId(), t.getMerchantName(),
                t.getMerchantCity(), t.getMerchantZip(),
                t.getCardNum(), t.getOrigTs(), t.getProcTs()
        );
    }
}
