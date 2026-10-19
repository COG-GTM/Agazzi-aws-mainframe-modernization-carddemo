import { useCallback, useEffect, useMemo, useState } from 'react';
import { createScreenApi, type Fields, type ScreenApi, type ScreenResult, type ScreenState } from './api/client';
import { isEnabled, loadFlags, type Flags } from './config/flags';
import { AID_KEYS, type Action } from './keyboard/pfkeys';
import { resolveScreen, screen, SIGN_ON, type ScreenIdName } from './screens/registry';
import { VIEWS } from './screens/views';
import { inputFields, layoutOf } from './terminal/layout';

export interface AppProps {
  api?: ScreenApi;
  flags?: Flags;
}

const emptyState = (id: ScreenIdName): ScreenState => ({ screen: id, messages: [], fieldErrors: [] });

/** Input values for the map: unprotected fields pre-filled from the response data (FSET/MDT semantics). */
export function initialInput(id: ScreenIdName, state: ScreenState): Fields {
  const out: Fields = {};
  for (const f of inputFields(layoutOf(id))) out[f.name!] = (state.data?.[f.name!] ?? '').trimEnd().slice(0, f.length);
  return out;
}

/** Request `fields` = every unprotected field of the map, trimmed and capped to the BMS LENGTH. */
export function buildFields(id: ScreenIdName, input: Fields): Fields {
  const out: Fields = {};
  for (const f of inputFields(layoutOf(id))) out[f.name!] = (input[f.name!] ?? '').trimEnd().slice(0, f.length);
  return out;
}

export function App({ api: apiProp, flags: flagsProp }: AppProps) {
  const api = useMemo(() => apiProp ?? createScreenApi(import.meta.env.VITE_API_BASE ?? ''), [apiProp]);
  const flags = useMemo(() => flagsProp ?? loadFlags(), [flagsProp]);
  const [id, setId] = useState<ScreenIdName>(SIGN_ON);
  const [state, setState] = useState<ScreenState>(emptyState(SIGN_ON));
  const [input, setInput] = useState<Fields>({});
  const [localError, setLocalError] = useState<string>();
  const [busy, setBusy] = useState(false);

  const show = useCallback((next: ScreenIdName, s: ScreenState, err?: string) => {
    setId(next);
    setState(s);
    setInput(initialInput(next, s));
    setLocalError(err);
  }, []);

  const apply = useCallback(
    async (current: ScreenIdName, res: ScreenResult): Promise<void> => {
      if (!res.ok) {
        if (res.reason === 'unauthorized' && current !== SIGN_ON) {
          const signOn = await api.get(SIGN_ON);
          show(SIGN_ON, signOn.ok ? signOn.state : emptyState(SIGN_ON), 'Please sign on first');
          return;
        }
        const msg =
          res.reason === 'forbidden'
            ? 'You are not authorized for this function'
            : res.reason === 'invalid'
              ? 'Invalid input'
              : `Unable to reach the server (${res.status || 'network'})`;
        setLocalError(msg);
        return;
      }
      const target = resolveScreen(res.state.nextScreen);
      if (!target || target.id === current) {
        show(current, res.state);
        return;
      }
      if (!isEnabled(target, flags)) {
        show(current, res.state, `${target.transactionId} is not enabled (extension ${target.extension})`);
        return;
      }
      // XCTL: first send of the target map (GET = PGM-CONTEXT ENTER), session carries the COMMAREA.
      const next = await api.get(target.id);
      if (next.ok) show(target.id, next.state);
      else await apply(target.id, next);
    },
    [api, flags, show],
  );

  useEffect(() => {
    let live = true;
    api.get(SIGN_ON).then((r) => live && (r.ok ? show(SIGN_ON, r.state) : setLocalError('Unable to reach the server')));
    return () => {
      live = false;
    };
  }, [api, show]);

  const onAid = useCallback(
    async (action: Action) => {
      setBusy(true);
      try {
        await apply(id, await api.post(id, action, buildFields(id, input)));
      } finally {
        setBusy(false);
      }
    },
    [api, apply, id, input],
  );

  const View = VIEWS[id];
  const def = screen(id);
  return (
    <main className="app">
      <View
        state={state}
        input={input}
        onInput={(name, value) => setInput((p) => ({ ...p, [name]: value }))}
        onAid={onAid}
        localError={localError}
        busy={busy}
      />
      <nav className="aid-bar" aria-label="AID keys">
        {AID_KEYS.map((k) => (
          <button key={k} type="button" disabled={busy} onClick={() => onAid(k)}>
            {k}
          </button>
        ))}
        <span className="status" data-testid="status">
          {def.transactionId} {def.program}
        </span>
      </nav>
    </main>
  );
}
