package com.carddemo.batch.orchestration.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.orchestration.BatchTestBase;
import com.carddemo.batch.orchestration.scheduler.Ca7Network.Edge;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** The Java scheduler definitions must carry exactly the legacy IN/OUT conditions and triggers. */
class SchedulerSourceParityTest {

  private static final Path SCHEDULER = BatchTestBase.repoRoot().resolve("app/scheduler");

  /** (JOBNAME, days, IN, OUT+, OUT-) in document order. */
  record Cm(String job, String days, List<String> in, List<String> add, List<String> del) {}

  private static Map<String, List<Cm>> parseControlM() throws Exception {
    var dbf = DocumentBuilderFactory.newInstance();
    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    var doc = dbf.newDocumentBuilder().parse(SCHEDULER.resolve("CardDemo.controlm").toFile());
    Map<String, List<Cm>> folders = new LinkedHashMap<>();
    NodeList jobs = doc.getElementsByTagName("JOB");
    for (int i = 0; i < jobs.getLength(); i++) {
      Element job = (Element) jobs.item(i);
      Element parent = (Element) job.getParentNode();
      String folder =
          parent.getTagName().equals("SMART_FOLDER")
              ? parent.getAttribute("PARENT_FOLDER")
              : parent.getAttribute("FOLDER_NAME");
      String days =
          job.hasAttribute("DAYS") ? job.getAttribute("DAYS") : parent.getAttribute("DAYS");
      List<String> in = new ArrayList<>();
      List<String> add = new ArrayList<>();
      List<String> del = new ArrayList<>();
      for (Node c = job.getFirstChild(); c != null; c = c.getNextSibling()) {
        if (c instanceof Element e && e.getTagName().equals("INCOND")) {
          in.add(e.getAttribute("NAME"));
        } else if (c instanceof Element e && e.getTagName().equals("OUTCOND")) {
          (e.getAttribute("SIGN").equals("+") ? add : del).add(e.getAttribute("NAME"));
        }
      }
      String d = days.isEmpty() || days.equals("ALL") ? "ALL" : days;
      folders
          .computeIfAbsent(folder, k -> new ArrayList<>())
          .add(new Cm(job.getAttribute("JOBNAME"), d, in, add, del));
    }
    return folders;
  }

  private static List<Cm> ours(SchedulerFolder f) {
    return f.jobs().stream()
        .map(
            j ->
                new Cm(
                    j.jobName(),
                    j.days().isEmpty() ? "ALL" : j.days().contains(DayOfWeek.SATURDAY) ? "SA" : "?",
                    j.inConds(),
                    j.addConds(),
                    j.delConds()))
        .toList();
  }

  @Test
  void controlMFoldersHaveTheSameJobsDaysAndConditions() throws Exception {
    Map<String, List<Cm>> legacy = parseControlM();
    assertThat(legacy.keySet())
        .containsExactly(
            ControlMDefinitions.DAILY, ControlMDefinitions.WEEKLY, ControlMDefinitions.MONTHLY);
    for (SchedulerFolder f : ControlMDefinitions.all()) {
      assertThat(ours(f)).as(f.name()).containsExactlyElementsOf(legacy.get(f.name()));
    }
  }

  @Test
  void ca7TriggerEdgesAreTheSame() throws Exception {
    Pattern ljob = Pattern.compile("^\\s*1?LJOB,JOB=(\\S+?),");
    Pattern trig = Pattern.compile("^\\s+JOB=(\\S+) SCHID=(\\d+)");
    List<Edge> legacy = new ArrayList<>();
    String current = null;
    for (String line : Files.readAllLines(SCHEDULER.resolve("CardDemo.ca7"))) {
      Matcher m = ljob.matcher(line);
      if (m.find()) {
        current = m.group(1);
        continue;
      }
      Matcher t = trig.matcher(line);
      if (t.find() && current != null) {
        legacy.add(new Edge(current, t.group(1), t.group(2)));
      }
    }
    List<Edge> ours = Ca7Definitions.all().stream().flatMap(n -> n.edges().stream()).toList();
    assertThat(legacy).hasSize(28);
    assertThat(ours).containsExactlyInAnyOrderElementsOf(legacy);
  }

  @Test
  void everyNetworkIsPlannableAndClosesWhatItOpens() {
    for (Ca7Network n : Ca7Definitions.all()) {
      List<List<SchedulerJob>> waves = ConditionPlanner.waves(n.toFolder());
      assertThat(waves.get(0)).extracting(SchedulerJob::jobName).containsExactly("CLOSEFIL");
      assertThat(waves.get(waves.size() - 1))
          .extracting(SchedulerJob::jobName)
          .containsExactly("OPENFIL");
    }
  }
}
