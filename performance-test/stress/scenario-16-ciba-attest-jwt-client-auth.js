import http from 'k6/http';
import { check } from 'k6';

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

/**
 * CIBA BC Request with Attestation-Based Client Authentication
 * （draft-ietf-oauth-attestation-based-client-auth-10）
 *
 * scenario-2-bc.js と同じ backchannel authentication リクエストを
 * attest_jwt_client_auth で投げる。クライアント認証方式だけが違うので、
 * 差分がトークンエンドポイント（scenario-5 対 scenario-15）と同じ傾向になるかを見る。
 *
 * PoP JWT の aud は issuer なので、トークンエンドポイント用と同じ JWT ペアを流用できる。
 *
 * 事前準備:
 *   node performance-test/scripts/generate-client-instances.js
 *   node performance-test/scripts/generate-client-instances.js --resign  # テスト直前
 */
const instanceData = JSON.parse(open('../data/performance-test-client-instances.json'));
const tenantData = JSON.parse(open('../data/performance-test-multi-tenant-users.json'));

const tenantIndex = parseInt(__ENV.TENANT_INDEX || '0');
const config = instanceData[tenantIndex];
const tenantConfig = tenantData[tenantIndex];

if (!config || !config.instances || config.instances.length === 0) {
  throw new Error(
    'No client instances found in performance-test-client-instances.json. ' +
      'Run: node performance-test/scripts/generate-client-instances.js'
  );
}

if (config.tenantId !== tenantConfig.tenantId) {
  throw new Error(
    `Tenant mismatch: instances=${config.tenantId} users=${tenantConfig.tenantId}. ` +
      'Regenerate the instance pool after re-registering tenants.'
  );
}

// PoP JWT の iat 許容窓は ±5 分。期限切れのプールで走らせると 401 の山になるので早期に止める。
const ageSeconds = Math.floor(Date.now() / 1000) - config.generatedAt;
if (ageSeconds > 240) {
  throw new Error(
    `Pre-signed attestation JWTs are ${ageSeconds}s old (window is 300s). ` +
      'Run: node performance-test/scripts/generate-client-instances.js --resign'
  );
}

const users = tenantConfig.users;
const userCount = users.length;

export default function () {
  const baseUrl = __ENV.BASE_URL || 'https://api.local.test';
  const tenantId = config.tenantId;
  const clientId = config.clientId;

  // VU・iteration ごとに違うインスタンスを循環（client_instance の鍵解決を分散させる）
  const instance = config.instances[(__VU + __ITER) % config.instances.length];

  // ユーザーをランダムに選択（scenario-2-bc と同じ sub:{subject} 形式）
  const randomIndex = Math.floor(Math.random() * userCount);
  const user = users[randomIndex];
  const loginHint = encodeURIComponent(`sub:${user.user_id}`);

  const url = `${baseUrl}/${tenantId}/v1/backchannel/authentications`;

  const payload =
    `client_id=${clientId}` +
    `&scope=openid profile phone email account management transfers` +
    `&binding_message=999` +
    `&login_hint=${loginHint}`;

  const params = {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      'OAuth-Client-Attestation': instance.attestationJwt,
      'OAuth-Client-Attestation-PoP': instance.popJwt,
    },
  };

  const backchannelRes = http.post(url, payload, params);

  check(backchannelRes, {
    'status is 200': (r) => r.status === 200,
  });
}
