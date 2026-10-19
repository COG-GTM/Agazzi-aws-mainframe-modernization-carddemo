import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
// @ts-expect-error untyped build script
import { generate } from '../../scripts/gen-bms-layouts.mjs';
import { SCREENS } from '../../src/screens/registry';
import { LAYOUTS, layoutOf, renderText } from '../../src/terminal/layout';
import { BMS_MD, FRONTEND } from '../paths';

const GOLDEN = join(FRONTEND, 'tests/golden/screens');
const update = process.env.UPDATE_GOLDEN === '1';

/** Named-field tables of modern/docs/inventory/bms.md (Phase-0 inventory). */
function inventory(): Record<string, { name: string; row: number; col: number; len: number; input: boolean; attrb: string[] }[]> {
  const out: Record<string, { name: string; row: number; col: number; len: number; input: boolean; attrb: string[] }[]> = {};
  let cur: string | null = null;
  for (const line of readFileSync(BMS_MD, 'utf8').split('\n')) {
    const h = /^## (\w+) \(/.exec(line);
    if (h) {
      cur = h[1];
      out[cur] = [];
      continue;
    }
    const r = /^\| (\w+) \| (\d+) \| (\d+) \| (\d+) \| (Y?) \| ([A-Z,]*) \|$/.exec(line);
    if (r && cur) {
      out[cur].push({ name: r[1], row: +r[2], col: +r[3], len: +r[4], input: r[5] === 'Y', attrb: r[6] ? r[6].split(',').sort() : [] });
    }
  }
  return out;
}

describe('BMS layout golden', () => {
  it('src/generated/bms-layouts.json is up to date with app/**/bms', () => {
    expect(JSON.parse(JSON.stringify(generate()))).toEqual(LAYOUTS);
  });

  const inv = inventory();
  for (const s of SCREENS) {
    it(`${s.id}: named fields match docs/inventory/bms.md`, () => {
      const fields = layoutOf(s.id)
        .fields.filter((f) => f.name)
        .map((f) => ({ name: f.name!, row: f.row, col: f.col, len: f.length, input: f.attrb.includes('UNPROT'), attrb: [...f.attrb].sort() }));
      expect(fields).toEqual(inv[s.id]);
    });

    it(`${s.id}: 24x80 screen matches golden ${s.id}.txt`, () => {
      const text = renderText(layoutOf(s.id));
      const lines = text.split('\n');
      expect(lines.length - 1).toBe(24);
      expect(Math.max(...lines.map((l) => l.length))).toBeLessThanOrEqual(80);
      const file = join(GOLDEN, `${s.id}.txt`);
      if (update || !existsSync(file)) {
        if (!update) throw new Error(`missing golden ${file} (run UPDATE_GOLDEN=1 npm run test:unit)`);
        mkdirSync(GOLDEN, { recursive: true });
        writeFileSync(file, text);
      }
      expect(text).toBe(readFileSync(file, 'utf8'));
    });
  }
});
