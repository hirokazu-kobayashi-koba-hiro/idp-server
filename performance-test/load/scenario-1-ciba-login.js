import http from "k6/http";
import { check, sleep } from "k6";

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

const data = JSON.parse(open('../data/performance-test-user.json'));

export function login() {
  const baseUrl = __ENV.BASE_URL;
  const clientId = __ENV.CLIENT_ID;
  const clientSecret = __ENV.CLIENT_SECRET;
  const tenantId = __ENV.TENANT_ID;

  const testUser = data[getRandomInt(499)];
  const deviceId = testUser.device_id;
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
  // console.log(backchannelRes)
  check(backchannelRes, { "auth request OK": (r) => r.status === 200 });
  const authReqId = JSON.parse(backchannelRes.body).auth_req_id;
  // console.log(authReqId)

  //authentication transaction
  const txRes = http.get(`${baseUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/authentication-devices/${deviceId}/authentications/latest`);
  check(txRes, { "txRes request OK": (r) => r.status === 200 });
  const tx = JSON.parse(txRes.body);
  // console.log(tx.id)

  //bindingMessage
  const bindingMessageRes = http.post(`${baseUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/authentications/${tx.id}/authentication-device-binding-message`,
    JSON.stringify({ binding_message: bindingMessage }),
    { headers: { "Content-Type": "application/json" } }
  );
  check(bindingMessageRes, { "authentication-device-binding-message request OK": (r) => r.status === 200 });
  // console.log(bindingMessageRes)

  //token
  const tokenRes = http.post(
    `${baseUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/tokens`,
    `grant_type=urn:openid:params:grant-type:ciba&auth_req_id=${authReqId}&client_id=${clientId}&client_secret=${clientSecret}`,
    { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
  );
  check(tokenRes, { "tokenRes request OK": (r) => r.status === 200 });

  // console.log(tokenRes.status);

  const jwksResponse = http.get("http://localhost:8080/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/jwks");

  check(jwksResponse, { "jwksResponse request OK": (r) => r.status === 200 });

}

export function introspect() {

  const baseUrl = __ENV.BASE_URL;
  const tenantId = __ENV.TENANT_ID;
  const token = __ENV.ACCESS_TOKEN
  const url = `${baseUrl}/${tenantId}/v1/tokens/introspection`;

  const payload = `token=${token}`

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

function getRandomInt(max) {
  return Math.floor(Math.random() * max);
}
