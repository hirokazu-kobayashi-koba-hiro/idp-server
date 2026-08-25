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
import { requestToken } from "../../../api/oauthClient";
import { adminServerConfig, backendUrl, serverConfig } from "../../testConfig";
import { createJwtWithPrivateKey, generateJti } from "../../../lib/jose";
import { toEpocTime } from "../../../lib/util";

const DEV_PLATFORM = "request-hash-binding-development-only";
const ATTESTATION_TYP = "oauth-client-attestation+jwt";
const POP_TYP = "oauth-client-attestation-pop+jwt";
const ATTESTATION_HEADER = "OAuth-Client-Attestation";
const POP_HEADER = "OAuth-Client-Attestation-PoP";

// Authentication device of the seeded test user (config/examples/e2e/test-tenant/initial.json).
const REGISTERED_DEVICE_ID = "7736a252-60b4-45f5-b817-65ea9a540860";

let managementHeaders;
let clientId;

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

const instancesUrl = () =>
  `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients/${clientId}/instances`;

/** Operator view: what the Authorization Server currently trusts for this device. */
const activeInstancesOf = async (deviceId) => {
  const response = await get({ url: instancesUrl(), headers: managementHeaders });
  expect(response.status).toBe(200);
  return response.data.list.filter((instance) => instance.device_id === deviceId);
};

const revokeInstancesOf = async (deviceId) => {
  for (const instance of await activeInstancesOf(deviceId)) {
    const response = await deletion({
      url: `${instancesUrl()}/${instance.id}`,
      headers: managementHeaders,
    });
    expect(response.status).toBe(204);
  }
};

/** App side: generate a key on the device and enroll it against a server-issued challenge. */
const enrollInstance = async (deviceId = REGISTERED_DEVICE_ID) => {
  const jwk = await generateInstanceJwk();

  const challengeResponse = await postWithJson({
    url: `${backendUrl}/${serverConfig.tenantId}/v1/client-instances/challenges`,
    body: { client_id: clientId, device_id: deviceId },
  });
  expect(challengeResponse.status).toBe(200);
  const { challenge, instance_id: instanceId } = challengeResponse.data;

  const registerResponse = await postWithJson({
    url: `${backendUrl}/${serverConfig.tenantId}/v1/client-instances`,
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
    aud: serverConfig.issuer,
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
    url: `${backendUrl}/${serverConfig.tenantId}/v1/client-attestation/challenges`,
    body: {},
  });
  expect(response.status).toBe(200);
  return response.data.attestation_challenge;
};

const requestTokenWith = async (instance, challenge) =>
  await requestToken({
    endpoint: serverConfig.tokenEndpoint,
    grantType: "client_credentials",
    scope: "account",
    clientId,
    additionalHeaders: {
      [ATTESTATION_HEADER]: selfSignedAttestationJwt(instance),
      [POP_HEADER]: popJwt(instance.jwk, challenge),
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

  clientId = uuidv4();
  const registrationResponse = await postWithJson({
    url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients`,
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

  await revokeInstancesOf(REGISTERED_DEVICE_ID);
});

describe("ABCA Use Case: an app that registers its own Client Instance Key", () => {

  it("first launch: registers the instance key on the device and then authenticates with it", async () => {
    console.log("\n=== Step 1: the app enrolls the key it generated on the device ===");
    const instance = await enrollInstance();
    expect(await activeInstancesOf(REGISTERED_DEVICE_ID)).toHaveLength(1);

    console.log("=== Step 2: the app authenticates with a self-signed Client Attestation JWT ===");
    const response = await requestTokenWith(instance);
    expect(response.status).toBe(200);
    expect(response.data).toHaveProperty("access_token");

    await revokeInstancesOf(REGISTERED_DEVICE_ID);
  });

  it("steady state: reuses one self-signed attestation across requests, with a server-provided Challenge", async () => {
    const instance = await enrollInstance();
    const challenge = await fetchChallenge();

    const first = await requestTokenWith(instance, challenge);
    const second = await requestTokenWith(instance, challenge);

    expect(first.status).toBe(200);
    expect(second.status).toBe(200);

    await revokeInstancesOf(REGISTERED_DEVICE_ID);
  });

  it("reinstall on the same device: the old key stops working and the newly enrolled one takes over", async () => {
    console.log("\n=== Step 1: the app is enrolled and working ===");
    const beforeReinstall = await enrollInstance();
    expect((await requestTokenWith(beforeReinstall)).status).toBe(200);

    console.log("=== Step 2: the previous instance is revoked so the device can enroll again ===");
    // Only one active instance per device is allowed, so a reinstall revokes before enrolling.
    await revokeInstancesOf(REGISTERED_DEVICE_ID);
    const afterReinstall = await enrollInstance();

    console.log("=== Step 3: the new key authenticates, the old one no longer does ===");
    const withNew = await requestTokenWith(afterReinstall);
    const withOld = await requestTokenWith(beforeReinstall);

    expect(withNew.status).toBe(200);
    expect(withOld.status).toBe(401);
    expect(withOld.data).toHaveProperty("error", "invalid_client_attestation");

    await revokeInstancesOf(REGISTERED_DEVICE_ID);
  });

  it("lost device: revoking the instance stops the app from authenticating", async () => {
    const instance = await enrollInstance();
    expect((await requestTokenWith(instance)).status).toBe(200);

    console.log("\n=== the operator revokes the instance of the lost device ===");
    await revokeInstancesOf(REGISTERED_DEVICE_ID);
    expect(await activeInstancesOf(REGISTERED_DEVICE_ID)).toHaveLength(0);

    const response = await requestTokenWith(instance);
    expect(response.status).toBe(401);
    expect(response.data).toHaveProperty("error", "invalid_client_attestation");
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
      url: serverConfig.pushedAuthorizationEndpoint,
      body: params,
      headers: {
        [ATTESTATION_HEADER]: selfSignedAttestationJwt(instance),
        [POP_HEADER]: popJwt(instance.jwk),
      },
    });
    expect(response.status).toBe(201);
    expect(response.data).toHaveProperty("request_uri");

    await revokeInstancesOf(REGISTERED_DEVICE_ID);
  });
});
