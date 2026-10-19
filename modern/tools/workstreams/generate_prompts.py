#!/usr/bin/env python3
"""Generate the 17 workstream prompts (plan §14) from ownership.yaml + inventory.json + contracts-v1.

Outputs: docs/workstreams/<ws-id>.md, docs/workstreams/index.json, MIGRATION_PROGRESS.md.
Run from anywhere: python3 modern/tools/workstreams/generate_prompts.py
"""
import json
import re
import sys
from pathlib import Path

import yaml

MODERN = Path(__file__).resolve().parents[2]
REPO = MODERN.parent
sys.path.insert(0, str(MODERN / "tools/inventory"))
from check_ownership import owners_by_file  # noqa: E402

REPO_SLUG = "COG-GTM/Agazzi-aws-mainframe-modernization-carddemo"
BASE = "migration/phase-1-4"
G = "carddemo-parity/src/test/resources/golden"

COMMON_PORTS = ["RecordCodec<T>", "BusinessClock", "GenerationStore", "WaitService"]
BATCH_DOD = [
    "Ogni job assegnato ha un `Job` Spring Batch eseguibile da CLI (`--spring.batch.job.name=<JCL>`), step 1:1 con gli step JCL, `JobParameters` al posto di `PARM`.",
    "Golden test file-in/file-out verdi su tutti gli output del job (file, scarti, report) e delta DB verificato (`RecordComparator`, `DbSnapshot`).",
    "Test di restart (dallo step fallito) e di input vuoto/malformato; abend → exit status mappati.",
    "Copertura JaCoCo ≥ 80% sul modulo; ArchUnit verde; nessun file toccato fuori dal perimetro.",
]
ONLINE_DOD = [
    "Una risorsa REST per mappa BMS (`GET` primo invio, `POST /api/v1/screens/<mappa>` con `action` + campi) conforme a `openapi.yaml` (test di contratto); risposta sempre `ScreenResponse<T>`.",
    "Logica dei paragrafi COBOL in `@Service` con le stesse validazioni, nello stesso ordine e con gli stessi testi dei messaggi; `ConversationContext` in sessione.",
    "Scenari YAML golden verdi: happy path, ogni messaggio di errore del programma COBOL, navigazione PF3/PF7/PF8/ENTER.",
    "Test di autorizzazione (utente vs admin, dati di altri utenti) con lo stesso comportamento del COBOL; correzioni di sicurezza registrate in `PARITY_EXCEPTIONS.md` come `proposed` (sezione PARITY-EXCEPTION della PR).",
    "Concorrenza: test di aggiornamento simultaneo (optimistic lock `@Version`) dove il programma fa REWRITE.",
    "Copertura JaCoCo ≥ 80% sul modulo; ArchUnit verde.",
]

