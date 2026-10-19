import { describe, expect, it } from "vitest";
import { SCREENS } from "../../src/screens/registry";
import { inputFields, layoutOf } from "../../src/terminal/layout";
import { openapi } from "../paths";

const cap = (id: string) => id[0] + id.slice(1).toLowerCase();

describe("openapi.yaml DTOs vs BMS layouts", () => {
  const schemas = openapi().components.schemas;
  for (const s of SCREENS) {
    it(`${s.id}: <Mapset>Input = unprotected fields, <Mapset>Output ⊆ named fields`, () => {
      const map = layoutOf(s.id);
      const input = schemas[`${cap(s.id)}Input`].properties as Record<
        string,
        { maxLength: number; description: string; "x-runtime-input"?: boolean }
      >;
      const output = schemas[`${cap(s.id)}Output`].properties as Record<
        string,
        { maxLength: number; description: string }
      >;
      // contracts-v1.1: PROT in BMS but unprotected by the program at runtime (x-runtime-input).
      const runtime = new Set(
        Object.keys(input).filter((n) => input[n]["x-runtime-input"]),
      );
      // inputFields() already includes them: the SPA renders them editable (O6, Fase 5A).
      const unprot = inputFields(map);
      for (const name of runtime) {
        expect(unprot.some((f) => f.name === name), `${s.id}.${name} editable`).toBe(true);
      }
      expect(Object.keys(input).sort()).toEqual(
        unprot.map((f) => f.name!).sort(),
      );
      for (const f of unprot) {
        expect(input[f.name!].maxLength, f.name!).toBe(f.length);
        expect(input[f.name!].description, f.name!).toBe(
          `row ${f.row} col ${f.col}`,
        );
      }
      const named = new Map(
        map.fields.filter((f) => f.name).map((f) => [f.name!, f]),
      );
      for (const [name, p] of Object.entries(output)) {
        expect(named.has(name), `${s.id}.${name}`).toBe(true);
        expect(p.maxLength, `${s.id}.${name}`).toBe(named.get(name)!.length);
      }
    });
  }
});
