# ADR-004 — Codec copybook: JRecord vs codegen

- **Stato**: accettato per Fase 0; decisione finale a 1A (proposta nella sua PR)
- **Contesto**: servono decode/encode byte-identici dei record VSAM/PS (DISPLAY zoned con overpunch, COMP, COMP-3, OCCURS, REDEFINES, VB) in ASCII e CP037.
- **Decisione Fase 0**: **codegen dedicato** da copybook (`tools/contracts/generate_contracts.py`) → `record` Java annotati `@CobolRecord`/`@CobolField` + codec riflessivo `ZonedRecordCodec` (DISPLAY + overpunch, ASCII/CP037) per i record core. Nessuna dipendenza runtime da JRecord.
- **Opzioni per 1A**: (a) estendere il codegen (COMP/COMP-3/OCCURS/REDEFINES → `sealed interface`); (b) JRecord/cb2xml come parser AST dentro il generatore. `net.sf.jrecord:JRecord` è nel `dependencyManagement` ma la versione dichiarata non è risolvibile dal mirror usato in Fase 0: se 1A lo adotta, propone la coordinata corretta via `CONTRACT-CHANGE`.
- **Vincolo**: l'interfaccia `RecordCodec<T>` e i record di `com.carddemo.contracts.record` non cambiano.
