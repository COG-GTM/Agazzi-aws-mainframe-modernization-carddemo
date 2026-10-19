import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { resolveScreen, SCREENS, screenPath } from '../../src/screens/registry';
import { VIEWS } from '../../src/screens/views';
import { LAYOUTS } from '../../src/terminal/layout';
import { openapi, SCREEN_ID_JAVA } from '../paths';

describe('ScreenId registry', () => {
  it('mirrors com.carddemo.contracts.screen.ScreenId exactly', () => {
    const java = readFileSync(SCREEN_ID_JAVA, 'utf8');
    const rows = [...java.matchAll(/^\s+(CO\w+)\("(\w+)", "(\w+)", "(\w+)", "([\w-]+)", (true|false)\)/gm)].map((m) => ({
      id: m[1], map: m[2], transactionId: m[3], program: m[4], workstream: m[5], optional: m[6] === 'true',
    }));
    expect(rows).toHaveLength(21);
    expect(SCREENS.map(({ id, map, transactionId, program, workstream, optional }) => ({ id, map, transactionId, program, workstream, optional }))).toEqual(rows);
  });

  it('has an OpenAPI GET+POST resource, a BMS layout and a view for every ScreenId', () => {
    const paths = openapi().paths;
    for (const s of SCREENS) {
      expect(paths[screenPath(s.id)], s.id).toHaveProperty('get');
      expect(paths[screenPath(s.id)], s.id).toHaveProperty('post');
      expect(LAYOUTS[s.id]?.maps[0].name, s.id).toBe(s.map);
      expect(typeof VIEWS[s.id], s.id).toBe('function');
    }
  });

  it('tags only X1/X2 screens as optional extensions', () => {
    const paths = openapi().paths;
    for (const s of SCREENS) {
      const tags: string[] = paths[screenPath(s.id)].get.tags;
      expect(tags.includes('optional-extension'), s.id).toBe(s.optional);
      expect(s.optional, s.id).toBe('extension' in s);
    }
  });

  it('resolves nextScreen given as mapset, map, program or transaction id', () => {
    expect(resolveScreen('COMEN01')?.id).toBe('COMEN01');
    expect(resolveScreen('comen1a')?.id).toBe('COMEN01');
    expect(resolveScreen('COACTVWC')?.id).toBe('COACTVW');
    expect(resolveScreen('CT02')?.id).toBe('COTRN02');
    expect(resolveScreen(' ')).toBeUndefined();
    expect(resolveScreen(null)).toBeUndefined();
    expect(resolveScreen('NOPE')).toBeUndefined();
  });
});
