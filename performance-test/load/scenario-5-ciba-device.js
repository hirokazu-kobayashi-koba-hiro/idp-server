import http from "k6/http";
import { check, sleep } from "k6";

// 環境変数でカスタマイズ可能なパラメータ
const TARGET_VUS = parseInt(__ENV.VU_COUNT || '120');
const RAMPUP_DURATION = __ENV.RAMPUP_DURATION || '30s';
const STEADY_DURATION = __ENV.STEADY_DURATION || '2m';
const COOLDOWN_DURATION = __ENV.COOLDOWN_DURATION || '30s';

// Think Time 設定（秒）
const THINK_TIME_MIN = parseFloat(__ENV.THINK_TIME_MIN || '0.1');
const THINK_TIME_MAX = parseFloat(__ENV.THINK_TIME_MAX || '0.5');

export let options = {
  // 段階的に負荷を上げる（ランプアップ）
  stages: [
    { duration: RAMPUP_DURATION, target: Math.floor(TARGET_VUS * 0.5) },  // ウォームアップ: 50%まで
    { duration: RAMPUP_DURATION, target: TARGET_VUS },                    // ランプアップ: 100%まで
    { duration: STEADY_DURATION, target: TARGET_VUS },                    // 定常負荷
    { duration: COOLDOWN_DURATION, target: 0 },                           // クールダウン
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests should complete below 500ms
    http_req_failed: ['rate<0.01'],   // Error rate should be less than 1%
  },
};

// ランダムな Think Time を生成
function randomThinkTime() {
  return THINK_TIME_MIN + Math.random() * (THINK_TIME_MAX - THINK_TIME_MIN);
}

// ランダムな binding_message を生成（3桁の数字）
function randomBindingMessage() {
  return String(Math.floor(Math.random() * 900) + 100);
}

// 設定ファイルから読み込み
const tenantData = JSON.parse(open('../data/performance-test-multi-tenant-users.json'));

const tenantIndex = parseInt(__ENV.TENANT_INDEX || '0');
const config = tenantData[tenantIndex];
const users = config.users;
const userCount = users.length;

export default function() {
  const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
  const clientId = config.clientId;
  const clientSecret = config.clientSecret;
  const tenantId = config.tenantId;

  // ユーザーをランダムに選択
  const randomIndex = Math.floor(Math.random() * userCount);
  const user = users[randomIndex];
  const userId = user.user_id;
  const deviceId = user.device_id;

  // ランダムな binding_message（実際のユーザーは毎回異なる値を見る）
  const bindingMessage = randomBindingMessage();
  const loginHint = encodeURIComponent(`device:${deviceId},idp:idp-server`);

  const url = `${baseUrl}/${tenantId}/v1/backchannel/authentications`;

  const payload =
    `client_id=${clientId}` +
    `&scope=openid profile phone email account management transfers` +
    `&binding_message=${bindingMessage}` +
    `&login_hint=${loginHint}` +
    `&client_secret=${clientSecret}`;

  const params = {
    headers: {
      "Content-Type": "application/x-www-form-urlencoded"
    }
  };

  // 1. バックチャンネル認証リクエスト
  const backchannelRes = http.post(url, payload, params);
  check(backchannelRes, { "auth request OK": (r) => r.status === 200 });
  const authReqId = JSON.parse(backchannelRes.body).auth_req_id;

  sleep(randomThinkTime());  // デバイスが通知を受け取る時間

  // 2. 認証トランザクション取得（デバイス側）
  const txRes = http.get(`${baseUrl}/${tenantId}/v1/authentication-devices/${deviceId}/authentications?attributes.auth_req_id=${authReqId}`);
  check(txRes, { "txRes request OK": (r) => r.status === 200 });
  const txList = JSON.parse(txRes.body);
  const tx = txList.list[0]

  sleep(randomThinkTime());  // ユーザーが binding_message を確認する時間

  // 3. Binding Message 確認（ユーザーがデバイスで確認）
  const bindingMessageRes = http.post(`${baseUrl}/${tenantId}/v1/authentications/${tx.id}/authentication-device-binding-message`,
    JSON.stringify({ binding_message: bindingMessage }),
    { headers: { "Content-Type": "application/json" } }
  );
  check(bindingMessageRes, { "authentication-device-binding-message request OK": (r) => r.status === 200 });

  sleep(randomThinkTime());  // 認証完了後、クライアントがポーリングする間隔

  // 4. トークン取得（クライアント側がポーリング）
  const tokenRes = http.post(
    `${baseUrl}/${tenantId}/v1/tokens`,
    `grant_type=urn:openid:params:grant-type:ciba&auth_req_id=${authReqId}&client_id=${clientId}&client_secret=${clientSecret}`,
    { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
  );
  check(tokenRes, { "tokenRes request OK": (r) => r.status === 200 });

  sleep(randomThinkTime());  // トークン検証前の処理時間

  // 5. JWKS取得（トークン検証用）
  const jwksResponse = http.get(`${baseUrl}/${tenantId}/v1/jwks`);
  check(jwksResponse, { "jwksResponse request OK": (r) => r.status === 200 });
}
