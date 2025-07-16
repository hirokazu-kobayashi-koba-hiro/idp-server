import http from "k6/http";
import { check, sleep } from "k6";

export const options = {

  scenarios: {
    tenant0: {
      executor: 'constant-arrival-rate',
      preAllocatedVUs: 50,
      maxVUs: 100,
      rate: 20,
      timeUnit: '1s',
      duration: '5m',
      exec: 'tenant0login',
    },
    tenant1: {
      executor: 'constant-arrival-rate',
      preAllocatedVUs: 50,
      maxVUs: 100,
      rate: 20,
      timeUnit: '1s',
      duration: '5m',
      exec: 'tenant1login',
    },
    tenant2: {
      executor: 'constant-arrival-rate',
      preAllocatedVUs: 50,
      maxVUs: 100,
      rate: 20,
      timeUnit: '1s',
      duration: '5m',
      exec: 'tenant2login',
    },
    tenant3: {
      executor: 'constant-arrival-rate',
      preAllocatedVUs: 50,
      maxVUs: 100,
      rate: 20,
      timeUnit: '1s',
      duration: '5m',
      exec: 'tenant3login',
    },
    tenant4: {
      executor: 'constant-arrival-rate',
      preAllocatedVUs: 50,
      maxVUs: 100,
      rate: 20,
      timeUnit: '1s',
      duration: '5m',
      exec: 'tenant4login',
    },
  },
};

const data = JSON.parse(open('../data/performance-test-tenant.json'));

export function tenant0login() {
  login(0)
}

export function tenant1login() {
  login(1)
}

export function tenant2login() {
  login(2)
}

export function tenant3login() {
  login(3)
}

export function tenant4login() {
  login(4)
}


function login(index) {
  const baseUrl = __ENV.BASE_URL;
  const testData = data[index];
  const clientId = testData.clientId;
  const clientSecret = testData.clientSecret;
  const tenantId = testData.tenantId;

  const deviceId = testData.deviceId;
  const bindingMessage = "999";
  const loginHint = encodeURIComponent(`sub:${deviceId},idp:idp-server`);

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