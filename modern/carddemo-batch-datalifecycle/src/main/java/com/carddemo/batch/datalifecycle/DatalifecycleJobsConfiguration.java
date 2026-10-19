package com.carddemo.batch.datalifecycle;

import com.carddemo.batch.datalifecycle.gdg.GdgCatalog;
import com.carddemo.batch.datalifecycle.job.JclJobBuilder;
import com.carddemo.batch.datalifecycle.program.SequentialDumpProgram;
import com.carddemo.batch.datalifecycle.step.Programs;
import com.carddemo.batch.datalifecycle.step.Utilities;
import com.carddemo.batch.datalifecycle.store.KsdsTable;
import com.carddemo.batch.datalifecycle.store.TableStore;
import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.contracts.port.MaintenanceModePort;
import com.carddemo.contracts.port.UserCredentialService;
import com.carddemo.contracts.record.UserSecurityRecord;
import java.util.List;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Data lifecycle jobs (workstream B4). Each bean is a Spring Batch job named after its JCL member
 * ({@code --spring.batch.job.name=<JCL>}) with one step per JCL EXEC; DD statements and PARM are
 * JobParameters ({@code dd.<DDNAME>}, {@code datasets}, {@code sysout}, {@code returnCodes}).
 */
@Configuration(proxyBeanMethods = false)
public class DatalifecycleJobsConfiguration {

  static final String HLQ = "AWS.M2.CARDDEMO.";

  /** In-stream SYSUT1 of DUSRSECJ / ESDSRRDS (CSUSR01Y, 80 bytes). */
  public static final List<String> USERS =
      List.of(
          "ADMIN001MARGARET            GOLD                PASSWORDA",
          "ADMIN002RUSSELL             RUSSELL             PASSWORDA",
          "ADMIN003RAYMOND             WHITMORE            PASSWORDA",
          "ADMIN004EMMANUEL            CASGRAIN            PASSWORDA",
          "ADMIN005GRANVILLE           LACHAPELLE          PASSWORDA",
          "USER0001LAWRENCE            THOMAS              PASSWORDU",
          "USER0002AJITH               KUMAR               PASSWORDU",
          "USER0003LAURITZ             ALME                PASSWORDU",
          "USER0004AVERARDO            MAZZI               PASSWORDU",
          "USER0005LEE                 TING                PASSWORDU");

  @Bean
  @ConditionalOnMissingBean
  TableStore datalifecycleTableStore(JdbcTemplate jdbc) {
    return new TableStore(jdbc);
  }

  @Bean
  @ConditionalOnMissingBean
  GdgCatalog datalifecycleGdgCatalog(JdbcTemplate jdbc, BusinessClock clock) {
    return new GdgCatalog(jdbc, clock);
  }

  @Bean
  Utilities datalifecycleUtilities(
      TableStore tables,
      GdgCatalog gdgs,
      GenerationStore generations,
      MaintenanceModePort maintenance) {
    return new Utilities(tables, gdgs, generations, maintenance);
  }

  @Bean
  Programs datalifecyclePrograms(TableStore tables, BusinessClock clock) {
    return new Programs(tables, clock);
  }

  private static JclJobBuilder jcl(JobRepository r, PlatformTransactionManager tx, String name) {
    return new JclJobBuilder(r, tx, name);
  }

  @Bean
  Job acctfileJob(JobRepository r, PlatformTransactionManager tx, Utilities u) {
    return jcl(r, tx, "ACCTFILE")
        .step("STEP05", Utilities.IDCAMS, u.deleteCluster(KsdsTable.ACCTDATA))
        .step("STEP10", Utilities.IDCAMS, u.defineCluster(KsdsTable.ACCTDATA))
        .step(
            "STEP15",
            Utilities.IDCAMS,
            u.repro("ACCTDATA", HLQ + "ACCTDATA.PS", KsdsTable.ACCTDATA, 300))
        .build();
  }

