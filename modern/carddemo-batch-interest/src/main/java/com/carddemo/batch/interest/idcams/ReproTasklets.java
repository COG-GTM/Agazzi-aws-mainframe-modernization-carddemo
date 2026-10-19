package com.carddemo.batch.interest.idcams;

import com.carddemo.batch.interest.support.Abend;
import com.carddemo.batch.interest.support.ReturnCodes;
import com.carddemo.batch.interest.support.Sysout;
import com.carddemo.contracts.port.RecordCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * IDCAMS steps of the VSAM reload jobs over a PostgreSQL table: DELETE CLUSTER (+ {@code SET MAXCC
 * = 0}) empties the table, DEFINE CLUSTER checks it exists (the schema is owned by Flyway), REPRO
 * loads the sequential file named by a job parameter. Failures end the step with RC 12.
 */
final class ReproTasklets {

  private ReproTasklets() {}

  static Tasklet deleteCluster(JdbcTemplate jdbc, String table, Sysout sysout) {
    return (contribution, context) -> {
      int deleted = jdbc.update("DELETE FROM " + table);
      contribution.incrementWriteCount(deleted);
      sysout.display("IDC0550I ENTRY (C) %s DELETED (%d RECORDS)".formatted(table, deleted));
      return RepeatStatus.FINISHED;
    };
  }

  static Tasklet defineCluster(JdbcTemplate jdbc, String table, Sysout sysout) {
    return (contribution, context) -> {
      try {
        jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
      } catch (DataAccessException e) {
        throw new Abend(ReturnCodes.IDCAMS_ERROR, "DEFINE CLUSTER " + table + " FAILED", e);
      }
      sysout.display("IDC0508I DATA ALLOCATION STATUS FOR VOLUME AWSHJ1 IS 0 (" + table + ")");
      return RepeatStatus.FINISHED;
    };
  }

  static <T> Tasklet repro(
      String parameter,
      RecordCodec<T> codec,
      JdbcTemplate jdbc,
      String insertSql,
      Function<T, Object[]> columns,
      Sysout sysout) {
    return (contribution, context) -> {
      String infile =
          context.getStepContext().getStepExecution().getJobParameters().getString(parameter);
      if (infile == null || !Files.isReadable(Path.of(infile))) {
        throw new Abend(
            ReturnCodes.IDCAMS_ERROR, 0, "IDC3300I ERROR OPENING " + parameter + ": " + infile);
      }
      List<String> lines;
      try {
        lines = Files.readAllLines(Path.of(infile), StandardCharsets.US_ASCII);
      } catch (IOException e) {
        throw new Abend(ReturnCodes.IDCAMS_ERROR, "IDC3309I RECORD READ ERROR " + infile, e);
      }
      int n = 0;
      for (String raw : lines) {
        String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
        if (line.isEmpty()) {
          continue;
        }
        n++;
        T value;
        try {
          if (line.length() > codec.recordLength()) {
            throw new IllegalArgumentException("record length " + line.length());
          }
          value = codec.decodeLine(line);
        } catch (IllegalArgumentException e) {
          sysout.display("IDC3302I ACTION ERROR ON " + parameter + " RECORD " + n);
          throw new Abend(
              ReturnCodes.IDCAMS_ERROR, "MALFORMED RECORD " + n + ": " + e.getMessage(), e);
        }
        try {
          jdbc.update(insertSql, columns.apply(value));
        } catch (DataAccessException e) {
          sysout.display("IDC3314I RECORD " + n + " OUT OF SEQUENCE OR DUPLICATE KEY");
          throw new Abend(ReturnCodes.IDCAMS_ERROR, "DUPLICATE KEY RECORD " + n, e);
        }
        contribution.incrementReadCount();
        contribution.incrementWriteCount(1);
      }
      sysout.display("IDC0005I NUMBER OF RECORDS PROCESSED WAS " + n);
      return RepeatStatus.FINISHED;
    };
  }
}
