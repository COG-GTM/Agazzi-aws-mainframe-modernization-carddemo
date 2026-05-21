package com.carddemo.dto.response;

import com.carddemo.entity.Account;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountResponse(
        Long acctId,
        String activeStatus,
        BigDecimal currBal,
        BigDecimal creditLimit,
        BigDecimal cashCreditLimit,
        LocalDate openDate,
        LocalDate expirationDate,
        LocalDate reissueDate,
        BigDecimal currCycCredit,
        BigDecimal currCycDebit,
        String addrZip,
        String groupId
) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(
                a.getAcctId(), a.getActiveStatus(), a.getCurrBal(),
                a.getCreditLimit(), a.getCashCreditLimit(),
                a.getOpenDate(), a.getExpirationDate(), a.getReissueDate(),
                a.getCurrCycCredit(), a.getCurrCycDebit(),
                a.getAddrZip(), a.getGroupId()
        );
    }
}
