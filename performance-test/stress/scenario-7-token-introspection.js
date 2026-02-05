import http from 'k6/http';
import { check, sleep } from 'k6';
import encoding from "k6/encoding";

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

function createBasicAuthHeaderValue({ username, password }) {
  const credentials = `${username}:${password}`;
  return encoding.b64encode(credentials);
}

export default function () {
  const baseUrl = __ENV.BASE_URL || 'https://api.local.dev';
  const tenantId = config.tenantId;
  const token = __ENV.ACCESS_TOKEN;
  const clientId = config.clientId;
  const clientSecret = config.clientSecret;
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
