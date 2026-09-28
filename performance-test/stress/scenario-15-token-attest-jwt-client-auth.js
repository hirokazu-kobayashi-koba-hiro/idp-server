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
 * Attestation-Based Client Authentication のトークン発行
 * （draft-ietf-oauth-attestation-based-client-auth-10）
 *
 * scenario-5-token-client-credentials.js と同じ client_credentials だが、
 * クライアント認証が attest_jwt_client_auth になる。1リクエストあたり
 * 追加で走るのは JWT 署名検証 2 回（Client Attestation JWT + PoP JWT）と
 * client_instance の鍵解決 1 クエリ。両シナリオの差分がその実コストになる。
 *
 * 事前準備（k6 は ES256 署名ができないため JWT は事前生成）:
 *   node performance-test/scripts/generate-client-instances.js
 *   node performance-test/scripts/generate-client-instances.js --resign  # テスト直前
 */
const instanceData = JSON.parse(open('../data/performance-test-client-instances.json'));

const tenantIndex = parseInt(__ENV.TENANT_INDEX || '0');
const config = instanceData[tenantIndex];

if (!config || !config.instances || config.instances.length === 0) {
  throw new Error(
    'No client instances found in performance-test-client-instances.json. ' +
      'Run: node performance-test/scripts/generate-client-instances.js'
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

export default function () {
  const baseUrl = __ENV.BASE_URL || 'https://api.local.test';
  const tenantId = config.tenantId;
  const clientId = config.clientId;

  // VU・iteration ごとに違うインスタンスを循環（client_instance の鍵解決を分散させる）
  const instance = config.instances[(__VU + __ITER) % config.instances.length];

  const url = `${baseUrl}/${tenantId}/v1/tokens`;

  const payload =
    `grant_type=client_credentials` +
    `&client_id=${clientId}` +
    `&scope=${encodeURIComponent('openid profile phone email account management')}`;

  const params = {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      'OAuth-Client-Attestation': instance.attestationJwt,
      'OAuth-Client-Attestation-PoP': instance.popJwt,
    },
  };

  const tokenRes = http.post(url, payload, params);

  check(tokenRes, {
    'status is 200': (r) => r.status === 200,
  });
}
