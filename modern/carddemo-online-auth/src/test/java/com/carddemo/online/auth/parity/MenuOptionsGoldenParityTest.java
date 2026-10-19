package com.carddemo.online.auth.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.carddemo.online.auth.service.CicsMessages;
import com.carddemo.online.auth.service.Comen01cService;
import com.carddemo.online.auth.service.MenuCatalog;
import com.carddemo.online.auth.service.OptionInput;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Golden {@code golden/o1-auth/MENUOPTS}: option parsing and menu/message STRING statements copied
 * verbatim from COMEN01C/COADM01C and executed with GnuCOBOL ({@code run.sh}), compared with the
 * Java implementation. Inputs with characters other than digits/spaces are accepted by the
 * compiler-dependent alphanumeric-to-numeric MOVE; Java rejects them (PARITY-EXCEPTION O1-PE-3).
 */
@Tag("parity")
class MenuOptionsGoldenParityTest {

  static Path dir() {
    return Path.of(System.getProperty("modern.root", ".."))
        .toAbsolutePath()
        .normalize()
        .resolve("carddemo-parity/src/test/resources/golden/o1-auth/MENUOPTS");
  }

  static List<String> golden(char kind) throws Exception {
    return Files.readAllLines(dir().resolve("expected/MENUOPTS.txt")).stream()
        .filter(l -> l.charAt(0) == kind)
        .toList();
  }

  /** O1-PE-3: the OPTION field contains something other than digits, spaces or low-values. */
  static boolean exception(byte[] in) {
    for (byte b : in) {
      if (!(b == ' ' || b == 0 || (b >= '0' && b <= '9'))) {
        return true;
      }
    }
    for (int i = 0; i < in.length; i++) {
      if (in[i] == 0 && (in[0] != 0 || in[1] != 0)) {
        return true;
      }
    }
    return false;
  }

  @Test
  void optionParsingMatchesCobol() throws Exception {
    List<String> inputs =
        Files.readAllLines(dir().resolve("input/OPTIONS.hex")).stream()
            .map(String::strip)
            .filter(l -> !l.isEmpty() && !l.startsWith("#"))
            .toList();
    List<String> out = golden('O');
    assertThat(out).hasSameSizeAs(inputs);
    List<String> diffs = new ArrayList<>();
    int exceptions = 0;
    for (int i = 0; i < inputs.size(); i++) {
      byte[] in = HexFormat.of().parseHex(inputs.get(i));
      String[] f = out.get(i).split("\\|", -1);
      OptionInput o = OptionInput.parse(new String(in, StandardCharsets.ISO_8859_1));
      String wsOptionX =
          HexFormat.of().formatHex(o.optionX().getBytes(StandardCharsets.ISO_8859_1));
      assertThat(wsOptionX).as("WS-OPTION-X of " + inputs.get(i)).isEqualTo(f[2]);
      boolean user = o.validFor(MenuCatalog.MAIN_MENU.size());
      boolean admin = o.validFor(MenuCatalog.ADMIN_MENU.size());
      boolean same = user == "Y".equals(f[6]) && admin == "Y".equals(f[7]);
      if (exception(in)) {
        exceptions++;
        assertThat(user || admin).as("O1-PE-3: Java rejects " + inputs.get(i)).isFalse();
      } else if (!same) {
        diffs.add(inputs.get(i) + " -> " + out.get(i));
      } else if (o.numeric()) {
        assertThat(HexFormat.of().formatHex(o.optiono().getBytes(StandardCharsets.US_ASCII)))
            .as("OPTIONO of " + inputs.get(i))
            .isEqualTo(f[5]);
      }
    }
    assertThat(diffs).isEmpty();
    assertThat(exceptions).isLessThan(inputs.size() / 2);
  }

  @Test
  void menuTextsAndMessagesMatchCobol() throws Exception {
    List<String> main = golden('M');
    List<String> admin = golden('A');
    assertThat(main).hasSize(MenuCatalog.MAIN_MENU.size());
    assertThat(admin).hasSize(MenuCatalog.ADMIN_MENU.size());
    for (int i = 0; i < main.size(); i++) {
      assertThat(MenuCatalog.MAIN_MENU.get(i).menuText()).isEqualTo(field(main.get(i), 1));
    }
    for (int i = 0; i < admin.size(); i++) {
      assertThat(MenuCatalog.ADMIN_MENU.get(i).menuText()).isEqualTo(field(admin.get(i), 1));
    }
    List<String> ni = golden('N');
    List<String> soon = golden('D');
    for (int i = 0; i < ni.size(); i++) {
      assertThat(Comen01cService.notInstalled(MenuCatalog.MAIN_MENU.get(i)))
          .isEqualTo(field(ni.get(i), 2).stripTrailing());
      assertThat(Comen01cService.comingSoon(MenuCatalog.MAIN_MENU.get(i)))
          .isEqualTo(field(soon.get(i), 2).stripTrailing());
    }
    assertThat(CicsMessages.ADMIN_NOT_INSTALLED)
        .isEqualTo(field(golden('P').get(0), 1).stripTrailing());
  }

  static String field(String line, int n) {
    return line.split("\\|", -1)[n];
  }

  /** Re-runs the COBOL harness (zero diff); mandatory in CI (CARDDEMO_REQUIRE_COBOL=true). */
  @Test
  void cobolHarnessIsReproducible() throws Exception {
    boolean hasCobc = onPath("cobc");
    if ("true".equalsIgnoreCase(System.getenv("CARDDEMO_REQUIRE_COBOL"))) {
      assertThat(hasCobc).as("cobc on PATH").isTrue();
    }
    assumeTrue(hasCobc, "GnuCOBOL not installed");
    Process p =
        new ProcessBuilder("bash", dir().resolve("run.sh").toString(), "--verify")
            .redirectErrorStream(true)
            .start();
    String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(p.waitFor(5, TimeUnit.MINUTES)).isTrue();
    assertThat(p.exitValue()).as(log).isZero();
  }

  private static boolean onPath(String exe) {
    for (String d : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
      if (Files.isExecutable(Path.of(d, exe))) {
        return true;
      }
    }
    return false;
  }
}
