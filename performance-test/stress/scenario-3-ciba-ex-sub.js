import http from "k6/http";
import { check, sleep } from "k6";

/**
 * CIBA Stress Test - External Subject (ex-sub:) login_hint pattern
 *
 * Tests the CIBA flow using ex-sub:{externalSubject},idp:{providerId} login_hint format.
 * This pattern uses findByExternalIdpSubject to look up users by their external IdP subject.
 */
export let options = {
  vus: 120,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

const data = JSON.parse(open('../data/performance-test-user-ex-sub.json'));

export default function() {
  const baseUrl = __ENV.BASE_URL;
  const clientId = __ENV.CLIENT_ID;
  const clientSecret = __ENV.CLIENT_SECRET;
  const tenantId = __ENV.TENANT_ID;

  const testUser = data[getRandomInt(499)]
  const externalUserId = testUser.external_user_id;
  const providerId = testUser.provider_id;
  const deviceId = testUser.device_id;
  const bindingMessage = "999";
  const loginHint = encodeURIComponent(`ex-sub:${externalUserId},idp:${providerId}`);

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
  check(backchannelRes, { "auth request OK": (r) => r.status === 200 });

  if (backchannelRes.status !== 200) {
    return;
  }

  const authReqId = JSON.parse(backchannelRes.body).auth_req_id;

  // authentication transaction
  const txRes = http.get(`${baseUrl}/${tenantId}/v1/authentication-devices/${deviceId}/authentications?attributes.auth_req_id=${authReqId}`);
  check(txRes, { "txRes request OK": (r) => r.status === 200 });

  if (txRes.status !== 200) {
    return;
  }

  const txList = JSON.parse(txRes.body);
  const tx = txList.list[0];

  // bindingMessage
  const bindingMessageRes = http.post(`${baseUrl}/${tenantId}/v1/authentications/${tx.id}/authentication-device-binding-message`,
    JSON.stringify({ binding_message: bindingMessage }),
    { headers: { "Content-Type": "application/json" } }
  );
  check(bindingMessageRes, { "authentication-device-binding-message request OK": (r) => r.status === 200 });

  // token
  const tokenRes = http.post(
    `${baseUrl}/${tenantId}/v1/tokens`,
    `grant_type=urn:openid:params:grant-type:ciba&auth_req_id=${authReqId}&client_id=${clientId}&client_secret=${clientSecret}`,
    { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
  );
  check(tokenRes, { "tokenRes request OK": (r) => r.status === 200 });

  const jwksResponse = http.get(`${baseUrl}/${tenantId}/v1/jwks`);
  check(jwksResponse, { "jwksResponse request OK": (r) => r.status === 200 });
}

function getRandomInt(max) {
  return Math.floor(Math.random() * max);
}
