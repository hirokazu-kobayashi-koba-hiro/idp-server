import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
  vus: 120, // Number of concurrent virtual users
  duration: '30s', // Test duration
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests should complete below 500ms
    http_req_failed: ['rate<0.01'],   // Error rate should be less than 1%
  },
};

export default function () {

  const baseUrl = __ENV.BASE_URL;
  const tenantId = __ENV.TENANT_ID;
  const token = __ENV.ACCESS_TOKEN
  const url = `${baseUrl}/${tenantId}/v1/tokens/introspection`;

  const payload = `token=${token}`

  const params = {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
  };

  const res = http.post(url, payload, params);
  // console.log(res)

  check(res, {
    'status is 200': (r) => r.status === 200,
  });

}
