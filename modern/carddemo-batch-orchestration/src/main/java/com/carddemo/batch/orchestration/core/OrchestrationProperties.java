package com.carddemo.batch.orchestration.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code carddemo.orchestration.*}: everything the JCL hard-coded (DSNs, SYSIN, hosts). */
@ConfigurationProperties(prefix = "carddemo.orchestration")
public class OrchestrationProperties {

  /** Root directory where sequential datasets (DSN = file name) are materialised. */
  private Path datasetsDir = Path.of("data", "datasets");

  /** WAITSTEP SYSIN default (COBSWAIT centiseconds, PIC 9(8)). */
  private String waitCentiseconds = "00003600";

  private final FullCycle fullCycle = new FullCycle();
  private final Scheduler scheduler = new Scheduler();
  private final Transfer transfer = new Transfer();

  public Path getDatasetsDir() {
    return datasetsDir;
  }

  public void setDatasetsDir(Path datasetsDir) {
    this.datasetsDir = datasetsDir;
  }

  public String getWaitCentiseconds() {
    return waitCentiseconds;
  }

  public void setWaitCentiseconds(String waitCentiseconds) {
    this.waitCentiseconds = waitCentiseconds;
  }

  public FullCycle getFullCycle() {
    return fullCycle;
  }

  public Scheduler getScheduler() {
    return scheduler;
  }

  public Transfer getTransfer() {
    return transfer;
  }

  /**
   * {@code fullBatchCycle}: jobs launched in order, by name, through the JobRegistry. TRANBKP
   * before COMBTRAN as in {@code scripts/run_full_batch.sh}: COMBTRAN reads TRANSACT.BKUP(0).
   */
  public static class FullCycle {
    private List<String> jobs =
        new ArrayList<>(
            List.of(
                "CLOSEFIL",
                "POSTTRAN",
                "INTCALC",
                "TRANBKP",
                "COMBTRAN",
                "TRANREPT",
                "CREASTMT",
                "OPENFIL"));

    public List<String> getJobs() {
      return jobs;
    }

    public void setJobs(List<String> jobs) {
      this.jobs = jobs;
    }
  }

  /** Time triggers replacing the Control-M/CA-7 calendars (disabled by default). */
  public static class Scheduler {
    private boolean enabled;
    private String dailyCron = "0 0 1 * * *";
    private String weeklyCron = "0 0 2 * * SAT";
    private String monthlyCron = "0 0 3 1 * *";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getDailyCron() {
      return dailyCron;
    }

    public void setDailyCron(String dailyCron) {
      this.dailyCron = dailyCron;
    }

    public String getWeeklyCron() {
      return weeklyCron;
    }

    public void setWeeklyCron(String weeklyCron) {
      this.weeklyCron = weeklyCron;
    }

    public String getMonthlyCron() {
      return monthlyCron;
    }

    public void setMonthlyCron(String monthlyCron) {
      this.monthlyCron = monthlyCron;
    }
  }

  /** FTPJCL replacement: {@code local} (default), {@code s3}. No credential is ever a property. */
  public static class Transfer {
    private String type = "local";
    private Path localDir = Path.of("data", "outbound");
    private String remoteDir = "/ftpfolder";
    private String bucket;
    private String region;
    private String endpoint;

    /** Secrets Manager id holding {@code {"accessKeyId":..,"secretAccessKey":..}}; else IAM. */
    private String credentialsSecretId;

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public Path getLocalDir() {
      return localDir;
    }

    public void setLocalDir(Path localDir) {
      this.localDir = localDir;
    }

    public String getRemoteDir() {
      return remoteDir;
    }

    public void setRemoteDir(String remoteDir) {
      this.remoteDir = remoteDir;
    }

    public String getBucket() {
      return bucket;
    }

    public void setBucket(String bucket) {
      this.bucket = bucket;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region = region;
    }

    public String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
    }

    public String getCredentialsSecretId() {
      return credentialsSecretId;
    }

    public void setCredentialsSecretId(String credentialsSecretId) {
      this.credentialsSecretId = credentialsSecretId;
    }
  }
}
