import http from "k6/http";
import { check } from "k6";

// 設定ファイルから読み込み
const tenantData = JSON.parse(open('../data/performance-test-tenant.json'));
const tenantCount = tenantData.length;

// 動的にシナリオを生成
const scenarios = {};
for (let i = 0; i < tenantCount; i++) {
  scenarios[`tenant${i}`] = {
    executor: 'constant-arrival-rate',
    preAllocatedVUs: Math.ceil(30 / tenantCount),
    maxVUs: Math.ceil(60 / tenantCount),
    rate: Math.ceil(10 / tenantCount) || 1,
    timeUnit: '1s',
    duration: '5m',
    exec: 'multiTenantAuthCodeFlow',
    env: { TENANT_INDEX: String(i) },
  };
}

export const options = {
  scenarios: scenarios,
};

export function multiTenantAuthCodeFlow() {
  const index = parseInt(__ENV.TENANT_INDEX);
  authCodeFlow(index);
}

function authCodeFlow(index) {
  const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
  const testData = tenantData[index];
  const tenantId = testData.tenantId;
  const clientId = testData.clientId;
  const clientSecret = testData.clientSecret;
  const userId = testData.userId;
  const userEmail = testData.userEmail;
  const userPassword = testData.userPassword;
  const redirectUri = 'http://localhost:8081/callback';

  const state = `state-${Date.now()}`;
  const nonce = `nonce-${Date.now()}`;
  const encodedRedirectUri = encodeURIComponent(redirectUri);

  // Step 1: Authorization Request
  const authRes = http.get(
    `${baseUrl}/${tenantId}/v1/authorizations?` +
    `scope=openid+profile+email+phone&` +
    `response_type=code&` +
    `client_id=${clientId}&` +
    `redirect_uri=${encodedRedirectUri}&` +
    `state=${state}&nonce=${nonce}`,
    { redirects: 0 }
  );

  check(authRes, {
    'Step 1: authorization request': (r) => r.status === 302,
  });

  const location = authRes.headers['Location'];
  const authorizationId = parseQueryParams(location).id;

  if (!authorizationId) {
    console.error('No authorization_id found');
    return;
  }

  // Step 2: Get View Data
  const viewRes = http.get(`${baseUrl}/${tenantId}/v1/authorizations/${authorizationId}/view-data`);
  check(viewRes, {
    'Step 2: view-data': (r) => r.status === 200,
  });

  // Step 3: Password Authentication
  const passwordAuthRes = http.post(
    `${baseUrl}/${tenantId}/v1/authorizations/${authorizationId}/password-authentication`,
    JSON.stringify({
      username: userEmail,
      password: userPassword,
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(passwordAuthRes, {
    'Step 3: password authentication': (r) => r.status === 200,
  });

  // Step 4: Authorize
  const authorizeRes = http.post(`${baseUrl}/${tenantId}/v1/authorizations/${authorizationId}/authorize`);

  check(authorizeRes, {
    'Step 4: authorize': (r) => r.status === 200,
  });

  const authorizeJson = authorizeRes.json();
  const callbackParams = parseQueryParams(authorizeJson.redirect_uri);

  if (!callbackParams.code) {
    console.error('No authorization code found');
    return;
  }

  // Step 5: Token Request
  const tokenRes = http.post(
    `${baseUrl}/${tenantId}/v1/tokens`,
    `grant_type=authorization_code` +
    `&code=${callbackParams.code}` +
    `&client_id=${clientId}` +
    `&client_secret=${clientSecret}` +
    `&redirect_uri=${redirectUri}`,
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
  );

  check(tokenRes, {
    'Step 5: token request': (r) => r.status === 200,
  });

  const tokenJson = tokenRes.json();
  const accessToken = tokenJson.access_token;

  if (!accessToken) {
    console.error('No access token found');
    return;
  }

  // Step 6: Userinfo Request
  const userinfoRes = http.get(
    `${baseUrl}/${tenantId}/v1/userinfo`,
    { headers: { 'Authorization': `Bearer ${accessToken}` } }
  );

  check(userinfoRes, {
    'Step 6: userinfo': (r) => r.status === 200,
  });

  // Step 7: Token Introspection
  const introspectionRes = http.post(
    `${baseUrl}/${tenantId}/v1/tokens/introspection`,
    `token=${accessToken}&client_id=${clientId}&client_secret=${clientSecret}`,
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
  );

  check(introspectionRes, {
    'Step 7: introspection': (r) => r.status === 200,
  });
}

function parseQueryParams(url) {
  if (!url) return {};

  const queryString = url.includes('?') ? url.split('?')[1] : url.split('#')[1];
  const params = {};

  if (!queryString) return params;

  queryString.split('&').forEach((pair) => {
    const [key, value] = pair.split('=');
    params[decodeURIComponent(key)] = decodeURIComponent(value || '');
  });

  return params;
}
