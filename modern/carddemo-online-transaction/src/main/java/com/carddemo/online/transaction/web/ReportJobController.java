package com.carddemo.online.transaction.web;

import com.carddemo.contracts.port.ReportJobLauncherPort;
import com.carddemo.contracts.port.ReportJobLauncherPort.ReportJobHandle;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v1/jobs/{executionId}}: status of a TRANREPT job submitted by CR00. */
@RestController
public class ReportJobController {

  private final ReportJobLauncherPort launcher;

  public ReportJobController(ReportJobLauncherPort launcher) {
    this.launcher = launcher;
  }

  @GetMapping("/api/v1/jobs/{executionId}")
  public ResponseEntity<ReportJobHandle> status(@PathVariable("executionId") long executionId) {
    return launcher
        .status(executionId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
