import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// カスタムメトリクス
const tokenDuration = new Trend('token_request_duration', true);
const introspectionDuration = new Trend('introspection_request_duration', true);
const tokenErrors = new Counter('token_errors');
const introspectionErrors = new Counter('introspection_errors');

// 環境変数でカスタマイズ可能なパラメータ
const VU_COUNT = parseInt(__ENV.VU_COUNT || '30');
const DURATION = __ENV.DURATION || '3m';
const INTROSPECTION_COUNT = parseInt(__ENV.INTROSPECTION_COUNT || '3');

export let options = {
  vus: VU_COUNT,
  duration: DURATION,
  thresholds: {
    token_request_duration: ['p(95)<500'],
    introspection_request_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
  },
};

// 設定ファイルから読み込み
const tenantData = JSON.parse(open('../data/performance-test-multi-tenant-users.json'));

const tenantIndex = parseInt(__ENV.TENANT_INDEX || '0');
const config = tenantData[tenantIndex];

export default function () {
  const baseUrl = __ENV.BASE_URL || 'https://api.local.dev';
  const tenantId = config.tenantId;
  const clientId = config.clientId;
  const clientSecret = config.clientSecret;

  // Step 1: Token Request (Client Credentials)
  const tokenUrl = `${baseUrl}/${tenantId}/v1/tokens`;
  const tokenPayload =
    `grant_type=client_credentials` +
    `&client_id=${clientId}` +
    `&scope=openid profile phone email` +
    `&client_secret=${clientSecret}`;

  const tokenRes = http.post(tokenUrl, tokenPayload, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    tags: { name: 'token' },
  });

  tokenDuration.add(tokenRes.timings.duration);

  const tokenOk = check(tokenRes, {
    'Step 1: token request - status 200': (r) => r.status === 200,
  });

  if (!tokenOk) {
    tokenErrors.add(1);
    return;
  }

  const accessToken = tokenRes.json().access_token;
  if (!accessToken) {
    tokenErrors.add(1);
    return;
  }

  // Step 2: Introspection N回
  const introspectionUrl = `${baseUrl}/${tenantId}/v1/tokens/introspection`;
  const introspectionPayload =
    `token=${accessToken}` +
    `&client_id=${clientId}` +
    `&client_secret=${clientSecret}`;

  for (let i = 0; i < INTROSPECTION_COUNT; i++) {
    const introspectionRes = http.post(introspectionUrl, introspectionPayload, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      tags: { name: 'introspection' },
    });

    introspectionDuration.add(introspectionRes.timings.duration);

    const introspectionOk = check(introspectionRes, {
      [`Step 2-${i + 1}: introspection - status 200`]: (r) => r.status === 200,
      [`Step 2-${i + 1}: introspection - active true`]: (r) => r.json().active === true,
    });

    if (!introspectionOk) {
      introspectionErrors.add(1);
    }

    sleep(0.1);
  }
}
