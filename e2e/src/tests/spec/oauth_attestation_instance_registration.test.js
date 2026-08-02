/**
 * Client Instance registration flow for end-user applications.
 *
 * Registration must not depend on user authentication: the token endpoint is where login itself
 * happens, so requiring a token to register would be circular. The endpoints are therefore
 * unauthenticated, and what authorizes a registration is
 *
 *   1. a server issued challenge that carries the authorization decision (client_id, device_id and
 *      the instance identifier to assign), and
 *   2. platform attestation bound to that challenge and to the key being registered.
 *
 * The binding is expressed as
 *
 *   request_hash = base64url_nopad( SHA-256( challenge_bytes || canonical_jwk_utf8 ) )
 *   canonical_jwk = {"crv":"P-256","kty":"EC","x":"...","y":"..."}   (RFC 7638 required members)
 *
 * These tests run against the development verifier, which checks that binding but performs no
 * application or device attestation (IDP_SERVER_CLIENT_INSTANCE_DEVELOPMENT_VERIFIER).
 */
import { beforeAll, describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import * as jose from "jose";
import crypto from "crypto";
import { get, postWithJson } from "../../lib/http";
import { requestToken } from "../../api/oauthClient";
import { adminServerConfig, backendUrl, serverConfig } from "../testConfig";
import { createJwtWithPrivateKey, generateJti } from "../../lib/jose";
import { toEpocTime } from "../../lib/util";

const DEV_PLATFORM = "request-hash-binding-development-only";
const ATTESTATION_TYP = "oauth-client-attestation+jwt";
const POP_TYP = "oauth-client-attestation-pop+jwt";

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
const canonicalJwk = (jwk) =>
  JSON.stringify({ crv: jwk.crv, kty: jwk.kty, x: jwk.x, y: jwk.y });

const publicJwkOf = (jwk) => ({ kty: jwk.kty, crv: jwk.crv, x: jwk.x, y: jwk.y });

const deriveRequestHash = (challenge, jwk) => {
  const digest = crypto.createHash("sha256");
  digest.update(base64urlDecode(challenge));
  digest.update(Buffer.from(canonicalJwk(jwk), "utf8"));
  return base64url(digest.digest());
};

const requestChallenge = async ({ client = () => clientId, deviceId = uuidv4() } = {}) =>
  await postWithJson({
    url: `${backendUrl}/${serverConfig.tenantId}/v1/client-instances/challenges`,
    body: { client_id: typeof client === "function" ? client() : client, device_id: deviceId },
  });

const registerInstance = async ({ challenge, jwk, requestHash }) =>
  await postWithJson({
    url: `${backendUrl}/${serverConfig.tenantId}/v1/client-instances`,
    body: {
      challenge,
      client_instance_public_key: publicJwkOf(jwk),
      platform_evidence: {
        platform: DEV_PLATFORM,
        request_hash: requestHash ?? deriveRequestHash(challenge, jwk),
      },
    },
  });

/** Full happy path: challenge -> register -> the instance can authenticate the client. */
const enrollInstance = async (deviceId = uuidv4()) => {
  const jwk = await generateInstanceJwk();
  const challengeResponse = await requestChallenge({ deviceId });
  expect(challengeResponse.status).toBe(200);

  const { challenge, instance_id: instanceId } = challengeResponse.data;
  const registerResponse = await registerInstance({ challenge, jwk });
  expect(registerResponse.status).toBe(201);

  return { jwk, instanceId, deviceId };
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

const popJwt = (jwk) =>
  createJwtWithPrivateKey({
    payload: {
      aud: serverConfig.issuer,
      jti: generateJti(),
      iat: toEpocTime({ adjusted: 0 }),
    },
    // the kid of the PoP is not used by the server; the attestation carries the lookup key
    privateKey: { ...jwk, kid: "client-instance-key", alg: "ES256" },
    algorithm: "ES256",
    additionalOptions: { header: { typ: POP_TYP } },
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
      client_name: "Client Instance Registration Test Client",
      token_endpoint_auth_method: "attest_jwt_client_auth",
      extension: { client_attestation_trust_source: "registered_instance_key" },
      grant_types: ["client_credentials"],
      redirect_uris: ["http://localhost:3000/callback"],
      response_types: ["code"],
      scope: "account management",
      enabled: true,
    },
  });
  expect(registrationResponse.status).toBe(201);
});

