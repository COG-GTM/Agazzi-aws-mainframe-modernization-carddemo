// Shared configuration for the CardDemo k6 scenarios. Everything is overridable with -e KEY=value
// (or env vars); defaults are the indicative 5B target: p95 < 200 ms at 200 concurrent users.
export const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
export const VUS = int('VUS', 200);
export const DURATION = __ENV.DURATION || '5m';
export const RAMP_UP = __ENV.RAMP_UP || '1m';
export const P95_MS = int('P95_MS', 200);
export const P99_MS = int('P99_MS', 500);
export const MAX_ERROR_RATE = float('MAX_ERROR_RATE', 0.01);
export const THINK_TIME_S = float('THINK_TIME_S', 0);
// Comma-separated USERID lists; both default to the DUSRSECJ sample users seeded by V2.
export const USERS = list('USERS', 'USER0001,USER0002,USER0003,USER0004,USER0005');
export const ADMINS = list('ADMINS', 'ADMIN001,ADMIN002,ADMIN003,ADMIN004');
// Sample-data credential (app/data, demo only); never point these scripts at a real system.
export const PASSWORD = __ENV.CARDDEMO_PASSWORD || 'PASSWORD';
// datagen output (k6/fixtures.json), absolute path (k6 open() resolves relative paths against
// lib/); when absent the 50 sample accounts are used.
export const FIXTURES = __ENV.FIXTURES || '';

export function int(name, dflt) {
  const v = __ENV[name];
  return v === undefined || v === '' ? dflt : parseInt(v, 10);
}

export function float(name, dflt) {
  const v = __ENV[name];
  return v === undefined || v === '' ? dflt : parseFloat(v);
}

export function list(name, dflt) {
  return (__ENV[name] || dflt).split(',').map((s) => s.trim()).filter((s) => s);
}

// Global + per-transaction thresholds; abortOnFail stays off so a baseline always completes.
export function thresholds(txIds) {
  const t = {
    http_req_failed: [`rate<${MAX_ERROR_RATE}`],
    checks: [`rate>${1 - MAX_ERROR_RATE}`],
    http_req_duration: [`p(95)<${P95_MS}`, `p(99)<${P99_MS}`],
  };
  for (const tx of txIds) {
    t[`http_req_duration{tx:${tx}}`] = [`p(95)<${P95_MS}`];
  }
  return t;
}

export function rampingStages() {
  return [
    { duration: RAMP_UP, target: VUS },
    { duration: DURATION, target: VUS },
    { duration: '15s', target: 0 },
  ];
}
