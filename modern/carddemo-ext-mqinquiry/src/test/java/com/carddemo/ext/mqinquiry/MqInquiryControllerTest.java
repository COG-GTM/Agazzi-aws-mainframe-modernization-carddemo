package com.carddemo.ext.mqinquiry;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.platform.time.FixedBusinessClock;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MqInquiryControllerTest {

  private final AccountRepository repo = mock(AccountRepository.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    var seed = SeedAccounts.load();
    when(repo.findById(anyLong()))
        .thenAnswer(i -> Optional.ofNullable(seed.get(i.<Long>getArgument(0))));
    mvc =
        MockMvcBuilders.standaloneSetup(
                new MqInquiryController(
                    new SystemDateInquiry(FixedBusinessClock.golden()),
                    new AccountInquiry(repo, "IN")))
            .build();
  }

  @Test
  void systemDate() throws Exception {
    mvc.perform(get("/api/v1/ext/mqinquiry/system-date"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.reply")
                .value(MqMessageLayout.buffer("SYSTEM DATE : 07-18-2022SYSTEM TIME : 12:00:00")))
        .andExpect(jsonPath("$.correlationId").isNotEmpty());
  }

  @Test
  void accountFoundNotFoundAndBadRequest() throws Exception {
    mvc.perform(get("/api/v1/ext/mqinquiry/accounts/00000000001"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.reply")
                .value(org.hamcrest.Matchers.startsWith("ACCOUNT ID : 00000000001")));
    mvc.perform(get("/api/v1/ext/mqinquiry/accounts/99999999999"))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.reply")
                .value(org.hamcrest.Matchers.startsWith("INVALID REQUEST PARAMETERS")));
    mvc.perform(get("/api/v1/ext/mqinquiry/accounts/12x")).andExpect(status().isBadRequest());
  }

  @Test
  void readFailureIsServerError() throws Exception {
    when(repo.findById(anyLong())).thenThrow(new DataAccessResourceFailureException("down"));
    mvc.perform(get("/api/v1/ext/mqinquiry/accounts/00000000001"))
        .andExpect(status().isInternalServerError());
  }
}
