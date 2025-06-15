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
  const clientId = __ENV.CLIENT_ID;
  const redirectUri = __ENV.REDIRECT_URI;
  const tenantId = __ENV.TENANT_ID;

  const res = http.get(`${baseUrl}/${tenantId}/v1/authorizations?scope=openid+profile+phone+emailaccount+management&response_type=id_token&client_id=${clientId}&redirect_uri=${redirectUri}&state=aiueo&nonce=nonce`);

  check(res, {
    'status is 200': (r) => r.status === 200,
  });

}
