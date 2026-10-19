package com.carddemo.online.account.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.online.account.testapp.AccountTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = AccountTestApplication.class)
@AutoConfigureMockMvc
class CoactvwControllerTest {

  @Autowired MockMvc mvc;

  @Test
  void firstSendThenDisplayAccount() throws Exception {
    MockHttpSession s = new MockHttpSession();
    mvc.perform(get("/api/v1/screens/coactvw").session(s).with(httpBasic("USER0001", "PASSWORD")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.screen").value("COACTVW"))
        .andExpect(jsonPath("$.data.INFOMSG").value("Enter or update id of account to display"));
    mvc.perform(
            post("/api/v1/screens/coactvw")
                .session(s)
                .with(httpBasic("USER0001", "PASSWORD"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"ENTER\",\"fields\":{\"ACCTSID\":\"00000000001\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.ACURBAL").value("+        194.00"))
        .andExpect(jsonPath("$.fieldErrors").isEmpty());
  }
}
