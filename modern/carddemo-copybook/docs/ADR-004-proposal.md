# ADR-004 (proposta 1A) — Copybook codec: parser dedicato + codegen deterministico

Stato: **proposta** (workstream 1a-copybook). Il file `modern/docs/adr/004-copybook-codec.md` è fuori
dal perimetro 1A: il leader lo aggiorna copiando questa sezione "Decisione".

## Contesto
Fase 0 aveva un codec riflessivo solo DISPLAY (`ZonedRecordCodec`) guidato dalle annotazioni dei DTO.
1A deve coprire `COMP`/`BINARY`, `COMP-5`, `COMP-3`, `OCCURS`/`ODO`, `REDEFINES`, 88-level, VB (RDW),
EBCDIC CP037, e generare record Java + layout per ogni 01-level.

## Opzioni
1. **JRecord 0.93.x / cb2xml** — parser maturo, ma: dipendenza GPL/LGPL pesante (JRecord porta
   cb2xml, jaxb), non pubblicato in modo affidabile sul mirror usato dalla CI (vedi note di Fase 0),
   API orientata a `AbstractLine` (mutabile, non `record`), e servirebbe comunque uno strato di
   codegen per ottenere record Java tipizzati e `sealed interface` per le REDEFINES.
2. **Parser + codegen dedicati** (scelta) — il sottoinsieme COBOL di CardDemo è piccolo (22 copybook
   core + 15 delle estensioni + FD/WS di CBACT01C), totalmente coperto da ~400 LOC di parser.

## Decisione
- `com.carddemo.copybook.parser.CopybookParser`: sorgente fixed-format → AST
  (`com.carddemo.copybook.layout.LayoutNode`) con offset/lunghezza/PIC/usage risolti
  (`RecordLayout` per ogni 01; copybook senza 01 → 01 sintetico col nome del copybook). Salta
  `EXEC SQL … END-EXEC` (DCLGEN) e copybook procedurali (`CSSETATY`, `CSSTRPFY`, `CSUTLDPY`, `CSDB2RPY`).
- `com.carddemo.copybook.gen.RecordGenerator`: genera in `com.carddemo.copybook.generated.*` un
  `record` Java per ogni 01 (gruppi → record annidati, `OCCURS` → `List`, `REDEFINES` esclusi dallo
  storage del record, 88-level → metodi `isXxx()` + `enum` per i campi con soli letterali singoli) e
  la costante `LAYOUT` (offset/lunghezza/PIC espliciti nel sorgente). Le famiglie REDEFINES
  configurate diventano `sealed interface` (es. `ExportRecord permits CustomerExport, …, RawExport`
  discriminato da `EXPORT-REC-TYPE`; `VbpsRecord` discriminato dalla lunghezza del record VB).
  Output deterministico: ordine del sorgente, nessun timestamp; `GeneratedSourcesUpToDateTest`
  rigenera e confronta (zero diff), `mvn exec`/`main` per rigenerare.
- Runtime `com.carddemo.copybook.codec`: `LayoutCodec` (binding posizionale layout → record),
  `VariantCodec` (sealed), `VariableBlockFile` (RDW IBM inclusivo / GnuCOBOL esclusivo),
  `PackedDecimal`, `BinaryInteger`, `ZonedDecimal` (overpunch IBM e segno nativo GnuCOBOL),
  `CodecOptions` (ISO-8859-1 per ASCII — binary safe —, CP037 per EBCDIC).
- `ZonedRecordCodec`/`CoreCodecs` mantengono l'API pubblica; internamente costruiscono un
  `RecordLayout` dalle annotazioni dei DTO contracts e delegano a `LayoutCodec`. Un test verifica
  che le annotazioni dei DTO coincidano con i layout parsati dai copybook.

## Conseguenze
- Nessuna nuova dipendenza runtime. JRecord resta nel `dependencyManagement` del padre ma non è usato.
- I record generati sono la vista "storage" dei dataset (fillers inclusi, per round-trip byte-identico);
  i DTO di `carddemo-contracts` restano l'API dei servizi.
