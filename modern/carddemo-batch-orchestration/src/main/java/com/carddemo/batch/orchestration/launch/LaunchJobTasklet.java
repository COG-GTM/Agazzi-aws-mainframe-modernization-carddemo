package com.carddemo.batch.orchestration.launch;

import com.carddemo.batch.orchestration.core.JclReturnCodes;
import com.carddemo.batch.orchestration.core.JclStepFailure;
import com.carddemo.batch.orchestration.core.StepWork;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;

/**
 * Scheduler job slot: runs the named job synchronously and fails (→ downstream OUTCOND not set)
 * when it does not end OK. On parent restart the failed child instance is restarted.
 */
public class LaunchJobTasklet implements StepWork {

  private final RegisteredJobs jobs;
  private final JobLauncher launcher;
  private final String jobName;

  public LaunchJobTasklet(RegisteredJobs jobs, JobLauncher launcher, String jobName) {
    this.jobs = jobs;
    this.launcher = launcher;
    this.jobName = jobName;
  }

  @Override
  public void run(StepExecution step) throws Exception {
    Job job =
        jobs.find(jobName)
            .orElseThrow(
                () ->
                    new JclStepFailure(
                        JclReturnCodes.NOJOB, "job " + jobName + " not registered (JCL error)"));
    String slot = step.getJobExecution().getJobInstance().getJobName() + "/" + step.getStepName();
    JobParameters params = ChildJobParameters.of(step.getJobParameters(), slot);
    JobExecution child;
    try {
      child = launcher.run(job, params);
    } catch (JobInstanceAlreadyCompleteException done) {
      step.getExecutionContext().putString("child.status", "ALREADY-COMPLETE");
      return;
    }
    int rc = JclReturnCodes.of(child);
    step.getExecutionContext().putLong("child.executionId", child.getId());
    step.getExecutionContext().putInt("child.rc", rc);
    if (child.getStatus() != BatchStatus.COMPLETED) {
      String code = child.getExitStatus().getExitCode();
      throw new JclStepFailure(
          code.startsWith("ABEND") || code.equals(JclReturnCodes.NOJOB)
              ? code
              : JclReturnCodes.rcExitCode(rc),
          jobName + " ended " + child.getStatus() + " " + code);
    }
  }
}
