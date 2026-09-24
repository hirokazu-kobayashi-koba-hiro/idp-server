/**
 * ABCA Use Case: an app that registers its own Client Instance Key
 *
 * There is no backend Client Attester here. The app generates a key on the device, registers it
 * with the Authorization Server, and from then on signs its own Client Attestation JWT with it
 * (client_attestation_trust_source = registered_instance_key). Section 10.8 leaves trust management
 * out of scope and Section 1.1 explicitly allows a client to act as its own attester.
 *
 * The instance belongs to a user. At first launch the user logs in in the hybrid flow
 * (response_type=code id_token) with nonce = request_hash, the app registers its key with the ID
 * token of that login, and exchanges the code of the same response with the key it just
 * registered: one login covers registration and the first token. The client cannot authenticate
 * before that, so the ID token is obtained without client authentication.
 *
 * These tests run against the development verifier, which checks the challenge-to-key binding of
 * the evidence but performs no real device attestation
 * (IDP_SERVER_CLIENT_INSTANCE_DEVELOPMENT_VERIFIER).
 *
 * What this covers beyond the spec-level tests, which check one request at a time:
 * 1. First launch: log in, register the key, and exchange the code of the same login with it
 * 2. Steady state: reuse one self-signed attestation, with a server-provided Challenge
 * 3. Reinstall: the user logs in again and the newly registered key takes over; the old instance
 *    is revoked as superseded, and stops at its next client authentication
 * 4. Lost device: revoking the instance stops the app from authenticating
 * 5. The same credentials working at the Pushed Authorization Request endpoint
 * 6. Lifecycle: revocation is final and keeps the key taken; deletion forgets the instance
 * 7. Tokens follow the instance: revoking or deleting it deletes its tokens, and a new registration
 *    of the same key does not inherit them
 * 8. An instance receives its own user's tokens only
 * 9. The operator finds instances across the clients of the tenant, by what is at hand
 */