  @Bean
  Job cardfileJob(JobRepository r, PlatformTransactionManager tx, Utilities u) {
    String aix = HLQ + "CARDDATA.VSAM.AIX";
    return jcl(r, tx, "CARDFILE")
        .step("CLCIFIL", Utilities.SDSF, u.cics(true, "CARDDAT", "CARDAIX"))
        .step("STEP05", Utilities.IDCAMS, u.deleteCluster(KsdsTable.CARDDATA))
        .step("STEP10", Utilities.IDCAMS, u.defineCluster(KsdsTable.CARDDATA))
        .step(
            "STEP15",
            Utilities.IDCAMS,
            u.repro("CARDDATA", HLQ + "CARDDATA.PS", KsdsTable.CARDDATA, 150))
        .step("STEP40", Utilities.IDCAMS, u.defineIndex("AIX", aix, "card_acct_id"))
        .step("STEP50", Utilities.IDCAMS, u.defineIndex("PATH", aix + ".PATH", "card_acct_id"))
        .step("STEP60", Utilities.IDCAMS, u.bldIndex(KsdsTable.CARDDATA, aix))
        .step("OPCIFIL", Utilities.SDSF, u.cics(false, "CARDDAT", "CARDAIX"))
        .build();
  }

  @Bean
  Job xreffileJob(JobRepository r, PlatformTransactionManager tx, Utilities u) {
    String aix = HLQ + "CARDXREF.VSAM.AIX";
    return jcl(r, tx, "XREFFILE")
        .step("STEP05", Utilities.IDCAMS, u.deleteCluster(KsdsTable.CARDXREF))
        .step("STEP10", Utilities.IDCAMS, u.defineCluster(KsdsTable.CARDXREF))
        .step(
            "STEP15",
            Utilities.IDCAMS,
            u.repro("XREFDATA", HLQ + "CARDXREF.PS", KsdsTable.CARDXREF, 50))
        .step("STEP20", Utilities.IDCAMS, u.defineIndex("AIX", aix, "xref_acct_id"))
        .step("STEP25", Utilities.IDCAMS, u.defineIndex("PATH", aix + ".PATH", "xref_acct_id"))
        .step("STEP30", Utilities.IDCAMS, u.bldIndex(KsdsTable.CARDXREF, aix))
        .build();
  }

  @Bean
  Job custfileJob(JobRepository r, PlatformTransactionManager tx, Utilities u) {
    return jcl(r, tx, "CUSTFILE")
        .step("CLCIFIL", Utilities.SDSF, u.cics(true, "CUSTDAT"))
        .step("STEP05", Utilities.IDCAMS, u.deleteCluster(KsdsTable.CUSTDATA))
        .step("STEP10", Utilities.IDCAMS, u.defineCluster(KsdsTable.CUSTDATA))
        .step(
            "STEP15",
            Utilities.IDCAMS,
            u.repro("CUSTDATA", HLQ + "CUSTDATA.PS", KsdsTable.CUSTDATA, 500))
        .step("OPCIFIL", Utilities.SDSF, u.cics(false, "CUSTDAT"))
        .build();
  }

  @Bean
  Job tranfileJob(JobRepository r, PlatformTransactionManager tx, Utilities u) {
    String aix = HLQ + "TRANSACT.VSAM.AIX";
    return jcl(r, tx, "TRANFILE")
        .step("CLCIFIL", Utilities.SDSF, u.cics(true, "TRANSACT", "CXACAIX"))
        .step("STEP05", Utilities.IDCAMS, u.deleteCluster(KsdsTable.TRANSACT))
        .step("STEP10", Utilities.IDCAMS, u.defineCluster(KsdsTable.TRANSACT))
        .step(
            "STEP15",
            Utilities.IDCAMS,
            u.repro("TRANSACT", HLQ + "DALYTRAN.PS.INIT", KsdsTable.TRANSACT, 350))
        .step("STEP20", Utilities.IDCAMS, u.defineIndex("AIX", aix, "tran_proc_ts"))
        .step("STEP25", Utilities.IDCAMS, u.defineIndex("PATH", aix + ".PATH", "tran_proc_ts"))
        .step("STEP30", Utilities.IDCAMS, u.bldIndex(KsdsTable.TRANSACT, aix))
        .step("OPCIFIL", Utilities.SDSF, u.cics(false, "TRANSACT", "CXACAIX"))
        .build();
  }

