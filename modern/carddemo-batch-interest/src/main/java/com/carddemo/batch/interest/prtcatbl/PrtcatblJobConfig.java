package com.carddemo.batch.interest.prtcatbl;

import com.carddemo.batch.interest.support.Abend;
import com.carddemo.batch.interest.support.B2JobListener;
import com.carddemo.batch.interest.support.B2StepListener;
import com.carddemo.batch.interest.support.GenerationWriter;
import com.carddemo.batch.interest.support.ReturnCodes;
import com.carddemo.batch.interest.support.Sysout;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.contracts.port.GenerationStore.Generation;
import com.carddemo.contracts.record.TranCatBalanceRecord;
import com.carddemo.copybook.CoreCodecs;
import com.carddemo.domain.entity.TranCatBalance;
import com.carddemo.domain.repository.TranCatBalanceRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * app/jcl/PRTCATBL.jcl: DELDEF (IEFBR14 delete of TCATBALF.REPT), STEP05R (REPROC: KSDS →
 * TCATBALF.BKUP(+1)), STEP10R (DFSORT by account/type/category with OUTREC EDIT → REPT, LRECL 40).
 * Job parameter {@code REPT} is the report file.
 */
@Configuration
public class PrtcatblJobConfig {

  public static final String JOB = "PRTCATBL";
  public static final String REPT_PARAMETER = "REPT";
  public static final String BKUP = "AWS.M2.CARDDEMO.TCATBALF.BKUP";
  static final int REPT_LRECL = 40;

  static final Comparator<TranCatBalanceRecord> SORT_FIELDS =
      Comparator.comparingLong(TranCatBalanceRecord::trancatAcctId)
          .thenComparing(TranCatBalanceRecord::trancatTypeCd)
          .thenComparingInt(TranCatBalanceRecord::trancatCd);

  private final JobRepository repository;
  private final PlatformTransactionManager transactionManager;
  private final B2StepListener stepListener;
  private final Sysout sysout;

  public PrtcatblJobConfig(
      JobRepository repository,
      PlatformTransactionManager transactionManager,
      B2StepListener stepListener,
      Sysout sysout) {
    this.repository = repository;
    this.transactionManager = transactionManager;
    this.stepListener = stepListener;
    this.sysout = sysout;
  }

  /**
   * OUTREC FIELDS=(ACCT-ID,X,TYPE-CD,X,CAT-CD,X,TRAN-CAT-BAL,EDIT=(TTTTTTTTT.TT),9X): 41 bytes
   * truncated to the SORTOUT LRECL of 40; the edit mask has no sign position (absolute value).
   */
  public static String reportLine(TranCatBalanceRecord r) {
    BigDecimal abs = r.tranCatBal().abs().setScale(2, RoundingMode.DOWN);
    String digits = "%011d".formatted(abs.unscaledValue().mod(java.math.BigInteger.TEN.pow(11)));
    String edited = digits.substring(0, 9) + "." + digits.substring(9);
    String line =
        "%011d %s %04d %s".formatted(r.trancatAcctId(), r.trancatTypeCd(), r.trancatCd(), edited)
            + " ".repeat(9);
    return line.substring(0, REPT_LRECL);
  }

  private Step step(String name, Tasklet tasklet) {
    return new StepBuilder(name, repository)
        .tasklet(tasklet, transactionManager)
        .listener(stepListener)
        .build();
  }

  private static Path rept(org.springframework.batch.core.scope.context.ChunkContext context) {
    String p =
        context.getStepContext().getStepExecution().getJobParameters().getString(REPT_PARAMETER);
    if (p == null || p.isBlank()) {
      throw new Abend(ReturnCodes.ABEND, 0, "MISSING JOB PARAMETER " + REPT_PARAMETER);
    }
    return Path.of(p);
  }

  @Bean
  Step prtcatblDeldef() {
    return step(
        "DELDEF",
        (contribution, context) -> {
          Files.deleteIfExists(rept(context));
          return RepeatStatus.FINISHED;
        });
  }

  @Bean
  Step prtcatblStep05r(TranCatBalanceRepository balances, GenerationStore generations) {
    return step(
        "STEP05R",
        (contribution, context) -> {
          try (GenerationWriter bkup = GenerationWriter.open(generations, BKUP)) {
            for (TranCatBalanceRecord r :
                balances.findAll().stream()
                    .map(PrtcatblJobConfig::toRecord)
                    .sorted(SORT_FIELDS)
                    .toList()) {
              bkup.write(CoreCodecs.TRAN_CAT_BALANCE.encodeLine(r));
              contribution.incrementWriteCount(1);
            }
            sysout.display("IDC0005I NUMBER OF RECORDS PROCESSED WAS " + bkup.count());
          }
          return RepeatStatus.FINISHED;
        });
  }

  @Bean
  Step prtcatblStep10r(GenerationStore generations) {
    return step(
        "STEP10R",
        (contribution, context) -> {
          Path rept = rept(context);
          Generation g =
              generations
                  .resolve(BKUP, 0)
                  .orElseThrow(
                      () ->
                          new Abend(ReturnCodes.ABEND, 0, "ICE083A SORTIN " + BKUP + " NOT FOUND"));
          List<TranCatBalanceRecord> in = new ArrayList<>();
          try (BufferedReader reader =
              new BufferedReader(
                  new InputStreamReader(generations.openInput(g), StandardCharsets.US_ASCII))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
              if (!line.isEmpty()) {
                in.add(CoreCodecs.TRAN_CAT_BALANCE.decodeLine(line));
              }
            }
          } catch (IllegalArgumentException e) {
            throw new Abend(ReturnCodes.ABEND, "ICE141A INVALID ZD FIELD IN SORTIN " + g.dsn(), e);
          }
          List<String> out =
              in.stream().sorted(SORT_FIELDS).map(PrtcatblJobConfig::reportLine).toList();
          try {
            Path parent = rept.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent, rept.getFileName().toString(), ".new");
            Files.write(tmp, out, StandardCharsets.US_ASCII);
            Files.move(tmp, rept, StandardCopyOption.REPLACE_EXISTING);
          } catch (IOException e) {
            throw new Abend(ReturnCodes.ABEND, "ICE046A SORTOUT " + rept + " OPEN ERROR", e);
          } catch (UncheckedIOException e) {
            throw new Abend(ReturnCodes.ABEND, "ICE046A SORTOUT " + rept + " OPEN ERROR", e);
          }
          contribution.incrementWriteCount(out.size());
          sysout.display(
              "ICE055I INSERT 0, DELETE 0 - RECORDS IN " + in.size() + ", OUT " + out.size());
          return RepeatStatus.FINISHED;
        });
  }

  private static TranCatBalanceRecord toRecord(TranCatBalance t) {
    return new TranCatBalanceRecord(
        t.getId().trancatAcctId(),
        t.getId().trancatTypeCd(),
        t.getId().trancatCd(),
        t.getTranCatBal(),
        t.getFiller());
  }

  @Bean
  Job prtcatblJob(
      B2JobListener listener, Step prtcatblDeldef, Step prtcatblStep05r, Step prtcatblStep10r) {
    return new JobBuilder(JOB, repository)
        .incrementer(new RunIdIncrementer())
        .listener(listener)
        .start(prtcatblDeldef)
        .next(prtcatblStep05r)
        .next(prtcatblStep10r)
        .build();
  }
}
