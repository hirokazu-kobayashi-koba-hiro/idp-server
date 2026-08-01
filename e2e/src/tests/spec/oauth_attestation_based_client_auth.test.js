/**
 * draft-ietf-oauth-attestation-based-client-auth-10:
 * OAuth 2.0 Attestation-Based Client Authentication (attest_jwt_client_auth)
 *
 * The Client Attester issues a Client Attestation JWT binding the Client
 * Instance Key (cnf.jwk). The Client Instance proves possession of that key
 * with a Client Attestation PoP JWT on each request. The Authorization Server
 * verifies both JWTs.
 *
 * Prerequisite: the test tenant's authorization-server enables attest_jwt_client_auth
 * (seeded by config/examples/e2e/test-tenant + config/scripts/e2e-test-data.sh).
 * Note: the server config is NOT updated here on purpose — the management API GET
 * masks jwks and PUT replaces the whole payload, so a GET->PUT round-trip would
 * wipe the tenant's signing keys.
 *
 * Setup performed via Control Plane management APIs:
 * - clients: register a client with token_endpoint_auth_method
 *   attest_jwt_client_auth and the trusted attester JWKS (client_attestation_jwks)
 *
 * @see https://www.ietf.org/archive/id/draft-ietf-oauth-attestation-based-client-auth-10.html
 */
import { beforeAll, describe, expect, it, xit } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import { get, post, postWithJson } from "../../lib/http";
import { requestToken } from "../../api/oauthClient";
import { adminServerConfig, backendUrl, serverConfig } from "../testConfig";
import {
  createJwtWithPrivateKey,
  generateECP256JWKSObject,
  generateJti,
} from "../../lib/jose";
import { toEpocTime } from "../../lib/util";

const ATTESTATION_TYP = "oauth-client-attestation+jwt";
const POP_TYP = "oauth-client-attestation-pop+jwt";
const ATTESTATION_HEADER = "OAuth-Client-Attestation";
const POP_HEADER = "OAuth-Client-Attestation-PoP";

let attesterPrivateJwk;
let attesterPublicJwk;
let instancePrivateJwk;
let instancePublicJwk;
let attestedClient;

const publicJwkOf = (privateJwk) => {
  const { d, ...publicJwk } = privateJwk;
  return publicJwk;
};

/**
 * Client Attester role: issues the Client Attestation JWT for the instance key.
 */
const createAttestationJwt = ({
  typ = ATTESTATION_TYP,
  sub = attestedClient.clientId,
  exp = toEpocTime({ adjusted: 300 }),
  cnf = { jwk: instancePublicJwk },
} = {}) => {
  const payload = { iss: "test-attester", sub, exp };
  if (cnf) {
    payload.cnf = cnf;
  }
  return createJwtWithPrivateKey({
    payload,
    privateKey: attesterPrivateJwk,
    additionalOptions: { header: { typ } },
  });
};

/**
 * Client Instance role: signs the Client Attestation PoP JWT with the instance key.
 */
const createPopJwt = ({
  typ = POP_TYP,
  aud = serverConfig.issuer,
  jti = generateJti(),
  iat,
  signingKey = instancePrivateJwk,
} = {}) => {
  const payload = { aud, jti };
  if (iat) {
    payload.iat = iat;
  }
  return createJwtWithPrivateKey({
    payload,
    privateKey: signingKey,
    additionalOptions: { header: { typ } },
  });
};

const requestTokenWithAttestation = async ({ attestationJwt, popJwt, scope = "account" }) => {
  return await requestToken({
    endpoint: serverConfig.tokenEndpoint,
    grantType: "client_credentials",
    scope,
    clientId: attestedClient.clientId,
    additionalHeaders: {
      ...(attestationJwt !== undefined && { [ATTESTATION_HEADER]: attestationJwt }),
      ...(popJwt !== undefined && { [POP_HEADER]: popJwt }),
    },
  });
};

