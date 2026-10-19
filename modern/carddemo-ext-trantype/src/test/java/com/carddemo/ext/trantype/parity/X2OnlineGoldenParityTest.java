package com.carddemo.ext.trantype.parity;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.ext.trantype.online.CtliService;
import com.carddemo.ext.trantype.online.CttuService;
import com.carddemo.ext.trantype.online.TrantypeConversations;
import com.carddemo.ext.trantype.online.TrantypeScreenController;
import com.carddemo.ext.trantype.support.GoldenScenarios;
import com.carddemo.ext.trantype.support.InMemoryTransactionTypeStore;
import com.carddemo.platform.time.FixedBusinessClock;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Gate L2 (online) of X2: CTLI/CTTU goldens over the controller with the seed data. */
@Tag("parity")
class X2OnlineGoldenParityTest {

  @TestFactory
  Stream<DynamicTest> ctliAndCttuGoldens() {
    return Stream.of("CTLI", "CTTU")
        .flatMap(t -> GoldenScenarios.online(t).stream())
        .map(p -> DynamicTest.dynamicTest(p.getFileName().toString(), () -> runScenario(p)));
  }

  private static void runScenario(Path p) throws Exception {
    InMemoryTransactionTypeStore store = InMemoryTransactionTypeStore.seeded();
    FixedBusinessClock clock = FixedBusinessClock.golden();
    MockMvc mvc =
        MockMvcBuilders.standaloneSetup(
                new TrantypeScreenController(
                    new CtliService(store, clock),
                    new CttuService(store, clock),
                    new TrantypeConversations()))
            .build();
    List<String> mismatches =
        GoldenScenarios.run(mvc, p, r -> r.principal(() -> "ADMIN001"), () -> store.types);
    assertThat(mismatches).as(p.toString()).isEmpty();
  }
}
