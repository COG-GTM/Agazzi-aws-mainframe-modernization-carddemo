package com.carddemo.ext.authorization.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.ext.authorization.model.PendingAuthDetail;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AuthFraudTimestampTest {

  @Test
  void authTsFromOrigDateAndInvertedTime() {
    PendingAuthDetail d = new PendingAuthDetail();
    d.setAuthOrigDate("220718");
    d.setAuthTime9c(999_999_999L - 134_501_123L);
    assertThat(AuthFraudService.authTimestamp(d))
        .isEqualTo(LocalDateTime.of(2022, 7, 18, 13, 45, 1, 123_000_000));
  }
}
