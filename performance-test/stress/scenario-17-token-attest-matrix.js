import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import {
  POP_SIGNING,
  assertTestFitsWindow,
  createK6Signer,
  presignedPair,
} from '../libs/attestation-pop.js';

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
 *   # テスト直前: 流す組み合わせだけ署名し直す
 *   node performance-test/scripts/generate-attestation-matrix.js --resign --only attester_jwks/RS256
 *
 * PoP JWT は 1 リクエストに 1 つずつ使う（サーバは jti の 2 回目を拒否する、#1893）。
 *
 * POP_SIGNING=k6 にすると、PoP JWT と Client Attestation JWT を k6 の中で署名する
 * （--resign 不要・時間と件数の制限なし。k6 側で署名の CPU を使うので、alg 同士の比較は presigned で取る）。
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

const RESIGN = `node performance-test/scripts/generate-attestation-matrix.js --resign --only ${config.key}`;

const presigned = POP_SIGNING === 'presigned';

// テスト中に PoP JWT の iat 許容窓を過ぎると 401 の山になるので、始める前に止める。
if (presigned) {
  assertTestFitsWindow({
    generatedAt: config.generatedAt,
    popWindowSeconds: config.popWindowSeconds,
    duration: DURATION,
    resignCommand: RESIGN,
  });
}

// 全 VU で 1 つを共有する（VU ごとに大きな JSON を持たない）
const popJwts = presigned ? new SharedArray('pop-jwts', () => {
  const pool = JSON.parse(open('../data/performance-test-client-instances-matrix-pops.json')).find(
    (entry) => entry.key === config.key && entry.generatedAt === config.generatedAt
  );
  if (!pool) {
    throw new Error(`No PoP JWTs signed for ${config.key}. Run: ${RESIGN}`);
  }
  return pool.popJwts;
}) : null;

const signer = presigned
  ? null
  : createK6Signer({
      alg: config.alg,
      issuer: config.issuer,
      clientId: config.clientId,
      instances: config.instances,
      attesterJwk: config.attesterJwk,
    });

console.log(`combination: ${config.key}${config.rsaBits ? ` (${config.rsaBits} bit)` : ''}`);

export default async function () {
  const baseUrl = __ENV.BASE_URL || 'https://api.local.test';

  // k 番目のリクエストは k % インスタンス数 番目のインスタンスを使う。
  const { attestationJwt, popJwt } = presigned
    ? presignedPair(config.instances, popJwts, RESIGN)
    : await signer.next();

  const url = `${baseUrl}/${config.tenantId}/v1/tokens`;

  const payload =
    `grant_type=client_credentials` +
    `&client_id=${config.clientId}` +
    `&scope=${encodeURIComponent('openid profile phone email account management')}`;

  const params = {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      'OAuth-Client-Attestation': attestationJwt,
      'OAuth-Client-Attestation-PoP': popJwt,
    },
  };

  const tokenRes = http.post(url, payload, params);

  check(tokenRes, {
    'status is 200': (r) => r.status === 200,
  });
}
