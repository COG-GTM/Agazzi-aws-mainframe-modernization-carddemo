package com.carddemo.copybook.gen;

import java.util.List;
import java.util.Map;

/** What the generator produces: one entry per legacy source (ADR-004 proposal, plan §11). */
public final class GenerationSpec {

  private GenerationSpec() {}

  /**
   * A legacy source and the package receiving its records.
   *
   * @param source path relative to {@code app/}
   * @param id copybook/program id used in {@code RecordLayout.copybook()}
   * @param pkg sub-package of {@code com.carddemo.copybook.generated}
   * @param programRecords for programs: the 01 names to extract (empty for copybooks)
   * @param names Java name overrides by 01 name
   */
  public record Target(
      String source,
      String id,
      String pkg,
      List<String> programRecords,
      Map<String, String> names) {
    static Target copybook(String source, String pkg) {
      String file = source.substring(source.lastIndexOf('/') + 1);
      return new Target(source, file.substring(0, file.indexOf('.')), pkg, List.of(), Map.of());
    }

    Target named(String record, String javaName) {
      Map<String, String> m = new java.util.TreeMap<>(names);
      m.put(record, javaName);
      return new Target(source, id, pkg, programRecords, Map.copyOf(m));
    }
  }

  /**
   * A sealed family: an interface over several records chosen by a discriminator.
   *
   * @param id source id of the members
   * @param pkg sub-package
   * @param interfaceName Java interface name
   * @param baseRecord 01 name whose {@code REDEFINES} define the variants ({@code null} when the
   *     members are distinct 01 records discriminated by length)
   * @param baseItem the redefined item (e.g. {@code EXPORT-RECORD-DATA})
   * @param discriminator discriminator item (e.g. {@code EXPORT-REC-TYPE}), {@code null} = length
   * @param members variants in declaration order
   * @param fallback Java name of the base-layout variant (raw redefined area), or {@code null}
   */
  public record Family(
      String id,
      String pkg,
      String interfaceName,
      String baseRecord,
      String baseItem,
      String discriminator,
      List<Member> members,
      String fallback) {}

  /**
   * Member of a family.
   *
   * @param key discriminator value (or record length)
   * @param item redefining item (redefines families) or 01 name (length families)
   * @param javaName Java record name
   */
  public record Member(String key, String item, String javaName) {}

  public static final List<Target> TARGETS =
      List.of(
          Target.copybook("cpy/COCOM01Y.cpy", "core"),
          Target.copybook("cpy/COTTL01Y.cpy", "core"),
          Target.copybook("cpy/CSMSG01Y.cpy", "core"),
          Target.copybook("cpy/CSMSG02Y.cpy", "core"),
          Target.copybook("cpy/CSSETATY.cpy", "core"),
          Target.copybook("cpy/CSSTRPFY.cpy", "core"),
          Target.copybook("cpy/CSUSR01Y.cpy", "core"),
          Target.copybook("cpy/CSUTLDPY.cpy", "core"),
          Target.copybook("cpy/CSUTLDWY.cpy", "core").named("CSUTLDWY", "DateEditWorkArea"),
          Target.copybook("cpy/CUSTREC.cpy", "core"),
          Target.copybook("cpy/CVACT01Y.cpy", "core"),
          Target.copybook("cpy/CVACT02Y.cpy", "core"),
          Target.copybook("cpy/CVACT03Y.cpy", "core"),
          Target.copybook("cpy/CVCRD01Y.cpy", "core"),
          Target.copybook("cpy/CVCUS01Y.cpy", "core"),
          Target.copybook("cpy/CVTRA01Y.cpy", "core"),
          Target.copybook("cpy/CVTRA02Y.cpy", "core"),
          Target.copybook("cpy/CVTRA03Y.cpy", "core"),
          Target.copybook("cpy/CVTRA04Y.cpy", "core"),
          Target.copybook("cpy/CVTRA05Y.cpy", "core"),
          Target.copybook("cpy/CVTRA06Y.cpy", "core"),
          Target.copybook("cpy/CVTRA07Y.cpy", "core"),
          Target.copybook("cpy/UNUSED1Y.cpy", "core"),
          new Target(
              "cbl/CBACT01C.cbl",
              "CBACT01C",
              "readacct",
              List.of("OUT-ACCT-REC", "ARR-ARRAY-REC"),
              Map.of("OUT-ACCT-REC", "PscompRecord", "ARR-ARRAY-REC", "ArrypsRecord")),
          Target.copybook("app-authorization-ims-db2-mq/cpy/CIPAUSMY.cpy", "ims")
              .named("CIPAUSMY", "PendingAuthSummary"),
          Target.copybook("app-authorization-ims-db2-mq/cpy/CIPAUDTY.cpy", "ims")
              .named("CIPAUDTY", "PendingAuthDetails"),
          Target.copybook("app-authorization-ims-db2-mq/cpy/CCPAUERY.cpy", "ims")
              .named("CCPAUERY", "AuthErrorLog"),
          Target.copybook("app-authorization-ims-db2-mq/cpy/CCPAURLY.cpy", "ims")
              .named("CCPAURLY", "AuthResponse"),
          Target.copybook("app-authorization-ims-db2-mq/cpy/CCPAURQY.cpy", "ims")
              .named("CCPAURQY", "AuthRequest"),
          Target.copybook("app-authorization-ims-db2-mq/cpy/IMSFUNCS.cpy", "ims"),
          Target.copybook("app-authorization-ims-db2-mq/cpy/PADFLPCB.CPY", "ims"),
          Target.copybook("app-authorization-ims-db2-mq/cpy/PASFLPCB.CPY", "ims"),
          Target.copybook("app-authorization-ims-db2-mq/cpy/PAUTBPCB.CPY", "ims"),
          Target.copybook("app-authorization-ims-db2-mq/dcl/AUTHFRDS.dcl", "ims"),
          Target.copybook("app-transaction-type-db2/cpy/CSDB2RPY.cpy", "db2"),
          Target.copybook("app-transaction-type-db2/cpy/CSDB2RWY.cpy", "db2")
              .named("CSDB2RWY", "Db2WorkArea"),
          Target.copybook("app-transaction-type-db2/dcl/DCLTRCAT.dcl", "db2"),
          Target.copybook("app-transaction-type-db2/dcl/DCLTRTYP.dcl", "db2"));

  public static final List<Family> FAMILIES =
      List.of(
          new Family(
              "CVEXPORT",
              "export",
              "ExportRecord",
              "EXPORT-RECORD",
              "EXPORT-RECORD-DATA",
              "EXPORT-REC-TYPE",
              List.of(
                  new Member("C", "EXPORT-CUSTOMER-DATA", "CustomerExport"),
                  new Member("A", "EXPORT-ACCOUNT-DATA", "AccountExport"),
                  new Member("X", "EXPORT-CARD-XREF-DATA", "CardXrefExport"),
                  new Member("T", "EXPORT-TRANSACTION-DATA", "TransactionExport"),
                  new Member("D", "EXPORT-CARD-DATA", "CardExport")),
              "RawExport"),
          new Family(
              "CBACT01C",
              "readacct",
              "VbpsRecord",
              null,
              null,
              null,
              List.of(
                  new Member("12", "VBRC-REC1", "VbrcRec1"),
                  new Member("39", "VBRC-REC2", "VbrcRec2")),
              null));

  /** Source of every family, relative to {@code app/}. */
  public static final Map<String, String> FAMILY_SOURCES =
      Map.of("CVEXPORT", "cpy/CVEXPORT.cpy", "CBACT01C", "cbl/CBACT01C.cbl");
}