WS = {
    "1a-copybook": dict(
        title="Copybook → record Java (codec completo)", module="carddemo-copybook", flyway=None,
        phase="Fase 1",
        work="Parser copybook → AST (JRecord/cb2xml o codegen dedicato, decidi e aggiorna l'ADR-004 nella PR come proposta) e generatore deterministico che produce un `record` Java + `RecordLayout` (offset/lunghezza/PIC) per ogni 01-level. Supporto DISPLAY zoned + overpunch, `COMP`/`BINARY` big-endian 2/4/8, `COMP-3`, `OCCURS`/`OCCURS DEPENDING ON`, `REDEFINES` → `sealed interface` (es. `ExportRecord permits CustomerExport, AccountExport, ...` discriminato da `EXPORT-REC-TYPE`), 88-level → enum/boolean, record VB (RDW), EBCDIC CP037 ↔ UTF-8. Sostituisci gli interni di `ZonedRecordCodec`/`CoreCodecs` senza cambiarne l'API pubblica.",
        dtos="tutti i record di `com.carddemo.contracts.record` (annotazioni `@CobolRecord`/`@CobolField`)",
        ports=["RecordCodec<T>"], tables=[],
        dod=["Round-trip byte-identico (decode → encode) su ogni record di `app/data/ASCII` e `app/data/EBCDIC` (incluso `EXPORT.DATA.PS` e `IMSDATA.DBPAUTP0.dat`).",
             "Property test su COMP-3/COMP con segno e limiti; codegen deterministico (rigenerazione = zero diff).",
             "Record generati per CVEXPORT, CSUTLDWY/PY, formati `ACCTDATA.PSCOMP`/`ARRYPS`/`VBPS` di CBACT01C e copybook IMS/DB2 delle estensioni.",
             "`GoldenMasterIntegrityTest` e `CoreCodecsRoundTripTest` restano verdi."],
        goldens=["tutti i golden batch esistenti (`GoldenMasterIntegrityTest` li decodifica con i tuoi codec)"]),
    "1b-datamigration": dict(
        title="Schema relazionale e migrazione dati", module="carddemo-datamigration", flyway="V100–V199",
        phase="Fase 1",
        work="Pipeline Spring Batch `vsamToRelational` (PS/export VSAM EBCDIC o ASCII → decodifica → validazione chiavi duplicate/FK orfane/date invalide → upsert idempotente per PK → report di riconciliazione) e job inverso `relationalToVsam` (rollback e confronti di parità). Indici/vincoli finali, tabelle ESDS/RRDS, migrazione password USRSEC → BCrypt (`{bcrypt}`) come migrazione separata e reversibile. CLI `migrate-data` (`java -jar … --job=vsamToRelational --source=ebcdic`).",
        dtos="record core di `com.carddemo.contracts.record` + entity/repository di `com.carddemo.domain`",
        ports=["RecordCodec<T>", "BusinessClock"], tables=["tutte le tabelle di V1"],
        dod=["Conteggi e checksum (`ACCT-CURR-BAL`, `TRAN-AMT`, `TRAN-CAT-BAL`, hash per colonna) identici fra sorgente e DB per tutti i dataset; `reconciliation.md` generato.",
             "`relationalToVsam` riproduce i file `app/data/ASCII` byte-identici; riesecuzione idempotente.",
             "Test Testcontainers PostgreSQL 16 (profilo `it`)."],
        goldens=["`app/data/ASCII/*` (round-trip DB)"]),
    "1c-platform": dict(
        title="Utility Assembler e date → Java", module="carddemo-platform", flyway=None, phase="Fase 1",
        work="`CobolDateFormatter` (conversioni COBDATFT, input/output type), `CeeDays` (validazione CSUTLDTC con stessi codici esito/messaggi), `WaitService` di produzione (MVSWAIT/COBSWAIT: centisecondi da `PARM`, virtual thread / step non bloccante), `BusinessClock` di produzione (ASKTIME/FORMATTIME). Le classi Fase 0 (`FixedBusinessClock`, `SystemBusinessClock`, `SleepingWaitService`, `CobolMath`, `FileSystemGenerationStore`, `InMemoryMaintenanceMode`, `InMemoryMessageChannel`) possono essere estese/sostituite internamente senza cambiare le interfacce in `carddemo-contracts`.",
        dtos="—", ports=["BusinessClock", "WaitService", "GenerationStore", "MaintenanceModePort"], tables=[],
        dod=["Test tabellari derivati dalla logica assembler e COBOL (formati, date limite, anni bisestili, input invalidi).",
             "Parità con gli output GnuCOBOL di CSUTLDTC e di CBACT01C (che chiama COBDATFT) nel golden harness (`tools/golden/run-cobol.sh`)."],
        goldens=["READACCT (CBACT01C → COBDATFT)", "nuovo golden CSUTLDTC (cattura con `tools/golden/run-cobol.sh`, fixture sotto il tuo perimetro golden)"]),
    "b1-posting": dict(
        title="Posting giornaliero transazioni", module="carddemo-batch-posting", flyway="V200–V299", phase="Fase 2",
        work="Job POSTTRAN (CBTRN02C), CBTRN01C, DALYREJS, TRANBKP, COMBTRAN, TRANIDX. Validazioni CBTRN02C con gli stessi codici e testi di scarto in DALYREJS (100 `INVALID CARD NUMBER FOUND`, 101 `ACCOUNT RECORD NOT FOUND`, 102 `OVERLIMIT TRANSACTION`, 103 `TRANSACTION RECEIVED AFTER ACCT EXPIRATION`); aggiornamento `TCATBALF` e saldi account; COMBTRAN = merge ordinato SYSTRAN + backup.",
        dtos="`DailyTransactionRecord`, `TransactionRecord`, `AccountRecord`, `CardXrefRecord`, `TranCatBalanceRecord`",
        ports=COMMON_PORTS, tables=["daily_transaction", "transaction", "account", "card_xref", "tran_cat_balance"],
        dod=BATCH_DOD, goldens=["POSTTRAN", "COMBTRAN"]),
    "b2-interest": dict(
        title="Interessi e saldi per categoria", module="carddemo-batch-interest", flyway="V300–V399", phase="Fase 2",
        work="Job INTCALC (CBACT04C), TCATBALF, PRTCATBL, DISCGRP. Formula `(TRAN-CAT-BAL * DIS-INT-RATE) / 1200` con troncamento COBOL (`CobolMath`); fallback al gruppo `DEFAULT`; transazioni di interesse in SYSTRAN con ID generati identici (data parametro + sequenza).",
        dtos="`TranCatBalanceRecord`, `DisclosureGroupRecord`, `AccountRecord`, `CardXrefRecord`, `TransactionRecord`",
        ports=COMMON_PORTS, tables=["tran_cat_balance", "disclosure_group", "account", "card_xref", "transaction"],
        dod=BATCH_DOD, goldens=["INTCALC"]),
    "b3-reports": dict(
        title="Report e statement", module="carddemo-batch-reports", flyway="V400–V499", phase="Fase 2",
        work="TRANREPT (SORT + CBTRN03C + PROC REPROC/TRANREPT), CREASTMT (CBSTM03A/B), TXT2PDF1. Report a 133 colonne byte-identico (intestazioni, totali pagina/conto/generale); statement testo + HTML identici; PDF da testo con OpenPDF (confronto sul testo estratto). Implementa `ReportJobLauncherPort` (usata da O4/CR00).",
        dtos="`TransactionRecord`, `CardXrefRecord`, `TransactionTypeRecord`, `TransactionCategoryRecord`, `StatementCustomerRecord`, `StatementTransactionRecord`",
        ports=COMMON_PORTS + ["ReportJobLauncherPort"],
        tables=["transaction", "card_xref", "transaction_type", "transaction_category", "customer", "account"],
        dod=BATCH_DOD + ["`ReportJobLauncherPort` implementato come bean Spring (sostituisce `UnavailableReportJobLauncher` di Fase 0 grazie a `@ConditionalOnMissingBean`)."],
        goldens=["TRANREPT", "CREASTMT"]),
    "b4-datalifecycle": dict(
        title="Data lifecycle: caricamento, export/import, utility", module="carddemo-batch-datalifecycle", flyway="V500–V599", phase="Fase 2",
        work="ACCTFILE, CARDFILE, XREFFILE, CUSTFILE, TRANFILE, TRANTYPE, TRANCATG, DUSRSECJ, DEFCUST, REPTFILE, ESDSRRDS, DEFGDGB/D, READACCT/CARD/XREF/CUST (CBACT01C–03C, CBCUS01C), CBEXPORT, CBIMPORT. Riusa (via porte) i job di 1B per il refresh; CBEXPORT/CBIMPORT sul tipo sealed `ExportRecord` di 1A (usa il codec minimo finché 1A non mergia); READACCT produce i 3 formati PSCOMP, ARRYPS, VBPS identici; GDG via `GenerationStore`.",
        dtos="record core di `com.carddemo.contracts.record`", ports=COMMON_PORTS,
        tables=["account", "card", "card_xref", "customer", "transaction", "transaction_type", "transaction_category", "app_user", "usrsec_esds", "usrsec_rrds"],
        dod=BATCH_DOD, goldens=["READACCT", "nuovi golden READCARD/READXREF/READCUST/CBEXPORT/CBIMPORT (adattamenti GnuCOBOL solo nel harness)"]),
    "b5-orchestration": dict(
        title="Orchestrazione batch e scheduler", module="carddemo-batch-orchestration", flyway="V600–V699", phase="Fase 2",
        work="Flussi Control-M DAILY/WEEKLY/MONTHLY e CA-7 → `Flow` Spring Batch con le stesse condizioni IN/OUT; CLOSEFIL/OPENFIL → `MaintenanceModePort`; WAITSTEP (COBSWAIT) → `WaitService`; INTRDRJ1/2 (submit asincrono) → `JobLauncher`; FTPJCL → SFTP/S3 (credenziali da secret manager, mai nel codice); `scripts/run_full_batch.sh` → job `fullBatchCycle` usato in Fase 5. I job degli altri workstream si invocano per nome (`JobRegistry`), mai per dipendenza Maven.",
        dtos="—", ports=COMMON_PORTS + ["MaintenanceModePort", "ReportJobLauncherPort"], tables=["BATCH_* (Spring Batch, V3)"],
        dod=BATCH_DOD + ["`fullBatchCycle` esegue la catena POSTTRAN → INTCALC → COMBTRAN → TRANREPT → CREASTMT nell'ordine dello scheduler."],
        goldens=["catena completa (POSTTRAN, INTCALC, COMBTRAN, TRANREPT, CREASTMT) come smoke di orchestrazione"]),
    "o1-auth": dict(
        title="Sign-on, menu, sicurezza", module="carddemo-online-auth", flyway="V1000–V1099", phase="Fase 3",
        work="CC00/CM00/CA00 (COSGN00C, COMEN01C, COADM01C). Spring Security su `app_user` via `UserCredentialService` (sostituisce `LegacyUserCredentialService` di Fase 0 e la `SecurityFilterChain` di `Fase0SecurityAutoConfiguration`, entrambe `@ConditionalOnMissingBean`); routing utente/admin; menu da `COMEN02Y`/`COADM02Y` (opzioni delle estensioni visibili solo se `carddemo.extensions.<nome>.enabled=true`); logout = PF3; sessione secondo ADR-002. Rimuovi `@Tag(\"pending-o1\")` da `CC00SignOnScenariosIT` e `status: pending-o1` dagli scenari CC00 quando sono verdi (sono nel tuo perimetro).",
        dtos="`Cosgn00Screen`, `Comen01Screen`, `Coadm01Screen`, `ConversationContext`, `ScreenResponse<T>`",
        ports=["UserCredentialService", "BusinessClock"], tables=["app_user"], dod=ONLINE_DOD,
        goldens=["online/CC00 (8 scenari già scritti)", "nuovi online/CM00, online/CA00"]),
    "o2-account": dict(
        title="Conto: visualizzazione e aggiornamento", module="carddemo-online-account", flyway="V1100–V1199", phase="Fase 3",
        work="CAVW, CAUP (COACTVWC, COACTUPC — il programma più grande, 4.236 righe): validazioni campo per campo (date, SSN, telefono, stato, ZIP via `CSLKPCDY`), confronto old vs new prima del REWRITE → `@Version` + diff; aggiornamento atomico account + customer.",
        dtos="`CoactvwScreen`, `CoactupScreen`, `AccountRecord`, `CustomerRecord`", ports=["BusinessClock"],
        tables=["account", "customer", "card_xref"], dod=ONLINE_DOD, goldens=["nuovi online/CAVW, online/CAUP"]),
    "o3-card": dict(
        title="Carte: lista, dettaglio, aggiornamento", module="carddemo-online-card", flyway="V1200–V1299", phase="Fase 3",
        work="CCLI, CCDL, CCUP (COCRDLIC, COCRDSLC, COCRDUPC): paginazione PF7/PF8 a 7 righe con filtri conto/carta; selezione `S`/`U` per riga; l'utente non admin vede solo le proprie carte (logica esistente).",
        dtos="`CocrdliScreen`, `CocrdslScreen`, `CocrdupScreen`, `CardRecord`", ports=["BusinessClock"],
        tables=["card", "card_xref", "account"], dod=ONLINE_DOD, goldens=["nuovi online/CCLI, online/CCDL, online/CCUP"]),
    "o4-transaction": dict(
        title="Transazioni, pagamenti, report", module="carddemo-online-transaction", flyway="V1300–V1399", phase="Fase 3",
        work="CT00, CT01, CT02, CB00, CR00 (COTRN00C–02C, COBIL00C, CORPT00C). ID transazione (ultimo ID + 1) sotto concorrenza → sequence o lock pessimistico con la stessa semantica; CB00 azzera il saldo e crea la transazione di pagamento; CR00 chiama `ReportJobLauncherPort` (implementata da B3; in Fase 0 c'è `UnavailableReportJobLauncher`) al posto della TDQ; stato job via `GET /api/v1/jobs/{executionId}`.",
        dtos="`Cotrn00Screen`, `Cotrn01Screen`, `Cotrn02Screen`, `Cobil00Screen`, `Corpt00Screen`, `TransactionRecord`",
        ports=["ReportJobLauncherPort", "BusinessClock"], tables=["transaction", "account", "card_xref", "transaction_type", "transaction_category"],
        dod=ONLINE_DOD, goldens=["nuovi online/CT00, CT01, CT02, CB00, CR00"]),
    "o5-useradmin": dict(
        title="Amministrazione utenti", module="carddemo-online-useradmin", flyway="V1400–V1499", phase="Fase 3",
        work="CU00–CU03 (COUSR00C–03C): CRUD `app_user` con paginazione; solo ruolo ADMIN; password BCrypt, mai restituita dalle API.",
        dtos="`Cousr00Screen`, `Cousr01Screen`, `Cousr02Screen`, `Cousr03Screen`, `UserSecurityRecord`",
        ports=["UserCredentialService"], tables=["app_user"], dod=ONLINE_DOD, goldens=["nuovi online/CU00, CU01, CU02, CU03"]),
    "o6-frontend": dict(
        title="Frontend SPA (opzionale)", module="frontend", flyway=None, phase="Fase 3",
        work="SPA React + TypeScript in `modern/frontend` contro `openapi.yaml` (client generato), layout fedele alle mappe 3270 (24×80, colori/attributi BMS da `DFHBMSCA`, posizioni da `@ScreenField`), shortcut per i tasti PF. Parte subito sui mock OpenAPI (Prism); `modern/frontend` NON è un modulo Maven: build e test npm propri (aggiungi un job CI solo come proposta nella PR).",
        dtos="tutti i DTO di schermata (via OpenAPI)", ports=[], tables=[],
        dod=["Una vista per ogni `ScreenId` non opzionale; le schermate X1–X2 dietro flag.",
             "Test unitari dei componenti e test di contratto contro i mock Prism generati da `openapi.yaml`.",
             "`npm ci && npm test && npm run build` verdi; nessun file fuori da `modern/frontend/**`."],
        goldens=["layout BMS da `docs/inventory/bms.md`"]),
    "x1-authorization": dict(
        title="Estensione autorizzazioni IMS/DB2/MQ", module="carddemo-ext-authorization", flyway="V2000–V2099", phase="Fase 4",
        work="`app/app-authorization-ims-db2-mq`: IMS HIDAM → `pending_auth_summary`/`pending_auth_detail` (FK + indice sulla chiave inversa del timestamp); DL/I → repository `findFirst/next/insert/replace/delete`; DB2 AUTHFRDS → entity JPA; CP00 (trigger MQ) → listener SQS request/reply (`CCPAURQY`/`CCPAURLY`) via `MessageChannelPort`; CPVS/CPVD → REST; CBPAUP0J (purge + ricalcolo credito) → job Spring Batch; unload/load IMS → job dati con `IMSDATA.DBPAUTP0.dat` come golden. Le tabelle nuove vanno nel tuo range Flyway, entity/repository nel tuo modulo. Attivabile con `carddemo.extensions.authorization.enabled=true` (default off).",
        dtos="`Copau00Screen`, `Copau01Screen`", ports=["MessageChannelPort", "BusinessClock"], tables=["account", "card_xref", "(nuove) pending_auth_summary, pending_auth_detail, auth_fraud"],
        dod=["Parità delle decisioni approva/rifiuta su un set di richieste golden; purge identico; marcatura frode → riga AUTHFRDS.",
             "Test di integrazione LocalStack SQS (profilo `it`); regressione app base con flag ON e OFF.", "Copertura ≥ 80%; ArchUnit verde."],
        goldens=["nuovi online/CPVS, online/CPVD", "batch CBPAUP0J", "`IMSDATA.DBPAUTP0.dat`"]),
    "x2-trantype": dict(
        title="Estensione tipi transazione DB2", module="carddemo-ext-trantype", flyway="V2100–V2199", phase="Fase 4",
        work="`app/app-transaction-type-db2`: CTLI/CTTU → REST su `transaction_type`/`transaction_category` (cursori avanti/indietro → keyset); MNTTRDB2 → job di manutenzione; TRANEXTR sostituito da un controllo di consistenza; opzioni del menu admin CA00 abilitate da `carddemo.extensions.trantype.enabled=true` (default off).",
        dtos="`CotrtliScreen`, `CotrtupScreen`, `TransactionTypeRecord`, `TransactionCategoryRecord`", ports=["BusinessClock"],
        tables=["transaction_type", "transaction_category"],
        dod=["Golden scenari CTLI/CTTU verdi; job MNTTRDB2 con lo stesso file di input/output.", "Regressione app base con flag ON e OFF.", "Copertura ≥ 80%; ArchUnit verde."],
        goldens=["nuovi online/CTLI, online/CTTU", "batch MNTTRDB2"]),
    "x3-mqinquiry": dict(
        title="Estensione inquiry via MQ", module="carddemo-ext-mqinquiry", flyway="V2200–V2299", phase="Fase 4",
        work="`app/app-vsam-mq` (CODATE01, COACCT01): CDRD/CDRA → listener SQS request/reply (data di sistema, dettaglio conto) via `MessageChannelPort` con lo stesso formato messaggio; attivabile con `carddemo.extensions.mqinquiry.enabled=true` (default off).",
        dtos="`AccountRecord`", ports=["MessageChannelPort", "BusinessClock"], tables=["account"],
        dod=["Test di integrazione LocalStack SQS; formato di risposta identico byte per byte.", "Regressione app base con flag ON e OFF.", "Copertura ≥ 80%; ArchUnit verde."],
        goldens=["nuovi golden messaggi CDRD/CDRA"]),
}


