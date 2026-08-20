# CardDemo 12-Factor Modernization Plan

Planning document. No implementation changes are proposed here — every item below is an option to
be selected, sequenced, and approved before any code moves.

Scope: this repository (`COG-GTM/Agazzi-aws-mainframe-modernization-carddemo`) at the commit this
document was added on. All counts and file references were measured from the working tree.

Reference for the target model: <https://12factor.net/> (Wiggins, last updated 2017).

---

## 1. Scope and Legacy Architecture Audit

### 1.1 What the system is

A credit card management system with two faces: a 3270 online application under CICS, and a nightly
batch chain under JCL. Base function is account/card/customer/transaction maintenance plus billing
and reporting; three optional modules add Db2, IMS DB, and IBM MQ.

### 1.2 Measured inventory

| Artifact class | Location | Files | Lines |
| :--- | :--- | ---: | ---: |
| COBOL programs (base) | `app/cbl` | 31 | 20,650 |
| Copybooks | `app/cpy` | 30 | 2,786 |
| JCL (base) | `app/jcl` | 38 | 2,429 |
| BMS map source | `app/bms` | 17 | 4,472 |
| BMS copybooks | `app/cpy-bms` | 18 | 5,632 |
| Assembler | `app/asm` | 2 | 114 |
| Procs | `app/proc` | 2 | 114 |
| CICS CSD definitions | `app/csd` | 2 | 505 |
| Catalog listing | `app/catlg` | 1 | 3,956 |
| Sample data (EBCDIC + ASCII) | `app/data` | 23 | — |
| Scheduler definitions | `app/scheduler` | 2 | 662 |
| Optional modules (Db2 / IMS+MQ / VSAM+MQ) | `app/app-*` | 71 | — |

Largest programs, which are also the decomposition risk concentration:

| Program | Lines | Function |
| :--- | ---: | :--- |
| `COACTUPC.cbl` | 4,236 | Account update (screen + validation + I/O in one module) |
| `COCRDUPC.cbl` | 1,560 | Card update |
| `COCRDLIC.cbl` | 1,459 | Card list with browse/paging |
| `COACTVWC.cbl` | 941 | Account view |
| `COCRDSLC.cbl` | 887 | Card view |
| `CBTRN02C.cbl` | 731 | POSTTRAN — daily transaction posting engine |
| `CBACT04C.cbl` | 652 | INTCALC — interest accrual |

### 1.3 Technology map

- **Language/runtime**: COBOL (Enterprise COBOL dialect; `scripts/local_compile.sh` uses GnuCOBOL
  `--std=ibm-strict` off-host), plus two Assembler utilities (`MVSWAIT` timer, `COBDATFT` date
  conversion).
- **Online**: CICS pseudo-conversational. 31 `EXEC CICS SEND`, 17 `RECEIVE`, 16 `XCTL`, 20 `READ`,
  browse via `STARTBR`/`READNEXT`/`READPREV`/`ENDBR`. Screens are 3270 BMS maps.
- **Data**: VSAM KSDS with alternate indexes as the system of record (8 files defined in
  `app/csd`: `ACCTDAT`, `CARDDAT`, `CARDAIX`, `CCXREF`, `CXACAIX`, `CUSTDAT`, `TRANSACT`,
  `USRSEC`); GDG generations for history; ESDS/RRDS and PDS in the optional paths; Db2 for
  transaction-type reference data (32 `EXEC SQL` statements across the optional modules); IMS DB
  for authorization data. Records are EBCDIC with `COMP-3`/zoned decimal fields and
  `REDEFINES`/`OCCURS DEPENDING ON` copybook structures.
- **Messaging**: IBM MQ, triggered. Input queue name arrives from the trigger message
  (`MQTM-QNAME`); reply and error queue names are program working-storage fields.
- **Security**: RACF at the platform level, but application sign-on is a COBOL string compare
  against the `USRSEC` VSAM file — `COSGN00C.cbl:223` is `IF SEC-USR-PWD = WS-USER-PWD`. The
  shipped sample data contains plaintext credentials (`app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS`
  first record: user `ADMIN001`, password `PASSWORD`).
- **Scheduling**: Control-M (`app/scheduler/CardDemo.controlm`, job chains expressed as
  `INCOND`/`OUTCOND` pairs) and CA7 (`CardDemo.ca7`).
