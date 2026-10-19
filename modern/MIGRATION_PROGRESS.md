# MIGRATION_PROGRESS — CardDemo → Java 21

Aggiornata dal leader a ogni merge (piano §4.5). Ordine di merge suggerito: 1C → 1A → 1B → B4 → B1 → B2 → B3 → B5 → O1 → O2…O5 → O6 → X1–X3.

| WS | Titolo | Modulo | Flyway | Branch | Stato | PR | Test (unit/parity/IT) | Gate parità | CONTRACT-CHANGE | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| `1a-copybook` | Copybook → record Java (codec completo) | `carddemo-copybook` | — | `migration/1a-copybook` | pending | — | — | — | — | |
| `1b-datamigration` | Schema relazionale e migrazione dati | `carddemo-datamigration` | V100–V199 | `migration/1b-datamigration` | pending | — | — | — | — | |
| `1c-platform` | Utility Assembler e date → Java | `carddemo-platform` | — | `migration/1c-platform` | pending | — | — | — | — | |
| `b1-posting` | Posting giornaliero transazioni | `carddemo-batch-posting` | V200–V299 | `migration/b1-posting` | pending | — | — | — | — | |
| `b2-interest` | Interessi e saldi per categoria | `carddemo-batch-interest` | V300–V399 | `migration/b2-interest` | pending | — | — | — | — | |
| `b3-reports` | Report e statement | `carddemo-batch-reports` | V400–V499 | `migration/b3-reports` | pending | — | — | — | — | |
| `b4-datalifecycle` | Data lifecycle: caricamento, export/import, utility | `carddemo-batch-datalifecycle` | V500–V599 | `migration/b4-datalifecycle` | pending | — | — | — | — | |
| `b5-orchestration` | Orchestrazione batch e scheduler | `carddemo-batch-orchestration` | V600–V699 | `migration/b5-orchestration` | pending | — | — | — | — | |
| `o1-auth` | Sign-on, menu, sicurezza | `carddemo-online-auth` | V1000–V1099 | `migration/o1-auth` | pending | — | — | — | — | |
| `o2-account` | Conto: visualizzazione e aggiornamento | `carddemo-online-account` | V1100–V1199 | `migration/o2-account` | pending | — | — | — | — | |
| `o3-card` | Carte: lista, dettaglio, aggiornamento | `carddemo-online-card` | V1200–V1299 | `migration/o3-card` | pending | — | — | — | — | |
| `o4-transaction` | Transazioni, pagamenti, report | `carddemo-online-transaction` | V1300–V1399 | `migration/o4-transaction` | pending | — | — | — | — | |
| `o5-useradmin` | Amministrazione utenti | `carddemo-online-useradmin` | V1400–V1499 | `migration/o5-useradmin` | pending | — | — | — | — | |
| `o6-frontend` | Frontend SPA (opzionale) | `frontend` | — | `migration/o6-frontend` | pending | — | — | — | — | |
| `x1-authorization` | Estensione autorizzazioni IMS/DB2/MQ | `carddemo-ext-authorization` | V2000–V2099 | `migration/x1-authorization` | pending | — | — | — | — | |
| `x2-trantype` | Estensione tipi transazione DB2 | `carddemo-ext-trantype` | V2100–V2199 | `migration/x2-trantype` | pending | — | — | — | — | |
| `x3-mqinquiry` | Estensione inquiry via MQ | `carddemo-ext-mqinquiry` | V2200–V2299 | `migration/x3-mqinquiry` | pending | — | — | — | — | |
