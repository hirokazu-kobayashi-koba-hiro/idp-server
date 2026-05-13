import http from 'k6/http';
import { check } from 'k6';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

// 環境変数でカスタマイズ可能なパラメータ
const VU_COUNT = parseInt(__ENV.VU_COUNT || '120');
const DURATION = __ENV.DURATION || '30s';

export let options = {
  vus: VU_COUNT,
  duration: DURATION,
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

// テナント・ユーザー設定読み込み（generate_users.py で生成）
const tenantData = JSON.parse(open('../data/performance-test-multi-tenant-users.json'));
const tenantIndex = parseInt(__ENV.TENANT_INDEX || '0');
const config = tenantData[tenantIndex];

// device_secret を持つユーザーだけに絞る
const usableUsers = config.users.filter((u) => u.device_secret);
if (usableUsers.length === 0) {
  throw new Error(
    `No users with device_secret in tenant ${config.tenantId}. ` +
      'Regenerate test data with generate_users.py (default issues device secrets).'
  );
}

function b64url(input) {
  return encoding.b64encode(input, 'rawurl');
}

function createJwtHS256(payload, secret) {
  const header = { alg: 'HS256', typ: 'JWT' };
  const headerB64 = b64url(JSON.stringify(header));
  const payloadB64 = b64url(JSON.stringify(payload));
  const signingInput = `${headerB64}.${payloadB64}`;
  const signature = crypto.hmac('sha256', secret, signingInput, 'binary');
  const signatureB64 = b64url(signature);
  return `${signingInput}.${signatureB64}`;
}

export default function () {
  const baseUrl = __ENV.BASE_URL || 'https://api.local.test';
  const tenantId = config.tenantId;
  const clientId = config.clientId;
  const clientSecret = config.clientSecret;

  // VU・iteration ごとに違うユーザーを循環
  const user = usableUsers[(__VU + __ITER) % usableUsers.length];

  const issuer = `${baseUrl}/${tenantId}`;
  const tokenEndpoint = `${issuer}/v1/tokens`;

  const now = Math.floor(Date.now() / 1000);
  const assertion = createJwtHS256(
    {
      iss: `device:${user.device_id}`,
      sub: user.device_id, // default subject_claim_mapping = device_id
      aud: issuer,
      jti: `${__VU}-${__ITER}-${now}`,
      iat: now,
      exp: now + 300,
    },
    user.device_secret
  );

  const payload =
    `grant_type=${encodeURIComponent('urn:ietf:params:oauth:grant-type:jwt-bearer')}` +
    `&assertion=${encodeURIComponent(assertion)}` +
    `&scope=${encodeURIComponent('openid profile email')}` +
    `&client_id=${clientId}` +
    `&client_secret=${clientSecret}`;

  const tokenRes = http.post(tokenEndpoint, payload, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  });

  check(tokenRes, {
    'status is 200': (r) => r.status === 200,
  });
}
