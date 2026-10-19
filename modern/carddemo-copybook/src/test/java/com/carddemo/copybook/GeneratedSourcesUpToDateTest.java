package com.carddemo.copybook;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.copybook.gen.GenerationSpec;
import com.carddemo.copybook.gen.RecordGenerator;
import com.carddemo.copybook.generated.GeneratedLayouts;
import com.carddemo.copybook.layout.RecordLayout;
import com.carddemo.copybook.parser.CopybookParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Codegen is deterministic and the committed sources are exactly its output (zero diff). */
class GeneratedSourcesUpToDateTest {

  static final String REGENERATE =
      "regenerate: cd modern/carddemo-copybook && java -cp target/classes:<contracts jar> "
          + "com.carddemo.copybook.gen.RecordGenerator ../../app src/main/java";

  static Path sourceRoot() {
    return TestPaths.modern().resolve("carddemo-copybook/src/main/java");
  }

  @Test
  void generationIsDeterministic() {
    assertThat(RecordGenerator.generate(TestPaths.app()))
        .containsExactlyEntriesOf(RecordGenerator.generate(TestPaths.app()));
  }

  @Test
  void committedSourcesMatchTheGenerator() throws Exception {
    Map<String, String> generated = RecordGenerator.generate(TestPaths.app());
    Path pkg = sourceRoot().resolve(RecordGenerator.BASE_PACKAGE.replace('.', '/'));
    TreeSet<String> onDisk = new TreeSet<>();
    try (Stream<Path> s = Files.walk(pkg)) {
      s.filter(Files::isRegularFile)
          .forEach(p -> onDisk.add(sourceRoot().relativize(p).toString().replace('\\', '/')));
    }
    assertThat(onDisk).as(REGENERATE).containsExactlyInAnyOrderElementsOf(generated.keySet());
    for (Map.Entry<String, String> e : generated.entrySet()) {
      assertThat(Files.readString(sourceRoot().resolve(e.getKey()), StandardCharsets.UTF_8))
          .as("%s is stale; %s", e.getKey(), REGENERATE)
          .isEqualTo(e.getValue());
    }
  }

  @Test
  void generatedLayoutsEqualTheParsedCopybooks() throws Exception {
    Map<String, RecordLayout> all = GeneratedLayouts.all();
    List<RecordLayout> parsed = new ArrayList<>();
    for (GenerationSpec.Target t : GenerationSpec.TARGETS) {
      String text =
          Files.readString(TestPaths.app().resolve(t.source()), StandardCharsets.ISO_8859_1);
      parsed.addAll(
          t.programRecords().isEmpty()
              ? CopybookParser.parse(t.id(), text)
              : CopybookParser.parseProgram(t.id(), text, t.programRecords()));
    }
    String vb =
        Files.readString(TestPaths.app().resolve("cbl/CBACT01C.cbl"), StandardCharsets.ISO_8859_1);
    parsed.addAll(CopybookParser.parseProgram("CBACT01C", vb, List.of("VBRC-REC1", "VBRC-REC2")));
    String exp =
        Files.readString(TestPaths.app().resolve("cpy/CVEXPORT.cpy"), StandardCharsets.ISO_8859_1);
    parsed.addAll(CopybookParser.parse("CVEXPORT", exp));
    assertThat(parsed).hasSameSizeAs(all.values());
    for (RecordLayout l : parsed) {
      assertThat(all.get(l.copybook() + "/" + l.name()))
          .as(l.copybook() + "/" + l.name())
          .isEqualTo(l);
    }
  }
}
