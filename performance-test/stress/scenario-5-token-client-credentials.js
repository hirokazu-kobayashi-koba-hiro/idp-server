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
const tenantData = JSON.parse(open('../data/performance-test-multi-tenant-users.json'));

const tenantIndex = parseInt(__ENV.TENANT_INDEX || '0');
const config = tenantData[tenantIndex];

export default function () {
  const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
  const clientId = config.clientId;
  const clientSecret = config.clientSecret;
  const tenantId = config.tenantId;

  const url = `${baseUrl}/${tenantId}/v1/tokens`;

  const payload =
    `grant_type=client_credentials` +
    `&client_id=${clientId}` +
    `&scope=openid profile phone email account management` +
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
