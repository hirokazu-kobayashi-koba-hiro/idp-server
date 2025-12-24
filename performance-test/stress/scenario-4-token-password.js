import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * Token Password Grant Stress Test
 *
 * Tests the Resource Owner Password Credentials Grant.
 * Note: This grant type is CPU-intensive due to bcrypt password verification.
 * VUs are set lower (20) to avoid overwhelming the server.
 */
// 環境変数でカスタマイズ可能なパラメータ
// Note: VU_COUNTのデフォルトは20（bcryptが重いため低めに設定）
const VU_COUNT = parseInt(__ENV.VU_COUNT || '20');
const DURATION = __ENV.DURATION || '30s';

export let options = {
  vus: VU_COUNT,
  duration: DURATION,
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests should complete below 500ms
    http_req_failed: ['rate<0.01'],   // Error rate should be less than 1%
  },
};

// 設定ファイルから読み込み（パスワードテストはテナント登録時のユーザーを使用）
const tenantData = JSON.parse(open('../data/performance-test-tenant.json'));

const tenantIndex = parseInt(__ENV.TENANT_INDEX || '0');
const config = tenantData[tenantIndex];

export default function () {
  const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
  const clientId = config.clientId;
  const clientSecret = config.clientSecret;
  const tenantId = config.tenantId;

  // パスワードグラントのテストユーザー（設定ファイルから読み込み）
  const username = config.userEmail;
  const password = config.userPassword;

  const url = `${baseUrl}/${tenantId}/v1/tokens`;

  const payload =
    `grant_type=password` +
    `&client_id=${clientId}` +
    `&scope=openid profile phone email account management transfers` +
    `&password=${password}` +
    `&username=${username}` +
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