- **Build/deploy**: compile JCL and procs under `samples/jcl` and `samples/proc`
  (`BUILDONL.prc`, `BUILDBAT.prc`, `BUILDBMS.prc`, `BLDCIDB2.prc`), driven from a workstation by
  `scripts/remote_compile.sh` (requires an FTP tunnel on port 2121 and a local `make`),
  `scripts/upld_module.sh`, `scripts/remote_refresh.sh` (CICS `NEWCOPY`). Version stamping is a
  shell script that rewrites a comment line into the source
  (`scripts/git-addSrcVersionInfo.sh`, `app_version=v2.0` hardcoded in the script).
- **Rehost/refactor artifacts already present**: `samples/m2/mf/CardDemo_runtime.zip` and
  `samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip`.
- **CI/CD**: none in the repository (no `.github/`, no pipeline definition of any kind).
- **Observability**: `DISPLAY` to SYSOUT — 371 statements, concentrated in the batch programs
  (`CBTRN02C` 53, `CBACT04C` 49, `CBTRN03C` 46, `CBACT01C` 43, `CBTRN01C` 42). Online writes to a
  CICS transient data queue `CSSL`. No metrics, no traces, no correlation IDs.

### 1.4 Deployment environment

Single z/OS image (or emulated equivalent). One CICS region serves the online workload; batch runs
against the same VSAM datasets, which is why the chain is bracketed by `CLOSEFIL` and `OPENFIL` —
the files are closed to CICS for the duration of the batch window. Datasets are addressed by a
literal high-level qualifier: `AWS.M2` appears 226 times across the JCL, and only one job
(`app/jcl/CBADMCDJ.jcl`) uses JCL `SET` symbolics. The Db2 subsystem `DAZ1` and `PLAN(CARDDEMO)`
are likewise literal in JCL.

### 1.5 Constraints to carry into every option below

These are the constraints this repository evidences. Constraints that belong to the real production
system this demo stands in for (contractual SLAs, PCI scope boundaries, data residency, audit
retention) must be supplied by the business before the roadmap in section 5 is committed to.

| Constraint | Evidence in repo | Consequence for modernization |
| :--- | :--- | :--- |
| Online must be down during batch | `CLOSEFIL`/`OPENFIL` bracket the batch chain (README "Running Batch Jobs") | Any target that promises 24×7 online changes the operating model, not just the code |
| Card and customer PII in flight | `CVCUS01Y`, `CVACT02Y` copybooks; 16-digit PAN in `COCOM01Y` (`CDEMO-CARD-NUM PIC 9(16)`) | PCI DSS scope; PAN in the inter-program state area must not become a log line or a URL |
| Binary numeric fidelity | `COMP-3`, zoned decimal, signed/unsigned fields | Money arithmetic must be validated bit-for-bit against the legacy, not "close enough" |
| Batch ordering is externally defined | Control-M `INCOND`/`OUTCOND` chains | Scheduler semantics migrate as a first-class artifact, not as an afterthought |
| Credentials are application-managed today | `COSGN00C.cbl:223`, plaintext `USRSEC` data | Identity is a replacement, not a port — cannot be lifted as-is |

---

## 2. The Twelve Factors, and the Legacy Pattern Each One Names

Summarized from <https://12factor.net/> with the mainframe anti-pattern this codebase exhibits.

| # | Factor | Modern statement | Legacy anti-pattern present here |
| :-- | :--- | :--- | :--- |
| I | Codebase | One codebase in revision control, many deploys | One repo, but deploys are hand-assembled load libraries; version is a comment injected by a script |
| II | Dependencies | Declare and isolate explicitly | `COPY` resolved from a SYSLIB search order; no manifest, no version pinning |
| III | Config | Store config in the environment | Dataset names, subsystem IDs, and plan names are literals in JCL and source |
| IV | Backing services | Attached resources, swappable by config | Files bound by DD name and CSD entry; Db2 by subsystem; queues by trigger definition |
| V | Build, release, run | Strictly separated stages | Compile JCL writes into the live load library; `NEWCOPY` is the release |
| VI | Processes | Stateless, share-nothing | State lives in the CICS COMMAREA between program invocations |
| VII | Port binding | Export the service by binding a port | Service is exported as a 3270 transaction ID; no network contract |
| VIII | Concurrency | Scale out via the process model | Scale is a CICS region and MVS initiator classes; batch is single-stream |
| IX | Disposability | Fast start, graceful shutdown | Long-lived region; batch restart is manual with GDG generation juggling |
| X | Dev/prod parity | Keep environments similar | Local GnuCOBOL vs host Enterprise COBOL; one shared HLQ |
| XI | Logs | Treat logs as event streams | 371 `DISPLAY` statements to SYSOUT; `CSSL` transient data queue |
| XII | Admin processes | One-off processes in the same environment | 38 JCL jobs, several of which are the only way to fix or reload data |

