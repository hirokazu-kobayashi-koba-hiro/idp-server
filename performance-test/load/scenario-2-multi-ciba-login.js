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
    { tenantId: "0e4337d8-fa7b-4229-b7cf-93db539ec82e", clientId: "clientSecretPost", clientSecret: "clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890", deviceId: "99173904-bd80-46a4-b311-f2d1e752afc7" },
    { tenantId: "2c5405e4-b75b-45b1-a076-a205da29955c", clientId: "clientSecretPost", clientSecret: "clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890", deviceId: "348f62af-a154-41c2-9e86-f70b6574a1d0" },
    { tenantId: "2f6e0db2-fa7c-4728-b7f0-49a12eacbd83", clientId: "clientSecretPost", clientSecret: "clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890", deviceId: "7a8c42d0-97eb-4621-a690-f2ccee6c7869" },
    { tenantId: "02a7c302-76ce-4b0a-9630-1a3aefcff15e", clientId: "clientSecretPost", clientSecret: "clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890", deviceId: "ceafd6ca-2bd8-41e2-b2bb-f2e9be1f741f" },
    { tenantId: "3bd3a3b3-e7a9-40e6-8e6c-ac5b0450caec", clientId: "clientSecretPost", clientSecret: "clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890", deviceId: "ae11a44b-2f21-480e-bf1c-d3d3c3261b22" }
  ][index]
}