  @Bean
  Job trantypeJob(JobRepository r, PlatformTransactionManager tx, Utilities u) {
    return jcl(r, tx, "TRANTYPE")
        .step("STEP05", Utilities.IDCAMS, u.deleteCluster(KsdsTable.TRANTYPE))
        .step("STEP10", Utilities.IDCAMS, u.defineCluster(KsdsTable.TRANTYPE))
        .step(
            "STEP15",
            Utilities.IDCAMS,
            u.repro("TRANTYPE", HLQ + "TRANTYPE.PS", KsdsTable.TRANTYPE, 60))
        .build();
  }

  @Bean
  Job trancatgJob(JobRepository r, PlatformTransactionManager tx, Utilities u) {
    return jcl(r, tx, "TRANCATG")
        .step("STEP05", Utilities.IDCAMS, u.deleteCluster(KsdsTable.TRANCATG))
        .step("STEP10", Utilities.IDCAMS, u.defineCluster(KsdsTable.TRANCATG))
        .step(
            "STEP15",
            Utilities.IDCAMS,
            u.repro("TRANCATG", HLQ + "TRANCATG.PS", KsdsTable.TRANCATG, 60))
        .build();
  }

  @Bean
  Job dusrsecjJob(
      JobRepository r,
      PlatformTransactionManager tx,
      Utilities u,
      UserCredentialService credentials) {
    String ps = HLQ + "USRSEC.PS";
    return jcl(r, tx, "DUSRSECJ")
        .step("PREDEL", Utilities.IEFBR14, u.deleteDatasets("DD01", ps))
        .step("STEP01", Utilities.IEBGENER, u.generateInstream("SYSUT2", ps, 80, USERS))
        .step("STEP02", Utilities.IDCAMS, u.redefineCluster(KsdsTable.USRSEC))
        .step(
            "STEP03",
            Utilities.IDCAMS,
            u.repro(
                "IN",
                ps,
                KsdsTable.USRSEC,
                80,
                rec -> {
                  UserSecurityRecord s = (UserSecurityRecord) rec;
                  return new UserSecurityRecord(
                      s.secUsrId(),
                      s.secUsrFname(),
                      s.secUsrLname(),
                      credentials.encode(s.secUsrPwd()),
                      s.secUsrType(),
                      s.secUsrFiller());
                }))
        .build();
  }

  @Bean
  Job esdsrrdsJob(JobRepository r, PlatformTransactionManager tx, Utilities u) {
    String ps = HLQ + "ESDSRRDS.PS";
    return jcl(r, tx, "ESDSRRDS")
        .step("PREDEL", Utilities.IEFBR14, u.deleteDatasets("DD01", ps))
        .step("STEP01", Utilities.IEBGENER, u.generateInstream("SYSUT2", ps, 80, USERS))
        .step(
            "STEP02", Utilities.IDCAMS, u.redefineCluster(HLQ + "USRSEC.VSAM.ESDS", "usrsec_esds"))
        .step(
            "STEP03",
            Utilities.IDCAMS,
            u.reproUserSecurity("IN", ps, "usrsec_esds", "rba", i -> (long) i * 80))
        .step(
            "STEP04", Utilities.IDCAMS, u.redefineCluster(HLQ + "USRSEC.VSAM.RRDS", "usrsec_rrds"))
        .step(
            "STEP05",
            Utilities.IDCAMS,
            u.reproUserSecurity("IN", ps, "usrsec_rrds", "rrn", i -> i + 1L))
        .build();
  }

  /** DEFCUST repeats the step name STEP05; the DEFINE step is STEP05B (unique step names). */
  @Bean
  Job defcustJob(JobRepository r, PlatformTransactionManager tx, Utilities u) {
    return jcl(r, tx, "DEFCUST")
        .step("STEP05", Utilities.IDCAMS, u.deleteCluster(KsdsTable.CUSTDATA))
        .step("STEP05B", Utilities.IDCAMS, u.defineCluster(KsdsTable.CUSTDATA))
        .build();
  }

  @Bean
  Job reptfileJob(JobRepository r, PlatformTransactionManager tx, Utilities u) {
    return jcl(r, tx, "REPTFILE")
        .step("STEP05", Utilities.IDCAMS, u.defineGdgs(10, false, HLQ + "TRANREPT"))
        .build();
  }

