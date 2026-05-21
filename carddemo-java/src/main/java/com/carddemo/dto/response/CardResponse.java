package com.carddemo.dto.response;

import com.carddemo.entity.Card;
import java.time.LocalDate;

public record CardResponse(
        String cardNum,
        Long acctId,
        String embossedName,
        LocalDate expirationDate,
        String activeStatus
) {
    public static CardResponse from(Card c) {
        return new CardResponse(
                c.getCardNum(), c.getAcctId(),
                c.getEmbossedName(), c.getExpirationDate(),
                c.getActiveStatus()
        );
    }
}
