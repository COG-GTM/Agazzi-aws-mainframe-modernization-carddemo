# Online goldens (plan §11.4)

One YAML per scenario under `<TRANID>/NNN-<slug>.yaml`; format documented in
`com.carddemo.parity.online.OnlineScenario`. Scenarios are derived from the COBOL paragraphs
(comment on top of each file) and run by `OnlineScenarioRunner` against the real app.

- `status: pending-<ws>`: the owning workstream has not implemented the screen yet; the IT that runs
  the scenario is tagged `@Tag("pending-<ws>")` and excluded by `failsafe.excludedGroups`.
  The owning workstream removes the tag (and the `status`) when the screen is implemented.
- Messages are compared after `strip()` (BMS ERRMSG is space padded).
