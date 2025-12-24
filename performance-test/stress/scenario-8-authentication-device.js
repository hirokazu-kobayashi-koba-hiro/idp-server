import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * Authentication Device Stress Test
 *
 * Tests the authentication device endpoint for fetching authentication transactions.
 */
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
const users = config.users;
const userCount = users.length;

export default function () {
  const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
  const tenantId = config.tenantId;

  // ユーザーをランダムに選択
  const randomIndex = Math.floor(Math.random() * userCount);
  const user = users[randomIndex];
  const deviceId = user.device_id;

  const txRes = http.get(`${baseUrl}/${tenantId}/v1/authentication-devices/${deviceId}/authentications?limit=1`);
  check(txRes, { "txRes request OK": (r) => r.status === 200 });

}
