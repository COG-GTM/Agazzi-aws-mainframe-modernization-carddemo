#!/usr/bin/env node
// Parses the legacy BMS mapsets (read-only, app/**/bms/*.bms) into src/generated/bms-layouts.json:
// every DFHMDF (named fields and literals) with POS/LENGTH/ATTRB/COLOR/HILIGHT/INITIAL/JUSTIFY.
import { readFileSync, readdirSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repo = join(here, '..', '..', '..');
const out = join(here, '..', 'src', 'generated', 'bms-layouts.json');

export function bmsFiles() {
  const dirs = [join(repo, 'app', 'bms')];
  for (const d of readdirSync(join(repo, 'app'))) {
    const sub = join(repo, 'app', d, 'bms');
    if (d.startsWith('app-') && existsSync(sub)) dirs.push(sub);
  }
  return dirs.flatMap((d) => readdirSync(d).filter((f) => f.endsWith('.bms')).sort().map((f) => join(d, f)));
}

/** Joins assembler statements: continuation = non-blank col 72, next line resumes at col 16. */
export function statements(text) {
  const stmts = [];
  let cur = null;
  let inQuote = false;
  for (const raw of text.split(/\r?\n/)) {
    if (cur === null && raw.startsWith('*')) continue;
    const line = raw.padEnd(80, ' ');
    let seg = cur === null ? line.slice(0, 71) : line.slice(15, 71);
    for (const ch of seg) if (ch === "'") inQuote = !inQuote;
    if (!inQuote) seg = seg.replace(/\s+$/, '');
    cur = (cur ?? '') + seg;
    if (line[71] !== ' ') continue;
    stmts.push(cur);
    cur = null;
    inQuote = false;
  }
  if (cur !== null) stmts.push(cur);
  return stmts;
}

/** Splits the operand field on top-level commas (quote and paren aware). */
export function operands(s) {
  const res = {};
  let depth = 0;
  let q = false;
  let tok = '';
  const flush = () => {
    const i = tok.indexOf('=');
    if (i > 0) res[tok.slice(0, i)] = tok.slice(i + 1);
    tok = '';
  };
  for (let i = 0; i < s.length; i++) {
    const ch = s[i];
    if (ch === "'") q = !q;
    if (!q && ch === ' ') break;
    if (!q && ch === '(') depth++;
    if (!q && ch === ')') depth--;
    if (!q && depth === 0 && ch === ',') flush();
    else tok += ch;
  }
  flush();
  return res;
}

const list = (v) => (v ? v.replace(/^\(|\)$/g, '').split(',').filter(Boolean) : []);
const unquote = (v) => (v === undefined ? undefined : v.replace(/^'|'$/g, '').replace(/''/g, "'"));

export function parseBms(text, path) {
  let mapset = null;
  const maps = [];
  let current = null;
  for (const st of statements(text)) {
    const m = /^(\S*)\s+(DFHMSD|DFHMDI|DFHMDF)\s+(.*)$/.exec(st);
    if (!m) continue;
    const [, name, op, rest] = m;
    const ops = operands(rest);
    if (op === 'DFHMSD') mapset ??= name;
    else if (op === 'DFHMDI') {
      const size = /\((\d+),(\d+)\)/.exec(ops.SIZE ?? '(24,80)');
      current = { name, rows: Number(size[1]), cols: Number(size[2]), fields: [] };
      maps.push(current);
    } else if (current) {
      const pos = /\((\d+),(\d+)\)/.exec(ops.POS ?? '');
      if (!pos) continue;
      const length = Number(ops.LENGTH ?? (unquote(ops.INITIAL) ?? '').length);
      const f = { name: name || null, row: Number(pos[1]), col: Number(pos[2]), length, attrb: list(ops.ATTRB) };
      if (ops.COLOR) f.color = ops.COLOR;
      if (ops.HILIGHT) f.hilight = ops.HILIGHT;
      if (ops.INITIAL !== undefined) f.initial = unquote(ops.INITIAL);
      if (ops.JUSTIFY) f.justify = list(ops.JUSTIFY);
      current.fields.push(f);
    }
  }
  return { mapset: mapset ?? null, path, maps };
}

export function generate() {
  const layouts = {};
  for (const file of bmsFiles()) {
    const parsed = parseBms(readFileSync(file, 'latin1'), relative(repo, file).split('\\').join('/'));
    layouts[parsed.mapset] = parsed;
  }
  return layouts;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const layouts = generate();
  writeFileSync(out, JSON.stringify(layouts, null, 1) + '\n');
  console.log(`bms-layouts.json: ${Object.keys(layouts).length} mapsets`);
}
