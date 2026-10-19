package com.carddemo.online.account.parity;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.online.account.testapp.AccountTestApplication;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** Parity gate of O2: online goldens CAVW and CAUP (plan §11.4). */
@Tag("parity")
@SpringBootTest(
    classes = AccountTestApplication.class,
    properties =
        "spring.datasource.url=jdbc:h2:mem:o2parity;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@DirtiesContext
class O2GoldenParityTest {

  @Autowired MockMvc mvc;

  @TestFactory
  Stream<DynamicTest> cavw() throws Exception {
    return scenarios("CAVW");
  }

  @TestFactory
  Stream<DynamicTest> caup() throws Exception {
    return scenarios("CAUP");
  }

  private Stream<DynamicTest> scenarios(String tranid) throws Exception {
    return GoldenScenarios.load(tranid).stream()
        .map(
            sc ->
                DynamicTest.dynamicTest(
                    sc.get("id") + " " + sc.get("title"),
                    () -> assertThat(GoldenScenarios.run(mvc, sc, null)).isEmpty()));
  }
}
