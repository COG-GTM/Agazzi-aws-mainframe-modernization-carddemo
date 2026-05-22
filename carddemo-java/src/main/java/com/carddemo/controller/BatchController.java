package com.carddemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/batch")
@Tag(name = "Batch Jobs", description = "Replaces JCL batch processing (POSTTRAN, INTCALC, CREASTMT)")
public class BatchController {

    private final JobLauncher jobLauncher;
    private final Job transactionPostingJob;
    private final Job interestCalculationJob;

    public BatchController(JobLauncher jobLauncher,
                           @Qualifier("transactionPostingJob") Job transactionPostingJob,
                           @Qualifier("interestCalculationJob") Job interestCalculationJob) {
        this.jobLauncher = jobLauncher;
        this.transactionPostingJob = transactionPostingJob;
        this.interestCalculationJob = interestCalculationJob;
    }

    @PostMapping("/post-transactions")
    @Operation(summary = "Post daily transactions", description = "Replaces POSTTRAN.jcl / CBTRN02C.cbl")
    public ResponseEntity<Map<String, Object>> postTransactions() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        var execution = jobLauncher.run(transactionPostingJob, params);
        return ResponseEntity.ok(Map.of(
                "jobId", execution.getJobId(),
                "status", execution.getStatus().toString(),
                "startTime", execution.getStartTime() != null ? execution.getStartTime().toString() : ""
        ));
    }

    @PostMapping("/calculate-interest")
    @Operation(summary = "Calculate interest", description = "Replaces INTCALC.jcl / CBACT04C.cbl")
    public ResponseEntity<Map<String, Object>> calculateInterest() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        var execution = jobLauncher.run(interestCalculationJob, params);
        return ResponseEntity.ok(Map.of(
                "jobId", execution.getJobId(),
                "status", execution.getStatus().toString(),
                "startTime", execution.getStartTime() != null ? execution.getStartTime().toString() : ""
        ));
    }
}
