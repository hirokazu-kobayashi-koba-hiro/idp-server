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
 * Attestation Client Auth の署名検証コスト比較ハーネス
 *
 * scenario-15 と同じトークンリクエストを、trust source と署名アルゴリズムを変えて投げる。
 * scenario-5-token-client-credentials（client_secret）を 0 点として差分を読む。
 *
 * 2 軸:
 *   TRUST_SOURCE=registered_instance_key  kid で client_instance を引く（DB アクセスあり）
 *   TRUST_SOURCE=attester_jwks            クライアント設定の JWKS で検証（DB アクセスなし）
 *     → 同じ ALG で 2 つを比べた差が client_instance の鍵解決コスト
 *
 *   ALG=ES256 | ES384 | ES512 | RS256 | PS256 | EdDSA
 *     → 同じ TRUST_SOURCE で比べた差が署名検証アルゴリズムの差
 *
 * 事前準備:
 *   node performance-test/scripts/generate-attestation-matrix.js
 *   node performance-test/scripts/generate-attestation-matrix.js --resign  # テスト直前
 *
 * Usage:
 *   ALG=RS256 TRUST_SOURCE=attester_jwks VU_COUNT=5 DURATION=20s \
 *     k6 run ./performance-test/stress/scenario-17-token-attest-matrix.js
 */
const matrix = JSON.parse(open('../data/performance-test-client-instances-matrix.json'));

const alg = __ENV.ALG || 'ES256';
const trustSource = __ENV.TRUST_SOURCE || 'registered_instance_key';
const config = matrix.find((entry) => entry.alg === alg && entry.trustSource === trustSource);

if (!config) {
  const available = matrix.map((entry) => entry.key).join(', ');
  throw new Error(
    `Combination not found: ${trustSource}/${alg}. Available: ${available}. ` +
      'Run: node performance-test/scripts/generate-attestation-matrix.js'
  );
}

// PoP JWT の iat 許容窓は ±5 分。期限切れのプールで走らせると 401 の山になるので早期に止める。
const ageSeconds = Math.floor(Date.now() / 1000) - config.generatedAt;
if (ageSeconds > 240) {
  throw new Error(
    `Pre-signed attestation JWTs are ${ageSeconds}s old (window is 300s). ` +
      'Run: node performance-test/scripts/generate-attestation-matrix.js --resign'
  );
}

console.log(`combination: ${config.key}${config.rsaBits ? ` (${config.rsaBits} bit)` : ''}`);

export default function () {
  const baseUrl = __ENV.BASE_URL || 'https://api.local.test';

  // VU・iteration ごとに違うインスタンスを循環
  const instance = config.instances[(__VU + __ITER) % config.instances.length];

  const url = `${baseUrl}/${config.tenantId}/v1/tokens`;

  const payload =
    `grant_type=client_credentials` +
    `&client_id=${config.clientId}` +
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
