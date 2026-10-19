import layoutsJson from '../generated/bms-layouts.json';
import type { ScreenIdName } from '../screens/registry';

export type BmsColor = 'BLUE' | 'RED' | 'PINK' | 'GREEN' | 'TURQUOISE' | 'YELLOW' | 'NEUTRAL' | 'DEFAULT';

export interface BmsField {
  name: string | null;
  row: number;
  col: number;
  length: number;
  attrb: string[];
  color?: string;
  hilight?: string;
  initial?: string;
  justify?: string[];
}

export interface BmsMap {
  name: string;
  rows: number;
  cols: number;
  fields: BmsField[];
}

export interface BmsMapset {
  mapset: string;
  path: string;
  maps: BmsMap[];
}

export const LAYOUTS = layoutsJson as unknown as Record<ScreenIdName, BmsMapset>;

export function layoutOf(id: ScreenIdName): BmsMap {
  return LAYOUTS[id].maps[0];
}

/** DFHBMSCA attribute semantics (ATTRB=). */
export const isInput = (f: BmsField) => f.attrb.includes('UNPROT');
export const isDark = (f: BmsField) => f.attrb.includes('DRK');
export const isBright = (f: BmsField) => f.attrb.includes('BRT');
export const isNumeric = (f: BmsField) => f.attrb.includes('NUM');
export const hasInitialCursor = (f: BmsField) => f.attrb.includes('IC');

export function inputFields(map: BmsMap): BmsField[] {
  return map.fields.filter((f) => f.name && isInput(f));
}

/**
 * 3270 colour: explicit COLOR=, else the base 4-colour default
 * (protected normal=blue, protected bright=white, unprotected normal=green, unprotected bright=red).
 */
export function colorOf(f: BmsField): BmsColor {
  const c = f.color as BmsColor | undefined;
  if (c && c !== 'DEFAULT') return c;
  if (isInput(f)) return isBright(f) ? 'RED' : 'GREEN';
  return isBright(f) ? 'NEUTRAL' : 'BLUE';
}

/** Value as the terminal shows it: truncated to LENGTH, JUSTIFY=RIGHT pads on the left. */
export function displayValue(f: BmsField, value: string | undefined): string {
  const v = (value ?? f.initial ?? '').slice(0, f.length);
  return f.justify?.includes('RIGHT') ? v.trimEnd().padStart(f.length) : v;
}

/**
 * Plain-text 24x80 rendering of the map: literals as INITIAL, named input fields as '_',
 * named output fields as '.' (used for the BMS layout golden files).
 */
export function renderText(map: BmsMap): string {
  const grid = Array.from({ length: map.rows }, () => Array<string>(map.cols).fill(' '));
  for (const f of map.fields) {
    const text = f.name ? (isInput(f) ? '_' : '.').repeat(f.length) : displayValue(f, undefined).padEnd(f.length);
    for (let i = 0; i < text.length; i++) {
      const c = f.col + i; // POS is the attribute byte; data starts one column to the right
      if (c >= map.cols) break;
      grid[f.row - 1][c] = text[i];
    }
  }
  return grid.map((r) => r.join('').trimEnd()).join('\n') + '\n';
}
