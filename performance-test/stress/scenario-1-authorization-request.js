import http from 'k6/http';
import { check, sleep } from 'k6';

// 環境変数でカスタマイズ可能なパラメータ
const VU_COUNT = parseInt(__ENV.VU_COUNT || '120');
const DURATION = __ENV.DURATION || '30s';

export let options = {
  vus: VU_COUNT,
  duration: DURATION,
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests should complete below 500ms
    http_req_failed: ['rate<0.01'],   // Error rate should be less than 1%
  },
};

// 設定ファイルから読み込み
let tenantData;
try {
  tenantData = JSON.parse(open('../data/performance-test-multi-tenant-users.json'));
} catch (e) {
  tenantData = JSON.parse(open('../data/performance-test-tenant.json'));
}

const tenantIndex = parseInt(__ENV.TENANT_INDEX || '0');
const config = tenantData[tenantIndex];

export default function () {
  const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
  const clientId = config.clientId;
  const redirectUri = __ENV.REDIRECT_URI || 'https://client.example.com/callback';
  const tenantId = config.tenantId;

  const res = http.get(`${baseUrl}/${tenantId}/v1/authorizations?scope=openid+profile+phone+emailaccount+management&response_type=id_token&client_id=${clientId}&redirect_uri=${redirectUri}&state=aiueo&nonce=nonce`, { redirects: 0 });

  check(res, {
    'status is 302': (r) => r.status === 302,
  });

}
