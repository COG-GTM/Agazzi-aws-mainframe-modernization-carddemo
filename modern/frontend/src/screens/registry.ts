// Mirror of com.carddemo.contracts.screen.ScreenId (contracts-v1); kept in sync by tests/unit/registry.test.ts.
export type Extension = 'authorization' | 'trantype';

export interface ScreenDef {
  id: ScreenIdName;
  map: string;
  transactionId: string;
  program: string;
  workstream: string;
  optional: boolean;
  extension?: Extension;
}

export const SCREENS = [
  { id: 'COACTUP', map: 'CACTUPA', transactionId: 'CAUP', program: 'COACTUPC', workstream: 'o2-account', optional: false },
  { id: 'COACTVW', map: 'CACTVWA', transactionId: 'CAVW', program: 'COACTVWC', workstream: 'o2-account', optional: false },
  { id: 'COADM01', map: 'COADM1A', transactionId: 'CA00', program: 'COADM01C', workstream: 'o1-auth', optional: false },
  { id: 'COBIL00', map: 'COBIL0A', transactionId: 'CB00', program: 'COBIL00C', workstream: 'o4-transaction', optional: false },
  { id: 'COCRDLI', map: 'CCRDLIA', transactionId: 'CCLI', program: 'COCRDLIC', workstream: 'o3-card', optional: false },
  { id: 'COCRDSL', map: 'CCRDSLA', transactionId: 'CCDL', program: 'COCRDSLC', workstream: 'o3-card', optional: false },
  { id: 'COCRDUP', map: 'CCRDUPA', transactionId: 'CCUP', program: 'COCRDUPC', workstream: 'o3-card', optional: false },
  { id: 'COMEN01', map: 'COMEN1A', transactionId: 'CM00', program: 'COMEN01C', workstream: 'o1-auth', optional: false },
  { id: 'CORPT00', map: 'CORPT0A', transactionId: 'CR00', program: 'CORPT00C', workstream: 'o4-transaction', optional: false },
  { id: 'COSGN00', map: 'COSGN0A', transactionId: 'CC00', program: 'COSGN00C', workstream: 'o1-auth', optional: false },
  { id: 'COTRN00', map: 'COTRN0A', transactionId: 'CT00', program: 'COTRN00C', workstream: 'o4-transaction', optional: false },
  { id: 'COTRN01', map: 'COTRN1A', transactionId: 'CT01', program: 'COTRN01C', workstream: 'o4-transaction', optional: false },
  { id: 'COTRN02', map: 'COTRN2A', transactionId: 'CT02', program: 'COTRN02C', workstream: 'o4-transaction', optional: false },
  { id: 'COUSR00', map: 'COUSR0A', transactionId: 'CU00', program: 'COUSR00C', workstream: 'o5-useradmin', optional: false },
  { id: 'COUSR01', map: 'COUSR1A', transactionId: 'CU01', program: 'COUSR01C', workstream: 'o5-useradmin', optional: false },
  { id: 'COUSR02', map: 'COUSR2A', transactionId: 'CU02', program: 'COUSR02C', workstream: 'o5-useradmin', optional: false },
  { id: 'COUSR03', map: 'COUSR3A', transactionId: 'CU03', program: 'COUSR03C', workstream: 'o5-useradmin', optional: false },
  { id: 'COPAU00', map: 'COPAU0A', transactionId: 'CPVS', program: 'COPAUS0C', workstream: 'x1-authorization', optional: true, extension: 'authorization' },
  { id: 'COPAU01', map: 'COPAU1A', transactionId: 'CPVD', program: 'COPAUS1C', workstream: 'x1-authorization', optional: true, extension: 'authorization' },
  { id: 'COTRTLI', map: 'CTRTLIA', transactionId: 'CTLI', program: 'COTRTLIC', workstream: 'x2-trantype', optional: true, extension: 'trantype' },
  { id: 'COTRTUP', map: 'CTRTUPA', transactionId: 'CTTU', program: 'COTRTUPC', workstream: 'x2-trantype', optional: true, extension: 'trantype' },
] as const satisfies readonly (Omit<ScreenDef, 'id'> & { id: string })[];

export type ScreenIdName = (typeof SCREENS)[number]['id'];

export const SIGN_ON: ScreenIdName = 'COSGN00';

const byId = new Map<string, ScreenDef>(SCREENS.map((s) => [s.id, s as ScreenDef]));

export function screen(id: ScreenIdName): ScreenDef {
  return byId.get(id)!;
}

export function screenPath(id: ScreenIdName): `/api/v1/screens/${Lowercase<ScreenIdName>}` {
  return `/api/v1/screens/${id.toLowerCase() as Lowercase<ScreenIdName>}`;
}

/**
 * `nextScreen` is the XCTL target: accept mapset (ScreenId), map, program or transaction id.
 * Blank/null = stay on the same screen.
 */
export function resolveScreen(target: string | null | undefined): ScreenDef | undefined {
  const t = target?.trim().toUpperCase();
  if (!t) return undefined;
  return SCREENS.find((s) => s.id === t || s.map === t || s.program === t || s.transactionId === t) as
    | ScreenDef
    | undefined;
}
