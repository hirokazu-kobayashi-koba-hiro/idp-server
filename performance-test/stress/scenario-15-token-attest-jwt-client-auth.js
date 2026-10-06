import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { assertTestFitsWindow, nextPopIndex } from '../libs/attestation-pop.js';

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
 *
 * PoP JWT は 1 リクエストに 1 つずつ使う（サーバは jti の 2 回目を拒否する、#1893）。
 * 流せるリクエストは --requests で署名した数まで。
 */
const RESIGN = 'node performance-test/scripts/generate-client-instances.js --resign';
const instanceData = JSON.parse(open('../data/performance-test-client-instances.json'));

const tenantIndex = parseInt(__ENV.TENANT_INDEX || '0');
const config = instanceData[tenantIndex];

if (!config || !config.instances || config.instances.length === 0) {
  throw new Error(
    'No client instances found in performance-test-client-instances.json. ' +
      'Run: node performance-test/scripts/generate-client-instances.js'
  );
}

// テスト中に PoP JWT の iat 許容窓を過ぎると 401 の山になるので、始める前に止める。
assertTestFitsWindow({
  generatedAt: config.generatedAt,
  popWindowSeconds: config.popWindowSeconds,
  duration: DURATION,
  resignCommand: RESIGN,
});

// 全 VU で 1 つを共有する（VU ごとに大きな JSON を持たない）
const popJwts = new SharedArray('pop-jwts', () => {
  const pool = JSON.parse(open('../data/performance-test-client-instances-pops.json')).find(
    (entry) => entry.tenantId === config.tenantId && entry.generatedAt === config.generatedAt
  );
  if (!pool) {
    throw new Error(`No PoP JWTs signed with the current instances. Run: ${RESIGN}`);
  }
  return pool.popJwts;
});

export default function () {
  const baseUrl = __ENV.BASE_URL || 'https://api.local.test';
  const tenantId = config.tenantId;
  const clientId = config.clientId;

  // k 番目のリクエストは k 番目の PoP JWT と、それを署名したインスタンス（k % インスタンス数）を使う。
  // インスタンスも循環するので、client_instance の鍵解決が分散する。
  const index = nextPopIndex(popJwts.length, RESIGN);
  const instance = config.instances[index % config.instances.length];

  const url = `${baseUrl}/${tenantId}/v1/tokens`;

  const payload =
    `grant_type=client_credentials` +
    `&client_id=${clientId}` +
    `&scope=${encodeURIComponent('openid profile phone email account management')}`;

  const params = {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      'OAuth-Client-Attestation': instance.attestationJwt,
      'OAuth-Client-Attestation-PoP': popJwts[index],
    },
  };

  const tokenRes = http.post(url, payload, params);

  check(tokenRes, {
    'status is 200': (r) => r.status === 200,
  });
}
