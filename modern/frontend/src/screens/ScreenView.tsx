import type { ScreenState } from '../api/client';
import type { Action } from '../keyboard/pfkeys';
import { Terminal3270 } from '../terminal/Terminal3270';
import { layoutOf } from '../terminal/layout';
import type { ScreenIdName } from './registry';

export interface ScreenViewProps {
  state: ScreenState;
  input: Record<string, string>;
  onInput(name: string, value: string): void;
  onAid(action: Action): void;
  /** Client-side message (network error, disabled extension...) shown on the ERRMSG line. */
  localError?: string;
  busy?: boolean;
}

/** ERRMSG / INFOMSG lines from ScreenResponse.messages (data.ERRMSG wins, like the COBOL MOVE). */
export function messageLines(id: ScreenIdName, state: ScreenState, localError?: string) {
  const map = layoutOf(id);
  const hasInfo = map.fields.some((f) => f.name === 'INFOMSG');
  const errors = state.messages.filter((m) => m.severity === 'ERROR').map((m) => m.text);
  const infos = state.messages.filter((m) => m.severity === 'INFO').map((m) => m.text);
  const err = localError || state.data?.ERRMSG?.trim() || errors[0] || (hasInfo ? '' : infos[0]) || '';
  const info = state.data?.INFOMSG?.trim() || (hasInfo ? infos[0] : '') || '';
  return { ERRMSG: err, ...(hasInfo ? { INFOMSG: info } : {}) };
}

export function makeScreenView(id: ScreenIdName) {
  const map = layoutOf(id);
  function View({ state, input, onInput, onAid, localError, busy }: ScreenViewProps) {
    const output = { ...state.data, ...messageLines(id, state, localError) };
    return (
      <Terminal3270
        map={map}
        output={output}
        input={input}
        fieldErrors={state.fieldErrors}
        onInput={onInput}
        onAid={onAid}
        busy={busy}
      />
    );
  }
  View.displayName = `${id}View`;
  return View;
}
