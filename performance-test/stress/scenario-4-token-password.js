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

function getRandomInt(max) {
  return Math.floor(Math.random() * max);
}

export default function () {
  const baseUrl = __ENV.BASE_URL;
  const clientId = __ENV.CLIENT_ID;
  const clientSecret = __ENV.CLIENT_SECRET;
  const tenantId = __ENV.TENANT_ID;

  const randomUserId = getRandomInt(100000);
  const email = `user${randomUserId}@example.com`;
  const url = `${baseUrl}/${tenantId}/v1/tokens`;

  const payload =
    `grant_type=password` +
    `&client_id=${clientId}` +
    `&scope=openid profile phone email account management transfers` +
    `&password=successUserCode` +
    `&username=${email}` +
    `&client_secret=${clientSecret}`;

  const params = {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
  };

  const tokenRes = http.post(url, payload, params);
  // console.log(tokenRes)

  check(tokenRes, {
    'status is 200': (r) => r.status === 200,
  });

}
