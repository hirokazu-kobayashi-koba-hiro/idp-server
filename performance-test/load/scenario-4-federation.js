import http from "k6/http";
import { check } from "k6";

export let options = {
  vus: 30,
  duration: '20m',
};

const BASE_URL = __ENV.BASE_URL;
const TENANT_ID = __ENV.TENANT_ID;
const CLIENT_ID = __ENV.CLIENT_ID;
const CLIENT_SECRET = __ENV.CLIENT_SECRET;
const REDIRECT_URI = __ENV.REDIRECT_URI;

export default function () {
  const state = 'test-state';
  const nonce = 'test-nonce';
  const encodedRedirectUri = encodeURIComponent(REDIRECT_URI)

  const authRes = http.get(`${BASE_URL}/${TENANT_ID}/v1/authorizations?` +
    `scope=openid+profile+email+phone&` +
    `response_type=code&client_id=${CLIENT_ID}&redirect_uri=${encodedRedirectUri}&` +
    `state=${state}&nonce=${nonce}`, { redirects: 0 });

  check(authRes, {
    'Step 1: authorization request': (r) => r.status === 302,
  });

  const { Location: url} = authRes.headers;
  const { id } = parseQueryParams(url)

  if (!id) {
    console.error('No authorization_id found');
    return;
  }

  const viewRes = http.get(`${BASE_URL}/${TENANT_ID}/v1/authorizations/${id}/view-data`);
  check(viewRes, {
    'Step 2: view-data': (r) => r.status === 200
  });


  const federations = viewRes.json('available_federations');
  const federation = federations.find(f => f.auto_selected);

  if (!federation) {
    console.error('No auto_selected federation found');
    return;
  }

  // Step 3: federation request
  const federationRes = http.post(`${BASE_URL}/${TENANT_ID}/v1/authorizations/${id}/federations/${federation.type}/${federation.sso_provider}`);

  check(federationRes, {
    'Step 3: federation request': (r) => r.status === 200,
  });

  const federationAuthorizationRequestUrl = federationRes.json("redirect_uri");
  const federationAuthRes = http.get(federationAuthorizationRequestUrl, { redirects: 0 });
  // console.log(federationAuthRes)

  check(federationAuthRes, {
    'Step 4: federationAuthRes': (r) => r.status === 302,
  });

  const { Location: federationUrl} = federationAuthRes.headers;
  const { id: federationAuthId, tenant_id: federationTenantId } = parseQueryParams(federationUrl)
  // console.log(federationAuthId, federationTenantId)

  const viewResAfterFederation = http.get(`${BASE_URL}/${federationTenantId}/v1/authorizations/${federationAuthId}/view-data`);
  check(viewResAfterFederation, {
    'Step 5: view-data after federation': (r) => r.status === 200,
  });

  const challengeRes = http.post(`${BASE_URL}/${federationTenantId}/v1/authorizations/${federationAuthId}/email-authentication-challenge`, JSON.stringify({
    email: randomEmail(),
    email_template: "authentication"
  }), {
    headers: { 'Content-Type': 'application/json' }
  });

  check(challengeRes, {
    'Step 6: email authentication challenge': (r) => r.status === 200,
  });

  const txRes = http.get(`${BASE_URL}/${federationTenantId}/v1/authentications?authorization_id=${federationAuthId}`);
  check(txRes, { 'Step 7: find authentication transaction': (r) => r.status === 200 });
  const tx = txRes.json();
  const txId = tx.list[0].id

  const adminTokenRes = http.post(`${BASE_URL}/${TENANT_ID}/v1/tokens`,
    {
      grant_type: 'password',
      username: "ito.ichiro",
      password: "successUserCode001",
      client_id: "clientSecretPost",
      client_secret: "clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890",
      scope: 'openid profile management'
    },
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
  );
  check(adminTokenRes, { 'Step 8: issue admin token': (r) => r.status === 200 });
  const accessToken = adminTokenRes.json('access_token');

  const interactionRes = http.get(`${BASE_URL}/v1/management/tenants/${federationTenantId}/authentication-interactions/${txId}/email`, {
    headers: {
      Authorization: `Bearer ${accessToken}`
    }
  });
  // console.log(interactionRes)
  check(interactionRes, { 'Step 9: find interaction email verification_code': (r) => r.status === 200 });
  const verificationCode = interactionRes.json('payload.verification_code');

  const authRes2 = http.post(`${BASE_URL}/${federationTenantId}/v1/authorizations/${federationAuthId}/email-authentication`, JSON.stringify({
    verification_code: verificationCode
  }), {
    headers: { 'Content-Type': 'application/json' }
  });

  check(authRes2, {
    'Step 10: email verification': (r) => r.status === 200,
  });

  const federationAuthorizeResponse = http.post(`${BASE_URL}/${federationTenantId}/v1/authorizations/${federationAuthId}/authorize`);

  check(federationAuthorizeResponse, {
    'Step 11: federation authorize': (r) => r.status === 200,
  });

  // console.log(authorizeResponse)
  const { redirect_uri: federationRedirectUri } = federationAuthorizeResponse.json();
  // console.log(federationRedirectUri)
  const federationCallbackParam = parseQueryParams(federationRedirectUri);

  const federationCallbackResponse = http.post(`${BASE_URL}/v1/authorizations/federations/oidc/callback`,
    federationCallbackParam,
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
  )
  // console.log(federationCallbackResponse)
  check(federationCallbackResponse, {
    'Step 12: federation callback': (r) => r.status === 200,
  });

  const authorizeResponse = http.post(`${BASE_URL}/${TENANT_ID}/v1/authorizations/${id}/authorize`);

  check(authorizeResponse, {
    'Step 13: authorize success': (r) => r.status === 200,
  });

  // console.log(authorizeResponse)
  const { redirect_uri: redirectUri } = authorizeResponse.json();
  // console.log(redirectUri)
  const callbackParams = parseQueryParams(redirectUri);

  const tokenRes = http.post(`${BASE_URL}/${TENANT_ID}/v1/tokens`,
    {
      grant_type: 'authorization_code',
      code: callbackParams.code,
      client_id: `${CLIENT_ID}`,
      client_secret: `${CLIENT_SECRET}`,
      redirect_uri: `${REDIRECT_URI}`
    },
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
  );
  check(tokenRes, { 'Step 14: token request': (r) => r.status === 200 });

}

function parseQueryParams(url) {
  const queryString = url.includes('?') ? url.split('?')[1] : url.split('#')[1];
  const params = {};

  if (!queryString) return params;

  queryString.split('&').forEach((pair) => {
    const [key, value] = pair.split('=');
    params[decodeURIComponent(key)] = decodeURIComponent(value || '');
  });

  // console.log(params)

  return params;
}

function randomEmail() {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
  const name = Array.from({ length: 30 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
  return `${name}@example.com`;
}