def loc(path):
    try:
        return sum(1 for _ in open(REPO / path, encoding="latin-1"))
    except (IsADirectoryError, FileNotFoundError):
        return 0


def endpoints(ws):
    api = yaml.safe_load(open(MODERN / "carddemo-contracts/src/main/resources/openapi/openapi.yaml"))
    out = []
    for p, ops in api["paths"].items():
        for m, op in ops.items():
            if ws in (op.get("tags") or []):
                opt = " _(opzionale, estensione)_" if "optional-extension" in (op.get("tags") or []) else ""
                out.append(f"`{m.upper()} {p}` — {op.get('summary', '')}{opt}")
    return out



def read_progress(path):
    """Leader-maintained part of MIGRATION_PROGRESS.md: status cells (Stato..Note) per WS and text after the table."""
    status, tail = {}, []
    if not path.exists():
        return status, tail
    lines = path.read_text(encoding="utf-8").splitlines()
    i = next((n for n, l in enumerate(lines) if l.startswith("| WS |")), len(lines))
    for n in range(i + 2, len(lines)):
        if not lines[n].startswith("|"):
            tail = lines[n:]
            break
        row = lines[n].strip()[1:]
        row = row[:-1] if row.endswith("|") and not row.endswith("\\|") else row
        cells = [c.strip() for c in re.split(r"(?<!\\)\|", row)]
        if len(cells) == 11:
            status[cells[0].strip("`")] = cells[5:]
    return status, tail