---

## 3. Gap Analysis Matrix

Compliance scale: **None** (no property of the factor present), **Partial** (property present but
by convention or accident, not by contract), **Aligned** (already satisfies the intent).

| # | Factor | Current state | Compliance | Blockers | Already aligned |
| :-- | :--- | :--- | :--- | :--- | :--- |
| I | Codebase | Single Git repo holds COBOL, JCL, BMS, CSD, data, and scheduler definitions. But the deployed unit is a load library assembled by `remote_compile.sh` + `upld_module.sh` per module, and identity is a comment line stamped by `git-addSrcVersionInfo.sh` with `app_version` hardcoded. | Partial | Per-module upload means the running system is not identifiable by a commit; no deploy is reproducible from a SHA | Source, screens, resource definitions, and scheduler definitions are all versioned together — an unusually good starting point |
| II | Dependencies | 30 base copybooks (+ 18 BMS copybooks) resolved by compiler search path; Assembler utilities and CICS/Db2/MQ stubs are link-edited or platform-supplied; no manifest exists. | None | Copybook change impact is invisible; `REDEFINES`/`ODO` structures make substitution risky; no dependency graph to test against | Copybooks are at least centralized in one directory rather than duplicated per program |
| III | Config | `AWS.M2` literal 226 times in JCL; `DSN SYSTEM(DAZ1)`, `PLAN(CARDDEMO)`, `PLAN(DSNTIAUL)` literal; only `CBADMCDJ.jcl` uses `SET`. Credentials in a data file. | None | Promoting between environments means editing 200+ literals; no separation between code and environment; secrets are data | The single `SET`-parameterized job proves the pattern is available on the platform |
| IV | Backing services | COBOL binds files by DD name (`SELECT ACCTFILE-FILE ASSIGN TO ACCTFILE`, and the same shape for `CARDFILE`, `XREFFILE`, `TCATBALF`, `DISCGRP`); CICS binds the same files via 8 CSD `DEFINE FILE` entries; MQ input queue name is taken from the trigger message. | Partial | A file is not an attachable resource — swapping it means changing JCL, CSD, or both, and stopping the region | Indirection through DD name and CSD name is a real seam; MQ input queue already arrives as runtime data, not a literal |
| V | Build, release, run | Compile procs (`BUILDONL`, `BUILDBAT`, `BUILDBMS`, `BLDCIDB2`) write load modules; `remote_refresh.sh` issues `CEMT SET PROG(x) NEWCOPY`. No CI, no artifact repository, no immutable release. | None | `NEWCOPY` mutates a running region — build and run are the same act; rollback is "recompile the old source" | Build logic is scripted and in the repo rather than living only in a developer's terminal |
| VI | Processes | `COCOM01Y` (`CARDDEMO-COMMAREA`) carries `CDEMO-FROM/TO-TRANID`, `CDEMO-USER-ID`, `CDEMO-USER-TYPE`, `CDEMO-PGM-CONTEXT`, plus customer, account, and card identifiers including `CDEMO-CARD-NUM PIC 9(16)`, across every `XCTL`. | Partial | Session state is a memory area owned by the region, so an instance is not interchangeable; PAN travels in that state | The pseudo-conversational design already means no program holds a terminal across a user think-time — the hard half of statelessness is done |
| VII | Port binding | Entry is transaction `CC00` → `COSGN00C`; 24 online transactions total, all 3270. Screens are 17 BMS maps with matching copybooks. | None | No callable interface at all — nothing can consume CardDemo except a terminal emulator | Transaction-per-function granularity maps cleanly onto endpoints once a protocol exists |
| VIII | Concurrency | Concurrency is CICS MAXTASK plus initiator classes. Batch is sequential; `POSTTRAN`/`INTCALC` read masters serially. | None | Throughput ceiling is the region and the batch window; no partitioning key in the batch design | Batch/online split is already a workload separation, which is the seam scale-out needs |
| IX | Disposability | CICS region is long-lived; batch failure recovery is rerun-from-step with GDG generations; `WAITSTEP`/`MVSWAIT` inserts fixed waits. | None | No idempotency guarantee on `POSTTRAN`; a partial run leaves masters mid-update; fixed waits encode timing assumptions | GDG generations do give a restore point per run, which is a usable basis for idempotent reruns |
| X | Dev/prod parity | `scripts/local_compile.sh` compiles with GnuCOBOL `--std=ibm-strict`; the host builds with Enterprise COBOL under `samples/proc`. `remote_compile.sh` depends on a manually started FTP tunnel (`ps f \| grep -c "2121:"`). One HLQ. | None | Two compilers means two behaviors; a developer cannot reproduce a production defect locally; environment promotion is a rename | ASCII copies of the sample data exist alongside EBCDIC (`app/data/ASCII`), which is the seed of a portable local fixture set |
| XI | Logs | 371 `DISPLAY` statements to SYSOUT; online writes to `CSSL` transient data queue. No structure, no severity, no correlation ID, no metrics or traces. | None | Every diagnostic is a JES spool hunt; nothing is queryable; no way to correlate an online failure with the batch run that caused it | The `DISPLAY` sites are already placed at the events worth logging — the semantics survive even if the mechanism changes |
| XII | Admin processes | 38 JCL jobs, chained in Control-M/CA7. Several are the only path to a state change: `DUSRSECJ` (load users), `ACCTFILE`/`CARDFILE`/`CUSTFILE`/`XREFFILE` (reload masters), `TRANIDX` (define AIX), `CLOSEFIL`/`OPENFIL` (file availability). | Partial | Admin tasks are authored in a different language than the app and run under a different security and logging model; some are destructive with no dry run | Admin tasks are versioned in the repo and run against the same data as the app — the factor's core intent |

