import http from "k6/http";
import { check, sleep } from "k6";

// 設定ファイルから読み込み
const data = JSON.parse(open('../data/performance-test-multi-tenant-users.json'));

// 環境変数でテナント数を制御可能（デフォルトは全テナント）
const maxTenants = parseInt(__ENV.TENANT_COUNT || String(data.length));
const tenantCount = Math.min(maxTenants, data.length);

// 環境変数でカスタマイズ可能なパラメータ
// テスト方針の測定観点に対応:
// - マルチテナント影響測定: テナント数1→5→10（JSONファイルで制御）
// - 同時負荷影響測定: TOTAL_VU_COUNT=50/100/200/500
const TOTAL_VU_COUNT = parseInt(__ENV.TOTAL_VU_COUNT || '50');
const TOTAL_RATE = parseInt(__ENV.TOTAL_RATE || '20');
const DURATION = __ENV.DURATION || '5m';

// 動的にシナリオを生成（テナント数に応じてVU/レートを分配）
const scenarios = {};
for (let i = 0; i < tenantCount; i++) {
  scenarios[`tenant${i}`] = {
    executor: 'constant-arrival-rate',
    preAllocatedVUs: Math.ceil(TOTAL_VU_COUNT / tenantCount),
    maxVUs: Math.ceil((TOTAL_VU_COUNT * 2) / tenantCount),
    rate: Math.ceil(TOTAL_RATE / tenantCount) || 1,
    timeUnit: '1s',
    duration: DURATION,
    exec: 'multiTenantLogin',
    env: { TENANT_INDEX: String(i) },
  };
}

export const options = {
  scenarios: scenarios,
};

export function multiTenantLogin() {
  const index = parseInt(__ENV.TENANT_INDEX);
  login(index);
}


function login(index) {
  const baseUrl = __ENV.BASE_URL || 'https://api.local.dev';
  const testData = data[index];
  const clientId = testData.clientId;
  const clientSecret = testData.clientSecret;
  const tenantId = testData.tenantId;

  // ユーザーをランダムに選択
  const users = testData.users;
  const randomIndex = Math.floor(Math.random() * users.length);
  const user = users[randomIndex];
  const userId = user.user_id;
  const deviceId = user.device_id;

  const bindingMessage = "999";
  const loginHint = encodeURIComponent(`sub:${userId},idp:idp-server`);

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

  const backchannelRes = http.post(url, payload, params);
  // console.log(JSON.parse(backchannelRes.body))
  check(backchannelRes, { "auth request OK": (r) => r.status === 200 });
  const authReqId = JSON.parse(backchannelRes.body).auth_req_id;
  // console.log(authReqId)

  //authentication transaction
  const txRes = http.get(`${baseUrl}/${tenantId}/v1/authentication-devices/${deviceId}/authentications?attributes.auth_req_id=${authReqId}`);
  check(txRes, { "txRes request OK": (r) => r.status === 200 });
  const txList = JSON.parse(txRes.body);
  const tx = txList.list[0]
  // console.log(tx.id)

  //bindingMessage
  const bindingMessageRes = http.post(`${baseUrl}/${tenantId}/v1/authentications/${tx.id}/authentication-device-binding-message`,
    JSON.stringify({ binding_message: bindingMessage }),
    { headers: { "Content-Type": "application/json" } }
  );
  check(bindingMessageRes, { "authentication-device-binding-message request OK": (r) => r.status === 200 });
  // console.log(bindingMessageRes)

  //token
  const tokenRes = http.post(
    `${baseUrl}/${tenantId}/v1/tokens`,
    `grant_type=urn:openid:params:grant-type:ciba&auth_req_id=${authReqId}&client_id=${clientId}&client_secret=${clientSecret}`,
    { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
  );
  check(tokenRes, { "tokenRes request OK": (r) => r.status === 200 || r.status === 400 });

  // console.log(tokenRes.status);

  const jwksResponse = http.get(`${baseUrl}/${tenantId}/v1/jwks`);

  check(jwksResponse, { "jwksResponse request OK": (r) => r.status === 200 });

}