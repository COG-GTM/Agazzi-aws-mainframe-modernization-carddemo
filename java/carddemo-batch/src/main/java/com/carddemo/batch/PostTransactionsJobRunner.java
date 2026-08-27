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

import java.util.List;
import java.util.Map;

@Component
public class PostTransactionsJobRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostTransactionsJobRunner.class);

    private final JobLauncher jobLauncher;
    private final Map<String, Job> jobs;
    private int exitCode;

    public PostTransactionsJobRunner(JobLauncher jobLauncher, Map<String, Job> jobs) {
        this.jobLauncher = jobLauncher;
        this.jobs = jobs;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!shouldRun(args)) {
            return;
        }
        try {
            String jobName = jobName(args);
            Job job = jobs.values().stream()
                    .filter(candidate -> candidate.getName().equals(jobName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown batch job: " + jobName));
            JobParametersBuilder parameters = new JobParametersBuilder()
                    .addLong("run.id", System.nanoTime());
            for (String option : args.getOptionNames()) {
                if (!"job.name".equals(option) && !"run-post-transactions".equals(option)) {
                    List<String> values = args.getOptionValues(option);
                    if (values != null && !values.isEmpty()) {
                        parameters.addString(option, values.get(values.size() - 1));
                    }
                }
            }
            JobExecution execution = jobLauncher.run(job, parameters.toJobParameters());
            String batchExitCode = execution.getExitStatus().getExitCode();
            exitCode = switch (batchExitCode) {
                case "COMPLETED" -> 0;
                case "COMPLETED_WITH_REJECTS" -> 4;
                default -> 1;
            };
        } catch (Exception exception) {
            exitCode = 1;
            LOGGER.error("postTransactionsJob failed", exception);
        }
    }

    private boolean shouldRun(ApplicationArguments args) {
        return args.containsOption("run-post-transactions")
                || args.containsOption("job.name");
    }

    private String jobName(ApplicationArguments args) {
        if (args.containsOption("run-post-transactions")) {
            return "postTransactionsJob";
        }
        return args.getOptionValues("job.name").get(0);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
