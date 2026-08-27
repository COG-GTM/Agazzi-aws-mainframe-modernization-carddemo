package com.carddemo.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

public class PostTransactionsJobListener implements JobExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostTransactionsJobListener.class);

    @Override
    public void afterJob(JobExecution jobExecution) {
        long transactionCount = jobExecution.getStepExecutions().stream()
                .mapToLong(step -> step.getReadCount())
                .sum();
        int rejectCount = jobExecution.getStepExecutions().stream()
                .mapToInt(step -> step.getExecutionContext().getInt("rejectCount", 0))
                .sum();

        LOGGER.info("TRANSACTIONS PROCESSED : {}", transactionCount);
        LOGGER.info("TRANSACTIONS REJECTED  : {}", rejectCount);
        if (rejectCount > 0 && jobExecution.getStatus() == BatchStatus.COMPLETED) {
            jobExecution.setExitStatus(new ExitStatus(
                    "FAILED",
                    "CBTRN02C return code 4: " + rejectCount + " rejected transactions"));
        }
    }
}
