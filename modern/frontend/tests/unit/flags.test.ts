import { describe, expect, it } from 'vitest';
import { isEnabled, loadFlags } from '../../src/config/flags';
import { screen, SCREENS } from '../../src/screens/registry';

describe('extension flags (X1/X2)', () => {
  it('are off by default', () => {
    const flags = loadFlags({});
    expect(flags).toEqual({ authorization: false, trantype: false });
    expect(SCREENS.filter((s) => isEnabled(s, flags)).map((s) => s.id)).toHaveLength(17);
  });

  it('enable only their own screens', () => {
    const flags = loadFlags({ VITE_CARDDEMO_EXT_AUTHORIZATION: 'true' });
    expect(isEnabled(screen('COPAU00'), flags)).toBe(true);
    expect(isEnabled(screen('COPAU01'), flags)).toBe(true);
    expect(isEnabled(screen('COTRTLI'), flags)).toBe(false);
    expect(isEnabled(screen('COSGN00'), flags)).toBe(true);
  });

  it('runtime config overrides build-time env', () => {
    expect(loadFlags({ VITE_CARDDEMO_EXT_TRANTYPE: '1' }, { trantype: false }).trantype).toBe(false);
    expect(loadFlags({}, { trantype: true }).trantype).toBe(true);
  });
});
