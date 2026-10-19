package com.carddemo.ext.mqinquiry;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST twins of CDRD/CDRA ({@code /api/v1/ext/mqinquiry}); absent (404) when the flag is off. */
@RestController
@RequestMapping("/api/v1/ext/mqinquiry")
@ConditionalOnProperty(
    prefix = "carddemo.extensions.mqinquiry",
    name = "enabled",
    havingValue = "true")
public class MqInquiryController {

  private final SystemDateInquiry systemDate;
  private final AccountInquiry account;

  public MqInquiryController(SystemDateInquiry systemDate, AccountInquiry account) {
    this.systemDate = systemDate;
    this.account = account;
  }

  @GetMapping("/system-date")
  public MqInquiryReply systemDate() {
    return new MqInquiryReply(systemDate.process("").payload(), UUID.randomUUID().toString());
  }

  @GetMapping("/accounts/{acctId}")
  public ResponseEntity<MqInquiryReply> account(@PathVariable String acctId) {
    if (!acctId.matches("[0-9]{11}")) {
      return ResponseEntity.badRequest().build();
    }
    InquiryOutcome outcome = account.process(AccountInquiry.FUNCTION_INQUIRY + acctId);
    if (!outcome.reply()) {
      return ResponseEntity.internalServerError().build();
    }
    MqInquiryReply body = new MqInquiryReply(outcome.payload(), UUID.randomUUID().toString());
    if (outcome.payload().startsWith("INVALID REQUEST PARAMETERS")) {
      return ResponseEntity.status(404).body(body);
    }
    return ResponseEntity.ok(body);
  }
}
