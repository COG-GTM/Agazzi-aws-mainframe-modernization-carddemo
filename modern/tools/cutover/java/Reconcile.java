// Fase 5D reconciliation: VSAM/ASCII master files vs PostgreSQL tables (runbook T-14, T-0, rollback).
// Per dataset: record count, SHA-256 of the canonical rows (copybook fields in key order) on both
// sides, first differing keys. Files are decoded with the Fase 0 codecs (CoreCodecs /
// ZonedRecordCodec); DbSnapshot is taken before and after the run to prove the DB was frozen
// (CLOSEFIL / maintenance mode) while reconciling.
// Exit: 0 = all MATCH, 1 = MISMATCH or DB changed during the run, 2 = usage/IO error.
// Run through tools/cutover/cutover.sh reconcile --help.

import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.copybook.CoreCodecs;
import com.carddemo.copybook.ZonedRecordCodec;
import com.carddemo.parity.db.DbSnapshot;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.yaml.snakeyaml.Yaml;

public class Reconcile {

  static final int MAX_DETAILS = 20;

  record Dataset(String name, String table, String copybook, String source, List<String> exclude) {}

  record Result(
      String dataset,
      String table,
      String status,
      String source,
      String sourceSha256,
      int sourceRecords,
      long dbRows,
      String sourceChecksum,
      String dbChecksum,
      List<String> details) {}

  public static void main(String[] args) throws Exception {
    Map<String, String> a = parseArgs(args);
    if (a.containsKey("help") || !a.containsKey("db-url")) {
      System.out.println(
          """
          usage: cutover.sh reconcile --db-url JDBC_URL [--db-user U] --source-root DIR
                 [--format ascii|ebcdic] [--manifest datasets.yaml] [--datasets A,B] [--out DIR]
          password: env CARDDEMO_DB_PASSWORD (never on the command line)""");
      System.exit(a.containsKey("help") ? 0 : 2);
    }
    Path here = Path.of(System.getProperty("cutover.home", "tools/cutover"));
    Path manifest = Path.of(a.getOrDefault("manifest", here.resolve("datasets.yaml").toString()));
    String format = a.getOrDefault("format", "ascii").toLowerCase(Locale.ROOT);
    Path root = Path.of(a.getOrDefault("source-root", "../app/data/ASCII"));
    Path out = Path.of(a.getOrDefault("out", "target/cutover/reconciliation"));
    List<Dataset> datasets = loadManifest(manifest, format, a.get("datasets"));

    List<Result> results = new ArrayList<>();
    boolean frozen;
    try (Connection c =
        DriverManager.getConnection(
            a.get("db-url"),
            a.getOrDefault("db-user", "carddemo"),
            System.getenv().getOrDefault("CARDDEMO_DB_PASSWORD", ""))) {
      c.setReadOnly(true);
      List<String> tables = datasets.stream().map(Dataset::table).toList();
      DbSnapshot before = DbSnapshot.take(c, tables);
      for (Dataset d : datasets) {
        results.add(reconcile(c, d, root, format, before));
      }
      List<DbSnapshot.RowChange> drift = before.diff(DbSnapshot.take(c, tables));
      frozen = drift.isEmpty();
      if (!frozen) {
        System.err.println("DB changed during reconciliation (" + drift.size() + " row changes):");
        drift.stream().limit(MAX_DETAILS).forEach(r -> System.err.println("  " + r.table() + " " + r.kind() + " " + r.key()));
      }
    }
    boolean ok = frozen && results.stream().allMatch(r -> !r.status().equals("MISMATCH"));
    writeReports(out, results, format, root, frozen, ok);
    results.forEach(
        r ->
            System.out.printf(
                "%-9s %-20s %-8s file=%d db=%d%n",
                r.dataset(), r.table(), r.status(), r.sourceRecords(), r.dbRows()));
    System.out.println((ok ? "RECONCILIATION OK" : "RECONCILIATION FAILED") + " -> " + out.toAbsolutePath());
    System.exit(ok ? 0 : 1);
  }

