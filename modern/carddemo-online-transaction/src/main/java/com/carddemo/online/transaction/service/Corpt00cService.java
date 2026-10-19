package com.carddemo.online.transaction.service;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.contracts.port.ReportJobLauncherPort;
import com.carddemo.contracts.port.ReportJobLauncherPort.ReportJobHandle;
import com.carddemo.contracts.port.ReportJobLauncherPort.ReportRequest;
import com.carddemo.contracts.port.ReportJobLauncherPort.ReportType;
import com.carddemo.contracts.screen.Action;
import com.carddemo.contracts.screen.ConversationContext;
import com.carddemo.contracts.screen.ConversationContext.ProgramContext;
import com.carddemo.contracts.screen.ScreenId;
import com.carddemo.contracts.screen.map.Corpt00Screen;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * CR00 CORPT00C: submit the TRANREPT report (monthly, yearly or custom period). The JCL written to
 * TDQ JOBS (internal reader) becomes {@link ReportJobLauncherPort#launch}. SEND-TRNRPT-SCREEN ends
 * with GO TO RETURN-TO-CICS, so the first message sent wins.
 */
@Service
public class Corpt00cService {

  static final String TRANID = "CR00";
  static final String PGM = "CORPT00C";

  /** Date fields defined ATTRB=NUM in CORPT00.bms. */
  public static final Set<String> NUM_FIELDS =
      Set.of("SDTMM", "SDTDD", "SDTYYYY", "EDTMM", "EDTDD", "EDTYYYY");

  private static final String[] INPUTS = {
    "MONTHLY", "YEARLY", "CUSTOM", "SDTMM", "SDTDD", "SDTYYYY", "EDTMM", "EDTDD", "EDTYYYY",
    "CONFIRM"
  };

  private final ReportJobLauncherPort launcher;
  private final BusinessClock clock;

  public Corpt00cService(ReportJobLauncherPort launcher, BusinessClock clock) {
    this.launcher = launcher;
    this.clock = clock;
  }

  public ProgramResult<Corpt00Screen.Output> run(
      ConversationContext ctx, CommareaExtension ext, Action aid, Map<String, String> input) {
    MapBuffer map = new MapBuffer(ScreenId.CORPT00, Corpt00Screen.Output.class, clock, true);
    Long[] job = {null};
    // CORPT00C MAIN-PARA
    try {
      if (ctx.programContext() != ProgramContext.REENTER) {
        ctx = ctx.reenter();
        map.lowValues();
        map.cursor("MONTHLY");
        map.send();
      } else {
        map.receive(input);
        switch (aid) {
          case ENTER -> processEnterKey(map, ctx, job);
          case PF3 -> {
            ConversationContext next = Navigation.xctl(ctx, TRANID, PGM, "COMEN01C");
            return new ProgramResult<>(
                MapBuffer.xctl(ScreenId.CORPT00, ScreenId.COMEN01, next),
                Cotrn02cService.withAid(ext, aid));
          }
          default -> {
            map.error(MapBuffer.MSG_INVALID_KEY, "MONTHLY");
            map.send();
          }
        }
      }
    } catch (MapBuffer.ReturnToCics end) {
      // GO TO RETURN-TO-CICS
    }
    return new ProgramResult<>(
        map.response(Corpt00Screen.Output.class, ctx), Cotrn01cService.noAid(ext), job[0]);
  }

  // CORPT00C PROCESS-ENTER-KEY
  private void processEnterKey(MapBuffer map, ConversationContext ctx, Long[] job) {
    String reportName;
    if (!CobolFmt.blank(map.get("MONTHLY"))) {
      reportName = "Monthly";
      LocalDate today = clock.today();
      LocalDate start = today.withDayOfMonth(1);
      LocalDate end = today.withDayOfMonth(today.lengthOfMonth());
      submitJobToIntrdr(map, ctx, job, reportName, ReportType.MONTHLY, start, end);
    } else if (!CobolFmt.blank(map.get("YEARLY"))) {
      reportName = "Yearly";
      int year = clock.today().getYear();
      submitJobToIntrdr(
          map,
          ctx,
          job,
          reportName,
          ReportType.YEARLY,
          LocalDate.of(year, 1, 1),
          LocalDate.of(year, 12, 31));
    } else if (!CobolFmt.blank(map.get("CUSTOM"))) {
      String[][] required = {
        {"SDTMM", "Start Date - Month can NOT be empty..."},
        {"SDTDD", "Start Date - Day can NOT be empty..."},
        {"SDTYYYY", "Start Date - Year can NOT be empty..."},
        {"EDTMM", "End Date - Month can NOT be empty..."},
        {"EDTDD", "End Date - Day can NOT be empty..."},
        {"EDTYYYY", "End Date - Year can NOT be empty..."}
      };
      for (String[] r : required) {
        if (CobolFmt.blank(map.get(r[0]))) {
          map.error(r[1], r[0]);
          map.send();
        }
      }
      for (String f : new String[] {"SDTMM", "SDTDD", "EDTMM", "EDTDD"}) {
        map.set(f, CobolFmt.numvalToDigits(map.get(f), 2));
      }
      map.set("SDTYYYY", CobolFmt.numvalToDigits(map.get("SDTYYYY"), 4));
      map.set("EDTYYYY", CobolFmt.numvalToDigits(map.get("EDTYYYY"), 4));
      checkPart(map, "SDTMM", "12", "Start Date - Not a valid Month...");
      checkPart(map, "SDTDD", "31", "Start Date - Not a valid Day...");
      checkPart(map, "SDTYYYY", null, "Start Date - Not a valid Year...");
      checkPart(map, "EDTMM", "12", "End Date - Not a valid Month...");
      checkPart(map, "EDTDD", "31", "End Date - Not a valid Day...");
      checkPart(map, "EDTYYYY", null, "End Date - Not a valid Year...");
      String start = map.get("SDTYYYY") + "-" + map.get("SDTMM") + "-" + map.get("SDTDD");
      String end = map.get("EDTYYYY") + "-" + map.get("EDTMM") + "-" + map.get("EDTDD");
      if (!CeeDays.accepted(start)) {
        map.error("Start Date - Not a valid date...", "SDTMM");
        map.send();
      }
      if (!CeeDays.accepted(end)) {
        map.error("End Date - Not a valid date...", "EDTMM");
        map.send();
      }
      reportName = "Custom";
      submitJobToIntrdr(
          map,
          ctx,
          job,
          reportName,
          ReportType.CUSTOM,
          LocalDate.parse(start),
          LocalDate.parse(end));
    } else {
      map.error("Select a report type to print report...", "MONTHLY");
      map.send();
      return;
    }
    if (!map.err()) {
      initializeAllFields(map);
      map.green();
      map.message(reportName + " report submitted for printing ...");
      map.cursor("MONTHLY");
      map.send();
    }
  }

  private static void checkPart(MapBuffer map, String field, String max, String message) {
    String v = map.get(field);
    if (!CobolFmt.isNumeric(v) || (max != null && v.compareTo(max) > 0)) {
      map.error(message, field);
      map.send();
    }
  }

  // CORPT00C SUBMIT-JOB-TO-INTRDR
  private void submitJobToIntrdr(
      MapBuffer map,
      ConversationContext ctx,
      Long[] job,
      String reportName,
      ReportType type,
      LocalDate start,
      LocalDate end) {
    String confirm = map.get("CONFIRM");
    if (CobolFmt.blank(confirm)) {
      map.error("Please confirm to print the " + reportName + " report...", "CONFIRM");
      map.send();
    }
    switch (confirm) {
      case "Y", "y" -> {
        // WIRTE-JOBSUB-TDQ: JCL to the internal reader -> ReportJobLauncherPort
        try {
          ReportJobHandle h = launcher.launch(new ReportRequest(type, start, end, ctx.userId()));
          job[0] = h.executionId();
        } catch (RuntimeException e) {
          map.error("Unable to Write TDQ (JOBS)...", "MONTHLY");
          map.send();
        }
      }
      case "N", "n" -> {
        initializeAllFields(map);
        map.err(true);
        map.send();
      }
      default -> {
        map.error("\"" + confirm.strip() + "\" is not a valid value to confirm...", "CONFIRM");
        map.send();
      }
    }
  }

  // CORPT00C INITIALIZE-ALL-FIELDS
  private static void initializeAllFields(MapBuffer map) {
    map.cursor("MONTHLY");
    map.spaces(INPUTS);
    map.message("");
  }
}
