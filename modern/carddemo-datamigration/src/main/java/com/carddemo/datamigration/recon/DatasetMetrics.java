package com.carddemo.datamigration.recon;

import com.carddemo.datamigration.io.SourceRecord;
import com.carddemo.datamigration.model.Dataset;
import com.carddemo.datamigration.model.FieldColumn;
import com.carddemo.datamigration.model.Organization;
import com.carddemo.datamigration.model.RecordMapper;
import com.carddemo.datamigration.validate.SourceValidator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Order-independent fingerprint of a dataset: record count, sum of the checksum fields and a
 * SHA-256 per column over the canonical values in key order (VSAM key for KSDS, record position for
 * PS/ESDS/RRDS), plus a SHA-256 of the canonical ASCII records.
 */
public record DatasetMetrics(
    Dataset dataset,
    long count,
    Map<String, BigDecimal> sums,
    Map<String, String> columnHashes,
    String recordHash) {

  public static DatasetMetrics of(Dataset ds, List<SourceRecord> records) {
    RecordMapper<Record> m = ds.mapper();
    List<SourceRecord> sorted = new ArrayList<>(records);
    if (ds.organization() == Organization.KSDS) {
      List<String> key = ds.keyColumns();
      sorted.sort(Comparator.comparing((SourceRecord r) -> SourceValidator.key(m, r.value(), key)));
    } else {
      sorted.sort(Comparator.comparingLong(SourceRecord::ordinal));
    }
    Map<String, BigDecimal> sums = new LinkedHashMap<>();
    for (String name : ds.checksumFields()) {
      FieldColumn f = m.field(name);
      BigDecimal s = BigDecimal.ZERO.setScale(RecordMapper.scaleOf(f.pic()));
      for (SourceRecord r : sorted) {
        s = s.add((BigDecimal) m.value(r.value(), f));
      }
      sums.put(name, s);
    }
    Map<String, MessageDigest> digests = new LinkedHashMap<>();
    m.fields().forEach(f -> digests.put(f.cobolName() + "@" + f.offset(), sha()));
    MessageDigest rec = sha();
    var codec = ds.codec(false);
    for (SourceRecord r : sorted) {
      for (FieldColumn f : m.fields()) {
        digests
            .get(f.cobolName() + "@" + f.offset())
            .update(
                (RecordMapper.canonical(m.value(r.value(), f)) + "\n")
                    .getBytes(StandardCharsets.UTF_8));
      }
      rec.update(codec.encode(r.value()));
    }
    Map<String, String> hashes = new LinkedHashMap<>();
    digests.forEach((k, d) -> hashes.put(k, HexFormat.of().formatHex(d.digest())));
    return new DatasetMetrics(
        ds, sorted.size(), sums, hashes, HexFormat.of().formatHex(rec.digest()));
  }

  private static MessageDigest sha() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
