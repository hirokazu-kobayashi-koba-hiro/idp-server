import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// カスタムメトリクス
const tokenDuration = new Trend('token_request_duration', true);
const introspectionDuration = new Trend('introspection_request_duration', true);
const tokenErrors = new Counter('token_errors');
const introspectionErrors = new Counter('introspection_errors');
const tokenCacheHits = new Counter('token_cache_hits');

// 環境変数でカスタマイズ可能なパラメータ
const VU_COUNT = parseInt(__ENV.VU_COUNT || '30');
const DURATION = __ENV.DURATION || '3m';
const INTROSPECTION_COUNT = parseInt(__ENV.INTROSPECTION_COUNT || '3');
const TOKEN_TTL_SECONDS = parseInt(__ENV.TOKEN_TTL_SECONDS || '300');

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

// VUごとのトークンキャッシュ（モジュールレベル変数はVU内で永続化される）
let cachedToken = null;
let tokenExpiresAt = 0;

function getToken(baseUrl, tenantId, clientId, clientSecret) {
  const now = Date.now() / 1000;

  // キャッシュが有効ならそのまま返す
  if (cachedToken && now < tokenExpiresAt) {
    tokenCacheHits.add(1);
    return cachedToken;
  }

  // 新規トークン取得
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
    'token request - status 200': (r) => r.status === 200,
  });

  if (!tokenOk) {
    tokenErrors.add(1);
    return null;
  }

  const body = tokenRes.json();
  if (!body.access_token) {
    tokenErrors.add(1);
    return null;
  }

  // キャッシュ更新（TTLはサーバーの expires_in とローカル設定の小さい方）
  cachedToken = body.access_token;
  const serverTtl = body.expires_in || TOKEN_TTL_SECONDS;
  const effectiveTtl = Math.min(serverTtl, TOKEN_TTL_SECONDS);
  tokenExpiresAt = now + effectiveTtl - 10; // 10秒の余裕

  return cachedToken;
}

export default function () {
  const baseUrl = __ENV.BASE_URL || 'https://api.local.dev';
  const tenantId = config.tenantId;
  const clientId = config.clientId;
  const clientSecret = config.clientSecret;

  // Step 1: トークン取得（キャッシュあり）
  const accessToken = getToken(baseUrl, tenantId, clientId, clientSecret);
  if (!accessToken) {
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
      [`introspection ${i + 1}: status 200`]: (r) => r.status === 200,
      [`introspection ${i + 1}: active true`]: (r) => r.json().active === true,
    });

    if (!introspectionOk) {
      introspectionErrors.add(1);
    }

    sleep(0.1);
  }
}
