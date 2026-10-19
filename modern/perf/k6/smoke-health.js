// Smoke/load on what every build has today: actuator health (+ info). Same thresholds as the
// online mix so the harness itself is validated before the screen modules are integrated.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, THINK_TIME_S, rampingStages, thresholds } from './lib/config.js';

export const options = {
  scenarios: {
    health: { executor: 'ramping-vus', startVUs: 0, stages: rampingStages(), gracefulRampDown: '5s' },
  },
  thresholds: thresholds(['HEALTH', 'INFO']),
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const h = http.get(`${BASE_URL}/actuator/health`, { tags: { tx: 'HEALTH' } });
  check(h, {
    'health 200': (r) => r.status === 200,
    'health UP': (r) => r.json('status') === 'UP',
  });
  const i = http.get(`${BASE_URL}/actuator/info`, { tags: { tx: 'INFO' } });
  check(i, { 'info 200': (r) => r.status === 200 });
  if (THINK_TIME_S > 0) {
    sleep(THINK_TIME_S);
  }
}
