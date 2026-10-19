# migrate/ - lancio parallelo delle sessioni Devin per la migrazione CardDemo

Migrazione di CardDemo (COBOL/CICS/VSAM/JCL in `app/`) verso Java 21 + Spring Boot 3 + PostgreSQL/Flyway (codice target in `modern/`, creato dalla fase 0), una sessione Devin per workstream, ognuna consegna un branch `migrate/<workstream>` e una PR separata.

| Fase | Esecuzione | Workstream (prompt in `prompts/`) |
|---|---|---|
| 0 | sequenziale | `phase0-foundations` - Maven multi-modulo, entity JPA dai copybook, seed Flyway, docker-compose, utility date |
| 1 | 8 in parallelo | `ws-a-auth`, `ws-b-account`, `ws-c-card`, `ws-d-customer`, `ws-e-transactions`, `ws-f-batch-posting`, `ws-g-batch-interest`, `ws-h-reports` |
| 2 | 3 in parallelo | `ws-i-ui`, `ws-j-integration`, `ws-k-data-migration` |

Una fase parte quando tutte le sessioni della fase precedente sono terminate **e** i loro branch risultano mergiati nel branch base (verifica con `gh`, fallback `git merge-base`).

## Uso

```bash
migrate/launch.sh --list                    # piano fasi/workstream
migrate/launch.sh --dry-run                 # renderizza prompt e payload, nessuna chiamata API
export DEVIN_API_KEY=cog_...                # obbligatoria; DEVIN_ORG_ID opzionale (API v3)
migrate/launch.sh                           # fasi 0 -> 1 -> 2 con attesa e gate di merge
migrate/launch.sh --phase 1 --only ws-a-auth,ws-c-card   # rilancio mirato
```

Opzioni e variabili d'ambiente (`BASE_BRANCH`, `POLL_INTERVAL`, `PHASE_TIMEOUT`, `DEVIN_PLAYBOOK_ID`, `DEVIN_MAX_ACU`, ...): `migrate/launch.sh --help`.

## Output

`migrate/sessions/<RUN_ID>/` (link `migrate/sessions/latest`), non versionato:

- `<ws>.prompt.md` - prompt inviato (header di contesto + `prompts/<ws>.md`)
- `<ws>.request.json` / `<ws>.response.json` / `<ws>.status.json` - payload e risposte API
- `sessions.tsv` - indice: fase, workstream, branch, session id, URL, stato, PR
- `launch.log`

## Convenzioni comuni ai prompt

Ogni prompt contiene le stesse "Regole comuni": package dedicato `com.carddemo.<dominio>`, copybook -> JPA (`COMP-3`/`S9(n)V9(m)` -> `BigDecimal`), `EXEC CICS` -> REST, VSAM -> Spring Data JPA, test con Testcontainers, range Flyway riservato per workstream, consegna come branch/PR separato.

Programmi non elencati esplicitamente nel mapping richiesto e assegnati per completezza: `COBIL00C` (pagamento) -> `ws-e-transactions`; `COMEN01C` (menu utente) -> `ws-a-auth` (API menu) + `ws-i-ui`. `CBACT04C` e' condiviso: `ws-b-account` espone l'API di aggiornamento conto, `ws-g-batch-interest` implementa il job interessi. Le estensioni opzionali in `app/app-*` (IMS/DB2/MQ) sono fuori scope.
