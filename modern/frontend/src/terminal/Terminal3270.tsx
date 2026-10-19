import { useEffect, useMemo, useRef } from 'react';
import type { KeyboardEvent } from 'react';
import { actionForKey, type Action } from '../keyboard/pfkeys';
import type { FieldError } from '../api/client';
import {
  colorOf,
  displayValue,
  hasInitialCursor,
  isBright,
  isDark,
  isInput,
  isNumeric,
  type BmsField,
  type BmsMap,
} from './layout';

export interface Terminal3270Props {
  map: BmsMap;
  /** Output values by BMS field name (data of the ScreenResponse). */
  output: Record<string, string | undefined>;
  /** Current input values by BMS field name. */
  input: Record<string, string>;
  fieldErrors: FieldError[];
  onInput(name: string, value: string): void;
  onAid(action: Action): void;
  busy?: boolean;
}

const fieldKey = (f: BmsField, i: number) => f.name ?? `lit-${i}-${f.row}-${f.col}`;

export function Terminal3270({ map, output, input, fieldErrors, onInput, onAid, busy }: Terminal3270Props) {
  const ref = useRef<HTMLDivElement>(null);
  const errored = useMemo(() => new Set(fieldErrors.map((e) => e.field)), [fieldErrors]);
  const inputs = useMemo(
    () => map.fields.filter((f) => f.name && isInput(f)).sort((a, b) => a.row - b.row || a.col - b.col),
    [map],
  );

  // Cursor: first field in error, else IC field, else first unprotected field.
  useEffect(() => {
    const target = inputs.find((f) => errored.has(f.name!)) ?? inputs.find(hasInitialCursor) ?? inputs[0];
    if (target) ref.current?.querySelector<HTMLInputElement>(`input[name="${target.name}"]`)?.focus();
  }, [map, inputs, errored]);

  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    const aid = actionForKey(e);
    if (!aid) return;
    e.preventDefault();
    if (!busy) onAid(aid);
  };

  const autoSkip = (f: BmsField, value: string) => {
    if (value.length < f.length) return;
    const next = inputs[inputs.indexOf(f) + 1];
    if (next) ref.current?.querySelector<HTMLInputElement>(`input[name="${next.name}"]`)?.focus();
  };

  return (
    <div
      ref={ref}
      className="t3270"
      role="application"
      aria-label={`3270 map ${map.name}`}
      aria-busy={busy || undefined}
      data-map={map.name}
      style={{ ['--rows' as string]: map.rows, ['--cols' as string]: map.cols }}
      onKeyDown={onKeyDown}
    >
      {map.fields.map((f, i) => {
        const style = {
          top: `calc(${f.row - 1} * var(--line))`,
          left: `${f.col}ch`,
          width: `${Math.min(f.length, map.cols - f.col)}ch`,
        };
        const cls = [
          'fld',
          `c-${colorOf(f).toLowerCase()}`,
          isBright(f) ? 'brt' : '',
          f.hilight === 'UNDERLINE' ? 'ul' : '',
          f.hilight === 'REVERSE' ? 'rev' : '',
          f.hilight === 'BLINK' ? 'blink' : '',
          f.name && errored.has(f.name) ? 'err' : '',
        ]
          .filter(Boolean)
          .join(' ');
        const key = fieldKey(f, i);
        if (f.name && isInput(f)) {
          const err = fieldErrors.find((e) => e.field === f.name);
          return (
            <input
              key={key}
              name={f.name}
              className={cls}
              style={style}
              type={isDark(f) ? 'password' : 'text'}
              inputMode={isNumeric(f) ? 'numeric' : undefined}
              maxLength={f.length}
              size={f.length}
              spellCheck={false}
              autoComplete="off"
              aria-label={f.name}
              aria-invalid={err ? true : undefined}
              title={err?.message}
              data-row={f.row}
              data-col={f.col}
              value={input[f.name] ?? ''}
              onChange={(e) => {
                const v = isNumeric(f) ? e.target.value.replace(/[^0-9.\-]/g, '') : e.target.value;
                onInput(f.name!, v);
                autoSkip(f, v);
              }}
            />
          );
        }
        const value = f.name ? output[f.name] : undefined;
        // DRK output fields (e.g. FKEY05) stay hidden until the program sends a value for them.
        if (isDark(f) && !(value && value.trim())) return null;
        const text = displayValue(f, value);
        return (
          <span key={key} className={cls} style={style} data-field={f.name ?? undefined} data-row={f.row} data-col={f.col}>
            {text}
          </span>
        );
      })}
    </div>
  );
}
