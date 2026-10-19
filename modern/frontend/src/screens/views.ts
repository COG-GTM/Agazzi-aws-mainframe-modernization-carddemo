import { makeScreenView } from './ScreenView';
import type { ScreenIdName } from './registry';

// One view per ScreenId (BMS mapset); X1 (COPAU*) and X2 (COTRT*) are reachable only with their flag on.
export const VIEWS = {
  COACTUP: makeScreenView('COACTUP'),
  COACTVW: makeScreenView('COACTVW'),
  COADM01: makeScreenView('COADM01'),
  COBIL00: makeScreenView('COBIL00'),
  COCRDLI: makeScreenView('COCRDLI'),
  COCRDSL: makeScreenView('COCRDSL'),
  COCRDUP: makeScreenView('COCRDUP'),
  COMEN01: makeScreenView('COMEN01'),
  CORPT00: makeScreenView('CORPT00'),
  COSGN00: makeScreenView('COSGN00'),
  COTRN00: makeScreenView('COTRN00'),
  COTRN01: makeScreenView('COTRN01'),
  COTRN02: makeScreenView('COTRN02'),
  COUSR00: makeScreenView('COUSR00'),
  COUSR01: makeScreenView('COUSR01'),
  COUSR02: makeScreenView('COUSR02'),
  COUSR03: makeScreenView('COUSR03'),
  COPAU00: makeScreenView('COPAU00'),
  COPAU01: makeScreenView('COPAU01'),
  COTRTLI: makeScreenView('COTRTLI'),
  COTRTUP: makeScreenView('COTRTUP'),
} satisfies Record<ScreenIdName, ReturnType<typeof makeScreenView>>;