Aggregate: 0 of 12 aligned, 5 partial, 7 none. The partials (I, IV, VI, XII) are where the
cheapest progress is, because a seam already exists.

---

## 4. Technology Options and Migration Paths

Three strategy families are in play. They are not exclusive — the roadmap in section 5 assumes a
mix, which is the normal outcome.

| Strategy | What it means here | 12-factor reach | Cost/risk profile |
| :--- | :--- | :--- | :--- |
| **Replatform / rehost** | Run the same COBOL + CICS + VSAM on distributed infrastructure. Artifacts already exist in this repo: `samples/m2/mf/CardDemo_runtime.zip`, `samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip`. | Reaches V, X, XI, XII and partially III/IV. Cannot reach VII, VIII, or VI's state question. | Lowest behavioral risk (same source), lowest ceiling. Licensing and runtime-vendor lock-in are the trade. |
| **Refactor / transform** | Automated COBOL→Java (or equivalent) transformation, then evolve the output. AWS Transform's mainframe agent targets exactly this codebase; the AWS prescriptive-guidance pattern uses CardDemo as its worked example ([pattern](https://docs.aws.amazon.com/prescriptive-guidance/latest/patterns/modernize-mainframe-app-transform-terraform.html), [announcement](https://aws.amazon.com/blogs/migration-and-modernization/accelerate-mainframe-modernization-with-aws-transform-a-comprehensive-refactor-approach/)). | Can reach all twelve, because the output is an ordinary application in a mainstream runtime. | Medium-high. Transformed code needs equivalence proof; generated structure may inherit the monolith shape (a 4,236-line program becomes a 4,236-line class unless decomposed deliberately). |
| **Replace / rebuild selectively** | Rewrite specific capabilities against a modern stack, strangling them out of the legacy. Best fits the two capabilities that are worst in place: sign-on/identity, and reporting. | Full compliance for what is rebuilt. | Highest per-unit cost, but bounded, and the only honest answer for the auth path. |

