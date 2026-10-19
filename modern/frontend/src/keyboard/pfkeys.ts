import type { components } from '../generated/api';

export type Action = components['schemas']['Action'];

export interface KeyLike {
  key: string;
  altKey?: boolean;
  ctrlKey?: boolean;
  shiftKey?: boolean;
  metaKey?: boolean;
}

/**
 * Keyboard -> DFHAID: Enter=ENTER, Esc/Pause=CLEAR, F1..F12=PF1..PF12,
 * Alt+1/Alt+2 = PA1/PA2, Alt+F1..F12 = PF1..PF12 (for browsers that reserve bare F-keys).
 */
export function actionForKey(e: KeyLike): Action | undefined {
  if (e.metaKey || e.ctrlKey) return undefined;
  if (e.key === 'Enter' && !e.altKey && !e.shiftKey) return 'ENTER';
  if (e.key === 'Escape' || e.key === 'Pause') return 'CLEAR';
  if (e.altKey && e.key === '1') return 'PA1';
  if (e.altKey && e.key === '2') return 'PA2';
  const f = /^F([1-9]|1[0-2])$/.exec(e.key);
  if (f && !e.shiftKey) return `PF${f[1]}` as Action;
  return undefined;
}

export const AID_KEYS: readonly Action[] = [
  'ENTER', 'CLEAR', 'PA1', 'PA2',
  'PF1', 'PF2', 'PF3', 'PF4', 'PF5', 'PF6', 'PF7', 'PF8', 'PF9', 'PF10', 'PF11', 'PF12',
];
