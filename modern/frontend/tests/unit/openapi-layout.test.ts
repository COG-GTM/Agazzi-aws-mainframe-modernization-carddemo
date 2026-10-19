import { describe, expect, it } from 'vitest';
import { SCREENS } from '../../src/screens/registry';
import { inputFields, layoutOf } from '../../src/terminal/layout';
import { openapi } from '../paths';

const cap = (id: string) => id[0] + id.slice(1).toLowerCase();

describe('openapi.yaml DTOs vs BMS layouts', () => {
  const schemas = openapi().components.schemas;
  for (const s of SCREENS) {
    it(`${s.id}: <Mapset>Input = unprotected fields, <Mapset>Output ⊆ named fields`, () => {
      const map = layoutOf(s.id);
      const input = schemas[`${cap(s.id)}Input`].properties as Record<string, { maxLength: number; description: string }>;
      const output = schemas[`${cap(s.id)}Output`].properties as Record<string, { maxLength: number; description: string }>;
      const unprot = inputFields(map);
      expect(Object.keys(input).sort()).toEqual(unprot.map((f) => f.name!).sort());
      for (const f of unprot) {
        expect(input[f.name!].maxLength, f.name!).toBe(f.length);
        expect(input[f.name!].description, f.name!).toBe(`row ${f.row} col ${f.col}`);
      }
      const named = new Map(map.fields.filter((f) => f.name).map((f) => [f.name!, f]));
      for (const [name, p] of Object.entries(output)) {
        expect(named.has(name), `${s.id}.${name}`).toBe(true);
        expect(p.maxLength, `${s.id}.${name}`).toBe(named.get(name)!.length);
      }
    });
  }
});
