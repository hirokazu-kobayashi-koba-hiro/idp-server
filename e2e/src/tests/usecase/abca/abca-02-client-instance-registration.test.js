/**
 * ABCA Use Case: an app that registers its own Client Instance Key
 *
 * There is no backend Client Attester here. The app generates a key on the device, registers it
 * with the Authorization Server, and from then on signs its own Client Attestation JWT with it
 * (client_attestation_trust_source = registered_instance_key). Section 9.8 leaves trust management
 * out of scope and Section 1 explicitly allows a client to act as its own attester.
 *
 * Registration cannot require an access token -- the token endpoint is where login happens -- so
 * the endpoints are unauthenticated and a server-issued challenge plus platform attestation carry
 * the authorization. These tests run against the development verifier, which checks the
 * challenge-to-key binding but performs no real device attestation
 * (IDP_SERVER_CLIENT_INSTANCE_DEVELOPMENT_VERIFIER).
 *
 * What this covers beyond the spec-level tests, which check one request at a time:
 * 1. First launch: register the instance key, then authenticate with it
 * 2. Steady state: reuse one self-signed attestation, with a server-provided Challenge
 * 3. Reinstall on the same device: revoke, re-register, and authenticate with the new key
 * 4. Lost device: revoking the instance stops the app from authenticating
 * 5. The same credentials working at the Pushed Authorization Request endpoint
 */