A complete rewrite is not proposed and should not be assumed anywhere in this plan; the audit above
shows 20,650 lines of base COBOL whose numeric behavior is the product.

### Per-factor options

| Factor | Option A (lowest lift) | Option B (target state) | Trade-off to decide |
| :--- | :--- | :--- | :--- |
| I Codebase | Tag releases and derive the version stamp from `git describe` instead of the hardcoded `app_version` in `git-addSrcVersionInfo.sh` | Build one versioned artifact per commit; deploy that artifact, never a file | A: hours, keeps per-module upload. B: requires V to be solved first |
| II Dependencies | Generate a copybook→program dependency graph and commit it; fail a build when a copybook changes without its consumers rebuilt | Package the app with a real dependency manager in the target runtime (Maven/Gradle after transform) | A works today on the host. B only exists post-transform |
| III Config | Replace the 226 literal HLQ occurrences with JCL `SET`/symbolic parameters, following the existing `CBADMCDJ.jcl` pattern; move `DAZ1`/`PLAN` to symbolics | Environment variables (12-factor III) plus a secrets manager (AWS Secrets Manager, HashiCorp Vault) for anything credential-shaped | A is mechanical and reversible, and removes the promotion edit. B needs the target runtime |
| IV Backing services | Keep DD/CSD indirection but make every name environment-derived (falls out of III) | Repository/DAO interfaces over the data stores; VSAM KSDS → RDS/Aurora PostgreSQL or DynamoDB depending on access pattern; Db2 → RDS; IMS → relational or document | Access patterns differ per file: `TRANSACT` with AIX browse suits a relational index; `USRSEC` should not be migrated at all, it should be replaced by an IdP |
| V Build/release/run | Wrap the existing compile JCL in a pipeline that compiles into a staging library and promotes on approval, so `NEWCOPY` stops being the build's side effect | CI on every commit, immutable artifacts in a registry, deploy = pointing a runtime at a version | A removes the "compile into production" hazard without changing the platform |
| VI Processes | Move PAN out of the COMMAREA, carry a surrogate token instead | Externalize session state — Redis/ElastiCache or DynamoDB — so any instance can serve any request | The COMMAREA is 12 fields; the change is small and it also reduces PCI surface, which is why it is worth doing early |
| VII Port binding | Front the existing transactions with an API layer (CICS web services or the M2/UniKix service enablement path) without touching business logic | Business logic behind HTTP handlers in the target runtime; BMS maps retired in favor of a web/mobile client | A is how you get a consumable API before the transform finishes; it also creates the test harness for the transform |
| VIII Concurrency | Partition the batch by account-ID range so `POSTTRAN` can run N streams | Horizontal scale-out of stateless service instances (containers/ECS/EKS/Lambda) plus a parallelizable batch runtime | Partitioning requires proving no cross-account dependency in `CBTRN02C` |
| IX Disposability | Make `POSTTRAN` and `INTCALC` idempotent per run-date so a rerun after partial failure is safe; replace `WAITSTEP` fixed waits with condition checks | Fast-starting stateless processes; graceful shutdown; scheduler retries instead of manual reruns | Idempotency is a business-rules question (is a double-posted transaction detectable?) and must be answered before automation |
| X Dev/prod parity | Standardize one compiler for all environments, or make the GnuCOBOL path a lint-only gate that cannot certify behavior | Containerized dev environment (Docker) + infrastructure as code (Terraform, per the AWS pattern above) so every environment is built from the same declaration | Keeping two compilers is defensible only if the second is explicitly not a correctness authority |
| XI Logs | Convert the 371 `DISPLAY` sites to a single logging subprogram emitting one structured line (timestamp, severity, program, run-id, message) to stdout/SYSOUT | Ship stdout to a centralized platform — CloudWatch, ELK, Datadog — add metrics and traces with a correlation ID spanning online and batch | The wrapper is a mechanical change with immediate operational payoff and no behavioral risk; it is the highest ROI item in this table |
| XII Admin processes | Inventory the 38 jobs; label each as routine / recovery / destructive; add dry-run and confirmation to the destructive reloads | Admin tasks as one-off invocations of the same artifact in the same environment (`app admin reload-accounts`), scheduled by the platform | Control-M/CA7 chain semantics must be preserved or deliberately re-expressed; the `INCOND`/`OUTCOND` graph is the specification |

