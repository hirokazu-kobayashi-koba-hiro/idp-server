/**
 * Client Instance registration flow for end-user applications (user bound).
 *
 * A registration is authenticated by two things, bound to one another:
 *
 *   1. an ID token this server issued: who the instance belongs to, and
 *   2. platform attestation bound to a server issued challenge: which device and key.
 *
 * The binding is the ID token nonce, which the client sets to
 *
 *   request_hash = base64url_nopad( SHA-256( challenge_bytes || canonical_jwk_utf8 ) )
 *   canonical_jwk = {"crv":"P-256","kty":"EC","x":"...","y":"..."}   (RFC 7638 required members)
 *
 * so an ID token only authenticates the registration of the key it was obtained for.
 *
 * The ID token is obtained without client authentication, which the client cannot perform before
 * it has an instance:
 *
 *   - N1: the client itself, in the hybrid flow (response_type=code id_token). The code of the same
 *     response is then exchanged with the key that was just registered.
 *   - N2: a public client the registering client lists in client_instance_registration_clients,
 *     for tenants that require PAR.
 *
 * These tests run against the development verifier, which checks the request_hash binding of the
 * evidence but performs no application or device attestation
 * (IDP_SERVER_CLIENT_INSTANCE_DEVELOPMENT_VERIFIER).
 */