describe("Client Instance registration (application plane)", () => {

  describe("challenge endpoint", () => {

    it("issues a challenge together with the instance identifier to be assigned", async () => {
      const response = await requestChallenge();

      expect(response.status).toBe(200);
      expect(response.data.challenge).toBeDefined();
      expect(response.data.instance_id).toBeDefined();
      expect(response.data.expires_in).toBeGreaterThan(0);
      // the challenge is consumed as bytes by the client, so it must be base64url without padding
      expect(response.data.challenge).toMatch(/^[A-Za-z0-9_-]+$/);
      expect(response.headers["cache-control"]).toContain("no-store");
    });

    it("rejects a client that does not use attest_jwt_client_auth", async () => {
      const response = await requestChallenge({ client: "clientSecretPost" });
      expect(response.status).toBe(400);
      expect(response.data).toHaveProperty("error", "invalid_request");
    });

    it("rejects an unknown client", async () => {
      const response = await requestChallenge({ client: uuidv4() });
      expect(response.status).toBe(400);
    });
  });

  describe("registration endpoint", () => {

    it("registers the instance key and lets the client authenticate with a self-signed attestation", async () => {
      const { jwk, instanceId } = await enrollInstance();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "client_credentials",
        scope: "account",
        clientId,
        additionalHeaders: {
          "OAuth-Client-Attestation": selfSignedAttestationJwt({ jwk, instanceId }),
          "OAuth-Client-Attestation-PoP": popJwt(jwk),
        },
      });

      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
    });

    it("assigns the instance identifier from the challenge, not from the request", async () => {
      const { instanceId, deviceId } = await enrollInstance();

      const listResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients/${clientId}/instances`,
        headers: managementHeaders,
      });
      expect(listResponse.status).toBe(200);

      const registered = listResponse.data.list.find((instance) => instance.id === instanceId);
      expect(registered).toBeDefined();
      expect(registered.device_id).toBe(deviceId);
    });

    it("rejects a request whose request_hash does not bind the challenge to the key", async () => {
      const jwk = await generateInstanceJwk();
      const challengeResponse = await requestChallenge();
      const otherJwk = await generateInstanceJwk();

      const response = await registerInstance({
        challenge: challengeResponse.data.challenge,
        jwk,
        // hash computed over a different key: the evidence does not cover the key being registered
        requestHash: deriveRequestHash(challengeResponse.data.challenge, otherJwk),
      });

      expect(response.status).toBe(400);
    });

    it("rejects an unknown challenge", async () => {
      const jwk = await generateInstanceJwk();
      const response = await registerInstance({
        challenge: base64url(crypto.randomBytes(32)),
        jwk,
      });
      expect(response.status).toBe(400);
    });

    it("rejects a challenge that was already used", async () => {
      const jwk = await generateInstanceJwk();
      const challengeResponse = await requestChallenge();
      const challenge = challengeResponse.data.challenge;

      const first = await registerInstance({ challenge, jwk });
      expect(first.status).toBe(201);

      const replayed = await registerInstance({ challenge, jwk: await generateInstanceJwk() });
      expect(replayed.status).toBe(400);
    });

    it("rejects a second active instance for the same device", async () => {
      const deviceId = uuidv4();
      await enrollInstance(deviceId);

      const jwk = await generateInstanceJwk();
      const challengeResponse = await requestChallenge({ deviceId });
      const response = await registerInstance({
        challenge: challengeResponse.data.challenge,
        jwk,
      });

      expect(response.status).toBe(400);
    });

    it("rejects an instance key that carries private key material", async () => {
      const jwk = await generateInstanceJwk();
      const challengeResponse = await requestChallenge();
      const challenge = challengeResponse.data.challenge;

      const response = await postWithJson({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/client-instances`,
        body: {
          challenge,
          // the full JWK still holds the private component d
          client_instance_public_key: jwk,
          platform_evidence: {
            platform: DEV_PLATFORM,
            request_hash: deriveRequestHash(challenge, jwk),
          },
        },
      });

      expect(response.status).toBe(400);
    });

    it("rejects an unknown platform rather than skipping verification", async () => {
      const jwk = await generateInstanceJwk();
      const challengeResponse = await requestChallenge();
      const challenge = challengeResponse.data.challenge;

      const response = await postWithJson({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/client-instances`,
        body: {
          challenge,
          client_instance_public_key: publicJwkOf(jwk),
          platform_evidence: { platform: "no-such-platform" },
        },
      });

      expect(response.status).toBe(400);
    });
  });
});