### Selection notes

- **Data store per file, not one decision for all.** `ACCTDAT`/`CUSTDAT`/`CARDDAT` are keyed
  lookups with an update path — relational. `TRANSACT` is append-heavy with AIX browse — relational
  with a secondary index, or a time-partitioned store. GDG history is archival — object storage
  (S3). `USRSEC` is not a data-store decision, it is an identity decision.
- **The auth path must not be ported.** A COBOL equality test against a plaintext field
  (`COSGN00C.cbl:223`) has no modern equivalent worth building. This is the one component where
  replace beats refactor unambiguously.
- **Numeric equivalence tooling is a prerequisite, not a phase.** `COMP-3` and zoned-decimal
  arithmetic is where transformation defects hide. Whatever strategy is chosen needs a harness that
  replays real input through both implementations and diffs outputs to the cent before anything is
  cut over.

---

## 5. Phased Roadmap

Effort is expressed in Devin sessions, where one session is a bounded, reviewable unit of work.
Dependencies are hard unless marked otherwise.

### Phase 0 — Discovery and baseline (prerequisite for everything)

| Step | Output | Depends on | Effort |
| :--- | :--- | :--- | ---: |
| 0.1 Static call/data graph: program → program (`XCTL`), program → file (DD/CSD), program → copybook | Machine-readable dependency graph committed to the repo | — | 1–2 |
| 0.2 Golden-data capture: real inputs and outputs for `POSTTRAN`, `INTCALC`, `CREASTMT` | Regression corpus with expected outputs to the cent | 0.1 | 1–2 |
| 0.3 Equivalence harness that replays the corpus through two implementations and diffs | The gate every later phase must pass | 0.2 | 2 |
| 0.4 Business constraint capture: SLAs, PCI scope boundary, retention, residency | Signed constraint list replacing section 1.5's placeholders | — | non-Devin |

Exit criterion: no phase past 0 starts until 0.3 can fail. A harness that has never caught a
difference has not been validated.

### Phase 1 — Foundational refactors (no architecture change, no platform change)

| Step | Factors | Depends on | Effort |
| :--- | :--- | :--- | ---: |
| 1.1 Structured logging subprogram; convert all 371 `DISPLAY` sites; add a run-correlation ID | XI | 0.3 | 2–3 |
| 1.2 Parameterize the 226 HLQ literals plus `DAZ1`/`PLAN` via JCL symbolics | III, IV | 0.1 | 1–2 |
| 1.3 Remove PAN from `COCOM01Y`; carry a surrogate identifier | VI, and PCI surface | 0.1, 0.3 | 1–2 |
| 1.4 Version stamp from `git describe`; tag releases | I | — | <1 |
| 1.5 Classify the 38 admin jobs; add dry-run to destructive reloads | XII | 0.1 | 1–2 |

Phase 1 is deliberately all-legacy-platform. It buys operational ROI (queryable logs, one-touch
environment promotion, reduced cardholder-data surface) with no runtime migration risk, and it
produces the observability needed to debug later phases.

### Phase 2 — Build/release discipline and parity

| Step | Factors | Depends on | Effort |
| :--- | :--- | :--- | ---: |
| 2.1 CI pipeline: compile all COBOL/BMS on every commit, run the 0.3 harness | V, X | 0.3 | 2–3 |
| 2.2 Compile into a staging load library; promote on approval; `NEWCOPY` becomes a deploy step, not a build side effect | V | 2.1 | 1–2 |
| 2.3 Decide the compiler question — one compiler everywhere, or GnuCOBOL demoted to lint-only | X | 2.1 | <1 + decision |
| 2.4 Containerized local dev environment with the ASCII fixtures in `app/data/ASCII` | X | 2.3 | 2 |

### Phase 3 — Service enablement (the strangler seam)

| Step | Factors | Depends on | Effort |
| :--- | :--- | :--- | ---: |
| 3.1 API facade over read-only transactions first (`CAVW` account view, `CCLI`/`CCDL` card list/view, `CT00`/`CT01` transaction list/view) | VII | Phase 2 | 3–4 |
| 3.2 Extend the facade to write transactions (`CAUP`, `CCUP`, `CT02`, `CB00`) behind feature flags | VII, IV | 3.1 | 3–4 |
| 3.3 Replace sign-on with an external IdP; retire the `USRSEC` compare and the plaintext file | VI, III | 3.1 | 2–3 |