def main():
    own = yaml.safe_load(open(MODERN / "ownership.yaml"))["workstreams"]
    assert sorted(own) == sorted(WS), f"ownership.yaml ws {sorted(own)} != {sorted(WS)}"
    files = {}
    for f, owners in owners_by_file()[0].items():
        if len(owners) == 1:
            files.setdefault(owners[0], []).append(f)
    inv = json.load(open(MODERN / "docs/inventory/inventory.json"))
    progs = {p["path"]: p for p in inv["programs"]}
    outdir = MODERN / "docs/workstreams"
    outdir.mkdir(parents=True, exist_ok=True)
    index = []
    for ws, m in WS.items():
        module_path = f"modern/{m['module']}/**"
        paths = [module_path]
        if m["module"] != "frontend":
            paths.append(f"modern/{G}/{ws}/**")
        if ws == "o1-auth":
            paths += [f"modern/{G}/online/CC00/**", f"modern/{G}/online/CM00/**", f"modern/{G}/online/CA00/**",
                      "modern/carddemo-parity/src/test/java/com/carddemo/parity/online/CC00SignOnScenariosIT.java"]
        fw = m["flyway"]
        branch = f"migration/{ws}"
        legacy = sorted(files.get(ws, []))
        total = sum(loc(f) for f in legacy)
        lines = [f"# {ws} — {m['title']}", "",
                 f"Repo: {REPO_SLUG} · base: `{BASE}` (contratti tag `contracts-v1`, Fase 0 già mergiata)",
                 f"Branch: `{branch}` · PR verso `{BASE}` (NON verso `main`, non mergiare: lo fa il leader)",
                 f"Fase: {m['phase']} del piano `docs/MIGRATION_PLAN_JAVA21.md` (leggi §3, §4, §11 e la sezione della tua fase).",
                 "", "## Perimetro (puoi modificare SOLO questi path)"]
        lines += [f"- `{p}`" for p in paths]
        if m["module"] != "frontend":
            lines.append(f"- fixture parità: `modern/{G}/{ws}/**` (golden batch nuovi: `.../golden/{ws}/<JOB>/`; scenari online: `.../golden/{ws}/online/<TRANID>/*.yaml`)")
        lines.append(f"- Flyway: {'`' + fw + '` (file in `modern/' + m['module'] + '/src/main/resources/db/migration/`)' if fw else 'nessuna migrazione (non creare file Flyway)'}")
        lines += ["", "Tutto il resto è **read-only**: in particolare NON modificare `modern/pom.xml` (POM padre), "
                  "`modern/carddemo-contracts/**`, `modern/carddemo-domain/**` esistenti, `V1__baseline.sql`, "
                  "`V2__seed_sample_data.sql`, `V3__spring_batch_metadata.sql`, né nulla sotto `app/`. "
                  "Le dipendenze si aggiungono SOLO nel `pom.xml` del tuo modulo (versioni già gestite nel "
                  "`dependencyManagement` del padre). Qualunque file toccato fuori perimetro è una **violazione di confine** "
                  "da dichiarare nel messaggio finale.", "",
                  "## Sorgenti legacy da migrare"]
        if legacy:
            lines.append(f"{len(legacy)} file, {total} LOC totali (da `modern/ownership.yaml`):")
            lines.append("")
            lines.append("| File | LOC | Tipo | COPY | CALL/XCTL/LINK |")
            lines.append("|---|---|---|---|---|")
            for f in legacy:
                p = progs.get(f)
                if p:
                    calls = ", ".join(sorted(set(p.get("calls", []) + p.get("xctl", []) + p.get("link", [])))) or "—"
                    lines.append(f"| `{f}` | {p['loc']} | {p['kind']} | {', '.join(p.get('copybooks', [])) or '—'} | {calls} |")
                else:
                    lines.append(f"| `{f}` | {loc(f)} | — | — | — |")
        else:
            lines.append("Nessun file legacy proprio: consumi `openapi.yaml` e i layout BMS (`modern/docs/inventory/bms.md`).")
        lines += ["", f"Inventario completo: `modern/docs/inventory/` (`programs.md`, `jcl.md`, `datasets.md`, `bms.md`, `call-graph.mmd`, `inventory.json`).",
                  "", "## Lavoro", m["work"], "",
                  "## Contratti da usare (non modificarli: per cambi usa CONTRACT-CHANGE)",
                  f"- DTO: {m['dtos']}",
                  "- Porte (`com.carddemo.contracts.port`): " + (", ".join(f"`{p}`" for p in m["ports"]) or "—"),
                  "- Tabelle (V1, entity/repository in `com.carddemo.domain`): " + (", ".join(f"`{t}`" for t in m["tables"]) or "—")]
        eps = endpoints(ws)
        lines.append("- Endpoint OpenAPI (`carddemo-contracts/src/main/resources/openapi/openapi.yaml`): " + ("" if eps else "—"))
        lines += [f"  - {e}" for e in eps]
        lines += ["- Regole numeriche §3.4: `BigDecimal` con scala del PIC, mai `double`/`float`; `COMPUTE` senza ROUNDED = troncamento (`CobolMath.Mode.TRUNCATE`), con ROUNDED = HALF_UP; `PIC X` padded nel dominio; date solo via `BusinessClock` (ArchUnit vieta `LocalDateTime.now()`).",
                  "- Convenzioni: `modern/CONVENTIONS.md`; ADR: `modern/docs/adr/`.",
                  "- **Finché 1A/1B/1C non sono mergiati usa le implementazioni minime di Fase 0** (`ZonedRecordCodec`/`CoreCodecs`, seed V2, `FixedBusinessClock`/`SystemBusinessClock`, `CobolMath`, `FileSystemGenerationStore`, `InMemoryMaintenanceMode`, `InMemoryMessageChannel`, fallback in `carddemo-app` con `@ConditionalOnMissingBean`): le interfacce non cambieranno.",
                  "", "### Protocollo CONTRACT-CHANGE (§4.4)",
                  "Se ti serve un cambio a `carddemo-contracts`, `carddemo-domain`, V1/V2/V3 o al POM padre **non applicarlo**: aggiungi alla descrizione della PR una sezione `CONTRACT-CHANGE:` con il diff proposto e la motivazione, e lavora con un adattatore locale nel tuo modulo. Sono accettati solo cambi additivi.",
                  "", "## Definition of Done"]
        lines += [f"- [ ] {d}" for d in m["dod"]]
        lines += [f"- [ ] Golden specifici: {', '.join(m['goldens'])}.",
                  "- [ ] `cd modern && ./mvnw -B verify` e `./mvnw -B -Pparity verify` verdi (gate di parità); `./mvnw -B -Pit verify` verde se hai IT; CI `modern-ci.yml` della PR verde.",
                  "- [ ] Nessun file fuori perimetro; `python3 modern/tools/inventory/check_ownership.py` verde.",
                  "", "Golden harness: `modern/tools/golden/run-cobol.sh <JOB> [--verify]` (GnuCOBOL 3.1.2 + faketime; `sudo apt-get install -y gnucobol faketime`), formato in `modern/tools/golden/README.md`; comparatori `RecordComparator` (diff per campo), `DbSnapshot` (delta DB), `OnlineScenarioRunner` (YAML online) in `carddemo-parity`; deviazioni volute solo in `modern/PARITY_EXCEPTIONS.md`.",
                  "", "## Messaggio finale obbligatorio",
                  "PR URL, branch, conteggio test (unit / parity / IT), stato gate parità, violazioni di confine, `CONTRACT-CHANGE` richiesti, blocker.", ""]
        prompt = outdir / f"{ws}.md"
        prompt.write_text("\n".join(lines), encoding="utf-8")
        index.append(dict(id=ws, title=m["title"], module=m["module"] if m["module"] == "frontend" else m["module"],
                          paths=paths, flyway_range=fw, branch=branch, base=BASE, pr_target=BASE,
                          prompt_file=f"modern/docs/workstreams/{ws}.md", legacy_files=len(legacy), legacy_loc=total))
    (outdir / "index.json").write_text(json.dumps({"base": BASE, "contracts": "contracts-v1", "workstreams": index}, indent=2) + "\n")
    rows = ["# MIGRATION_PROGRESS — CardDemo → Java 21", "",
            "Aggiornata dal leader a ogni merge (piano §4.5). Ordine di merge suggerito: 1C → 1A → 1B → B4 → B1 → B2 → B3 → B5 → O1 → O2…O5 → O6 → X1–X3.", "",
            "| WS | Titolo | Modulo | Flyway | Branch | Stato | PR | Test (unit/parity/IT) | Gate parità | CONTRACT-CHANGE | Note |",
            "|---|---|---|---|---|---|---|---|---|---|---|"]
    progress = MODERN / "MIGRATION_PROGRESS.md"
    status, tail = read_progress(progress)
    for e in index:
        cells = status.get(e["id"], ["pending", "—", "—", "—", "—", ""])
        rows.append(f"| `{e['id']}` | {e['title']} | `{e['module']}` | {e['flyway_range'] or '—'} | `{e['branch']}` | "
                    + " | ".join(cells) + (" |" if cells[-1] else "|"))
    progress.write_text("\n".join(rows + tail) + "\n", encoding="utf-8")
    print(f"generated {len(index)} prompts in {outdir}")


if __name__ == "__main__":
    main()
