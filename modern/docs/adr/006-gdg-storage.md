# ADR-006 — GDG su filesystem/S3

- **Stato**: accettato
- **Contesto**: i job usano GDG (`DEFGDGB/D`, `TRANSACT.BKUP(+1)`, `SYSTRAN(0)`) con generazioni relative e limite di versioni.
- **Decisione**: porta `GenerationStore` (`Generation(base, number).dsn()` = `<BASE>.GnnnnV00`). Fase 0: `FileSystemGenerationStore` (directory per base, `(+1)`/`(0)`/`(-n)`, limite e scratch delle generazioni più vecchie). Profilo `aws`: implementazione S3 (prefisso per base, oggetto per generazione, versioning bucket) fornita da 1C/B5 senza cambiare la porta.
- **Conseguenze**: i job Spring Batch non vedono mai path fisici; i golden usano lo stesso naming.