import { beforeAll, describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import * as jose from "jose";
import { deletion, get, post, postWithJson } from "../../../lib/http";
import { onboarding } from "../../../api/managementClient";
import { inspectToken, requestToken } from "../../../api/oauthClient";
import { adminServerConfig, backendUrl } from "../../testConfig";
import crypto from "crypto";
import {
  canonicalJwk,
  deriveRequestHash,
  enablePasswordLogin,
  loginForRegistration,
} from "../../../lib/clientInstance";
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
let managementClientId;
let managementClientSecret;
let introspectionEndpoint;
let userSub;
let username;
let password;

const REDIRECT_URI = "http://localhost:3000/callback";

const generateInstanceJwk = async () => {
  const { privateKey } = await jose.generateKeyPair("ES256", { extractable: true });
  return await jose.exportJWK(privateKey);
};

const publicJwkOf = (jwk) => ({ kty: jwk.kty, crv: jwk.crv, x: jwk.x, y: jwk.y });

const clientsUrl = () => `${backendUrl}/v1/management/tenants/${tenantId}/clients`;
const instancesUrl = () => `${backendUrl}/v1/management/tenants/${tenantId}/client-instances`;

/** Operator view: the instances the Authorization Server currently trusts for this user. */
const activeInstancesOf = async (user = userSub) => {
  const response = await get({
    url: `${instancesUrl()}?client_id=${clientId}&user_id=${user}&status=active`,
    headers: managementHeaders,
  });
  expect(response.status).toBe(200);
  return response.data.list;
};

const revokeInstance = async (instanceId) =>
  await post({ url: `${instancesUrl()}/${instanceId}/revoke`, headers: managementHeaders });

const deleteInstance = async (instanceId) =>
  await deletion({ url: `${instancesUrl()}/${instanceId}`, headers: managementHeaders });

const getInstance = async (instanceId) =>
  await get({ url: `${instancesUrl()}/${instanceId}`, headers: managementHeaders });

/** Operator action on a lost device, and what the reinstall does to the instance left behind. */
const revokeInstancesOf = async (user = userSub) => {
  for (const instance of await activeInstancesOf(user)) {
    expect((await revokeInstance(instance.id)).status).toBe(200);
  }
};

/** Clean up between tests, so that each one starts without an instance of the user. */
const deleteInstancesOf = async (user = userSub) => {
  const response = await get({ url: `${instancesUrl()}?client_id=${clientId}&user_id=${user}`, headers: managementHeaders });
  for (const instance of response.data.list.filter((i) => i.user_id === user)) {
    expect((await deleteInstance(instance.id)).status).toBe(204);
  }
};

/**
 * App side, at first launch: generate a key on the device, log the user in with nonce =
 * request_hash, and register the key with the ID token of that login. Returns the code of the same
 * authorization response, for the app to exchange with the key it just registered.
 */
const enrollInstance = async ({
  jwk = undefined,
  expectedStatus = 201,
  as = { username: undefined, password: undefined },
} = {}) => {
  jwk = jwk ?? (await generateInstanceJwk());

  const challengeResponse = await postWithJson({
    url: `${issuer}/v1/client-instances/challenges`,
    body: { client_id: clientId },
  });
  expect(challengeResponse.status).toBe(200);
  const { challenge, instance_id: instanceId } = challengeResponse.data;

  const requestHash = deriveRequestHash(challenge, jwk);
  const { code, idToken } = await loginForRegistration({
    tenantId,
    clientId,
    redirectUri: REDIRECT_URI,
    nonce: requestHash,
    username: as.username ?? username,
    password: as.password ?? password,
    scope: "openid account",
  });

  const registerResponse = await postWithJson({
    url: `${issuer}/v1/client-instances`,
    body: {
      challenge,
      id_token: idToken,
      client_instance_public_key: publicJwkOf(jwk),
      platform_evidence: {
        platform: DEV_PLATFORM,
        request_hash: requestHash,
      },
    },
  });
  expect(registerResponse.status).toBe(expectedStatus);

  return { jwk, instanceId, code, registerResponse };
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

/** The app exchanges the code of the login it registered with, using the registered key. */
const exchangeCodeWith = async (instance) =>
  await requestToken({
    endpoint: tokenEndpoint,
    grantType: "authorization_code",
    code: instance.code,
    redirectUri: REDIRECT_URI,
    clientId,
    additionalHeaders: {
      [ATTESTATION_HEADER]: selfSignedAttestationJwt(instance),
      [POP_HEADER]: popJwt(instance.jwk, await fetchChallenge()),
    },
  });

/** The app refreshes with the key of the instance it runs as. */
const refreshWith = async (instance, refreshToken) =>
  await requestToken({
    endpoint: tokenEndpoint,
    grantType: "refresh_token",
    refreshToken,
    clientId,
    additionalHeaders: {
      [ATTESTATION_HEADER]: selfSignedAttestationJwt(instance),
      [POP_HEADER]: popJwt(instance.jwk, await fetchChallenge()),
    },
  });

/** Resource Server view: is the access token still active? */
const isActive = async (accessToken) => {
  const response = await inspectToken({
    endpoint: introspectionEndpoint,
    token: accessToken,
    clientId: managementClientId,
    clientSecret: managementClientSecret,
  });
  expect(response.status).toBe(200);
  return response.data.active;
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
  userSub = uuidv4();
  username = `admin-${timestamp}@abca-instance.example.com`;
  password = `AbcaPass_${timestamp}!`;
  issuer = `${backendUrl}/${tenantId}`;
  tokenEndpoint = `${issuer}/v1/tokens`;
  pushedAuthorizationEndpoint = `${issuer}/v1/authorizations/push`;
  challengeEndpoint = `${issuer}/v1/client-attestation/challenges`;
  introspectionEndpoint = `${issuer}/v1/tokens/introspection`;
  managementClientId = uuidv4();
  managementClientSecret = `cs-${timestamp}`;

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
        token_introspection_endpoint: introspectionEndpoint,
        token_endpoint_auth_methods_supported: ["client_secret_post", "attest_jwt_client_auth"],
        client_attestation_signing_alg_values_supported: ["ES256"],
        client_attestation_pop_signing_alg_values_supported: ["ES256"],
        challenge_endpoint: challengeEndpoint,
        grant_types_supported: ["authorization_code", "password", "client_credentials", "refresh_token"],
        scopes_supported: ["openid", "profile", "email", "account", "management"],
        response_types_supported: ["code", "code id_token"],
        response_modes_supported: ["query", "fragment"],
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
        sub: userSub,
        provider_id: "idp-server",
        email: username,
        email_verified: true,
        raw_password: password,
      },
      client: {
        client_id: managementClientId,
        client_secret: managementClientSecret,
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
        client_instance_registration_policy: "user_bound",
      },
      grant_types: ["authorization_code", "client_credentials", "refresh_token"],
      redirect_uris: [REDIRECT_URI],
      response_types: ["code", "code id_token"],
      scope: "openid account management",
      enabled: true,
    },
  });
  expect(registrationResponse.status).toBe(201);

  await enablePasswordLogin({ tenantId, headers: managementHeaders });
});