beforeAll(async () => {
  // admin access token for Control Plane APIs
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
  const accessToken = tokenResponse.data.access_token;
  const managementHeaders = { Authorization: `Bearer ${accessToken}` };

  // attester key pair (Client Attester) and instance key pair (Client Instance)
  const attesterJwks = await generateECP256JWKSObject({ kid: "attester-key-1" });
  attesterPrivateJwk = attesterJwks.keys[0];
  attesterPublicJwk = publicJwkOf(attesterPrivateJwk);
  const instanceJwks = await generateECP256JWKSObject({ kid: "instance-key-1" });
  instancePrivateJwk = instanceJwks.keys[0];
  instancePublicJwk = publicJwkOf(instancePrivateJwk);

  // prerequisite check: the seeded tenant must enable attest_jwt_client_auth
  // (run config/scripts/e2e-test-data.sh if this fails)
  const discoveryResponse = await get({ url: serverConfig.discoveryEndpoint });
  expect(discoveryResponse.status).toBe(200);
  expect(discoveryResponse.data.token_endpoint_auth_methods_supported).toContain(
    "attest_jwt_client_auth"
  );

  // clients: register the attested client with the trusted attester JWKS
  const clientId = uuidv4();
  const registrationResponse = await postWithJson({
    url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients`,
    headers: managementHeaders,
    body: {
      client_id: clientId,
      client_name: "Attestation Based Client Auth Test Client",
      token_endpoint_auth_method: "attest_jwt_client_auth",
      client_attestation_jwks: JSON.stringify({ keys: [attesterPublicJwk] }),
      grant_types: ["client_credentials"],
      redirect_uris: ["http://localhost:3000/callback"],
      response_types: ["code"],
      scope: "account management",
      enabled: true,
    },
  });
  console.log("client registration:", registrationResponse.status, registrationResponse.data);
  expect(registrationResponse.status).toBe(201);
  attestedClient = { clientId };
});

describe("draft-ietf-oauth-attestation-based-client-auth-10: OAuth 2.0 Attestation-Based Client Authentication", () => {

  describe("4.1. Client Attestation JWT", () => {

    it("authenticates the client when Client Attestation JWT and Client Attestation PoP JWT are valid", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt(),
      });
      console.log(response.data);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("access_token");
    });

    it("typ REQUIRED. The typ (JWT type) header MUST be oauth-client-attestation+jwt.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ typ: "JWT" }),
        popJwt: createPopJwt(),
      });
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_client");
    });

    it("sub REQUIRED. The sub (subject) claim MUST specify client_id value of the OAuth Client.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ sub: "another-client" }),
        popJwt: createPopJwt(),
      });
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_client");
    });

    it("exp REQUIRED. The exp (expiration time) claim MUST specify the time at which the Client Attestation is considered expired by its issuer.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ exp: toEpocTime({ adjusted: -300 }) }),
        popJwt: createPopJwt(),
      });
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_client");
    });

    it("cnf REQUIRED. The cnf (confirmation) claim MUST specify a key conforming to [RFC7800] that is used by the Client Instance to generate the Client Attestation PoP JWT.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ cnf: null }),
        popJwt: createPopJwt(),
      });
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_client");
    });

    it("The signature MUST verify with a Client Attester key the Authorization Server trusts (client_attestation_jwks).", async () => {
      const untrustedAttesterJwks = await generateECP256JWKSObject({ kid: "attester-key-1" });
      const untrustedAttestation = createJwtWithPrivateKey({
        payload: {
          iss: "test-attester",
          sub: attestedClient.clientId,
          exp: toEpocTime({ adjusted: 300 }),
          cnf: { jwk: instancePublicJwk },
        },
        privateKey: untrustedAttesterJwks.keys[0],
        additionalOptions: { header: { typ: ATTESTATION_TYP } },
      });
      const response = await requestTokenWithAttestation({
        attestationJwt: untrustedAttestation,
        popJwt: createPopJwt(),
      });
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_client");
    });
  });

  describe("4.2. Client Attestation PoP JWT", () => {

    it("typ REQUIRED. The typ (JWT type) header MUST be oauth-client-attestation-pop+jwt.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ typ: "JWT" }),
      });
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_client");
    });

    it("The JWT MUST be digitally signed using an asymmetric cryptographic algorithm. The signature verifies with the Client Instance Key (cnf.jwk).", async () => {
      const anotherInstanceJwks = await generateECP256JWKSObject({ kid: "instance-key-1" });
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ signingKey: anotherInstanceJwks.keys[0] }),
      });
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_client");
    });

    it("aud REQUIRED. The aud (audience) claim MUST specify a value that identifies the intended audience of the JWT. The RFC8414 issuer identifier URL of the authorization server MUST be used.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ aud: "https://other-as.example.com" }),
      });
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_client");
    });

    it("jti REQUIRED. The jti (JWT identifier) claim MUST specify a unique identifier for the Client Attestation PoP.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ jti: null }),
      });
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_client");
    });

    it("iat REQUIRED. The iat (issued at) claim MUST specify the time at which the Client Attestation PoP was issued. The value is checked against an acceptable time window.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ iat: toEpocTime({ adjusted: -600 }) }),
      });
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_client");
    });
  });

  describe("6. Using Attestations in Client Authentication", () => {

    it("There is precisely one OAuth-Client-Attestation HTTP request header field containing a Client Attestation JWT. (absence is rejected)", async () => {
      const response = await requestTokenWithAttestation({
        popJwt: createPopJwt(),
      });
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_client");
    });

    it("There is precisely one OAuth-Client-Attestation-PoP HTTP request header field containing a Client Attestation PoP JWT. (absence is rejected)", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
      });
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_client");
    });

    it("There is precisely one OAuth-Client-Attestation HTTP request header field. (multiple header fields are rejected)", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: [createAttestationJwt(), createAttestationJwt()],
        popJwt: createPopJwt(),
      });
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_client");
    });

    it("The attestation applies to endpoints where the client authenticates: Pushed Authorization Request endpoint (RFC 9126).", async () => {
      const params = new URLSearchParams();
      params.append("response_type", "code");
      params.append("client_id", attestedClient.clientId);
      params.append("redirect_uri", "http://localhost:3000/callback");
      params.append("scope", "account");
      params.append("state", "attestation-par-test");
      const response = await post({
        url: serverConfig.pushedAuthorizationEndpoint,
        body: params,
        headers: {
          [ATTESTATION_HEADER]: createAttestationJwt(),
          [POP_HEADER]: createPopJwt(),
        },
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(201);
      expect(response.data).toHaveProperty("request_uri");
    });
  });

  describe("8. Authorization Server Metadata", () => {

    it("The Authorization Server SHOULD communicate support by using the value attest_jwt_client_auth in the token_endpoint_auth_methods_supported within its published metadata. client_attestation_signing_alg_values_supported and client_attestation_pop_signing_alg_values_supported MUST be included when supported.", async () => {
      const response = await get({ url: serverConfig.discoveryEndpoint });
      expect(response.status).toBe(200);
      expect(response.data.token_endpoint_auth_methods_supported).toContain(
        "attest_jwt_client_auth"
      );
      expect(response.data).toHaveProperty("client_attestation_signing_alg_values_supported");
      expect(response.data).toHaveProperty(
        "client_attestation_pop_signing_alg_values_supported"
      );
    });
  });

  describe("9. Challenge Endpoint (not implemented yet)", () => {

    xit("The Authorization Server or Resource Server MAY offer a challenge endpoint for Clients to fetch Challenges. The challenge_endpoint metadata is published.", async () => {});

    xit("use_attestation_challenge: the error response MUST include the OAuth-Client-Attestation-Challenge HTTP header field with a new challenge.", async () => {});

    xit("use_fresh_attestation: The Client Attestation JWT is not sufficiently fresh.", async () => {});

    xit("challenge OPTIONAL. When the server provided a challenge, the challenge claim in the Client Attestation PoP JWT MUST match it.", async () => {});
  });

  describe("11. Security Considerations (not implemented yet)", () => {

    xit("11.1. Replay detection: The Authorization Server SHOULD implement measures to detect replay attacks (witnessed jti values within the validity time window).", async () => {});

    xit("DPoP combined mode: DPoP can be used alongside attest_jwt_client_auth. client_attestation_pop_methods_supported metadata.", async () => {});

    xit("invalid_client_attestation MAY be used in addition to the more general invalid_client error code if the attestation or its proof of possession could not be successfully verified.", async () => {});
  });
});
