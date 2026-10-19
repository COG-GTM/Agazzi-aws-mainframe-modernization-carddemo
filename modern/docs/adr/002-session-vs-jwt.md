# ADR-002 — Sessione server-side vs JWT

- **Stato**: accettato (implementazione O1)
- **Contesto**: CICS pseudo-conversazionale: la COMMAREA `COCOM01Y` passa da un task al successivo; XCTL cambia programma mantenendo il contesto.
- **Decisione**: `ConversationContext` (specchio di `COCOM01Y`) conservato in **sessione HTTP server-side** (Spring Session, cookie `HttpOnly`/`SameSite=Strict`), non in un JWT. Ogni risposta restituisce comunque `context` in `ScreenResponse<T>` per la SPA (sola lettura). Il JWT resta un'opzione per i client machine-to-machine (estensioni) se richiesto in Fase 5.
- **Motivi**: il contesto cambia a ogni schermata (un JWT andrebbe riemesso a ogni risposta), contiene chiavi di conto/carta da non esporre come token firmato riutilizzabile, logout = PF3 = invalidazione sessione come in COSGN00C.
- **Fase 0**: `Fase0SecurityAutoConfiguration` (HTTP Basic, `@ConditionalOnMissingBean(SecurityFilterChain)`) è un segnaposto che O1 sostituisce.