  static Map<String, String> parseArgs(String[] args) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i < args.length; i++) {
      if (!args[i].startsWith("--")) {
        throw new IllegalArgumentException("unexpected argument " + args[i]);
      }
      String k = args[i].substring(2);
      if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
        m.put(k, args[++i]);
      } else {
        m.put(k, "true");
      }
    }
    return m;
  }

  @SuppressWarnings("unchecked")
  static List<Dataset> loadManifest(Path manifest, String format, String only) throws IOException {
    Map<String, Object> y = new Yaml().load(Files.readString(manifest));
    Map<String, Map<String, Object>> ds = (Map<String, Map<String, Object>>) y.get("datasets");
    List<String> filter = only == null ? null : List.of(only.toUpperCase(Locale.ROOT).split(","));
    List<Dataset> list = new ArrayList<>();
    ds.forEach(
        (n, d) -> {
          if (filter == null || filter.contains(n)) {
            list.add(
                new Dataset(
                    n,
                    (String) d.get("table"),
                    (String) d.get("copybook"),
                    (String) d.get(format),
                    (List<String>) d.getOrDefault("exclude_columns", List.of())));
          }
        });
    if (filter != null && list.size() != filter.size()) {
      throw new IllegalArgumentException("unknown dataset in --datasets " + only);
    }
    return list;
  }

  static Result reconcile(Connection c, Dataset d, Path root, String format, DbSnapshot snap)
      throws Exception {
    ZonedRecordCodec<?> codec =
        "ebcdic".equals(format)
            ? ZonedRecordCodec.ebcdic(((ZonedRecordCodec<?>) CoreCodecs.forCopybook(d.copybook())).recordType())
            : (ZonedRecordCodec<?>) CoreCodecs.forCopybook(d.copybook());
    List<ZonedRecordCodec.FieldLayout> fields =
        codec.fields().stream().filter(f -> !d.exclude().contains(column(f.name()))).toList();
    List<String> pk = primaryKey(c, d.table());
    boolean ordinalKey = pk.stream().noneMatch(p -> fields.stream().anyMatch(f -> column(f.name()).equals(p)));

    if (d.source() == null) {
      return new Result(d.name(), d.table(), "SKIPPED", null, null, 0, snap.rowCount(d.table()), null, null,
          List.of("no " + format + " source in manifest"));
    }
    List<byte[]> records = new ArrayList<>();
    String fileSha = null;
    if (!d.source().equals("EMPTY")) {
      Path file = root.resolve(d.source().replaceAll("#.*", ""));
      if (!Files.exists(file)) {
        return new Result(d.name(), d.table(), "MISMATCH", d.source(), null, 0, snap.rowCount(d.table()), null,
            null, List.of("source file not found: " + file));
      }
      byte[] raw = Files.readAllBytes(file);
      fileSha = sha256(raw);
      records = split(raw, codec, format, d.source().contains("#") ? d.source().replaceAll(".*#", "") : null);
    }

    TreeMap<String, Map<String, String>> fileRows = new TreeMap<>();
    int n = 0;
    for (byte[] r : records) {
      Record rec = (Record) codec.decode(r);
      Map<String, String> row = new LinkedHashMap<>();
      for (ZonedRecordCodec.FieldLayout f : fields) {
        row.put(column(f.name()), canonical(component(rec, f.component()), f));
      }
      n++;
      fileRows.put(ordinalKey ? "%012d".formatted(n) : key(row, pk), row);
    }

    TreeMap<String, Map<String, String>> dbRows = new TreeMap<>();
    String order = String.join(", ", pk);
    try (Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("SELECT * FROM " + d.table() + " ORDER BY " + order)) {
      int i = 0;
      while (rs.next()) {
        Map<String, String> row = new LinkedHashMap<>();
        for (ZonedRecordCodec.FieldLayout f : fields) {
          row.put(column(f.name()), canonical(rs.getObject(column(f.name())), f));
        }
        i++;
        dbRows.put(ordinalKey ? "%012d".formatted(i) : key(row, pk), row);
      }
    }

    List<String> details = new ArrayList<>();
    for (var e : fileRows.entrySet()) {
      Map<String, String> db = dbRows.get(e.getKey());
      if (db == null) {
        details.add("missing in DB: " + e.getKey());
      } else if (!db.equals(e.getValue())) {
        String col = e.getValue().keySet().stream().filter(k -> !e.getValue().get(k).equals(db.get(k))).findFirst().orElse("?");
        details.add("differs " + e.getKey() + " " + col + ": file [" + e.getValue().get(col).strip() + "] db [" + String.valueOf(db.get(col)).strip() + "]");
      }
    }
    dbRows.keySet().stream().filter(k -> !fileRows.containsKey(k)).forEach(k -> details.add("extra in DB: " + k));
    String fileSum = checksum(fileRows);
    String dbSum = checksum(dbRows);
    long count = snap.rowCount(d.table());
    if (count != dbRows.size()) {
      details.add("snapshot row count " + count + " != select " + dbRows.size());
    }
    boolean match = details.isEmpty() && fileSum.equals(dbSum) && fileRows.size() == count;
    if (!d.exclude().isEmpty()) {
      details.add(0, "excluded columns (registered deviation): " + d.exclude());
    }
    return new Result(d.name(), d.table(), match ? "MATCH" : "MISMATCH", d.source(), fileSha, fileRows.size(),
        count, fileSum, dbSum, details.size() > MAX_DETAILS ? details.subList(0, MAX_DETAILS) : details);
  }

  /** Splits a source into records: text lines (ascii), fixed LRECL blocks (ebcdic), instream DD. */
  static List<byte[]> split(byte[] raw, RecordCodec<?> codec, String format, String instreamDd) {
    int lrecl = codec.recordLength();
    List<byte[]> out = new ArrayList<>();
    if ("ebcdic".equals(format)) {
      if (raw.length % lrecl != 0) {
        throw new IllegalArgumentException("EBCDIC file length " + raw.length + " not a multiple of LRECL " + lrecl);
      }
      for (int i = 0; i < raw.length; i += lrecl) {
        out.add(java.util.Arrays.copyOfRange(raw, i, i + lrecl));
      }
      return out;
    }
    List<String> lines = List.of(new String(raw, StandardCharsets.ISO_8859_1).split("\r?\n"));
    if (instreamDd != null) {
      int start = -1;
      for (int i = 0; i < lines.size(); i++) {
        if (lines.get(i).startsWith("//" + instreamDd) && lines.get(i).contains("DD *")) {
          start = i + 1;
          break;
        }
      }
      if (start < 0) {
        throw new IllegalArgumentException("instream DD " + instreamDd + " not found");
      }
      List<String> body = new ArrayList<>();
      for (String l : lines.subList(start, lines.size())) {
        if (l.startsWith("/*") || l.startsWith("//")) {
          break;
        }
        body.add(l);
      }
      lines = body;
    }
    for (String l : lines) {
      if (instreamDd == null && l.isEmpty()) {
        continue;
      }
      String p = l.length() >= lrecl ? l.substring(0, lrecl) : l + " ".repeat(lrecl - l.length());
      out.add(p.getBytes(StandardCharsets.ISO_8859_1));
    }
    return out;
  }

  static Object component(Record r, String name) throws Exception {
    for (RecordComponent rc : r.getClass().getRecordComponents()) {
      if (rc.getName().equals(name)) {
        return rc.getAccessor().invoke(r);
      }
    }
    throw new IllegalStateException("no component " + name);
  }

  static String canonical(Object v, ZonedRecordCodec.FieldLayout f) {
    if (v == null) {
      return "<null>";
    }
    if (f.numeric()) {
      return new BigDecimal(v.toString()).setScale(f.scale()).toPlainString();
    }
    return v.toString();
  }

  /** Same naming as tools/contracts/generate_contracts.py column(). */
  static String column(String cobolName) {
    return cobolName.replace("EXPIRAION", "EXPIRATION").toLowerCase(Locale.ROOT).replace('-', '_');
  }

  static List<String> primaryKey(Connection c, String table) throws Exception {
    TreeMap<Integer, String> seq = new TreeMap<>();
    try (ResultSet rs = c.getMetaData().getPrimaryKeys(null, null, table)) {
      while (rs.next()) {
        seq.put(rs.getInt("KEY_SEQ"), rs.getString("COLUMN_NAME"));
      }
    }
    if (seq.isEmpty()) {
      throw new IllegalStateException("table " + table + " not found or without primary key");
    }
    return List.copyOf(seq.values());
  }

  static String key(Map<String, String> row, List<String> pk) {
    return String.join("|", pk.stream().map(row::get).toList());
  }

  static String checksum(TreeMap<String, Map<String, String>> rows) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    for (Map<String, String> r : rows.values()) {
      md.update(String.join("\u001f", r.values()).getBytes(StandardCharsets.UTF_8));
      md.update((byte) '\n');
    }
    return HexFormat.of().formatHex(md.digest());
  }

  static String sha256(byte[] b) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
  }

  static void writeReports(Path out, List<Result> results, String format, Path root, boolean frozen, boolean ok)
      throws IOException {
    Files.createDirectories(out);
    StringBuilder md = new StringBuilder("# Reconciliation report\n\n");
    md.append("- Verdict: **").append(ok ? "OK" : "FAILED").append("**\n");
    md.append("- Source: `").append(root).append("` (").append(format).append(")\n");
    md.append("- DB frozen during run (DbSnapshot before = after): ").append(frozen).append("\n\n");
    md.append("| Dataset | Table | Status | File records | DB rows | File SHA-256 | Row checksum (file / db) |\n|---|---|---|---|---|---|---|\n");
    StringBuilder json = new StringBuilder("{\"ok\":" + ok + ",\"frozen\":" + frozen + ",\"format\":\"" + format + "\",\"datasets\":[");
    for (int i = 0; i < results.size(); i++) {
      Result r = results.get(i);
      md.append("| %s | `%s` | %s | %d | %d | `%s` | `%s` / `%s` |\n".formatted(r.dataset(), r.table(), r.status(),
          r.sourceRecords(), r.dbRows(), abbrev(r.sourceSha256()), abbrev(r.sourceChecksum()), abbrev(r.dbChecksum())));
      json.append(i == 0 ? "" : ",").append("{\"dataset\":\"%s\",\"table\":\"%s\",\"status\":\"%s\",\"sourceRecords\":%d,\"dbRows\":%d,\"sourceSha256\":%s,\"sourceChecksum\":%s,\"dbChecksum\":%s,\"details\":[%s]}"
          .formatted(r.dataset(), r.table(), r.status(), r.sourceRecords(), r.dbRows(), q(r.sourceSha256()),
              q(r.sourceChecksum()), q(r.dbChecksum()), String.join(",", r.details().stream().map(Reconcile::q).toList())));
    }
    json.append("]}\n");
    md.append("\n## Details\n\n");
    results.forEach(r -> r.details().forEach(d -> md.append("- ").append(r.dataset()).append(": ").append(d).append('\n')));
    md.append("\nSign-off: ______________________ (data owner)   ______________________ (migration lead)\n");
    Files.writeString(out.resolve("reconciliation.md"), md);
    Files.writeString(out.resolve("reconciliation.json"), json);
  }

  static String abbrev(String s) {
    return s == null ? "-" : s.substring(0, 16);
  }

  static String q(String s) {
    return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
