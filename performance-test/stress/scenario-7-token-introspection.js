import http from 'k6/http';
import { check, sleep } from 'k6';
import encoding from "k6/encoding";

export let options = {
  vus: 120, // Number of concurrent virtual users
  duration: '30s', // Test duration
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests should complete below 500ms
    http_req_failed: ['rate<0.01'],   // Error rate should be less than 1%
  },
};

function createBasicAuthHeaderValue({ username, password }) {
  const credentials = `${username}:${password}`;
  return encoding.b64encode(credentials);
}

export default function () {

  const baseUrl = __ENV.BASE_URL;
  const tenantId = __ENV.TENANT_ID;
  const token = __ENV.ACCESS_TOKEN
  const clientId = __ENV.CLIENT_ID;
  const clientSecret = __ENV.CLIENT_SECRET;
  const url = `${baseUrl}/${tenantId}/v1/tokens/introspection`;

  const payload = `token=${token}&client_id=${clientId}&client_secret=${clientSecret}`

  const params = {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    },
  };

  const res = http.post(url, payload, params);
  // console.log(res)

  check(res, {
    'status is 200': (r) => r.status === 200,
  });

}
