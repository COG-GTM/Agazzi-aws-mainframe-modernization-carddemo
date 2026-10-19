import createClient from 'openapi-fetch';
import type { components, paths } from '../generated/api';
import type { Action } from '../keyboard/pfkeys';
import { screenPath, type ScreenIdName } from '../screens/registry';

export type ScreenMessage = components['schemas']['ScreenMessage'];
export type FieldError = components['schemas']['FieldError'];
export type ConversationContext = components['schemas']['ConversationContext'];

/** Common shape of every <Mapset>Response (ScreenResponse<T> in carddemo-contracts). */
export interface ScreenState {
  screen: ScreenIdName;
  data?: Record<string, string | undefined>;
  messages: ScreenMessage[];
  nextScreen?: string | null;
  fieldErrors: FieldError[];
  context?: ConversationContext;
}

export type ScreenResult =
  | { ok: true; state: ScreenState }
  | { ok: false; status: number; reason: 'unauthorized' | 'forbidden' | 'invalid' | 'error'; detail?: string };

export type Fields = Record<string, string>;

export interface ScreenApi {
  get(id: ScreenIdName): Promise<ScreenResult>;
  post(id: ScreenIdName, action: Action, fields: Fields): Promise<ScreenResult>;
}

type ScreenPath = ReturnType<typeof screenPath>;

function reasonOf(status: number): 'unauthorized' | 'forbidden' | 'invalid' | 'error' {
  if (status === 401) return 'unauthorized';
  if (status === 403) return 'forbidden';
  if (status === 400 || status === 422) return 'invalid';
  return 'error';
}

/** Typed client generated from openapi.yaml; the session cookie (ADR-002) travels with credentials: include. */
export function createScreenApi(baseUrl = '', fetchImpl: typeof fetch = (...a) => fetch(...a)): ScreenApi {
  const client = createClient<paths>({ baseUrl, credentials: 'include', fetch: fetchImpl });

  const wrap = async (p: Promise<{ data?: unknown; error?: unknown; response: Response }>): Promise<ScreenResult> => {
    try {
      const { data, error, response } = await p;
      if (response.ok && data) return { ok: true, state: data as ScreenState };
      return {
        ok: false,
        status: response.status,
        reason: reasonOf(response.status),
        detail: error ? JSON.stringify(error) : response.statusText,
      };
    } catch (e) {
      return { ok: false, status: 0, reason: 'error', detail: String(e) };
    }
  };

  return {
    get: (id) => wrap(client.GET(screenPath(id) as ScreenPath, {} as never)),
    post: (id, action, fields) =>
      wrap(client.POST(screenPath(id) as ScreenPath, { body: { action, fields } } as never)),
  };
}
