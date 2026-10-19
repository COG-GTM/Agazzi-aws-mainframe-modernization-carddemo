// 5B online load: weighted mix of the CardDemo transactions on the contracts-v1 screen API.
// Each VU signs on once (CC00) and keeps its session, then loops over the mix; CC00 is also
// re-measured every SIGNON_EVERY iterations. Tags: tx=<CICS transid>, screen=<program map>.
//
//   k6 run -e BASE_URL=http://localhost:8080 -e VUS=200 -e DURATION=5m \
//          -e FIXTURES=$PWD/modern/target/perf-data/k6/fixtures.json modern/perf/k6/online-mix.js
//
// MIX selects transactions and weights, e.g. -e MIX=CAVW:3,CCLI:2 (default below).
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';
import { ADMINS, THINK_TIME_S, USERS, VUS, int, rampingStages, thresholds } from './lib/config.js';
import {
  expectBody,
  fixtures,
  messageMatches,
  noFieldErrors,
  pick,
  screenGet,
  screenPost,
  signOn,
} from './lib/carddemo.js';

const DEFAULT_MIX = 'CAVW:20,CCLI:15,CT00:15,CT01:15,CT02:10,CB00:10,CU00:5,CC00:10';
const MIX = (__ENV.MIX || DEFAULT_MIX).split(',').map((p) => {
  const [tx, w] = p.split(':');
  return { tx: tx.trim(), w: parseInt(w || '1', 10) };
});
const SIGNON_EVERY = int('SIGNON_EVERY', 50);
const TXS = MIX.map((m) => m.tx);
// CU00 needs an admin (CDEMO-USRTYP-ADMIN): it runs in its own scenario whose VU count is the CU00
// share of MIX (VUS * w(CU00) / total weight); all other transactions share the user scenario.
const USER_MIX = MIX.filter((m) => m.tx !== 'CU00');
const USER_W = USER_MIX.reduce((s, m) => s + m.w, 0);
const CU00_W = MIX.filter((m) => m.tx === 'CU00').reduce((s, m) => s + m.w, 0);
const ADMIN_VUS = CU00_W > 0 ? Math.max(1, Math.round((VUS * CU00_W) / (USER_W + CU00_W))) : 0;
const USER_VUS = USER_W > 0 ? VUS - ADMIN_VUS : 0;
const payments = new Counter('cb00_payments');

function ramping(exec, target) {
  return { executor: 'ramping-vus', startVUs: 0, stages: rampingStages(target), gracefulRampDown: '10s', exec };
}

const scenarios = {};
if (USER_VUS > 0) {
  scenarios.online = ramping('user', USER_VUS);
}
if (ADMIN_VUS > 0) {
  scenarios.admin = ramping('admin', ADMIN_VUS);
}

