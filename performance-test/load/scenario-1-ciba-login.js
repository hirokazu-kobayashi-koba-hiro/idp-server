import http from "k6/http";
import { check, sleep } from "k6";

// 設定ファイルから読み込み（環境変数BASE_URLのみ必要）
const tenantData = JSON.parse(open('../data/performance-test-tenant.json'));
const tenantIndex = parseInt(__ENV.TENANT_INDEX || '0');
const config = tenantData[tenantIndex];

export const options = {

  scenarios: {
    login: {
      executor: 'constant-arrival-rate',
      preAllocatedVUs: 50,
      maxVUs: 100,
      rate: 20,
      timeUnit: '1s',
      duration: '5m',
      exec: 'login',
    },
    introspection: {
      executor: 'constant-arrival-rate',
      preAllocatedVUs: 50,
      maxVUs: 100,
      rate: 80,
      timeUnit: '1s',
      duration: '5m',
      exec: 'introspect',
    },
  },
};

export function login() {
  const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
  const clientId = config.clientId;
  const clientSecret = config.clientSecret;
  const tenantId = config.tenantId;

  // テナント登録時に作成されたユーザー/デバイスを使用
  const userId = config.userId;
  const deviceId = config.deviceId;
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