import { beforeAll, describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import * as jose from "jose";
import crypto from "crypto";
import { get, postWithJson } from "../../lib/http";
import { requestToken } from "../../api/oauthClient";
import { requestAuthorizations } from "../../oauth/request";
import { adminServerConfig, backendUrl, clientSecretPostClient, serverConfig } from "../testConfig";
import { createJwtWithPrivateKey, generateJti } from "../../lib/jose";
import { toEpocTime } from "../../lib/util";
import { deriveRequestHash } from "../../lib/clientInstance";

const DEV_PLATFORM = "request-hash-binding-development-only";
const ATTESTATION_TYP = "oauth-client-attestation+jwt";
const POP_TYP = "oauth-client-attestation-pop+jwt";

const REDIRECT_URI = "http://localhost:3000/callback";

let managementHeaders;
let clientId;
let registrationClientId;
let policylessClientId;

const base64url = (buffer) =>
  buffer.toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

const generateInstanceJwk = async () => {
  const { privateKey } = await jose.generateKeyPair("ES256", { extractable: true });
  return await jose.exportJWK(privateKey);
};

const publicJwkOf = (jwk) => ({ kty: jwk.kty, crv: jwk.crv, x: jwk.x, y: jwk.y });

const requestChallenge = async ({ client = () => clientId } = {}) =>
  await postWithJson({
    url: `${backendUrl}/${serverConfig.tenantId}/v1/client-instances/challenges`,
    body: { client_id: typeof client === "function" ? client() : client },
  });

/**
 * Logs in and returns the authorization response of the hybrid flow: the code and the ID token.
 * The nonce is what binds the ID token to the key; by default it is the request hash of the
 * challenge and the key about to be registered.
 */
const loginForRegistration = async ({
  client = () => clientId,
  redirectUri = REDIRECT_URI,
  challenge,
  jwk,
  nonce,
}) => {
  const { authorizationResponse } = await requestAuthorizations({
    endpoint: serverConfig.authorizationEndpoint,
    clientId: typeof client === "function" ? client() : client,
    responseType: "code id_token",
    state: uuidv4(),
    scope: "openid account",
    redirectUri,
    nonce: nonce ?? deriveRequestHash(challenge, jwk),
  });
  expect(authorizationResponse.idToken).toBeDefined();
  return authorizationResponse;
};

const registerInstance = async ({ challenge, jwk, idToken, requestHash }) =>
  await postWithJson({
    url: `${backendUrl}/${serverConfig.tenantId}/v1/client-instances`,
    body: {
      challenge,
      id_token: idToken,
      client_instance_public_key: publicJwkOf(jwk),
      platform_evidence: {
        platform: DEV_PLATFORM,
        request_hash: requestHash ?? deriveRequestHash(challenge, jwk),
      },
    },
  });

/** N1 happy path: challenge -> hybrid login -> register. Returns what the token request needs. */
const enrollInstance = async () => {
  const jwk = await generateInstanceJwk();
  const challengeResponse = await requestChallenge();
  expect(challengeResponse.status).toBe(200);

  const { challenge, instance_id: instanceId } = challengeResponse.data;
  const authorizationResponse = await loginForRegistration({ challenge, jwk });
  const registerResponse = await registerInstance({
    challenge,
    jwk,
    idToken: authorizationResponse.idToken,
  });
  expect(registerResponse.status).toBe(201);

  return { jwk, instanceId, code: authorizationResponse.code, idToken: authorizationResponse.idToken };
};

const selfSignedAttestationJwt = ({ jwk, instanceId, sub = () => clientId }) =>
  createJwtWithPrivateKey({
    payload: {
      sub: typeof sub === "function" ? sub() : sub,
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

const attestationHeaders = ({ jwk, instanceId }) => ({
  "OAuth-Client-Attestation": selfSignedAttestationJwt({ jwk, instanceId }),
  "OAuth-Client-Attestation-PoP": popJwt(jwk),
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

  const registerClient = async (body) =>
    await postWithJson({
      url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients`,
      headers: managementHeaders,
      body: {
        redirect_uris: [REDIRECT_URI],
        scope: "openid account management",
        enabled: true,
        ...body,
      },
    });

  // N2: a public client that only obtains the ID token for the registration.
  registrationClientId = uuidv4();
  const registrationClientResponse = await registerClient({
    client_id: registrationClientId,
    client_name: `Client Instance Registration Bootstrap ${registrationClientId}`,
    token_endpoint_auth_method: "none",
    grant_types: ["authorization_code"],
    response_types: ["code", "code id_token"],
    scope: "openid",
  });
  expect(registrationClientResponse.status).toBe(201);

  clientId = uuidv4();
  const clientResponse = await registerClient({
    client_id: clientId,
    client_name: `Client Instance Registration Test Client ${clientId}`,
    token_endpoint_auth_method: "attest_jwt_client_auth",
    grant_types: ["authorization_code", "client_credentials"],
    response_types: ["code", "code id_token"],
    extension: {
      client_attestation_trust_source: "registered_instance_key",
      client_instance_registration_policy: "user_bound",
      client_instance_registration_clients: [registrationClientId],
    },
  });
  expect(clientResponse.status).toBe(201);

  // A client whose registration policy was never configured.
  policylessClientId = uuidv4();
  const policylessResponse = await registerClient({
    client_id: policylessClientId,
    client_name: `Client Instance Registration Policyless ${policylessClientId}`,
    token_endpoint_auth_method: "attest_jwt_client_auth",
    grant_types: ["client_credentials"],
    response_types: ["code"],
    extension: {
      client_attestation_trust_source: "registered_instance_key",
    },
  });
  expect(policylessResponse.status).toBe(201);
});

describe("Client Instance registration (application plane, user bound)", () => {

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

    it("rejects a client whose client_instance_registration_policy is not configured", async () => {
      const response = await requestChallenge({ client: () => policylessClientId });
      expect(response.status).toBe(400);
      expect(response.data).toHaveProperty("error", "invalid_request");
    });
  });

  describe("registration endpoint", () => {

    it("N1: registers with the client's own ID token, and the code of the same response is exchanged with the new key", async () => {
      const { jwk, instanceId, code } = await enrollInstance();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "authorization_code",
        code,
        redirectUri: REDIRECT_URI,
        clientId,
        additionalHeaders: attestationHeaders({ jwk, instanceId }),
      });

      console.log(tokenResponse.status, tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
      expect(tokenResponse.data).toHaveProperty("id_token");
    });

    it("binds the instance to the user of the ID token, with the identifier from the challenge", async () => {
      const { instanceId, idToken } = await enrollInstance();
      const { sub } = jose.decodeJwt(idToken);

      const listResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/client-instances?client_id=${clientId}`,
        headers: managementHeaders,
      });
      expect(listResponse.status).toBe(200);

      const registered = listResponse.data.list.find((instance) => instance.id === instanceId);
      expect(registered).toBeDefined();
      expect(registered.user_id).toBe(sub);
      expect(registered).not.toHaveProperty("device_id");
      // The development verifier establishes the request hash binding and nothing about the app or
      // the device, and the instance records it as such.
      expect(registered.attestation_evidence).toMatchObject({
        platform: DEV_PLATFORM,
        binding_only: true,
      });
      expect(registered.attestation_evidence.verified_at).toBeDefined();
    });

    it("N2: registers with the ID token of a registration client the client lists", async () => {
      const jwk = await generateInstanceJwk();
      const challengeResponse = await requestChallenge();
      const { challenge, instance_id: instanceId } = challengeResponse.data;

      const { idToken } = await loginForRegistration({
        client: () => registrationClientId,
        challenge,
        jwk,
      });
      const registerResponse = await registerInstance({ challenge, jwk, idToken });
      expect(registerResponse.status).toBe(201);

      // The registration client only obtained the ID token. The app then logs the user in with its
      // own client and exchanges that code with the key it registered: a user bound instance
      // obtains tokens in its user's context, never through client_credentials.
      const { code } = await loginForRegistration({ challenge, jwk });
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "authorization_code",
        code,
        redirectUri: REDIRECT_URI,
        clientId,
        additionalHeaders: attestationHeaders({ jwk, instanceId }),
      });
      expect(tokenResponse.status).toBe(200);

      const withoutUser = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "client_credentials",
        scope: "account",
        clientId,
        additionalHeaders: attestationHeaders({ jwk, instanceId }),
      });
      expect(withoutUser.status).toBe(400);
      expect(withoutUser.data).toHaveProperty("error", "unauthorized_client");
    });

    it("rejects the ID token of a client that is not listed", async () => {
      const jwk = await generateInstanceJwk();
      const { challenge } = (await requestChallenge()).data;

      const { idToken } = await loginForRegistration({
        client: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        challenge,
        jwk,
      });

      const response = await registerInstance({ challenge, jwk, idToken });
      expect(response.status).toBe(400);
      expect(response.data).toHaveProperty("error", "invalid_request");
    });

    it("rejects a registration without an ID token", async () => {
      const jwk = await generateInstanceJwk();
      const { challenge } = (await requestChallenge()).data;

      const response = await registerInstance({ challenge, jwk });
      expect(response.status).toBe(400);
    });

    it("rejects an ID token obtained for another key, even with valid evidence for the presented key", async () => {
      // A leaked ID token next to the attacker's own key: the nonce names the victim's key.
      const victimJwk = await generateInstanceJwk();
      const attackerJwk = await generateInstanceJwk();
      const { challenge } = (await requestChallenge()).data;

      const { idToken } = await loginForRegistration({ challenge, jwk: victimJwk });

      const response = await registerInstance({ challenge, jwk: attackerJwk, idToken });
      expect(response.status).toBe(400);
    });

    it("rejects an ID token whose nonce covers the challenge only", async () => {
      const jwk = await generateInstanceJwk();
      const { challenge } = (await requestChallenge()).data;

      const { idToken } = await loginForRegistration({ challenge, jwk, nonce: challenge });

      const response = await registerInstance({ challenge, jwk, idToken });
      expect(response.status).toBe(400);
    });

    it("rejects evidence whose request_hash does not bind the challenge to the key", async () => {
      const jwk = await generateInstanceJwk();
      const otherJwk = await generateInstanceJwk();
      const { challenge } = (await requestChallenge()).data;
      const { idToken } = await loginForRegistration({ challenge, jwk });

      const response = await registerInstance({
        challenge,
        jwk,
        idToken,
        // hash computed over a different key: the evidence does not cover the key being registered
        requestHash: deriveRequestHash(challenge, otherJwk),
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
      const { challenge } = (await requestChallenge()).data;
      const { idToken } = await loginForRegistration({ challenge, jwk });

      const first = await registerInstance({ challenge, jwk, idToken });
      expect(first.status).toBe(201);

      const replayed = await registerInstance({ challenge, jwk, idToken });
      expect(replayed.status).toBe(400);
    });

    it("consumes a challenge once even when the registrations arrive at the same time", async () => {
      // The consumption is a single conditional update, not a read followed by a write: of requests
      // racing on one challenge, exactly one gets it.
      const jwk = await generateInstanceJwk();
      const { challenge } = (await requestChallenge()).data;
      const { idToken } = await loginForRegistration({ challenge, jwk });

      const responses = await Promise.all(
        Array.from({ length: 10 }, () => registerInstance({ challenge, jwk, idToken }))
      );
      const statuses = responses.map((response) => response.status).sort();

      expect(statuses.filter((status) => status === 201)).toHaveLength(1);
      expect(statuses.filter((status) => status === 400)).toHaveLength(9);
    });

    it("rejects a key that is already registered to an instance", async () => {
      // A refresh token is bound to the key: a second instance holding it would redeem the tokens of
      // the first, and revoking the first would not stop the key.
      const { jwk } = await enrollInstance();

      const { challenge } = (await requestChallenge()).data;
      const { idToken } = await loginForRegistration({ challenge, jwk });

      const response = await registerInstance({ challenge, jwk, idToken });
      expect(response.status).toBe(400);
      expect(response.data).toEqual({ error: "invalid_request" });
    });

    it("rejects an instance key that carries private key material", async () => {
      const jwk = await generateInstanceJwk();
      const { challenge } = (await requestChallenge()).data;
      const { idToken } = await loginForRegistration({ challenge, jwk });

      const response = await postWithJson({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/client-instances`,
        body: {
          challenge,
          id_token: idToken,
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
      const { challenge } = (await requestChallenge()).data;
      const { idToken } = await loginForRegistration({ challenge, jwk });

      const response = await postWithJson({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/client-instances`,
        body: {
          challenge,
          id_token: idToken,
          client_instance_public_key: publicJwkOf(jwk),
          platform_evidence: { platform: "no-such-platform" },
        },
      });

      expect(response.status).toBe(400);
    });
  });
});
