// Thin client for the contracts-v1 screen API (openapi.yaml): GET = first send of the BMS map,
// POST = terminal input {action, fields}. The session cookie returned by CC00 sign-on is kept in
// the per-VU cookie jar, as a 3270 terminal keeps its CICS session.
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { BASE_URL, FIXTURES, PASSWORD } from './config.js';

const JSON_HEADERS = { 'Content-Type': 'application/json', Accept: 'application/json' };

export function screenGet(screen, tx) {
  const r = http.get(`${BASE_URL}/api/v1/screens/${screen}`, {
    headers: JSON_HEADERS,
    tags: { tx, screen, name: `GET ${screen}` },
  });
  okScreen(r, screen);
  return r;
}

/** CSRF double-submit (O1 chain): echo the XSRF-TOKEN cookie of the VU jar in X-XSRF-TOKEN. */
function postHeaders() {
  const jar = http.cookieJar().cookiesForURL(BASE_URL);
  const token = jar['XSRF-TOKEN'] && jar['XSRF-TOKEN'][0];
  return token ? { ...JSON_HEADERS, 'X-XSRF-TOKEN': token } : JSON_HEADERS;
}

export function screenPost(screen, tx, action, fields) {
  const r = http.post(
    `${BASE_URL}/api/v1/screens/${screen}`,
    JSON.stringify({ action, fields: fields || {} }),
    { headers: postHeaders(), tags: { tx, screen, name: `POST ${screen} ${action}` } },
  );
  okScreen(r, screen);
  return r;
}

export function okScreen(r, screen) {
  return check(r, {
    [`${screen} 200`]: (x) => x.status === 200,
    [`${screen} body`]: (x) => body(x) !== null && body(x).screen === screen.toUpperCase(),
    // nextScreen COSGN00 on any other map = no COMMAREA in the session (EIBCALEN = 0 / not signed on)
    [`${screen} in session`]: (x) =>
      screen === 'cosgn00' || (body(x) !== null && body(x).nextScreen !== 'COSGN00'),
  });
}

function body(r) {
  try {
    return r.json();
  } catch (e) {
    return null;
  }
}

/** Business-level assertions on a screen response, e.g. {'CT02 posted': messageMatches(/added/)}. */
export function expectBody(r, checks) {
  const b = body(r);
  const named = {};
  for (const [name, pred] of Object.entries(checks)) {
    named[name] = () => b !== null && pred(b);
  }
  return check(r, named);
}

export const noFieldErrors = (b) => Array.isArray(b.fieldErrors) && b.fieldErrors.length === 0;

export function messageMatches(re) {
  return (b) => (b.messages || []).some((m) => re.test(m.text));
}

/** CC00: sign-on through COSGN00 (public endpoint); establishes the session cookie. */
export function signOn(userId) {
  http.cookieJar().clear(BASE_URL);
  screenGet('cosgn00', 'CC00'); // first send of the map: sets the XSRF-TOKEN cookie
  return screenPost('cosgn00', 'CC00', 'ENTER', { USERID: userId, PASSWD: PASSWORD });
}

const SAMPLE_ACCOUNTS = Array.from({ length: 50 }, (_, i) => ({
  acctId: String(i + 1).padStart(11, '0'),
  cardNum: '',
  custId: String(i + 1).padStart(9, '0'),
}));

export const fixtures = new SharedArray('fixtures', () => {
  if (!FIXTURES) {
    return [{ accounts: SAMPLE_ACCOUNTS, tranIds: [] }];
  }
  const f = JSON.parse(open(FIXTURES));
  return [{ accounts: f.accounts, tranIds: f.tranIds }];
});

export function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}
