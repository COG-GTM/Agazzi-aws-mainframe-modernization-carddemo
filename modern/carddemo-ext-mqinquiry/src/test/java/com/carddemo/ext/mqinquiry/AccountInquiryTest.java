package com.carddemo.ext.mqinquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.domain.entity.Account;
import com.carddemo.domain.repository.AccountRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class AccountInquiryTest {

  private final AccountRepository repo = mock(AccountRepository.class);
  private final AccountInquiry inquiry = new AccountInquiry(repo, "CARD-DEMO-REQUEST-ACCT");
  private final Map<Long, Account> seed = SeedAccounts.load();

  @BeforeEach
  void stubRepository() {
    when(repo.findById(anyLong()))
        .thenAnswer(inv -> Optional.ofNullable(seed.get(inv.<Long>getArgument(0))));
  }

  @Test
  void validInquiryReturnsAccountResponse() {
    InquiryOutcome out = inquiry.process("INQA00000000001");
    assertThat(out.reply()).isTrue();
    assertThat(out.payload())
        .hasSize(1000)
        .startsWith(
            "ACCOUNT ID : 00000000001ACCOUNT STATUS : YBALANCE : 00000001940{"
                + "CREDIT LIMIT : 00000020200{CASH LIMIT : 00000010200{OPEN DATE : 2014-11-20"
                + "EXPR DATE : 2025-05-20REIS DATE : 2025-05-20CREDIT BAL : 00000000000{"
                + "DEBIT BAL : 00000000000{GROUP ID : ");
  }

  @Test
  void missingAccountIsNotFound() {
    assertThat(inquiry.process("INQA99999999999").payload())
        .startsWith("INVALID REQUEST PARAMETERS ACCT ID : 99999999999 ");
  }

  @Test
  void wrongFunctionOrZeroKeyIsInvalid() {
    assertThat(inquiry.process("INQX00000000001").payload())
        .startsWith("INVALID REQUEST PARAMETERS ACCT ID : 00000000001FUNCTION : INQX ");
    assertThat(inquiry.process("INQA00000000000").payload())
        .startsWith("INVALID REQUEST PARAMETERS ACCT ID : 00000000000FUNCTION : INQA ");
    assertThat(inquiry.process("").payload())
        .startsWith("INVALID REQUEST PARAMETERS ACCT ID : " + " ".repeat(11) + "FUNCTION : ");
    verify(repo, never()).findById(0L);
  }

  @Test
  void nonNumericKeyComparesLikeReferenceRuntime() {
    assertThat(AccountInquiry.displayValue("1          ")).isNegative();
    assertThat(AccountInquiry.displayValue("0000000000A")).isPositive();
    assertThat(inquiry.process("INQA0000000000A").payload())
        .startsWith("INVALID REQUEST PARAMETERS ACCT ID : 0000000000A ");
  }

  @Test
  void readFailureGoesToErrorQueue() {
    when(repo.findById(anyLong())).thenThrow(new DataAccessResourceFailureException("down"));
    InquiryOutcome out = inquiry.process("INQA00000000001");
    assertThat(out.reply()).isFalse();
    assertThat(out.payload())
        .hasSize(1000)
        .startsWith(
            " ".repeat(25) + "  ERROR WHILE READING ACCTF  17  00000  CARD-DEMO-REQUEST-ACCT ");
  }
}