3.1 is the highest-leverage step in the plan: it produces a consumable interface, and that
interface is also the black-box test surface for Phase 4.

### Phase 4 — Transformation and decomposition

| Step | Factors | Depends on | Effort |
| :--- | :--- | :--- | ---: |
| 4.1 Pilot transform of one bounded slice (`COACTVWC` account view, 941 lines, read-only) and prove equivalence via 0.3 + 3.1 | II, VI, VII | Phase 3 | 3–4 |
| 4.2 Transform the batch engines (`CBTRN02C`, `CBACT04C`) with the golden corpus as the gate | IX | 4.1 | 4–6 |
| 4.3 Decompose `COACTUPC` (4,236 lines) deliberately rather than accepting a 1:1 translation | II, VI | 4.1 | 4–6 |
| 4.4 Data migration: VSAM → relational per section 4's per-file selection, with dual-write or CDC during cutover | IV | 4.1 | 6–10 |

### Phase 5 — Cloud-native operations

| Step | Factors | Depends on | Effort |
| :--- | :--- | :--- | ---: |
| 5.1 Infrastructure as code for the target environment (Terraform, per the AWS pattern) | X | Phase 4 | 3–4 |
| 5.2 Externalize session state; run stateless instances behind a load balancer | VI, VIII | 4.1, 5.1 | 2–3 |
| 5.3 Partition batch by account range; parallel streams | VIII | 4.2 | 2–3 |
| 5.4 Idempotent reruns; retries in the scheduler; retire `WAITSTEP`'s fixed waits | IX | 5.3 | 2–3 |
| 5.5 Centralized logs, metrics, traces; SLOs; alerting | XI | 1.1, 5.1 | 2–3 |
| 5.6 Re-express the Control-M/CA7 `INCOND`/`OUTCOND` graph in the target orchestrator | XII | 5.3 | 2–3 |

### Milestones

| Milestone | Definition of done |
| :--- | :--- |
| M1 — Baseline provable | 0.3 harness catches an injected arithmetic defect in `CBACT04C` |
| M2 — Operable legacy | Every log line queryable with a run ID; environment promotion needs zero source edits |
| M3 — Reproducible builds | Any commit builds to a deployable artifact; no compile writes to a live library |
| M4 — Consumable service | All read-only functions available over an API, no terminal required |
| M5 — First slice modern | One transformed capability serving production traffic, legacy path still available |
| M6 — Cloud-native | Batch and online both horizontally scalable; scheduler and observability off the mainframe |

---

## 6. Risk Register

Probability and impact are Low/Medium/High. "Owner" is the role that must decide, not the role that
does the work.