describe("ABCA Use Case: an app that registers its own Client Instance Key", () => {

  it("first launch: one login registers the key and yields the first token", async () => {
    console.log("\n=== Step 1: the user logs in and the app registers the key with that login ===");
    const instance = await enrollInstance();

    const instances = await activeInstancesOf();
    expect(instances).toHaveLength(1);
    expect(instances[0].id).toBe(instance.instanceId);

    console.log("=== Step 2: the code of the same login is exchanged with the registered key ===");
    const exchanged = await exchangeCodeWith(instance);
    expect(exchanged.status).toBe(200);
    expect(exchanged.data).toHaveProperty("access_token");
    expect(exchanged.data).toHaveProperty("id_token");

    console.log("=== Step 3: later requests authenticate with a self-signed Client Attestation JWT ===");
    const response = await requestTokenWith(instance);
    expect(response.status).toBe(200);
    expect(response.data).toHaveProperty("access_token");

    await deleteInstancesOf();
  });

  it("steady state: reuses one self-signed attestation across requests, with a server-provided Challenge", async () => {
    const instance = await enrollInstance();
    const challenge = await fetchChallenge();

    const first = await requestTokenWith(instance, challenge);
    const second = await requestTokenWith(instance, challenge);

    expect(first.status).toBe(200);
    expect(second.status).toBe(200);

    await deleteInstancesOf();
  });

  it("reinstall: the user logs in again, and the newly registered key takes over from the old one", async () => {
    console.log("\n=== Step 1: the app is registered and working ===");
    const beforeReinstall = await enrollInstance();
    const beforeTokens = await exchangeCodeWith(beforeReinstall);
    expect(beforeTokens.status).toBe(200);
    expect(await isActive(beforeTokens.data.access_token)).toBe(true);

    console.log("=== Step 2: the reinstalled app registers a new key with a fresh login ===");
    // Nobody revokes the instance the uninstalled app left behind: registering the new one does.
    const afterReinstall = await enrollInstance();

    console.log("=== Step 3: the new key authenticates, the old one no longer does ===");
    const withNew = await requestTokenWith(afterReinstall);
    const withOld = await requestTokenWith(beforeReinstall);

    expect(withNew.status).toBe(200);
    expect(withOld.status).toBe(401);
    expect(withOld.data).toHaveProperty("error", "invalid_client_attestation");

    console.log("=== Step 4: the old instance is kept, revoked as superseded; one stays active ===");
    const old = await getInstance(beforeReinstall.instanceId);
    expect(old.data).toHaveProperty("status", "revoked");
    expect(old.data).toHaveProperty("revocation_reason", "superseded");
    expect(old.data).toHaveProperty("revoked_at");
    const actives = await activeInstancesOf();
    expect(actives.map((instance) => instance.id)).toEqual([afterReinstall.instanceId]);

    console.log("=== Step 5: the old device stops at its next client authentication ===");
    // Registering another device replaces the old instance; it does not sign the old device out.
    // Its refresh fails with the instance, and its access token runs until it expires.
    const oldRefresh = await refreshWith(beforeReinstall, beforeTokens.data.refresh_token);
    expect(oldRefresh.status).toBe(401);
    expect(oldRefresh.data).toHaveProperty("error", "invalid_client_attestation");
    expect(await isActive(beforeTokens.data.access_token)).toBe(true);

    await deleteInstancesOf();
  });

  it("lost device: revoking the instance stops the app from authenticating", async () => {
    const instance = await enrollInstance();
    expect((await requestTokenWith(instance)).status).toBe(200);

    console.log("\n=== the operator revokes the instance of the lost device ===");
    await revokeInstancesOf();
    expect(await activeInstancesOf()).toHaveLength(0);

    const response = await requestTokenWith(instance);
    expect(response.status).toBe(401);
    expect(response.data).toHaveProperty("error", "invalid_client_attestation");

    await deleteInstancesOf();
  });

  it("the tenant enforces the Challenge: a PoP without one is rejected and a Challenge is handed back", async () => {
    const instance = await enrollInstance();

    const withoutChallenge = await requestTokenWith(instance, null);
    expect(withoutChallenge.status).toBe(400);
    expect(withoutChallenge.data).toHaveProperty("error", "use_attestation_challenge");

    const handedBack = withoutChallenge.headers["oauth-client-attestation-challenge"];
    expect(handedBack).toBeDefined();

    const retried = await requestTokenWith(instance, handedBack);
    expect(retried.status).toBe(200);

    await deleteInstancesOf();
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

    await deleteInstancesOf();
  });

  it("lifecycle: revocation is final and keeps the key taken; deletion forgets the instance", async () => {
    const instance = await enrollInstance();
    expect((await requestTokenWith(instance)).status).toBe(200);

    console.log("\n=== revoke: the instance stays, marked revoked, and stops authenticating ===");
    const dryRun = await post({
      url: `${instancesUrl()}/${instance.instanceId}/revoke?dry_run=true`,
      headers: managementHeaders,
    });
    expect(dryRun.status).toBe(200);
    expect(dryRun.data).toHaveProperty("dry_run", true);
    expect((await getInstance(instance.instanceId)).data.status).toBe("active");

    const revoked = await revokeInstance(instance.instanceId);
    expect(revoked.status).toBe(200);
    expect(revoked.data).toHaveProperty("status", "revoked");
    expect(revoked.data).toHaveProperty("revoked_at");
    expect(revoked.data).toHaveProperty("revocation_reason", "operator");

    const afterRevocation = await getInstance(instance.instanceId);
    expect(afterRevocation.status).toBe(200);
    expect(afterRevocation.data).toHaveProperty("status", "revoked");
    expect(afterRevocation.data.revoked_at).toBe(revoked.data.revoked_at);
    expect(afterRevocation.data).toHaveProperty("user_id", userSub);
    expect(afterRevocation.data).toHaveProperty("attestation_evidence");

    const withRevoked = await requestTokenWith(instance);
    expect(withRevoked.status).toBe(401);
    expect(withRevoked.data).toHaveProperty("error", "invalid_client_attestation");

    console.log("=== revocation is final: revoking again is refused, and there is no way back ===");
    const again = await revokeInstance(instance.instanceId);
    expect(again.status).toBe(400);
    expect((await getInstance(instance.instanceId)).data.revoked_at).toBe(revoked.data.revoked_at);

    console.log("=== the revoked key stays taken: registering it again is refused ===");
    await enrollInstance({ jwk: instance.jwk, expectedStatus: 400 });

    console.log("=== a new key is how the device is trusted again ===");
    const renewed = await enrollInstance();
    expect((await requestTokenWith(renewed)).status).toBe(200);

    console.log("=== delete: the instance is forgotten, and its key with it ===");
    expect((await deleteInstance(instance.instanceId)).status).toBe(204);
    expect((await getInstance(instance.instanceId)).status).toBe(404);
    expect((await deleteInstance(instance.instanceId)).status).toBe(404);
    expect((await revokeInstance(instance.instanceId)).status).toBe(404);

    const reRegistered = await enrollInstance({ jwk: instance.jwk });
    expect(reRegistered.instanceId).not.toBe(instance.instanceId);
    expect((await requestTokenWith(reRegistered)).status).toBe(200);

    await deleteInstancesOf();
  });

  it("tokens follow the instance: revoking it stops its access and refresh tokens at once", async () => {
    const instance = await enrollInstance();
    const issued = await exchangeCodeWith(instance);
    expect(issued.status).toBe(200);
    expect(issued.data).toHaveProperty("refresh_token");

    console.log("\n=== while the instance is active, it refreshes its own tokens ===");
    const refreshed = await refreshWith(instance, issued.data.refresh_token);
    expect(refreshed.status).toBe(200);
    expect(await isActive(refreshed.data.access_token)).toBe(true);

    console.log("=== revoke: its tokens are deleted with it ===");
    expect((await revokeInstance(instance.instanceId)).status).toBe(200);
    expect(await isActive(refreshed.data.access_token)).toBe(false);

    const afterRevocation = await refreshWith(instance, refreshed.data.refresh_token);
    expect(afterRevocation.status).toBe(401);
    expect(afterRevocation.data).toHaveProperty("error", "invalid_client_attestation");

    await deleteInstancesOf();
  });

  it("tokens follow the instance: deleting it and registering the same key again does not bring them back", async () => {
    const instance = await enrollInstance();
    const issued = await exchangeCodeWith(instance);
    expect(issued.status).toBe(200);
    expect(await isActive(issued.data.access_token)).toBe(true);

    console.log("\n=== delete: the instance and its tokens are forgotten ===");
    expect((await deleteInstance(instance.instanceId)).status).toBe(204);
    expect(await isActive(issued.data.access_token)).toBe(false);

    console.log("=== the same key registered again is a new instance, without the old refresh token ===");
    const reRegistered = await enrollInstance({ jwk: instance.jwk });
    expect(reRegistered.instanceId).not.toBe(instance.instanceId);

    const withOldRefreshToken = await refreshWith(reRegistered, issued.data.refresh_token);
    expect(withOldRefreshToken.status).toBe(400);
    expect(withOldRefreshToken.data).toHaveProperty("error", "invalid_grant");

    await deleteInstancesOf();
  });

  it("an instance receives its own user's tokens only: the code of another user is refused", async () => {
    const otherSub = uuidv4();
    const otherUsername = `other-${Date.now()}@abca-instance.example.com`;
    const otherPassword = `AbcaOther_${Date.now()}!`;
    const created = await postWithJson({
      url: `${backendUrl}/v1/management/tenants/${tenantId}/users`,
      headers: managementHeaders,
      body: {
        sub: otherSub,
        provider_id: "idp-server",
        name: otherUsername,
        email: otherUsername,
        email_verified: true,
        raw_password: otherPassword,
      },
    });
    expect(created.status).toBe(201);

    console.log("\n=== the other user's app registers its own instance ===");
    const othersInstance = await enrollInstance({
      as: { username: otherUsername, password: otherPassword },
    });

    console.log("=== a code issued to this user, redeemed by the other user's instance ===");
    const { code } = await loginForRegistration({
      tenantId,
      clientId,
      redirectUri: REDIRECT_URI,
      nonce: uuidv4(),
      username,
      password,
      scope: "openid account",
    });
    const crossUser = await exchangeCodeWith({ ...othersInstance, code });
    expect(crossUser.status).toBe(400);
    expect(crossUser.data).toHaveProperty("error", "invalid_grant");

    console.log("=== the other user's own code is redeemed by its instance ===");
    expect((await exchangeCodeWith(othersInstance)).status).toBe(200);

    await deleteInstancesOf(otherSub);
  });

  it("the operator finds instances across the clients of the tenant, by what is at hand", async () => {
    const first = await enrollInstance();
    const second = await enrollInstance(); // supersedes the first
    const search = async (query) =>
      await get({ url: `${instancesUrl()}?${query}`, headers: managementHeaders });

    console.log("\n=== a user asking about their devices: by user, no client needed ===");
    const byUser = await search(`user_id=${userSub}`);
    expect(byUser.status).toBe(200);
    expect(byUser.data.total_count).toBe(2);
    expect(byUser.data.list.map((instance) => instance.id)).toEqual([
      second.instanceId,
      first.instanceId,
    ]);

    console.log("=== what is trusted now, and what was replaced ===");
    const active = await search(`user_id=${userSub}&status=active`);
    expect(active.data.list.map((instance) => instance.id)).toEqual([second.instanceId]);
    const superseded = await search(`user_id=${userSub}&revocation_reason=superseded`);
    expect(superseded.data.list.map((instance) => instance.id)).toEqual([first.instanceId]);

    console.log("=== a key: which instance holds it (RFC 7638 thumbprint) ===");
    const thumbprint = crypto
      .createHash("sha256")
      .update(canonicalJwk(publicJwkOf(second.jwk)))
      .digest("base64url");
    const byKey = await search(`instance_key_thumbprint=${thumbprint}`);
    expect(byKey.data.list.map((instance) => instance.id)).toEqual([second.instanceId]);

    console.log("=== paging carries the total ===");
    const firstPage = await search(`user_id=${userSub}&limit=1`);
    expect(firstPage.data.total_count).toBe(2);
    expect(firstPage.data.list).toHaveLength(1);

    console.log("=== a condition no value can meet is refused, not answered with nothing ===");
    expect((await search("status=deleted")).status).toBe(400);
    expect((await search("user_id=not-a-uuid")).status).toBe(400);

    console.log("=== another tenant sees none of it: not by id, not by key ===");
    const otherTenantInstances = `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/client-instances`;
    const fromOtherTenant = await get({
      url: `${otherTenantInstances}/${second.instanceId}`,
      headers: managementHeaders,
    });
    expect(fromOtherTenant.status).toBe(404);
    const byKeyFromOtherTenant = await get({
      url: `${otherTenantInstances}?instance_key_thumbprint=${thumbprint}`,
      headers: managementHeaders,
    });
    expect(byKeyFromOtherTenant.status).toBe(200);
    expect(byKeyFromOtherTenant.data.total_count).toBe(0);
    const revokeFromOtherTenant = await post({
      url: `${otherTenantInstances}/${second.instanceId}/revoke`,
      headers: managementHeaders,
    });
    expect(revokeFromOtherTenant.status).toBe(404);
    expect((await getInstance(second.instanceId)).data.status).toBe("active");

    console.log("=== an instance is addressed by id alone; an unknown or malformed id is not found ===");
    expect((await getInstance(uuidv4())).status).toBe(404);
    expect((await getInstance("not-a-uuid")).status).toBe(404);

    await deleteInstancesOf();
  });

  it("the operator registers an instance for a client named in the body, with a UUID of its own", async () => {
    const register = async (body) =>
      await postWithJson({ url: instancesUrl(), headers: managementHeaders, body });
    const key = publicJwkOf(await generateInstanceJwk());

    expect((await register({ instance_key: key })).status).toBe(400);
    expect((await register({ client_id: uuidv4(), instance_key: key })).status).toBe(400);
    expect((await register({ id: "instance-1", client_id: clientId, instance_key: key })).status).toBe(400);

    const id = uuidv4();
    const created = await register({ id, client_id: clientId, instance_key: key });
    expect(created.status).toBe(201);
    expect(created.data.result).toHaveProperty("client_id", clientId);

    const otherKey = publicJwkOf(await generateInstanceJwk());
    const sameId = await register({ id, client_id: clientId, instance_key: otherKey });
    expect(sameId.status).toBe(400);

    expect((await deleteInstance(id)).status).toBe(204);
  });
});
