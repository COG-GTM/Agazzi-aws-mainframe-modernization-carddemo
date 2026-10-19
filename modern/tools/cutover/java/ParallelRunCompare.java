// Fase 5D parallel-run comparison (plan §11.5, runbook T-7..T-1): compares the batch outputs
// downloaded from the mainframe with the outputs of the Java jobs for the same business day and
// writes the daily report. Layout/LRECL of each output DD come from the golden job.yaml
// (carddemo-parity), the field-level comparison is RecordComparator with PARITY_EXCEPTIONS.md.
//
// Directory layout (both sides): <root>/<JOB>/<DD>.dat (text, one record per line, as the golden
// harness and `aws s3 cp s3://<gdg-bucket>/<run-date>/` produce) + optional RETURN-CODES.txt.
// Exit: 0 = all jobs MATCH, 1 = at least one unregistered difference or missing output, 2 = usage.

import com.carddemo.parity.batch.GoldenJob;
import com.carddemo.parity.compare.FieldDiff;
import com.carddemo.parity.compare.ParityExceptions;
import com.carddemo.parity.compare.RecordComparator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ParallelRunCompare {

  record DdResult(String job, String dd, String status, int mainframeRecords, int javaRecords, List<FieldDiff> diffs) {}

  public static void main(String[] args) throws Exception {
    Map<String, String> a = new LinkedHashMap<>();
    for (int i = 0; i < args.length; i++) {
      a.put(args[i].replaceFirst("^--", ""), i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "true");
    }
    if (a.containsKey("help") || !a.containsKey("mainframe") || !a.containsKey("java")) {
      System.out.println(
          """
          usage: cutover.sh compare --mainframe DIR --java DIR [--date YYYY-MM-DD] [--jobs POSTTRAN,INTCALC]
                 [--modern-root .] [--out target/cutover/parallel-run]""");
      System.exit(a.containsKey("help") ? 0 : 2);
    }
    Path modern = Path.of(a.getOrDefault("modern-root", "."));
    Path mf = Path.of(a.get("mainframe"));
    Path jv = Path.of(a.get("java"));
    String date = a.getOrDefault("date", LocalDate.now().toString());
    Path out = Path.of(a.getOrDefault("out", "target/cutover/parallel-run")).resolve(date);
    // Expected jobs of the business day: explicit --jobs (runbook 7.6), else every golden job.
    // A job missing on either side is a NO-GO, never silently skipped.
    List<String> jobs = a.containsKey("jobs") ? List.of(a.get("jobs").split(",")) : GoldenJob.jobNames(modern);
    ParityExceptions exc = ParityExceptions.load(modern.resolve("PARITY_EXCEPTIONS.md"));
    RecordComparator cmp = new RecordComparator(exc);
    RecordComparator raw = new RecordComparator(ParityExceptions.none());

    List<DdResult> results = new ArrayList<>();
    List<String> rcLines = new ArrayList<>();
    int registered = 0;
    for (String job : jobs) {
      GoldenJob g = GoldenJob.load(modern, job);
      if (!Files.isDirectory(mf.resolve(job)) || !Files.isDirectory(jv.resolve(job))) {
        String side = Files.isDirectory(mf.resolve(job)) ? "java" : "mainframe";
        results.add(new DdResult(job, "*", "MISSING " + side, 0, 0, List.of()));
        continue;
      }
      for (String dd : g.outputs()) {
        GoldenJob.Dataset ds = g.datasets().get(dd);
        Path m = mf.resolve(job).resolve(dd + ".dat");
        Path j = jv.resolve(job).resolve(dd + ".dat");
        if (!Files.exists(m) || !Files.exists(j)) {
          results.add(new DdResult(job, dd, "MISSING " + (Files.exists(m) ? "java" : "mainframe"), 0, 0, List.of()));
          continue;
        }
        int nm = RecordComparator.readRecords(m, ds.lrecl()).size();
        int nj = RecordComparator.readRecords(j, ds.lrecl()).size();
        List<FieldDiff> all = raw.compareFiles(job, dd, ds.layout(), ds.lrecl(), m, j);
        List<FieldDiff> diffs = cmp.compareFiles(job, dd, ds.layout(), ds.lrecl(), m, j);
        registered += all.size() - diffs.size();
        results.add(new DdResult(job, dd, diffs.isEmpty() ? "MATCH" : "DIFF", nm, nj, diffs));
      }
      Path rm = mf.resolve(job).resolve("RETURN-CODES.txt");
      Path rj = jv.resolve(job).resolve("RETURN-CODES.txt");
      if (!Files.exists(rm) || !Files.exists(rj)) {
        results.add(new DdResult(job, "RETURN-CODES", "MISSING " + (Files.exists(rm) ? "java" : "mainframe"), 0, 0, List.of()));
      } else {
        boolean same = Files.readString(rm).strip().equals(Files.readString(rj).strip());
        rcLines.add("| " + job + " | " + Files.readString(rm).strip().replace("\n", ", ") + " | "
            + Files.readString(rj).strip().replace("\n", ", ") + " | " + (same ? "MATCH" : "DIFF") + " |");
        if (!same) {
          results.add(new DdResult(job, "RETURN-CODES", "DIFF", 0, 0, List.of()));
        }
      }
    }
    boolean ok = !results.isEmpty() && results.stream().allMatch(r -> r.status().equals("MATCH"));

    StringBuilder md = new StringBuilder("# Parallel run report — business day " + date + "\n\n");
    md.append("- Verdict: **").append(ok ? "GO (no unregistered differences)" : "NO-GO").append("**\n");
    md.append("- Mainframe outputs: `").append(mf).append("`; Java outputs: `").append(jv).append("`\n");
    md.append("- Differences covered by PARITY_EXCEPTIONS.md: ").append(registered).append("\n\n");
    md.append("| Job | DD | Status | Mainframe records | Java records | Unregistered diffs |\n|---|---|---|---|---|---|\n");
    results.forEach(r -> md.append("| %s | %s | %s | %d | %d | %d |\n".formatted(r.job(), r.dd(), r.status(),
        r.mainframeRecords(), r.javaRecords(), r.diffs().size())));
    if (!rcLines.isEmpty()) {
      md.append("\n## Return codes\n\n| Job | Mainframe | Java | Status |\n|---|---|---|---|\n");
      rcLines.forEach(l -> md.append(l).append('\n'));
    }
    md.append("\n## First differences\n\n");
    results.forEach(r -> r.diffs().stream().limit(20).forEach(d -> md.append("- ").append(d).append('\n')));
    StringBuilder json = new StringBuilder("{\"date\":\"" + date + "\",\"ok\":" + ok + ",\"registered\":" + registered + ",\"results\":[");
    for (int i = 0; i < results.size(); i++) {
      DdResult r = results.get(i);
      json.append(i == 0 ? "" : ",").append("{\"job\":\"%s\",\"dd\":\"%s\",\"status\":\"%s\",\"diffs\":%d}"
          .formatted(r.job(), r.dd(), r.status(), r.diffs().size()));
    }
    json.append("]}\n");
    Files.createDirectories(out);
    Files.writeString(out.resolve("parallel-run-report.md"), md);
    Files.writeString(out.resolve("parallel-run-report.json"), json);
    results.forEach(r -> System.out.printf("%-9s %-14s %-16s mf=%d java=%d diffs=%d%n", r.job(), r.dd(), r.status(),
        r.mainframeRecords(), r.javaRecords(), r.diffs().size()));
    System.out.println((ok ? "PARALLEL RUN OK" : "PARALLEL RUN FAILED") + " -> " + out.toAbsolutePath());
    System.exit(ok ? 0 : 1);
  }
}