  @Bean
  Job defgdgbJob(JobRepository r, PlatformTransactionManager tx, Utilities u) {
    return jcl(r, tx, "DEFGDGB")
        .step(
            "STEP05",
            Utilities.IDCAMS,
            u.defineGdgs(
                5,
                true,
                HLQ + "TRANSACT.BKUP",
                HLQ + "TRANSACT.DALY",
                HLQ + "TRANREPT",
                HLQ + "TCATBALF.BKUP",
                HLQ + "SYSTRAN",
                HLQ + "TRANSACT.COMBINED"))
        .build();
  }

  @Bean
  Job defgdgdJob(JobRepository r, PlatformTransactionManager tx, Utilities u) {
    return jcl(r, tx, "DEFGDGD")
        .step("STEP10", Utilities.IDCAMS, u.defineGdgs(5, false, HLQ + "TRANTYPE.BKUP"))
        .stepCondZeroNe(
            "STEP20",
            Utilities.IEBGENER,
            u.copyToNewGeneration(HLQ + "TRANTYPE.PS", HLQ + "TRANTYPE.BKUP"))
        .stepCondZeroNe(
            "STEP30", Utilities.IDCAMS, u.defineGdgs(5, false, HLQ + "TRANCATG.PS.BKUP"))
        .stepCondZeroNe(
            "STEP40",
            Utilities.IEBGENER,
            u.copyToNewGeneration(HLQ + "TRANCATG.PS", HLQ + "TRANCATG.PS.BKUP"))
        .step("STEP50", Utilities.IDCAMS, u.defineGdgs(5, false, HLQ + "DISCGRP.BKUP"))
        .stepCondZeroNe(
            "STEP60",
            Utilities.IEBGENER,
            u.copyToNewGeneration(HLQ + "DISCGRP.PS", HLQ + "DISCGRP.BKUP"))
        .build();
  }

  @Bean
  Job readacctJob(JobRepository r, PlatformTransactionManager tx, Utilities u, Programs p) {
    return jcl(r, tx, "READACCT")
        .step(
            "PREDEL",
            Utilities.IEFBR14,
            u.deleteDatasets(
                "OUTFILE", HLQ + "ACCTDATA.PSCOMP",
                "ARRYFILE", HLQ + "ACCTDATA.ARRYPS",
                "VBRCFILE", HLQ + "ACCTDATA.VBPS"))
        .step("STEP05", "CBACT01C", p.cbact01c())
        .build();
  }

  @Bean
  Job readcardJob(JobRepository r, PlatformTransactionManager tx, Programs p) {
    return jcl(r, tx, "READCARD")
        .step(
            "STEP05",
            "CBACT02C",
            p.dump(SequentialDumpProgram.CBACT02C, "CARDFILE", KsdsTable.CARDDATA, 150, 16))
        .build();
  }

  @Bean
  Job readxrefJob(JobRepository r, PlatformTransactionManager tx, Programs p) {
    return jcl(r, tx, "READXREF")
        .step(
            "STEP05",
            "CBACT03C",
            p.dump(SequentialDumpProgram.CBACT03C, "XREFFILE", KsdsTable.CARDXREF, 50, 16))
        .build();
  }

  @Bean
  Job readcustJob(JobRepository r, PlatformTransactionManager tx, Programs p) {
    return jcl(r, tx, "READCUST")
        .step(
            "STEP05",
            "CBCUS01C",
            p.dump(SequentialDumpProgram.CBCUS01C, "CUSTFILE", KsdsTable.CUSTDATA, 500, 9))
        .build();
  }

  @Bean
  Job cbexportJob(JobRepository r, PlatformTransactionManager tx, Utilities u, Programs p) {
    return jcl(r, tx, "CBEXPORT")
        .step("STEP01", Utilities.IDCAMS, u.redefineFile("EXPFILE", Programs.EXPORT_DSN))
        .step("STEP02", "CBEXPORT", p.cbexport())
        .build();
  }

  @Bean
  Job cbimportJob(JobRepository r, PlatformTransactionManager tx, Programs p) {
    return jcl(r, tx, "CBIMPORT").step("STEP01", "CBIMPORT", p.cbimport()).build();
  }
}
