import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '1m', target: 50 },
    { duration: '1m', target: 100 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.05'],
    checks: ['rate>0.95'],
  },
};

const url = 'http://127.0.0.1:8080/users';

export default function () {

  const headers = {
    'Content-Type': 'application/json',
    'X-Correlation-ID': uuidv4(),
  };

  const res = http.get(url, { headers });

  check(res, {
    'status is 200': (r) => r.status === 200,
    'not timed out': (r) => r.status !== 504,
  });

  sleep(1);
}
