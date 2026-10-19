import type { Extension, ScreenDef } from '../screens/registry';

/** Mirrors carddemo.extensions.<name>.enabled: X1/X2 screens are off unless the flag is on. */
export type Flags = Record<Extension, boolean>;

declare global {
  interface Window {
    __CARDDEMO_FLAGS__?: Partial<Flags>;
  }
}

const truthy = (v: unknown) => v === true || v === 'true' || v === '1';

export function loadFlags(env: Record<string, unknown> = import.meta.env, runtime?: Partial<Flags>): Flags {
  const rt = runtime ?? (typeof window !== 'undefined' ? window.__CARDDEMO_FLAGS__ : undefined) ?? {};
  return {
    authorization: rt.authorization ?? truthy(env.VITE_CARDDEMO_EXT_AUTHORIZATION),
    trantype: rt.trantype ?? truthy(env.VITE_CARDDEMO_EXT_TRANTYPE),
  };
}

export function isEnabled(s: ScreenDef, flags: Flags): boolean {
  return !s.optional || (s.extension !== undefined && flags[s.extension]);
}
