# modern/frontend — placeholder (owner: O6 `o6-frontend`)

SPA React + TypeScript that replaces the 3270 BMS screens (plan §3.2, Fase 3).

- Not a Maven module: O6 owns everything under `modern/frontend/**` (own `package.json`, build, tests).
- The API client is generated from `carddemo-contracts/src/main/resources/openapi/openapi.yaml`
  (contracts-v1); one view per BMS map (`ScreenId`), 24x80 field layout from `@ScreenField`.
- Every response is a `ScreenResponse` (`messages`, `nextScreen`, `fieldErrors`, `context`):
  the SPA navigates to `nextScreen` and renders `messages` in the ERRMSG line.
- PF keys map to `action` (`ENTER`, `PF3`, `PF7`, `PF8`, ...), see `Action` in the OpenAPI.
