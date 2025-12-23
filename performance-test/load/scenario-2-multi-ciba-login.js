import http from "k6/http";
import { check, sleep } from "k6";

const data = JSON.parse(open('../data/performance-test-tenant.json'));
const tenantCount = data.length;

// 動的にシナリオを生成
const scenarios = {};
for (let i = 0; i < tenantCount; i++) {
  scenarios[`tenant${i}`] = {
    executor: 'constant-arrival-rate',
    preAllocatedVUs: Math.ceil(50 / tenantCount),
    maxVUs: Math.ceil(100 / tenantCount),
    rate: Math.ceil(20 / tenantCount) || 1,
    timeUnit: '1s',
    duration: '5m',
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
  const baseUrl = __ENV.BASE_URL;
  const testData = data[index];
  const clientId = testData.clientId;
  const clientSecret = testData.clientSecret;
  const tenantId = testData.tenantId;

  const userId = testData.userId;
  const deviceId = testData.deviceId;
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