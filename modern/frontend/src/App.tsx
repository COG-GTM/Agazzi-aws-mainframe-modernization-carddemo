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

/**
 * Same-screen redisplay. A response carrying `data` is the full SEND MAP (fields it omits are cleared);
 * a response without `data` (e.g. validation errors only) keeps what the operator typed.
 */
export function mergeInput(id: ScreenIdName, prev: Fields, state: ScreenState): Fields {
  if (state.data) return initialInput(id, state);
  const out: Fields = {};
  for (const f of inputFields(layoutOf(id))) out[f.name!] = prev[f.name!] ?? '';
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
  /** XCTL target whose first send failed with a retryable error: ENTER retries its GET. */
  const [retryTarget, setRetryTarget] = useState<ScreenIdName>();

  const show = useCallback((next: ScreenIdName, s: ScreenState, err?: string) => {
    setId(next);
    setState(s);
    setInput(initialInput(next, s));
    setLocalError(err);
    setRetryTarget(undefined);
  }, []);

  const redisplay = useCallback((current: ScreenIdName, s: ScreenState, err?: string) => {
    setState(s);
    setInput((prev) => mergeInput(current, prev, s));
    setLocalError(err);
  }, []);

  const errorText = (res: Extract<ScreenResult, { ok: false }>): string =>
    res.reason === 'forbidden'
      ? 'You are not authorized for this function'
      : res.reason === 'invalid'
        ? 'Invalid input'
        : `Unable to reach the server (${res.status || 'network'})`;

  /**
   * First send of a map (GET). On failure the current map stays active (PF keys keep working on it);
   * a retryable failure arms `retryTarget` so ENTER re-issues the GET.
   */
  const enter = useCallback(
    async (target: ScreenIdName): Promise<boolean> => {
      const res = await api.get(target);
      if (res.ok) {
        show(target, res.state);
        return true;
      }
      if (res.reason === 'unauthorized' && target !== SIGN_ON) {
        const signOn = await api.get(SIGN_ON);
        show(SIGN_ON, signOn.ok ? signOn.state : emptyState(SIGN_ON), 'Please sign on first');
        return true;
      }
      const retryable = res.reason === 'error';
      setRetryTarget(retryable ? target : undefined);
      setLocalError(`${screen(target).transactionId}: ${errorText(res)}${retryable ? ' - press ENTER to retry' : ''}`);
      return false;
    },
    [api, show],
  );

  const apply = useCallback(
    async (current: ScreenIdName, res: ScreenResult): Promise<void> => {
      if (!res.ok) {
        if (res.reason === 'unauthorized' && current !== SIGN_ON) {
          const signOn = await api.get(SIGN_ON);
          show(SIGN_ON, signOn.ok ? signOn.state : emptyState(SIGN_ON), 'Please sign on first');
          return;
        }
        setLocalError(errorText(res));
        return;
      }
      const target = resolveScreen(res.state.nextScreen);
      if (!target || target.id === current) {
        redisplay(current, res.state);
        return;
      }
      if (!isEnabled(target, flags)) {
        redisplay(current, res.state, `${target.transactionId} is not enabled (extension ${target.extension})`);
        return;
      }
      // XCTL: first send of the target map (GET = PGM-CONTEXT ENTER), session carries the COMMAREA.
      await enter(target.id);
    },
    [api, flags, show, redisplay],
  );

  useEffect(() => {
    let live = true;
    api.get(SIGN_ON).then((r) => {
      if (!live) return;
      if (r.ok) show(SIGN_ON, r.state);
      else if (r.reason !== 'unauthorized') setLocalError(errorText(r));
    });
    return () => {
      live = false;
    };
  }, [api, show]);

  const onAid = useCallback(
    async (action: Action) => {
      setBusy(true);
      try {
        const retry = retryTarget;
        setRetryTarget(undefined);
        if (retry && action === 'ENTER') await enter(retry);
        else await apply(id, await api.post(id, action, buildFields(id, input)));
      } finally {
        setBusy(false);
      }
    },
    [api, apply, enter, id, input, retryTarget],
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
