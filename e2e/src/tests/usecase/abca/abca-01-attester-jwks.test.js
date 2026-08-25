/**
 * ABCA Use Case: Client Attester with a static JWKS
 *
 * The deployment has a backend Client Attester that vouches for its app instances. The
 * Authorization Server is given the Attester's public keys once, as
 * extension.client_attestation_attester_jwks, and trusts any Client Attestation JWT signed with
 * them (client_attestation_trust_source = attester_jwks).
 *
 * What this covers beyond the spec-level tests, which check one request at a time:
 * 1. App start-up: fetch a Challenge, have the Attester issue a Client Attestation JWT, and
 *    authenticate
 * 2. Reuse of a single Client Attestation JWT across requests (Section 9.2), with a fresh PoP each
 *    time
 * 3. Expiry of the Client Attestation JWT and recovery from use_fresh_attestation
 * 4. Attester key rotation: publishing both keys, then retiring the old one
 * 5. The same credentials working at the Pushed Authorization Request endpoint
 */
import { beforeAll, describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import * as jose from "jose";
import { post, postWithJson, putWithJson } from "../../../lib/http";
import { onboarding } from "../../../api/managementClient";
import { requestToken } from "../../../api/oauthClient";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { createJwtWithPrivateKey, generateECP256JWKS, generateJti } from "../../../lib/jose";
import { toEpocTime } from "../../../lib/util";

const ATTESTATION_TYP = "oauth-client-attestation+jwt";
const POP_TYP = "oauth-client-attestation-pop+jwt";
const ATTESTATION_HEADER = "OAuth-Client-Attestation";
const POP_HEADER = "OAuth-Client-Attestation-PoP";
const CHALLENGE_HEADER = "OAuth-Client-Attestation-Challenge";

let managementHeaders;
let tenantId;
let issuer;
let tokenEndpoint;
let pushedAuthorizationEndpoint;
let challengeEndpoint;
let clientId;
let attesterCurrentJwk;
let attesterNextJwk;
let instanceJwk;

const generateSigningJwk = async (kid) => {
  const { privateKey } = await jose.generateKeyPair("ES256", { extractable: true });
  const jwk = await jose.exportJWK(privateKey);
  return { ...jwk, use: "sig", kid, alg: "ES256" };
};

const publicJwkOf = (privateJwk) => {
  const { d, ...publicJwk } = privateJwk;
  return publicJwk;
};

const clientsUrl = () => `${backendUrl}/v1/management/tenants/${tenantId}/clients`;
const clientUrl = () => `${clientsUrl()}/${clientId}`;

const clientBody = (attesterJwks) => ({
  client_id: clientId,
  client_name: "ABCA Attester JWKS Use Case Client",
  token_endpoint_auth_method: "attest_jwt_client_auth",
  extension: {
    client_attestation_trust_source: "attester_jwks",
    client_attestation_attester_jwks: JSON.stringify({ keys: attesterJwks.map(publicJwkOf) }),
  },
  grant_types: ["client_credentials"],
  redirect_uris: ["http://localhost:3000/callback"],
  response_types: ["code"],
  scope: "account management",
  enabled: true,
});

/** Client Attester role: vouches for the Client Instance Key by binding it in cnf. */
const issueAttestationJwt = ({
  signingKey = () => attesterCurrentJwk,
  exp = toEpocTime({ adjusted: 300 }),
} = {}) => {
  const key = typeof signingKey === "function" ? signingKey() : signingKey;
  return createJwtWithPrivateKey({
    payload: {
      iss: "attester.example.com",
      sub: clientId,
      iat: toEpocTime({ adjusted: 0 }),
      exp,
      cnf: { jwk: publicJwkOf(instanceJwk) },
    },
    privateKey: key,
    algorithm: "ES256",
    additionalOptions: { header: { typ: ATTESTATION_TYP } },
  });
};

/** Client Instance role: proves possession of the key the Attester bound in cnf. */
const createPopJwt = ({ challenge } = {}) => {
  const payload = {
    aud: issuer,
    jti: generateJti(),
    iat: toEpocTime({ adjusted: 0 }),
  };
  if (challenge) {
    payload.challenge = challenge;
  }
  return createJwtWithPrivateKey({
    payload,
    privateKey: instanceJwk,
    algorithm: "ES256",
    additionalOptions: { header: { typ: POP_TYP } },
  });
};

const fetchChallenge = async () => {
  const response = await postWithJson({
    url: challengeEndpoint,
    body: {},
  });
  expect(response.status).toBe(200);
  return response.data.attestation_challenge;
};

const requestTokenWith = async ({ attestationJwt, popJwt }) =>
  await requestToken({
    endpoint: tokenEndpoint,
    grantType: "client_credentials",
    scope: "account",
    clientId,
    additionalHeaders: {
      [ATTESTATION_HEADER]: attestationJwt,
      [POP_HEADER]: popJwt,
    },
  });

beforeAll(async () => {
  const tokenResponse = await requestToken({
    endpoint: adminServerConfig.tokenEndpoint,
    grantType: "password",
    username: adminServerConfig.oauth.username,
    password: adminServerConfig.oauth.password,
    scope: adminServerConfig.adminClient.scope,
    clientId: adminServerConfig.adminClient.clientId,
    clientSecret: adminServerConfig.adminClient.clientSecret,
  });
  expect(tokenResponse.status).toBe(200);
  managementHeaders = { Authorization: `Bearer ${tokenResponse.data.access_token}` };

  // A tenant of its own, so that this use case can pick its own attestation policy without
  // disturbing the shared test tenant.
  const timestamp = Date.now();
  const organizationId = uuidv4();
  tenantId = uuidv4();
  issuer = `${backendUrl}/${tenantId}`;
  tokenEndpoint = `${issuer}/v1/tokens`;
  pushedAuthorizationEndpoint = `${issuer}/v1/authorizations/push`;
  challengeEndpoint = `${issuer}/v1/client-attestation/challenges`;

  const onboardingResponse = await onboarding({
    headers: managementHeaders,
    body: {
      organization: {
        id: organizationId,
        name: `ABCA Attester JWKS ${timestamp}`,
        description: "ABCA use case: Client Attester with a static JWKS",
      },
      tenant: {
        id: tenantId,
        name: `ABCA Attester JWKS Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        identity_policy_config: { identity_unique_key_type: "EMAIL" },
        session_config: { cookie_name: `AB1_${tenantId.substring(0, 8)}`, use_secure_cookie: false },
        cors_config: { allow_origins: [backendUrl] },
      },
      authorization_server: {
        issuer,
        authorization_endpoint: `${issuer}/v1/authorizations`,
        token_endpoint: tokenEndpoint,
        userinfo_endpoint: `${issuer}/v1/userinfo`,
        jwks_uri: `${issuer}/v1/jwks`,
        jwks: await generateECP256JWKS(),
        pushed_authorization_request_endpoint: pushedAuthorizationEndpoint,
        token_endpoint_auth_methods_supported: ["client_secret_post", "attest_jwt_client_auth"],
        client_attestation_signing_alg_values_supported: ["ES256"],
        client_attestation_pop_signing_alg_values_supported: ["ES256"],
        // Section 6.1: advertising the endpoint is what tells clients Challenges are available.
        challenge_endpoint: challengeEndpoint,
        grant_types_supported: ["authorization_code", "password", "client_credentials"],
        scopes_supported: ["openid", "profile", "email", "account", "management"],
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["ES256"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        extension: {
          access_token_type: "JWT",
          // The rollout position: Challenges are offered but not yet mandatory, so clients can
          // adopt them before requests without one start failing.
          client_attestation_challenge_required: false,
        },
      },
      user: {
        sub: uuidv4(),
        provider_id: "idp-server",
        email: `admin-${timestamp}@abca-attester.example.com`,
        email_verified: true,
        raw_password: `AbcaPass_${timestamp}!`,
      },
      client: {
        client_id: uuidv4(),
        client_secret: `cs-${timestamp}`,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "password"],
        scope: "openid profile email management",
        client_name: "ABCA Attester JWKS Management Client",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    },
  });
  expect(onboardingResponse.status).toBe(201);

  attesterCurrentJwk = await generateSigningJwk("attester-current");
  attesterNextJwk = await generateSigningJwk("attester-next");
  instanceJwk = await generateSigningJwk("instance-1");

  clientId = uuidv4();
  const registrationResponse = await postWithJson({
    url: clientsUrl(),
    headers: managementHeaders,
    body: clientBody([attesterCurrentJwk]),
  });
  expect(registrationResponse.status).toBe(201);
});

describe("ABCA Use Case: Client Attester with a static JWKS", () => {

  it("app start-up: fetches a Challenge, gets a Client Attestation JWT from its Attester, and authenticates", async () => {
    console.log("\n=== Step 1: the Client Instance fetches a Challenge ===");
    const challenge = await fetchChallenge();
    expect(typeof challenge).toBe("string");

    console.log("=== Step 2: the Attester issues a Client Attestation JWT for the instance key ===");
    const attestationJwt = issueAttestationJwt();

    console.log("=== Step 3: the Client Instance authenticates at the token endpoint ===");
    const response = await requestTokenWith({
      attestationJwt,
      popJwt: createPopJwt({ challenge }),
    });
    expect(response.status).toBe(200);
    expect(response.data).toHaveProperty("access_token");
  });

  it("reuses one Client Attestation JWT across requests while producing a fresh PoP each time (Section 9.2)", async () => {
    // The Attester is a backend round-trip, so an instance is expected to keep its attestation for
    // its whole lifetime and only re-sign the cheap PoP.
    const attestationJwt = issueAttestationJwt();
    const challenge = await fetchChallenge();

    const first = await requestTokenWith({
      attestationJwt,
      popJwt: createPopJwt({ challenge }),
    });
    const second = await requestTokenWith({
      attestationJwt,
      popJwt: createPopJwt({ challenge }),
    });
    const third = await requestTokenWith({
      attestationJwt,
      popJwt: createPopJwt({ challenge }),
    });

    expect(first.status).toBe(200);
    expect(second.status).toBe(200);
    expect(third.status).toBe(200);
    expect(first.data.access_token).not.toBe(second.data.access_token);
  });

  it("recovers from an expired Client Attestation JWT by asking the Attester for a new one", async () => {
    console.log("\n=== Step 1: the cached attestation has expired ===");
    const expired = await requestTokenWith({
      attestationJwt: issueAttestationJwt({ exp: toEpocTime({ adjusted: -60 }) }),
      popJwt: createPopJwt(),
    });
    expect(expired.status).toBe(401);
    expect(expired.data).toHaveProperty("error", "use_fresh_attestation");

    console.log("=== Step 2: the client asks its Attester for a fresh attestation and retries ===");
    const retried = await requestTokenWith({
      attestationJwt: issueAttestationJwt(),
      popJwt: createPopJwt(),
    });
    expect(retried.status).toBe(200);
  });

  it("rotates the Attester key: publishing both keys keeps old and new attestations working", async () => {
    console.log("\n=== Step 1: publish the next Attester key alongside the current one ===");
    const updated = await putWithJson({
      url: clientUrl(),
      headers: managementHeaders,
      body: clientBody([attesterCurrentJwk, attesterNextJwk]),
    });
    expect(updated.status).toBe(200);

    console.log("=== Step 2: attestations signed by either key are accepted ===");
    const withCurrent = await requestTokenWith({
      attestationJwt: issueAttestationJwt({ signingKey: () => attesterCurrentJwk }),
      popJwt: createPopJwt(),
    });
    const withNext = await requestTokenWith({
      attestationJwt: issueAttestationJwt({ signingKey: () => attesterNextJwk }),
      popJwt: createPopJwt(),
    });

    expect(withCurrent.status).toBe(200);
    expect(withNext.status).toBe(200);
  });

  it("retires the old Attester key: attestations signed by it stop being accepted", async () => {
    const updated = await putWithJson({
      url: clientUrl(),
      headers: managementHeaders,
      body: clientBody([attesterNextJwk]),
    });
    expect(updated.status).toBe(200);

    const withRetired = await requestTokenWith({
      attestationJwt: issueAttestationJwt({ signingKey: () => attesterCurrentJwk }),
      popJwt: createPopJwt(),
    });
    expect(withRetired.status).toBe(401);
    expect(withRetired.data).toHaveProperty("error", "invalid_client_attestation");

    const withNext = await requestTokenWith({
      attestationJwt: issueAttestationJwt({ signingKey: () => attesterNextJwk }),
      popJwt: createPopJwt(),
    });
    expect(withNext.status).toBe(200);
  });

  it("uses the same credentials at the Pushed Authorization Request endpoint", async () => {
    const params = new URLSearchParams();
    params.append("response_type", "code");
    params.append("client_id", clientId);
    params.append("redirect_uri", "http://localhost:3000/callback");
    params.append("scope", "account");
    params.append("state", "abca-usecase-par");

    const response = await post({
      url: pushedAuthorizationEndpoint,
      body: params,
      headers: {
        [ATTESTATION_HEADER]: issueAttestationJwt({ signingKey: () => attesterNextJwk }),
        [POP_HEADER]: createPopJwt(),
      },
    });
    expect(response.status).toBe(201);
    expect(response.data).toHaveProperty("request_uri");
  });

  it("hands back a Challenge to use next when the presented one is not one the server issued", async () => {
    const response = await requestTokenWith({
      attestationJwt: issueAttestationJwt({ signingKey: () => attesterNextJwk }),
      popJwt: createPopJwt({ challenge: "stale-challenge-from-an-old-session" }),
    });
    expect(response.status).toBe(401);
    expect(response.data).toHaveProperty("error", "use_attestation_challenge");

    const handedBack = response.headers[CHALLENGE_HEADER.toLowerCase()];
    expect(handedBack).toBeDefined();

    const retried = await requestTokenWith({
      attestationJwt: issueAttestationJwt({ signingKey: () => attesterNextJwk }),
      popJwt: createPopJwt({ challenge: handedBack }),
    });
    expect(retried.status).toBe(200);
  });
});