import { beforeAll, describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import * as jose from "jose";
import crypto from "crypto";
import { deletion, get, post, postWithJson } from "../../../lib/http";
import { onboarding } from "../../../api/managementClient";
import { requestToken } from "../../../api/oauthClient";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { createJwtWithPrivateKey, generateECP256JWKS, generateJti } from "../../../lib/jose";
import { toEpocTime } from "../../../lib/util";

const DEV_PLATFORM = "request-hash-binding-development-only";
const ATTESTATION_TYP = "oauth-client-attestation+jwt";
const POP_TYP = "oauth-client-attestation-pop+jwt";
const ATTESTATION_HEADER = "OAuth-Client-Attestation";
const POP_HEADER = "OAuth-Client-Attestation-PoP";

let managementHeaders;
let tenantId;
let issuer;
let tokenEndpoint;
let pushedAuthorizationEndpoint;
let challengeEndpoint;
let clientId;
let deviceId;

const base64url = (buffer) =>
  buffer.toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const base64urlDecode = (value) =>
  Buffer.from(value.replace(/-/g, "+").replace(/_/g, "/"), "base64");

const generateInstanceJwk = async () => {
  const { privateKey } = await jose.generateKeyPair("ES256", { extractable: true });
  return await jose.exportJWK(privateKey);
};

/** RFC 7638 required members only, lexicographic, no whitespace. */
const canonicalJwk = (jwk) => JSON.stringify({ crv: jwk.crv, kty: jwk.kty, x: jwk.x, y: jwk.y });

const publicJwkOf = (jwk) => ({ kty: jwk.kty, crv: jwk.crv, x: jwk.x, y: jwk.y });

const deriveRequestHash = (challenge, jwk) => {
  const digest = crypto.createHash("sha256");
  digest.update(base64urlDecode(challenge));
  digest.update(Buffer.from(canonicalJwk(jwk), "utf8"));
  return base64url(digest.digest());
};

const clientsUrl = () => `${backendUrl}/v1/management/tenants/${tenantId}/clients`;
const instancesUrl = () => `${clientsUrl()}/${clientId}/instances`;

/** Operator view: what the Authorization Server currently trusts for this device. */
const activeInstancesOf = async (device) => {
  const response = await get({ url: instancesUrl(), headers: managementHeaders });
  expect(response.status).toBe(200);
  return response.data.list.filter((instance) => instance.device_id === device);
};

const revokeInstancesOf = async (device = deviceId) => {
  for (const instance of await activeInstancesOf(device)) {
    const response = await deletion({
      url: `${instancesUrl()}/${instance.id}`,
      headers: managementHeaders,
    });
    expect(response.status).toBe(204);
  }
};

/** App side: generate a key on the device and enroll it against a server-issued challenge. */
const enrollInstance = async (device = deviceId) => {
  const jwk = await generateInstanceJwk();

  const challengeResponse = await postWithJson({
    url: `${issuer}/v1/client-instances/challenges`,
    body: { client_id: clientId, device_id: device },
  });
  expect(challengeResponse.status).toBe(200);
  const { challenge, instance_id: instanceId } = challengeResponse.data;

  const registerResponse = await postWithJson({
    url: `${issuer}/v1/client-instances`,
    body: {
      challenge,
      client_instance_public_key: publicJwkOf(jwk),
      platform_evidence: {
        platform: DEV_PLATFORM,
        request_hash: deriveRequestHash(challenge, jwk),
      },
    },
  });
  expect(registerResponse.status).toBe(201);

  return { jwk, instanceId };
};

const selfSignedAttestationJwt = ({ jwk, instanceId }) =>
  createJwtWithPrivateKey({
    payload: {
      sub: clientId,
      iat: toEpocTime({ adjusted: 0 }),
      exp: toEpocTime({ adjusted: 300 }),
      cnf: { jwk: publicJwkOf(jwk) },
    },
    privateKey: { ...jwk, kid: instanceId, alg: "ES256" },
    algorithm: "ES256",
    additionalOptions: { header: { typ: ATTESTATION_TYP } },
  });

const popJwt = (jwk, challenge) => {
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
    privateKey: { ...jwk, kid: "client-instance-key", alg: "ES256" },
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

/**
 * This tenant enforces the Challenge, so one is fetched unless the caller supplies its own (or
 * explicitly passes null to exercise the enforcement).
 */
const requestTokenWith = async (instance, challenge = undefined) =>
  await requestToken({
    endpoint: tokenEndpoint,
    grantType: "client_credentials",
    scope: "account",
    clientId,
    additionalHeaders: {
      [ATTESTATION_HEADER]: selfSignedAttestationJwt(instance),
      [POP_HEADER]: popJwt(instance.jwk, challenge === undefined ? await fetchChallenge() : challenge),
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

  // A tenant of its own, which lets this use case run with the Challenge enforced. Turning that on
  // for the shared test tenant would break every client there that does not send one yet.
  const timestamp = Date.now();
  const organizationId = uuidv4();
  tenantId = uuidv4();
  deviceId = uuidv4();
  issuer = `${backendUrl}/${tenantId}`;
  tokenEndpoint = `${issuer}/v1/tokens`;
  pushedAuthorizationEndpoint = `${issuer}/v1/authorizations/push`;
  challengeEndpoint = `${issuer}/v1/client-attestation/challenges`;

  const onboardingResponse = await onboarding({
    headers: managementHeaders,
    body: {
      organization: {
        id: organizationId,
        name: `ABCA Client Instance ${timestamp}`,
        description: "ABCA use case: an app that registers its own Client Instance Key",
      },
      tenant: {
        id: tenantId,
        name: `ABCA Client Instance Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        identity_policy_config: { identity_unique_key_type: "EMAIL" },
        session_config: { cookie_name: `AB2_${tenantId.substring(0, 8)}`, use_secure_cookie: false },
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
          // Section 7.2 item 5 enforced: every Client Attestation PoP JWT has to carry a Challenge.
          client_attestation_challenge_required: true,
        },
      },
      user: {
        sub: uuidv4(),
        provider_id: "idp-server",
        email: `admin-${timestamp}@abca-instance.example.com`,
        email_verified: true,
        raw_password: `AbcaPass_${timestamp}!`,
        authentication_devices: [{ id: deviceId, app_name: "ABCA Use Case App" }],
      },
      client: {
        client_id: uuidv4(),
        client_secret: `cs-${timestamp}`,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "password"],
        scope: "openid profile email management",
        client_name: "ABCA Client Instance Management Client",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    },
  });
  expect(onboardingResponse.status).toBe(201);

  clientId = uuidv4();
  const registrationResponse = await postWithJson({
    url: clientsUrl(),
    headers: managementHeaders,
    body: {
      client_id: clientId,
      client_name: "ABCA Client Instance Registration Use Case Client",
      token_endpoint_auth_method: "attest_jwt_client_auth",
      extension: {
        client_attestation_trust_source: "registered_instance_key",
        client_instance_registration_policy: "require_authentication_device",
      },
      grant_types: ["client_credentials"],
      redirect_uris: ["http://localhost:3000/callback"],
      response_types: ["code"],
      scope: "account management",
      enabled: true,
    },
  });
  expect(registrationResponse.status).toBe(201);
});

describe("ABCA Use Case: an app that registers its own Client Instance Key", () => {

  it("first launch: registers the instance key on the device and then authenticates with it", async () => {
    console.log("\n=== Step 1: the app enrolls the key it generated on the device ===");
    const instance = await enrollInstance();
    expect(await activeInstancesOf(deviceId)).toHaveLength(1);

    console.log("=== Step 2: the app authenticates with a self-signed Client Attestation JWT ===");
    const response = await requestTokenWith(instance);
    expect(response.status).toBe(200);
    expect(response.data).toHaveProperty("access_token");

    await revokeInstancesOf();
  });

  it("steady state: reuses one self-signed attestation across requests, with a server-provided Challenge", async () => {
    const instance = await enrollInstance();
    const challenge = await fetchChallenge();

    const first = await requestTokenWith(instance, challenge);
    const second = await requestTokenWith(instance, challenge);

    expect(first.status).toBe(200);
    expect(second.status).toBe(200);

    await revokeInstancesOf();
  });

  it("reinstall on the same device: the old key stops working and the newly enrolled one takes over", async () => {
    console.log("\n=== Step 1: the app is enrolled and working ===");
    const beforeReinstall = await enrollInstance();
    expect((await requestTokenWith(beforeReinstall)).status).toBe(200);

    console.log("=== Step 2: the previous instance is revoked so the device can enroll again ===");
    // Only one active instance per device is allowed, so a reinstall revokes before enrolling.
    await revokeInstancesOf();
    const afterReinstall = await enrollInstance();

    console.log("=== Step 3: the new key authenticates, the old one no longer does ===");
    const withNew = await requestTokenWith(afterReinstall);
    const withOld = await requestTokenWith(beforeReinstall);

    expect(withNew.status).toBe(200);
    expect(withOld.status).toBe(401);
    expect(withOld.data).toHaveProperty("error", "invalid_client_attestation");

    await revokeInstancesOf();
  });

  it("lost device: revoking the instance stops the app from authenticating", async () => {
    const instance = await enrollInstance();
    expect((await requestTokenWith(instance)).status).toBe(200);

    console.log("\n=== the operator revokes the instance of the lost device ===");
    await revokeInstancesOf();
    expect(await activeInstancesOf(deviceId)).toHaveLength(0);

    const response = await requestTokenWith(instance);
    expect(response.status).toBe(401);
    expect(response.data).toHaveProperty("error", "invalid_client_attestation");
  });

  it("the tenant enforces the Challenge: a PoP without one is rejected and a Challenge is handed back", async () => {
    const instance = await enrollInstance();

    const withoutChallenge = await requestTokenWith(instance, null);
    expect(withoutChallenge.status).toBe(401);
    expect(withoutChallenge.data).toHaveProperty("error", "use_attestation_challenge");

    const handedBack = withoutChallenge.headers["oauth-client-attestation-challenge"];
    expect(handedBack).toBeDefined();

    const retried = await requestTokenWith(instance, handedBack);
    expect(retried.status).toBe(200);

    await revokeInstancesOf();
  });

  it("uses the same credentials at the Pushed Authorization Request endpoint", async () => {
    const instance = await enrollInstance();

    const params = new URLSearchParams();
    params.append("response_type", "code");
    params.append("client_id", clientId);
    params.append("redirect_uri", "http://localhost:3000/callback");
    params.append("scope", "account");
    params.append("state", "abca-instance-usecase-par");

    const response = await post({
      url: pushedAuthorizationEndpoint,
      body: params,
      headers: {
        [ATTESTATION_HEADER]: selfSignedAttestationJwt(instance),
        [POP_HEADER]: popJwt(instance.jwk, await fetchChallenge()),
      },
    });
    expect(response.status).toBe(201);
    expect(response.data).toHaveProperty("request_uri");

    await revokeInstancesOf();
  });
});