| ID | Phase | Risk | P | I | Mitigation | Owner |
| :--- | :--- | :--- | :-- | :-- | :--- | :--- |
| R1 | 0 | Golden corpus does not cover the paths that matter; harness passes while behavior differs | H | H | Coverage measured against the 0.1 call graph, not by feel; deliberately inject defects to prove the harness fails | Eng lead |
| R2 | All | `COMP-3`/zoned-decimal rounding differences produce cent-level drift | H | H | Bit-level comparison in 0.3; no cutover without a clean diff on a full production-volume replay | Eng lead + Finance |
| R3 | 1.3 | Removing PAN from the COMMAREA breaks a consumer that reads it positionally | M | M | 0.1 graph identifies every reader; change all readers in one commit; harness covers each screen flow | Eng lead |
| R4 | 1.2 | A missed HLQ literal points a job at the wrong environment's data | M | H | Automated check that fails the build on any remaining literal HLQ; run first against a copy of prod data | Eng lead |
| R5 | 2.2 | Promotion to the live load library while a transaction is in flight | M | H | Deploy only inside a defined window until the platform supports drain; `NEWCOPY` as an explicit, logged step | Ops |
| R6 | 3.2 | API write path double-applies a transaction already applied by the terminal path | M | H | Feature flags default off; idempotency key on every write; shadow mode before enabling | Eng lead |
| R7 | 3.3 | IdP cutover locks out operators during an incident | M | H | Break-glass local account with audited use; parallel run of both sign-on paths before retirement | Security |
| R8 | 4.x | Transformed output inherits the monolith shape — a 4,236-line program becomes an equally coupled class | H | M | Decomposition is an explicit step (4.3), budgeted and reviewed, not a hoped-for side effect | Architect |
| R9 | 4.4 | Data migration loses or reorders history; GDG generations have no relational equivalent | M | H | Dual-write with reconciliation counts; archive GDG contents to object storage before conversion; read-only cutover rehearsal | Data owner |
| R10 | 4.4 | EBCDIC→UTF-8 conversion corrupts packed or binary fields treated as text | M | H | Field-level conversion driven by the copybooks, never a whole-file `iconv`; checksum per numeric column | Eng lead |
| R11 | 5.3 | Batch partitioning assumes account independence that `CBTRN02C` does not guarantee | M | H | Prove independence from the 0.1 data graph before partitioning; run partitioned and serial in parallel and diff | Eng lead |
| R12 | 5.x | Batch window shrinks below the time the chain needs; online availability promise slips | M | M | Measure per-job runtime from the first CI run onward; treat window headroom as an SLO | Ops |
| R13 | All | COBOL expertise is concentrated in a few people; they are also the reviewers | H | M | Every phase produces a documented artifact (graph, corpus, decomposition rationale); pair reviews; no undocumented tribal decision | Eng manager |
| R14 | All | Modernization work and feature delivery contend for the same code | H | M | Phase 1 changes are mechanical and reviewable in small commits; freeze windows negotiated per phase, not globally | Eng manager |
| R15 | 3.x, 4.x | Feature-flagged dual paths become permanent, doubling maintenance | M | M | Every flag ships with a removal date and an owner; flag inventory reviewed at each milestone | Eng lead |

Rollout techniques assumed throughout: shadow/dark reads before any write path is enabled, feature
flags on every dual-path step, blue/green or canary at 5.1–5.2, and a documented rollback for each
milestone (Phase 1–2 rollbacks are `git revert` + recompile; Phase 4 onward the legacy path stays
warm until the milestone after cutover).

---

## 7. Reference Material

| Source | Relevance | Link |
| :--- | :--- | :--- |
| The Twelve-Factor App | The target model this plan measures against | <https://12factor.net/> |
| AWS Prescriptive Guidance: modernize a mainframe app with AWS Transform and Terraform | Uses this exact CardDemo codebase as its worked example; COBOL→Java plus IaC deployment | <https://docs.aws.amazon.com/prescriptive-guidance/latest/patterns/modernize-mainframe-app-transform-terraform.html> |
| AWS: comprehensive refactor approach with AWS Transform | Describes the automated analysis/refactor path for COBOL, JCL, CICS, Db2, VSAM | <https://aws.amazon.com/blogs/migration-and-modernization/accelerate-mainframe-modernization-with-aws-transform-a-comprehensive-refactor-approach/> |
| `aws-samples/aws-mainframe-modernization-carddemo` | Upstream of this repo; the reference implementation other migration tooling is tested against | <https://github.com/aws-samples/aws-mainframe-modernization-carddemo/> |
| `samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip`, `samples/m2/mf/CardDemo_runtime.zip` | Replatform artifacts already in this repository — evidence the rehost path has been exercised on this code | in-repo |

Case studies of comparable migrations were not verified for this document. Any that get cited to a
customer must be sourced first; nothing in section 5's effort estimates depends on them.

---

## 8. Open Questions Blocking Commitment

1. What are the real availability and batch-window SLAs? Section 1.5 records the constraint the
   repo evidences, not the one the business signed.
2. Is a double-posted transaction detectable after the fact? R11 and step 5.4 both depend on the
   answer.
3. What is the PCI scope boundary in the target architecture? It determines whether 1.3 is
   sufficient or only a start.
4. Which strategy family (section 4) is the intended destination? The roadmap is written so that
   Phases 0–3 are valuable under all three, but Phase 4 onward assumes refactor.
5. Who owns the Control-M/CA7 job graph, and can it be re-expressed, or must it be preserved
   verbatim?
