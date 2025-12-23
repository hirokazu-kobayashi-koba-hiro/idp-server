import http from "k6/http";
import { check, sleep } from "k6";

// 設定ファイルから読み込み
// マルチユーザーデータがある場合はそちらを優先（100K+ユーザーでのベンチマーク用）
// なければシングルユーザーデータを使用（簡易テスト用）
let tenantData;
let useMultiUser = false;

try {
  tenantData = JSON.parse(open('../data/performance-test-multi-tenant-users.json'));
  useMultiUser = true;
} catch (e) {
  tenantData = JSON.parse(open('../data/performance-test-tenant.json'));
  useMultiUser = false;
}

const tenantIndex = parseInt(__ENV.TENANT_INDEX || '0');
const config = tenantData[tenantIndex];

// マルチユーザーモードの場合、ユーザー配列を取得
const users = useMultiUser ? config.users : null;
const userCount = users ? users.length : 1;

// 環境変数でカスタマイズ可能なパラメータ
// テスト方針の測定観点に対応:
// - ベースライン測定: VU_COUNT=10, RATE=5, DURATION=5m
// - 同時負荷影響測定: VU_COUNT=50/100/200/500, RATE=100/200/400/1000
const VU_COUNT = parseInt(__ENV.VU_COUNT || '50');
const MAX_VU_COUNT = parseInt(__ENV.MAX_VU_COUNT || String(VU_COUNT * 2));
const LOGIN_RATE = parseInt(__ENV.LOGIN_RATE || '20');
const INTROSPECT_RATE = parseInt(__ENV.INTROSPECT_RATE || '80');
const DURATION = __ENV.DURATION || '5m';

export const options = {
  scenarios: {
    login: {
      executor: 'constant-arrival-rate',
      preAllocatedVUs: VU_COUNT,
      maxVUs: MAX_VU_COUNT,
      rate: LOGIN_RATE,
      timeUnit: '1s',
      duration: DURATION,
      exec: 'login',
    },
    introspection: {
      executor: 'constant-arrival-rate',
      preAllocatedVUs: VU_COUNT,
      maxVUs: MAX_VU_COUNT,
      rate: INTROSPECT_RATE,
      timeUnit: '1s',
      duration: DURATION,
      exec: 'introspect',
    },
  },
};

export function login() {
  const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
  const clientId = config.clientId;
  const clientSecret = config.clientSecret;
  const tenantId = config.tenantId;

  // ユーザーをランダムに選択（マルチユーザーモードの場合）
  let userId, deviceId;
  if (useMultiUser && users) {
    const randomIndex = Math.floor(Math.random() * userCount);
    const user = users[randomIndex];
    userId = user.user_id;
    deviceId = user.device_id;
  } else {
    // シングルユーザーモード（テナント登録時に作成されたユーザー）
    userId = config.userId;
    deviceId = config.deviceId;
  }

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
  // console.log(backchannelRes)
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
  check(tokenRes, { "tokenRes request OK": (r) => r.status === 200 });

  // console.log(tokenRes.status);

  const jwksResponse = http.get(`${baseUrl}/${tenantId}/v1/jwks`);

  check(jwksResponse, { "jwksResponse request OK": (r) => r.status === 200 });

}

export function introspect() {
  const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
  const tenantId = config.tenantId;
  const clientId = config.clientId;
  const clientSecret = config.clientSecret;
  const token = __ENV.ACCESS_TOKEN;
  const url = `${baseUrl}/${tenantId}/v1/tokens/introspection`;

  const payload = `token=${token}&client_id=${clientId}&client_secret=${clientSecret}`;

  const params = {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
  };

  const res = http.post(url, payload, params);
  // console.log(res)

  check(res, {
    'status is 200': (r) => r.status === 200,
  });

}
