package com.carddemo.batch.orchestration.scheduler;

import com.carddemo.batch.orchestration.scheduler.Ca7Network.Node;
import java.util.List;

/**
 * {@code app/scheduler/CardDemo.ca7}: the 28 trigger edges, grouped in the three networks the
 * listing describes, each opening and closing the CICS files.
 */
public final class Ca7Definitions {

  public static final String POSTING = "CA7-POSTING";
  public static final String REFRESH = "CA7-REFRESH";
  public static final String STATEMENTS = "CA7-STATEMENTS";

  private Ca7Definitions() {}

  public static List<Ca7Network> all() {
    return List.of(posting(), refresh(), statements());
  }

  private static Node alias(String id, String ca7Name, String jcl, String schid, String... by) {
    return new Node(id, ca7Name, jcl, schid, List.of(by));
  }

  private static Node n(String id, String schid, String... by) {
    String name = id.contains("#") ? id.substring(0, id.indexOf('#')) : id;
    return alias(id, name, name, schid, by);
  }

  /** CLOSEFIL → CBPAUP0J → POSTTRAN → WAITSTEP → OPENFIL (SCHID 030). */
  public static Ca7Network posting() {
    return new Ca7Network(
        POSTING,
        List.of(
            n("CLOSEFIL", "030"),
            n("CBPAUP0J", "030", "CLOSEFIL"),
            n("POSTTRAN", "030", "CBPAUP0J"),
            n("WAITSTEP", "030", "POSTTRAN"),
            n("OPENFIL", "030", "WAITSTEP")));
  }

  /**
   * CLOSEFIL → TRANTYPE → WAITSTEP → (CLOSEFIL1 → TRANCATG → WAITSTEP [031] ∥ CLOSEFIL2 → TCATBALF
   * → WAITSTEP [032]) → CLOSEFIL → READACCT → READCARD → READCUST → READXREF → WAITSTEP → OPENFIL.
   */
  public static Ca7Network refresh() {
    return new Ca7Network(
        REFRESH,
        List.of(
            n("CLOSEFIL", "030"),
            n("TRANTYPE", "030", "CLOSEFIL"),
            n("WAITSTEP", "030", "TRANTYPE"),
            alias("CLOSEFIL1", "CLOSEFIL1", "CLOSEFIL", "031", "WAITSTEP"),
            alias("CLOSEFIL2", "CLOSEFIL2", "CLOSEFIL", "032", "WAITSTEP"),
            n("TRANCATG", "031", "CLOSEFIL1"),
            n("TCATBALF", "032", "CLOSEFIL2"),
            n("WAITSTEP#031", "031", "TRANCATG"),
            n("WAITSTEP#032", "032", "TCATBALF"),
            n("CLOSEFIL#2", "030", "WAITSTEP#031", "WAITSTEP#032"),
            n("READACCT", "030", "CLOSEFIL#2"),
            n("READCARD", "030", "READACCT"),
            n("READCUST", "030", "READCARD"),
            n("READXREF", "030", "READCUST"),
            n("WAITSTEP#3", "030", "READXREF"),
            n("OPENFIL", "030", "WAITSTEP#3")));
  }

  /**
   * CLOSEFIL → CREASTMT → TXT2PDF1 → WAITSTEP → OPENFIL → CLOSEFIL → PRTCATBL → WAITSTEP → OPENFIL.
   */
  public static Ca7Network statements() {
    return new Ca7Network(
        STATEMENTS,
        List.of(
            n("CLOSEFIL", "030"),
            n("CREASTMT", "030", "CLOSEFIL"),
            n("TXT2PDF1", "030", "CREASTMT"),
            n("WAITSTEP", "030", "TXT2PDF1"),
            n("OPENFIL", "030", "WAITSTEP"),
            n("CLOSEFIL#031", "031", "OPENFIL"),
            n("PRTCATBL", "031", "CLOSEFIL#031"),
            n("WAITSTEP#031", "031", "PRTCATBL"),
            n("OPENFIL#031", "031", "WAITSTEP#031")));
  }
}
