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
 * CIBA BC Request with Attestation-Based Client Authentication
 * （draft-ietf-oauth-attestation-based-client-auth-10）
 *
 * scenario-2-bc.js と同じ backchannel authentication リクエストを
 * attest_jwt_client_auth で投げる。クライアント認証方式だけが違うので、
 * 差分がトークンエンドポイント（scenario-5 対 scenario-15）と同じ傾向になるかを見る。
 *
 * PoP JWT の aud は issuer なので、トークンエンドポイント用と同じプールを使える。
 * ただし PoP JWT は 1 リクエストに 1 つずつ使う（サーバは jti の 2 回目を拒否する、#1893）ので、
 * scenario-15 を流したあとは --resign し直すこと。
 *
 * 事前準備:
 *   node performance-test/scripts/generate-client-instances.js
 *   node performance-test/scripts/generate-client-instances.js --resign  # テスト直前
 */
const RESIGN = 'node performance-test/scripts/generate-client-instances.js --resign';
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

const users = tenantConfig.users;
const userCount = users.length;

export default function () {
  const baseUrl = __ENV.BASE_URL || 'https://api.local.test';
  const tenantId = config.tenantId;
  const clientId = config.clientId;

  // k 番目のリクエストは k 番目の PoP JWT と、それを署名したインスタンス（k % インスタンス数）を使う。
  const index = nextPopIndex(popJwts.length, RESIGN);
  const instance = config.instances[index % config.instances.length];

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
      'OAuth-Client-Attestation-PoP': popJwts[index],
    },
  };

  const backchannelRes = http.post(url, payload, params);

  check(backchannelRes, {
    'status is 200': (r) => r.status === 200,
  });
}
