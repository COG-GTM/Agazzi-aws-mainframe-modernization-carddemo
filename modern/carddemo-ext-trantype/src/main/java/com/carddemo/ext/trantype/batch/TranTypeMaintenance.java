package com.carddemo.ext.trantype.batch;

import com.carddemo.ext.trantype.TrantypeEnabled;
import com.carddemo.ext.trantype.store.Pic;
import com.carddemo.ext.trantype.store.TransactionTypeStore;
import com.carddemo.ext.trantype.store.TrantypeSqlException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * COBTUPDT (job MNTTRDB2): applies the INPFILE maintenance records (col 1 = A/U/D/*, cols 2-3 type,
 * cols 4-53 description) to {@code transaction_type}; SYSOUT reproduces the program DISPLAYs.
 */
@Component
@TrantypeEnabled
public class TranTypeMaintenance {

  public static final int RECORD_LENGTH = 53;
  private static final int MSG_LENGTH = 80;

  private final TransactionTypeStore store;

  public TranTypeMaintenance(TransactionTypeStore store) {
    this.store = store;
  }

  /** Runs the job over already opened INPFILE records and returns RETURN-CODE. */
  public int run(List<String> records, Consumer<String> sysout) {
    Run r = new Run(sysout);
    sysout.accept("OPEN FILE OK");
    for (String raw : records) {
      r.treat(Pic.pad(raw, RECORD_LENGTH));
    }
    return r.returnCode;
  }

  /** 0001-OPEN-FILES failure path (INPFILE missing or unreadable). */
  public int openFailed(Consumer<String> sysout) {
    sysout.accept("OPEN FILE NOT OK");
    return 12;
  }

  private final class Run {
    final Consumer<String> sysout;
    final char[] returnMsg = new char[MSG_LENGTH];
    int returnCode;

    Run(Consumer<String> sysout) {
      this.sysout = sysout;
      Arrays.fill(returnMsg, ' ');
    }

    void treat(String rec) {
      sysout.accept("PROCESSING   " + rec);
      String type = rec.substring(1, 3);
      String desc = rec.substring(3, RECORD_LENGTH);
      switch (rec.charAt(0)) {
        case 'A' -> {
          sysout.accept("ADDING RECORD");
          sql(() -> store.insert(type, desc), "RECORD INSERTED SUCCESSFULLY", false);
        }
        case 'U' -> {
          sysout.accept("UPDATING RECORD");
          sql(() -> store.updateDescription(type, desc), "RECORD UPDATED SUCCESSFULLY", true);
        }
        case 'D' -> {
          sysout.accept("DELETING RECORD");
          sql(() -> store.delete(type), "RECORD DELETED SUCCESSFULLY", true);
        }
        case '*' -> sysout.accept("IGNORING COMMENTED LINE");
        default -> abend("ERROR: TYPE NOT VALID");
      }
    }

    private void sql(Runnable statement, String ok, boolean notFoundIsError) {
      try {
        statement.run();
        sysout.accept(ok);
      } catch (TrantypeSqlException e) {
        if (e.sqlcode() == TrantypeSqlException.NOT_FOUND) {
          if (notFoundIsError) {
            abend("No records found.");
          } else {
            sysout.accept(ok);
          }
        } else {
          abend(
              "Error accessing: TRANSACTION_TYPE table. SQLCODE:"
                  + String.format("%5d", e.sqlcode()));
        }
      }
    }

    /** STRING ... INTO WS-RETURN-MSG (never cleared) + 9999-ABEND (DISPLAY, RC 4, continue). */
    private void abend(String text) {
      for (int i = 0; i < text.length() && i < MSG_LENGTH; i++) {
        returnMsg[i] = text.charAt(i);
      }
      sysout.accept(new String(returnMsg));
      returnCode = 4;
    }
  }
}
