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
  const testData = getTestData(index);
  const clientId = testData.clientId;
  const clientSecret = testData.clientSecret;
  const tenantId = testData.tenantId;

  const deviceId = testData.deviceId;
  const bindingMessage = "999";
  const loginHint = encodeURIComponent(`sub:${deviceId},idp-server`);

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
  const txRes = http.get(`${baseUrl}/${tenantId}/v1/authentication-devices/${deviceId}/authentications/latest`);
  check(txRes, { "txRes request OK": (r) => r.status === 200 });
  const tx = JSON.parse(txRes.body);
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

//TODO change your data
const getTestData = (index) => {
  return [
    { tenantId: "0db93323-cc6c-4248-a6d4-1ba691b6e096", clientId: "clientSecretPost", clientSecret: "clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890", deviceId: "0b8dc6fb-9330-4037-9f22-7447c2bcd7ed" },
    { tenantId: "0ee91f98-ad99-430d-9bc5-6ec5b7eb1368", clientId: "clientSecretPost", clientSecret: "clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890", deviceId: "0cf90ab2-e81e-476f-826c-04a056bbb206" },
    { tenantId: "1b738a2b-2b91-46af-b1a8-295dc1d77e65", clientId: "clientSecretPost", clientSecret: "clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890", deviceId: "b66f3ac6-c5d5-4cb7-800d-3ec99b17931b" },
    { tenantId: "1f26fc2f-dbca-4fd6-9397-fd3fd0b0b37a", clientId: "clientSecretPost", clientSecret: "clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890", deviceId: "5aa68e9b-a447-42b0-b29c-8b15f246d23f" },
    { tenantId: "2c4330c1-dcc0-42cb-a6d8-f6e32e7b2504", clientId: "clientSecretPost", clientSecret: "clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890", deviceId: "73dafa1b-5ad4-4a3c-a6a5-4f885cd0d8d0" }
  ][index]
}