import { act, fireEvent, render, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { App, buildFields } from '../../src/App';
import type { ScreenApi, ScreenResult, ScreenState } from '../../src/api/client';
import type { ScreenIdName } from '../../src/screens/registry';

const st = (screen: ScreenIdName, extra: Partial<ScreenState> = {}): ScreenResult => ({
  ok: true,
  state: { screen, messages: [], fieldErrors: [], ...extra },
});

function fakeApi(post: ScreenApi['post'], get?: ScreenApi['get']): ScreenApi {
  return {
    get: vi.fn(get ?? (async (id) => st(id, { data: { TRNNAME: id } }))),
    post: vi.fn(post),
  };
}

const flagsOff = { authorization: false, trantype: false };
const mapOf = (c: HTMLElement) => c.querySelector('[data-map]')?.getAttribute('data-map');

describe('App (pseudo-conversational flow)', () => {
  it('starts on COSGN00 with a GET (first send)', async () => {
    const api = fakeApi(async () => st('COSGN00'));
    const { container } = render(<App api={api} flags={flagsOff} />);
    await waitFor(() => expect(mapOf(container)).toBe('COSGN0A'));
    expect(api.get).toHaveBeenCalledWith('COSGN00');
  });

  it('posts the AID key + unprotected fields and XCTLs to nextScreen', async () => {
    const api = fakeApi(async () => st('COSGN00', { nextScreen: 'COMEN01' }));
    const { container } = render(<App api={api} flags={flagsOff} />);
    await waitFor(() => expect(mapOf(container)).toBe('COSGN0A'));
    fireEvent.change(container.querySelector('input[name="USERID"]')!, { target: { value: 'USER0001' } });
    fireEvent.change(container.querySelector('input[name="PASSWD"]')!, { target: { value: 'PASSWORD' } });
    await act(async () => {
      fireEvent.keyDown(container.querySelector('input[name="PASSWD"]')!, { key: 'Enter' });
    });
    expect(api.post).toHaveBeenCalledWith('COSGN00', 'ENTER', { USERID: 'USER0001', PASSWD: 'PASSWORD' });
    await waitFor(() => expect(mapOf(container)).toBe('COMEN1A'));
    expect(api.get).toHaveBeenLastCalledWith('COMEN01');
  });

  it('stays on the same screen and shows ERRMSG when nextScreen is null', async () => {
    const api = fakeApi(async () =>
      st('COSGN00', { messages: [{ severity: 'ERROR', text: 'Wrong Password. Try again ...' }], fieldErrors: [{ field: 'PASSWD', message: 'x' }] }),
    );
    const { container } = render(<App api={api} flags={flagsOff} />);
    await waitFor(() => expect(mapOf(container)).toBe('COSGN0A'));
    await act(async () => {
      fireEvent.click(container.querySelector('button')!); // ENTER
    });
    await waitFor(() => expect(container.querySelector('[data-field="ERRMSG"]')!.textContent).toBe('Wrong Password. Try again ...'));
    expect(container.querySelector('input[name="PASSWD"]')!.getAttribute('aria-invalid')).toBe('true');
  });

  it('blocks X1/X2 screens while their flag is off', async () => {
    const api = fakeApi(async () => st('COMEN01', { nextScreen: 'COPAU00' }));
    const { container } = render(<App api={api} flags={flagsOff} />);
    await waitFor(() => expect(mapOf(container)).toBe('COSGN0A'));
    await act(async () => {
      fireEvent.keyDown(container.querySelector('input')!, { key: 'Enter' });
    });
    expect(mapOf(container)).toBe('COSGN0A');
    expect(container.querySelector('[data-field="ERRMSG"]')!.textContent).toContain('CPVS is not enabled');
    expect(api.get).not.toHaveBeenCalledWith('COPAU00');
  });

  it('opens X1 screens when the authorization flag is on', async () => {
    const api = fakeApi(async () => st('COMEN01', { nextScreen: 'COPAU00' }));
    const { container } = render(<App api={api} flags={{ authorization: true, trantype: false }} />);
    await waitFor(() => expect(mapOf(container)).toBe('COSGN0A'));
    await act(async () => {
      fireEvent.keyDown(container.querySelector('input')!, { key: 'Enter' });
    });
    await waitFor(() => expect(mapOf(container)).toBe('COPAU0A'));
  });

  it('returns to sign-on on 401 (session expired)', async () => {
    let first = true;
    const api = fakeApi(
      async () => ({ ok: false, status: 401, reason: 'unauthorized' }),
      async (id) => {
        if (id === 'COSGN00' && first) {
          first = false;
          return st('COSGN00', { nextScreen: 'COMEN01' });
        }
        return st(id);
      },
    );
    // first GET (COSGN00) navigates to the menu, POST there answers 401
    const { container } = render(<App api={api} flags={flagsOff} />);
    await waitFor(() => expect(mapOf(container)).toBe('COSGN0A'));
    api.post = vi.fn(async () => st('COSGN00', { nextScreen: 'COMEN01' }));
    await act(async () => {
      fireEvent.keyDown(container.querySelector('input')!, { key: 'Enter' });
    });
    await waitFor(() => expect(mapOf(container)).toBe('COMEN1A'));
    api.post = vi.fn(async () => ({ ok: false as const, status: 401, reason: 'unauthorized' as const }));
    await act(async () => {
      fireEvent.keyDown(container.querySelector('input')!, { key: 'Enter' });
    });
    await waitFor(() => expect(mapOf(container)).toBe('COSGN0A'));
    expect(container.querySelector('[data-field="ERRMSG"]')!.textContent).toBe('Please sign on first');
  });

  it('shows a network error on the ERRMSG line', async () => {
    const api = fakeApi(async () => ({ ok: false, status: 0, reason: 'error' }));
    const { container } = render(<App api={api} flags={flagsOff} />);
    await waitFor(() => expect(mapOf(container)).toBe('COSGN0A'));
    await act(async () => {
      fireEvent.keyDown(container.querySelector('input')!, { key: 'F3' });
    });
    expect(container.querySelector('[data-field="ERRMSG"]')!.textContent).toContain('Unable to reach the server');
  });
});

describe('buildFields', () => {
  it('keeps typed values on a same-screen response that omits data', async () => {
    const api = fakeApi(async () => st('COSGN00', { messages: [{ severity: 'ERROR', text: 'Please enter Password ...' }] }));
    const { container } = render(<App api={api} flags={flagsOff} />);
    await waitFor(() => expect(mapOf(container)).toBe('COSGN0A'));
    fireEvent.change(container.querySelector('input[name="USERID"]')!, { target: { value: 'USER0001' } });
    await act(async () => {
      fireEvent.click(container.querySelector('button')!);
    });
    await waitFor(() => expect(container.querySelector('[data-field="ERRMSG"]')!.textContent).toBe('Please enter Password ...'));
    expect((container.querySelector('input[name="USERID"]') as HTMLInputElement).value).toBe('USER0001');
  });

  it('keeps the current map when an XCTL first send fails and retries the GET on ENTER', async () => {
    let fail = true;
    const api = fakeApi(
      async () => st('COSGN00', { nextScreen: 'COMEN01' }),
      async (id) => (id === 'COMEN01' && fail ? { ok: false, status: 503, reason: 'error' } : st(id)),
    );
    const { container } = render(<App api={api} flags={flagsOff} />);
    await waitFor(() => expect(mapOf(container)).toBe('COSGN0A'));
    await act(async () => {
      fireEvent.click(container.querySelector('button')!); // ENTER
    });
    await waitFor(() => expect(container.querySelector('[data-field="ERRMSG"]')!.textContent).toContain('503'));
    expect(mapOf(container)).toBe('COSGN0A');
    fail = false;
    await act(async () => {
      fireEvent.click(container.querySelector('button')!); // ENTER = retry
    });
    expect(api.post).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(mapOf(container)).toBe('COMEN1A'));
  });

  it('lets PF keys act on the current map after a failed first send, and does not retry a 403', async () => {
    const api = fakeApi(
      async () => st('COSGN00', { nextScreen: 'COMEN01' }),
      async (id) => (id === 'COMEN01' ? { ok: false, status: 403, reason: 'forbidden' } : st(id)),
    );
    const { container } = render(<App api={api} flags={flagsOff} />);
    await waitFor(() => expect(mapOf(container)).toBe('COSGN0A'));
    await act(async () => {
      fireEvent.click(container.querySelector('button')!); // ENTER
    });
    await waitFor(() => expect(container.querySelector('[data-field="ERRMSG"]')!.textContent).toContain('not authorized'));
    expect(container.querySelector('[data-field="ERRMSG"]')!.textContent).not.toContain('retry');
    await act(async () => {
      fireEvent.keyDown(container.querySelector('input[name="USERID"]')!, { key: 'F3' });
    });
    expect(api.post).toHaveBeenLastCalledWith('COSGN00', 'PF3', expect.anything());
  });

  it('clears inputs when a same-screen response carries data without them (SEND MAP of a cleared map)', async () => {
    const api = fakeApi(async () => st('COSGN00', { data: {} }));
    const { container } = render(<App api={api} flags={flagsOff} />);
    await waitFor(() => expect(mapOf(container)).toBe('COSGN0A'));
    fireEvent.change(container.querySelector('input[name="USERID"]')!, { target: { value: 'USER0001' } });
    await act(async () => {
      fireEvent.keyDown(container.querySelector('input[name="USERID"]')!, { key: 'F4' });
    });
    await waitFor(() => expect((container.querySelector('input[name="USERID"]') as HTMLInputElement).value).toBe(''));
  });

  it('treats an initial 401 on COSGN00 as a sign-on prompt, not an outage', async () => {
    const api = fakeApi(async () => st('COSGN00'), async () => ({ ok: false, status: 401, reason: 'unauthorized' }));
    const { container } = render(<App api={api} flags={flagsOff} />);
    await waitFor(() => expect(api.get).toHaveBeenCalled());
    expect(mapOf(container)).toBe('COSGN0A');
    expect(container.querySelector('[data-field="ERRMSG"]')!.textContent).not.toContain('Unable');
  });

  it('sends only unprotected fields, trimmed and capped to LENGTH', () => {
    expect(buildFields('COSGN00', { USERID: 'ADMIN001XYZ', PASSWD: 'PASS   ', TITLE01: 'x' })).toEqual({
      USERID: 'ADMIN001',
      PASSWD: 'PASS',
    });
  });
});
