import http from "k6/http";
import { check, sleep } from "k6";
import encoding from "k6/encoding";

export const options = {
  scenarios: {
    peakLoad: {
      executor: 'ramping-arrival-rate',
      startRate: 30, // Initial request rate (requests per second)
      timeUnit: '1s',
      preAllocatedVUs: 5, // Minimum number of VUs to pre-allocate
      maxVUs: 20,         // Maximum number of VUs allowed
      stages: [
        { target: 30, duration: '3m' }, // Ramp-up phase
        { target: 30, duration: '5m' }, // Sustained peak phase
        { target: 0, duration: '2m' }     // Ramp-down phase
      ],
      exec: 'peakLogin'
    },
    deleteExpiredData: {
      executor: 'constant-arrival-rate',
      rate: '1',
      timeUnit: '30s',
      duration: '10m',
      preAllocatedVUs: 1,
      maxVUs: 1,
      exec: 'deleteExpiredData',
    }
  }
}

const data = JSON.parse(open('../data/performance-test-tenant.json'));
const tenantCount = data.length;

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

export function deleteExpiredData() {
  const baseUrl = __ENV.BASE_URL;

  const adminApikey = __ENV.ADMIN_API_KEY;
  const adminApiSecret = __ENV.ADMIN_API_SECRET;
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
