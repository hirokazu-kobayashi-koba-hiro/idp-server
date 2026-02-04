import http from "k6/http";
import { check, sleep } from "k6";

/**
 * CIBA Stress Test - Email (email:) login_hint pattern
 *
 * Tests the CIBA flow using email:{email},idp:{providerId} login_hint format.
 * This pattern uses findByEmail to look up users by their email address.
 */
// 環境変数でカスタマイズ可能なパラメータ
const VU_COUNT = parseInt(__ENV.VU_COUNT || '120');
const DURATION = __ENV.DURATION || '30s';

export let options = {
  vus: VU_COUNT,
  duration: DURATION,
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

// 設定ファイルから読み込み
const tenantData = JSON.parse(open('../data/performance-test-multi-tenant-users.json'));

const tenantIndex = parseInt(__ENV.TENANT_INDEX || '0');
const config = tenantData[tenantIndex];
const users = config.users;
const userCount = users.length;

export default function() {
  const baseUrl = __ENV.BASE_URL || 'https://api.local.dev';
  const clientId = config.clientId;
  const clientSecret = config.clientSecret;
  const tenantId = config.tenantId;

  // ユーザーをランダムに選択
  const randomIndex = Math.floor(Math.random() * userCount);
  const user = users[randomIndex];
  const email = user.email;
  const deviceId = user.device_id;
  const providerId = user.provider_id || 'idp-server';

  const bindingMessage = "999";
  const loginHint = encodeURIComponent(`email:${email},idp:${providerId}`);

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