export const options = {
  // the CC00 session cookie must survive across iterations (k6 resets the jar per iteration by default)
  noCookiesReset: true,
  scenarios,
  thresholds: thresholds([...new Set([...TXS, 'CC00'])]),
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function chooseTx() {
  let r = Math.random() * USER_W;
  for (const m of USER_MIX) {
    r -= m.w;
    if (r < 0) {
      return m.tx;
    }
  }
  return USER_MIX[USER_MIX.length - 1].tx;
}

function isAdminVu() {
  return exec.scenario.name === 'admin';
}

function userFor() {
  const pool = isAdminVu() ? ADMINS : USERS;
  return pool[exec.vu.idInTest % pool.length];
}

const today = new Date().toISOString().slice(0, 10);

const FLOWS = {
  CC00: () => signOn(userFor()),
  // CAVW: account view by ACCT-ID
  CAVW: (f) => {
    screenGet('coactvw', 'CAVW');
    const r = screenPost('coactvw', 'CAVW', 'ENTER', { ACCTSID: pick(f.accounts).acctId });
    expectBody(r, { 'CAVW no field errors': noFieldErrors, 'CAVW account shown': (b) => !!(b.data && b.data.ACSTTUS) });
  },
  // CCLI: card list filtered by account, then paginated forward/backward (PF8/PF7)
  CCLI: (f) => {
    screenGet('cocrdli', 'CCLI');
    const r = screenPost('cocrdli', 'CCLI', 'ENTER', { ACCTSID: pick(f.accounts).acctId });
    expectBody(r, { 'CCLI no field errors': noFieldErrors });
    screenPost('cocrdli', 'CCLI', 'PF8', {});
    screenPost('cocrdli', 'CCLI', 'PF7', {});
  },
  // CT00: transaction list, browse forward (STARTBR/READNEXT on TRANSACT)
  CT00: (f) => {
    screenGet('cotrn00', 'CT00');
    const from = f.tranIds.length ? pick(f.tranIds) : '';
    const r = screenPost('cotrn00', 'CT00', 'ENTER', { TRNIDIN: from });
    expectBody(r, { 'CT00 no field errors': noFieldErrors });
    screenPost('cotrn00', 'CT00', 'PF8', {});
  },
  // CT01: transaction view by TRAN-ID
  CT01: (f) => {
    const id = f.tranIds.length ? pick(f.tranIds) : '0000000000000001';
    screenGet('cotrn01', 'CT01');
    const r = screenPost('cotrn01', 'CT01', 'ENTER', { TRNIDIN: id });
    expectBody(r, { 'CT01 no field errors': noFieldErrors });
  },
  // CT02: add transaction (validate, then CONFIRM=Y writes TRANSACT)
  CT02: (f) => {
    const a = pick(f.accounts);
    const fields = {
      ACTIDIN: a.acctId,
      TTYPCD: '01',
      TCATCD: '0001',
      TRNSRC: 'POS TERM',
      TDESC: 'k6 load test purchase',
      TRNAMT: '+00000012.34',
      TORIGDT: today,
      TPROCDT: today,
      MID: '000000001',
      MNAME: 'K6 MERCHANT',
      MCITY: 'PERF CITY',
      MZIP: '00000',
    };
    screenGet('cotrn02', 'CT02');
    expectBody(screenPost('cotrn02', 'CT02', 'ENTER', fields), { 'CT02 validated': noFieldErrors });
    const r = screenPost('cotrn02', 'CT02', 'ENTER', { ...fields, CONFIRM: 'Y' });
    expectBody(r, { 'CT02 added': messageMatches(/Transaction added successfully/) });
  },
  // CB00: bill payment (balance shown, then CONFIRM=Y posts the payment)
  CB00: (f) => {
    const acct = pick(f.accounts).acctId;
    screenGet('cobil00', 'CB00');
    screenPost('cobil00', 'CB00', 'ENTER', { ACTIDIN: acct });
    const r = screenPost('cobil00', 'CB00', 'ENTER', { ACTIDIN: acct, CONFIRM: 'Y' });
    // an account already paid off by an earlier iteration answers "You have nothing to pay..."
    expectBody(r, { 'CB00 paid or nothing due': messageMatches(/Payment successful|nothing to pay/) });
    try {
      if (messageMatches(/Payment successful/)(r.json())) {
        payments.add(1);
      }
    } catch (e) {
      // non-JSON body already counted as a failed check
    }
  },
  // CU00: user list (admin), page forward
  CU00: () => {
    screenGet('cousr00', 'CU00');
    screenPost('cousr00', 'CU00', 'PF8', {});
  },
};

function iterate(tx) {
  const f = fixtures[0];
  if (exec.vu.iterationInScenario % SIGNON_EVERY === 0) {
    signOn(userFor());
  }
  (FLOWS[tx] || FLOWS.CAVW)(f);
  if (THINK_TIME_S > 0) {
    sleep(THINK_TIME_S);
  }
}

export function user() {
  iterate(chooseTx());
}

export function admin() {
  iterate('CU00');
}
