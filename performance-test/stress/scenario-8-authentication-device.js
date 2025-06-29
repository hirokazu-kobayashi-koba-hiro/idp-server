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

const data = JSON.parse(open('../data/performance-test-user.json'));

export default function () {

  const baseUrl = __ENV.BASE_URL;
  const tenantId = __ENV.TENANT_ID;
  const testUser = data[getRandomInt(499)]
  const deviceId = testUser.device_id

  const txRes = http.get(`${baseUrl}/${tenantId}/v1/authentication-devices/${deviceId}/authentications/latest`);
  check(txRes, { 'txRes request OK': (r) => r.status === 200 });

}

function getRandomInt(max) {
  return Math.floor(Math.random() * max);
}
