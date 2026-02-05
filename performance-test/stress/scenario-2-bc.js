import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * CIBA BC Request Stress Test
 *
 * Tests the CIBA backchannel authentication request endpoint.
 * Uses sub:{subject} login_hint format.
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
  const baseUrl = __ENV.BASE_URL || 'https://api.local.dev';
  const clientId = config.clientId;
  const clientSecret = config.clientSecret;
  const tenantId = config.tenantId;

  // ユーザーをランダムに選択
  const randomIndex = Math.floor(Math.random() * userCount);
  const user = users[randomIndex];
  const userId = user.user_id;

  const loginHint = encodeURIComponent(`sub:${userId}`);

  const url = `${baseUrl}/${tenantId}/v1/backchannel/authentications`;

  const payload =
    `client_id=${clientId}` +
    `&scope=openid profile phone email account management transfers` +
    `&binding_message=999` +
    `&login_hint=${loginHint}` +
    `&client_secret=${clientSecret}`;

  const params = {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
  };

  const backchannelRes = http.post(url, payload, params);

  check(backchannelRes, {
    'status is 200': (r) => r.status === 200,
  });

}
