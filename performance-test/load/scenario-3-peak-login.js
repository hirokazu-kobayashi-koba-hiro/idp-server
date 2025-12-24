import http from "k6/http";
import { check, sleep } from "k6";
import encoding from "k6/encoding";

// 設定ファイルから読み込み
const data = JSON.parse(open('../data/performance-test-multi-tenant-users.json'));
const tenantCount = data.length;

// 環境変数でカスタマイズ可能なパラメータ
// テスト方針の測定観点に対応:
// - 負荷限界検証: PEAK_RATE=30/50/100/200でブレークポイント特定
// - ランプアップ/ダウンパターンで安定性確認
const PEAK_RATE = parseInt(__ENV.PEAK_RATE || '30');
const VU_COUNT = parseInt(__ENV.VU_COUNT || '5');
const MAX_VU_COUNT = parseInt(__ENV.MAX_VU_COUNT || '20');
const RAMP_UP_DURATION = __ENV.RAMP_UP_DURATION || '3m';
const SUSTAIN_DURATION = __ENV.SUSTAIN_DURATION || '5m';
const RAMP_DOWN_DURATION = __ENV.RAMP_DOWN_DURATION || '2m';

export const options = {
  scenarios: {
    peakLoad: {
      executor: 'ramping-arrival-rate',
      startRate: PEAK_RATE,
      timeUnit: '1s',
      preAllocatedVUs: VU_COUNT,
      maxVUs: MAX_VU_COUNT,
      stages: [
        { target: PEAK_RATE, duration: RAMP_UP_DURATION },
        { target: PEAK_RATE, duration: SUSTAIN_DURATION },
        { target: 0, duration: RAMP_DOWN_DURATION }
      ],
      exec: 'peakLogin'
    },
    deleteExpiredData: {
      executor: 'constant-arrival-rate',
      rate: '1',
      timeUnit: '1s',
      duration: '10m',
      preAllocatedVUs: 1,
      maxVUs: 1,
      exec: 'deleteExpiredData',
    }
  }
}

export function peakLogin() {
  const index = Math.floor(Math.random() * tenantCount);
  login(index);
}

function login(index) {
  const baseUrl = __ENV.BASE_URL;
  const testData = data[index];
  const clientId = testData.clientId;
  const clientSecret = testData.clientSecret;
  const tenantId = testData.tenantId;

  // ユーザーをランダムに選択
  const users = testData.users;
  const randomUserIndex = Math.floor(Math.random() * users.length);
  const user = users[randomUserIndex];
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

export function deleteExpiredData() {
  const baseUrl = __ENV.BASE_URL;

  const adminApikey = __ENV.IDP_SERVER_API_KEY;
  const adminApiSecret = __ENV.IDP_SERVER_API_SECRET;
  const url = `${baseUrl}/v1/admin/operations/delete-expired-data`;

  const payload = JSON.stringify({
    max_deletion_number: 100,
  });

  const credentials = `${adminApikey}:${adminApiSecret}`;
  const encodedCredentials = encoding.b64encode(credentials);

  const params = {
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Basic ${encodedCredentials}`
    }
  };

  const res = http.post(url, payload, params);
  // console.log(JSON.parse(res.body))
  check(res, { "deleteExpiredData request OK": (r) => r.status === 200 });
}
