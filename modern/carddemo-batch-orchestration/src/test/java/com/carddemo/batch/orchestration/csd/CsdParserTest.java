package com.carddemo.batch.orchestration.csd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.batch.orchestration.BatchTestBase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CsdParserTest {

  @Test
  void bundledSysinIsTheCbadmcdjSysin() throws Exception {
    List<String> jcl = Files.readAllLines(BatchTestBase.repoRoot().resolve("app/jcl/CBADMCDJ.jcl"));
    int start =
        jcl.indexOf(jcl.stream().filter(l -> l.startsWith("//SYSIN")).findFirst().orElseThrow())
            + 1;
    int end = jcl.subList(start, jcl.size()).indexOf("/*") + start;
    List<String> bundled =
        new String(
                new ClassPathResource(CsdCatalogTasklet.DEFAULT_SYSIN)
                    .getInputStream()
                    .readAllBytes(),
                StandardCharsets.UTF_8)
            .lines()
            .toList();
    assertThat(bundled).containsExactlyElementsOf(jcl.subList(start, end));
  }

  @Test
  void parsesEveryDefineOfCbadmcdj() throws Exception {
    List<String> lines =
        new String(
                new ClassPathResource(CsdCatalogTasklet.DEFAULT_SYSIN)
                    .getInputStream()
                    .readAllBytes(),
                StandardCharsets.UTF_8)
            .lines()
            .toList();
    List<CsdDefinition> defs = CsdParser.parse(lines, "AWS.M2.CARDDEMO");
    assertThat(defs).hasSize(45);
    assertThat(defs).extracting(CsdDefinition::group).containsOnly("CARDDEMO");
    assertThat(defs)
        .filteredOn(d -> d.type().equals("TRANSACTION"))
        .extracting(CsdDefinition::name)
        .contains("CCDM", "CCT1", "CCT4");
    CsdDefinition lib = defs.get(0);
    assertThat(lib.type()).isEqualTo("LIBRARY");
    assertThat(lib.attributesText()).isEqualTo("DSNAME01(AWS.M2.CARDDEMO.LOADLIB)");
  }

  @Test
  void syntaxErrors() {
    assertThatThrownBy(() -> CsdParser.parse(List.of("  FOO BAR"), "H"))
        .hasMessageContaining("line 1");
    assertThatThrownBy(() -> CsdParser.parse(List.of("  DEFINE PROGRAM(X)"), "H"))
        .hasMessageContaining("GROUP");
    assertThatThrownBy(() -> CsdParser.parse(List.of("  DEFINE WIDGET(X) GROUP(G)"), "H"))
        .hasMessageContaining("invalid");
    assertThatThrownBy(() -> CsdParser.parse(List.of("  DEFINE PROGRAM(X) GROUP(G) junk"), "H"))
        .hasMessageContaining("malformed");
    assertThatThrownBy(() -> CsdParser.parse(List.of("  DEFINE"), "H"))
        .hasMessageContaining("malformed");
    assertThat(CsdParser.parse(List.of("* only comments", "", "  LIST GROUP(G)"), "H")).isEmpty();
  }
}
