package com.carddemo.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

@Component
public class PostTransactionsJobRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostTransactionsJobRunner.class);

    private final JobLauncher jobLauncher;
    private final Job postTransactionsJob;
    private int exitCode;

    public PostTransactionsJobRunner(JobLauncher jobLauncher, Job postTransactionsJob) {
        this.jobLauncher = jobLauncher;
        this.postTransactionsJob = postTransactionsJob;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!shouldRun(args)) {
            return;
        }
        try {
            JobExecution execution = jobLauncher.run(
                    postTransactionsJob,
                    new JobParametersBuilder()
                            .addLong("run.id", System.nanoTime())
                            .toJobParameters());
            exitCode = "FAILED".equals(execution.getExitStatus().getExitCode()) ? 4 : 0;
        } catch (Exception exception) {
            exitCode = 1;
            LOGGER.error("postTransactionsJob failed", exception);
        }
    }

    private boolean shouldRun(ApplicationArguments args) {
        return args.containsOption("run-post-transactions")
                || (args.containsOption("job.name")
                && args.getOptionValues("job.name").contains(postTransactionsJob.getName()));
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
