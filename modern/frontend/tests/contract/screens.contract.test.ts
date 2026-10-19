import { describe, expect, inject, it } from 'vitest';
import { buildFields } from '../../src/App';
import { createScreenApi } from '../../src/api/client';
import { AID_KEYS } from '../../src/keyboard/pfkeys';
import { SCREENS, screenPath } from '../../src/screens/registry';
import { inputFields, layoutOf } from '../../src/terminal/layout';

const base = inject('prismUrl');
const api = createScreenApi(base);

/** A value of exactly LENGTH characters for every unprotected field (worst case for maxLength). */
function fullInput(id: (typeof SCREENS)[number]['id']) {
  const v: Record<string, string> = {};
  for (const f of inputFields(layoutOf(id))) v[f.name!] = (f.attrb.includes('NUM') ? '9' : 'X').repeat(f.length);
  return buildFields(id, v);
}

describe('contract: generated client vs Prism mock of openapi.yaml', () => {
  for (const s of SCREENS) {
    it(`${s.id}: GET first send returns a ${s.id} ScreenResponse`, async () => {
      const r = await api.get(s.id);
      expect(r.ok).toBe(true);
      if (!r.ok) return;
      expect(r.state.screen).toBe(s.id);
      expect(Array.isArray(r.state.messages)).toBe(true);
      expect(Array.isArray(r.state.fieldErrors)).toBe(true);
    });

    it(`${s.id}: POST with every AID key and full-length fields is a valid request`, async () => {
      for (const action of AID_KEYS) {
        const r = await api.post(s.id, action, fullInput(s.id));
        expect(r.ok, `${s.id} ${action} ${!r.ok ? r.detail : ''}`).toBe(true);
        if (r.ok) expect(r.state.screen).toBe(s.id);
      }
    });
  }

  it('Prism rejects a field longer than the BMS LENGTH (request validation is active)', async () => {
    const r = await api.post('COSGN00', 'ENTER', { USERID: 'TOOLONGUSER', PASSWD: 'x' });
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.reason).toBe('invalid');
  });

  it('Prism rejects an unknown AID key', async () => {
    const res = await fetch(base + screenPath('COSGN00'), {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ action: 'PF24', fields: {} }),
    });
    expect(res.status).toBe(422);
  });

  it('maps 401 (not signed on) and 403 (non-admin) responses', async () => {
    const preferCode = (code: number) =>
      createScreenApi(base, (input, init) => {
        const req = new Request(input, init);
        req.headers.set('Prefer', `code=${code}`);
        return fetch(req);
      });
    const r401 = await preferCode(401).get('COADM01');
    expect(r401).toMatchObject({ ok: false, status: 401, reason: 'unauthorized' });
    const r403 = await preferCode(403).post('COUSR00', 'ENTER', fullInput('COUSR00'));
    expect(r403).toMatchObject({ ok: false, status: 403, reason: 'forbidden' });
  });
});
