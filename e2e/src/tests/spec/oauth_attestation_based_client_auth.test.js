/**
 * draft-ietf-oauth-attestation-based-client-auth-10:
 * OAuth 2.0 Attestation-Based Client Authentication (attest_jwt_client_auth)
 *
 * The Client Attester issues a Client Attestation JWT binding the Client
 * Instance Key (cnf.jwk). The Client Instance proves possession of that key
 * with a Client Attestation PoP JWT on each request. The Authorization Server
 * verifies both JWTs.
 *
 * Section structure of this file traces draft-10:
 *   4.  Client Attestation JWT (format)
 *   5.1 Client Attestation PoP JWT (format)
 *   6.  Challenges
 *   7.1 / 7.2 Verification rules (numbered, 1:1)
 *   7.3 DPoP Combined Mode
 *   7.4 Errors
 *   7.5 Client Attestation as an OAuth Client Authentication
 *   7.6 Client Attestation as an additional security signal
 *   8.  Authorization Server Metadata
 *   9.3 Refresh token binding
 *   9.4 Binding of OAuth protocol artifacts
 *   11.1 Replay Attacks
 *
 * Prerequisite: the test tenant's authorization-server enables attest_jwt_client_auth
 * with client_attestation(_pop)_signing_alg_values_supported = [ES256, RS256]
 * (seeded by config/examples/e2e/test-tenant + config/scripts/e2e-test-data.sh).
 * Note: the server config is NOT updated here on purpose — the management API GET
 * masks jwks and PUT replaces the whole payload, so a GET->PUT round-trip would
 * wipe the tenant's signing keys.
 *
 * Setup performed via Control Plane management APIs:
 * - clients: register a client with token_endpoint_auth_method
 *   attest_jwt_client_auth and the trusted attester JWKS
 *   (extension.client_attestation_trust_source = attester_jwks)
 *
 * @see https://www.ietf.org/archive/id/draft-ietf-oauth-attestation-based-client-auth-10.html
 */
import { beforeAll, describe, expect, it, xit } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import * as jose from "jose";
import { get, post, postWithJson } from "../../lib/http";
import { requestToken } from "../../api/oauthClient";
import { adminServerConfig, backendUrl, serverConfig } from "../testConfig";
import { createJwt, createJwtWithPrivateKey, generateJti } from "../../lib/jose";
import { toEpocTime } from "../../lib/util";

const ATTESTATION_TYP = "oauth-client-attestation+jwt";
const POP_TYP = "oauth-client-attestation-pop+jwt";
const ATTESTATION_HEADER = "OAuth-Client-Attestation";
const POP_HEADER = "OAuth-Client-Attestation-PoP";
const CHALLENGE_HEADER = "OAuth-Client-Attestation-Challenge";

let attesterEs256Jwk;
let attesterEs384Jwk;
let instanceEs256Jwk;
let instanceEs384Jwk;
let attestedClient;

const generateSigningJwk = async (alg, kid) => {
  const { privateKey } = await jose.generateKeyPair(alg, { extractable: true });
  const jwk = await jose.exportJWK(privateKey);
  return { ...jwk, use: "sig", kid, alg };
};

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
  cnf = () => ({ jwk: publicJwkOf(instanceEs256Jwk) }),
  extraClaims = {},
  signingKey = () => attesterEs256Jwk,
} = {}) => {
  const payload = { iss: "test-attester", exp, ...extraClaims };
  if (sub !== null) {
    payload.sub = sub;
  }
  const cnfValue = typeof cnf === "function" ? cnf() : cnf;
  if (cnfValue) {
    payload.cnf = cnfValue;
  }
  const key = typeof signingKey === "function" ? signingKey() : signingKey;
  return createJwtWithPrivateKey({
    payload,
    privateKey: key,
    algorithm: key.alg,
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
  extraClaims = {},
  signingKey = () => instanceEs256Jwk,
} = {}) => {
  const payload = { aud, ...extraClaims };
  if (jti !== null) {
    payload.jti = jti;
  }
  if (iat) {
    payload.iat = iat;
  }
  const key = typeof signingKey === "function" ? signingKey() : signingKey;
  return createJwtWithPrivateKey({
    payload,
    privateKey: key,
    algorithm: key.alg,
    additionalOptions: { header: { typ } },
  });
};

/** Section 6.1: fetch a server-provided Challenge from the challenge endpoint. */
const fetchChallenge = async () => {
  const response = await postWithJson({
    url: `${backendUrl}/${serverConfig.tenantId}/v1/client-attestation/challenges`,
    body: {},
  });
  expect(response.status).toBe(200);
  return response.data.attestation_challenge;
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

const expectInvalidClient = (response) => {
  expect(response.status).toBe(401);
  expect(response.data).toHaveProperty("error", "invalid_client");
};

/**
 * Section 7.4: a failure of the Client Attestation JWT or of its proof of possession is reported
 * with the dedicated code. Presenting no attestation at all stays on the general invalid_client.
 */
const expectInvalidClientAttestation = (response) => {
  expect(response.status).toBe(401);
  expect(response.data).toHaveProperty("error", "invalid_client_attestation");
};

/**
 * Section 7.4: the PoP JWT did not use an expected server-provided challenge. The error MUST be
 * accompanied by the OAuth-Client-Attestation-Challenge header carrying a Challenge to use next.
 */
const expectUseAttestationChallenge = (response) => {
  expect(response.status).toBe(401);
  expect(response.data).toHaveProperty("error", "use_attestation_challenge");
  expect(response.headers[CHALLENGE_HEADER.toLowerCase()]).toBeDefined();
};

/** Section 7.4: the Client Attestation JWT is no longer fresh; the client must obtain a new one. */
const expectUseFreshAttestation = (response) => {
  expect(response.status).toBe(401);
  expect(response.data).toHaveProperty("error", "use_fresh_attestation");
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

  // prerequisite check: the seeded tenant must enable attest_jwt_client_auth
  // (run config/scripts/e2e-test-data.sh if this fails)
  const discoveryResponse = await get({ url: serverConfig.discoveryEndpoint });
  expect(discoveryResponse.status).toBe(200);
  expect(discoveryResponse.data.token_endpoint_auth_methods_supported).toContain(
    "attest_jwt_client_auth"
  );

  // attester key pair (Client Attester) and instance key pair (Client Instance).
  // ES384 variants exercise the alg allow-list ([ES256, RS256]) rejection paths.
  attesterEs256Jwk = await generateSigningJwk("ES256", "attester-es256");
  attesterEs384Jwk = await generateSigningJwk("ES384", "attester-es384");
  instanceEs256Jwk = await generateSigningJwk("ES256", "instance-es256");
  instanceEs384Jwk = await generateSigningJwk("ES384", "instance-es384");

  // clients: register the attested client with the trusted attester JWKS
  const clientId = uuidv4();
  const registrationResponse = await postWithJson({
    url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients`,
    headers: managementHeaders,
    body: {
      client_id: clientId,
      client_name: "Attestation Based Client Auth Test Client",
      token_endpoint_auth_method: "attest_jwt_client_auth",
      extension: {
        client_attestation_trust_source: "attester_jwks",
        client_attestation_attester_jwks: JSON.stringify({
          keys: [publicJwkOf(attesterEs256Jwk), publicJwkOf(attesterEs384Jwk)],
        }),
      },
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

  describe("4. Client Attestation JWT", () => {

    it("typ REQUIRED. The typ (JWT type) header MUST be oauth-client-attestation+jwt.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ typ: "JWT" }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(response);
    });

    it("sub REQUIRED. The sub (subject) claim MUST specify client_id value of the OAuth Client.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ sub: null }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(response);
    });

    it("exp REQUIRED. The Authorization Server MUST reject any JWT with an expiration time that has passed.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ exp: toEpocTime({ adjusted: -300 }) }),
        popJwt: createPopJwt(),
      });
      expectUseFreshAttestation(response);
    });

    it("cnf REQUIRED. The cnf (confirmation) claim MUST specify a key conforming to [RFC7800] that is used by the Client Instance to generate the Client Attestation PoP JWT.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ cnf: null }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(response);
    });

    it("The key MUST be expressed using the \"jwk\" representation. (cnf without jwk is rejected)", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({
          cnf: { jkt: "NzbLsXh8uDCcd-6MNwXF4W_7noWXFZAfHkxZsRGC9Xs" },
        }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(response);
    });

    it("The JWT MAY contain other claims. All claims that are not understood by implementations MUST be ignored.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({
          extraClaims: { wallet_name: "test-wallet", "urn:example:attestation_ext": true },
        }),
        popJwt: createPopJwt({ extraClaims: { "urn:example:pop_ext": "ignored" } }),
      });
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("access_token");
    });
  });

  describe("5.1. Client Attestation PoP JWT", () => {

    it("typ REQUIRED. The typ (JWT type) header MUST be oauth-client-attestation-pop+jwt.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ typ: "JWT" }),
      });
      expectInvalidClientAttestation(response);
    });

    it("The JWT MUST be digitally signed using an asymmetric cryptographic algorithm. (MAC-signed PoP is rejected)", async () => {
      const hmacPop = createJwt({
        payload: { aud: serverConfig.issuer, jti: generateJti() },
        secret: "shared-secret-value-for-hmac-signing-test",
        options: { algorithm: "HS256", header: { typ: POP_TYP } },
      });
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: hmacPop,
      });
      expectInvalidClientAttestation(response);
    });

    it("aud REQUIRED. When the JWT is presented to an Authorization Server, the [RFC8414] issuer identifier URL of the Authorization Server MUST be used.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ aud: "https://other-as.example.com" }),
      });
      expectInvalidClientAttestation(response);
    });

    it("jti REQUIRED. The jti (JWT identifier) claim MUST specify a unique identifier for the Client Attestation PoP.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ jti: null }),
      });
      expectInvalidClientAttestation(response);
    });

    it("iat REQUIRED. The iat (issued at) claim MUST specify the time at which the Client Attestation PoP was issued. (outside the acceptable window is rejected)", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ iat: toEpocTime({ adjusted: -600 }) }),
      });
      expectInvalidClientAttestation(response);
    });
  });

  describe("6. Challenges", () => {

    it("6.1. The Authorization Server or Resource Server MAY offer a challenge endpoint for Clients to fetch Challenges. It MUST signal support by including the metadata entry challenge_endpoint.", async () => {
      const response = await get({ url: serverConfig.discoveryEndpoint });
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("challenge_endpoint");
      expect(response.data.challenge_endpoint).toContain("/v1/client-attestation/challenges");
    });

    it("6.1. The response contains attestation_challenge. The Authorization Server MUST make the response uncacheable by adding a Cache-Control header field including the value no-store.", async () => {
      const response = await postWithJson({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/client-attestation/challenges`,
        body: {},
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("attestation_challenge");
      expect(typeof response.data.attestation_challenge).toBe("string");
      expect(response.headers["cache-control"]).toContain("no-store");
    });

    it("6.1. The value of the challenge is opaque to the client and is not reused across requests.", async () => {
      const first = await fetchChallenge();
      const second = await fetchChallenge();

      expect(first).not.toBe(second);
    });

    it("6.2. The Authorization Server MAY provide a fresh Challenge with any HTTP response using the OAuth-Client-Attestation-Challenge HTTP header field.", async () => {
      // A challenge the server never issued is rejected, and the rejection carries the Challenge
      // the client is expected to use next.
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ extraClaims: { challenge: "never-issued-by-this-server" } }),
      });
      console.log(response.status, response.data, response.headers[CHALLENGE_HEADER.toLowerCase()]);
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "use_attestation_challenge");
      expect(response.headers[CHALLENGE_HEADER.toLowerCase()]).toBeDefined();
    });

    it("challenge OPTIONAL. If the Authorization Server offers a challenge endpoint, the Client MUST retrieve a challenge and MUST use this challenge in the Client Attestation PoP JWT.", async () => {
      const challenge = await fetchChallenge();
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ extraClaims: { challenge } }),
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("access_token");
    });

    it("6.1. A Challenge stays usable for its whole lifetime, so one Challenge covers a polling cycle.", async () => {
      // Section 9.7: a challenge bound to a Client Instance session is validated against the single
      // value expected for that session, without a seen-values store. It is therefore not consumed.
      const challenge = await fetchChallenge();

      const first = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ extraClaims: { challenge } }),
      });
      const second = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ extraClaims: { challenge } }),
      });

      expect(first.status).toBe(200);
      expect(second.status).toBe(200);
    });
  });

  describe("7.1. Verification: Client Attestation JWT", () => {

    it("1. There is precisely one OAuth-Client-Attestation HTTP request header field containing a Client Attestation JWT. (absence is rejected)", async () => {
      const response = await requestTokenWithAttestation({
        popJwt: createPopJwt(),
      });
      expectInvalidClient(response);
    });

    it("1. There is precisely one OAuth-Client-Attestation HTTP request header field. (multiple header fields are rejected)", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: [createAttestationJwt(), createAttestationJwt()],
        popJwt: createPopJwt(),
      });
      expectInvalidClient(response);
    });

    it("3. The alg JOSE Header Parameter contains a registered algorithm, is not none, is supported by the application, and is acceptable per local policy. (alg outside client_attestation_signing_alg_values_supported is rejected)", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ signingKey: () => attesterEs384Jwk }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(response);
    });

    it("4. The signature of the Client Attestation JWT verifies with the public key of a known and trusted Client Attester.", async () => {
      const untrustedAttesterJwk = await generateSigningJwk("ES256", "attester-es256");
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ signingKey: () => untrustedAttesterJwk }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(response);
    });

    it("5. The key contained in the cnf claim of the Client Attestation JWT is not a private key.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ cnf: () => ({ jwk: instanceEs256Jwk }) }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(response);
    });

    xit("6. The Client Attestation JWT is fresh enough per local policy by checking the iat or exp claims. (iat-based freshness policy / use_fresh_attestation, exp expiry is covered in Section 4)", async () => {});
  });

  describe("7.2. Verification: Client Attestation PoP JWT", () => {

    it("1. There is precisely one OAuth-Client-Attestation-PoP HTTP request header field containing a Client Attestation PoP JWT. (absence is rejected)", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
      });
      expectInvalidClient(response);
    });

    it("1. There is precisely one OAuth-Client-Attestation-PoP HTTP request header field. (multiple header fields are rejected)", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: [createPopJwt(), createPopJwt()],
      });
      expectInvalidClient(response);
    });

    it("3. The alg JOSE Header Parameter contains a registered algorithm, is not none, is supported by the application, and is acceptable per local policy. (alg outside client_attestation_pop_signing_alg_values_supported is rejected)", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({
          cnf: () => ({ jwk: publicJwkOf(instanceEs384Jwk) }),
        }),
        popJwt: createPopJwt({ signingKey: () => instanceEs384Jwk }),
      });
      expectInvalidClientAttestation(response);
    });

    it("4. The signature of the Client Attestation PoP JWT verifies with the public key contained in the cnf claim of the Client Attestation JWT.", async () => {
      const anotherInstanceJwk = await generateSigningJwk("ES256", "instance-es256");
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ signingKey: () => anotherInstanceJwk }),
      });
      expectInvalidClientAttestation(response);
    });

    it("4. A Client Attestation JWT captured from a legitimate instance cannot be paired with a PoP signed by another key.", async () => {
      // The attacker view of item 4: both headers travel in plain sight, so a captured Client
      // Attestation JWT is not a credential on its own. Possession of the cnf key is what counts.
      const attackerJwk = await generateSigningJwk("ES256", "attacker-key");
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ signingKey: () => attackerJwk }),
      });
      expectInvalidClientAttestation(response);
    });

    it("5. If the server provided a challenge value to the client, the challenge claim is present in the Client Attestation PoP JWT and matches the server-provided challenge value.", async () => {
      const challenge = await fetchChallenge();
      const accepted = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ extraClaims: { challenge } }),
      });
      expect(accepted.status).toBe(200);

      const mismatched = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ extraClaims: { challenge: `${challenge}-tampered` } }),
      });
      expectUseAttestationChallenge(mismatched);
    });

    it("8. If the Client received a challenge through the Authorization Server's challenge endpoint or within previous responses, it MUST match the challenge claim of the Client Attestation PoP JWT.", async () => {
      // The Challenge handed back on a previous response is accepted on the next request, which is
      // the Section 6.2 hand-off working end to end.
      const rejected = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ extraClaims: { challenge: "never-issued-by-this-server" } }),
      });
      expectUseAttestationChallenge(rejected);

      const handedBack = rejected.headers[CHALLENGE_HEADER.toLowerCase()];
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ extraClaims: { challenge: handedBack } }),
      });
      expect(response.status).toBe(200);
    });

    xit("9. Depending on the security requirements of the deployment, additional checks to guarantee replay protection for the Client Attestation PoP JWT might need to be applied.", async () => {});
  });

  describe("7.3. DPoP Combined Mode (not implemented yet)", () => {

    xit("1. There is no OAuth-Client-Attestation-PoP HTTP request header field present in the request.", async () => {});

    xit("2. There is precisely one DPoP HTTP request header field present in the request.", async () => {});

    xit("3. Validate the DPoP proof in accordance with [RFC9449].", async () => {});

    xit("4. The public key in the jwk header parameter of the DPoP proof MUST be identical to the public key in the cnf claim of the Client Attestation JWT.", async () => {});

    xit("5. If the Client received a challenge, it MUST match the nonce payload claim of the DPoP proof.", async () => {});
  });

  describe("7.4. Errors", () => {

    it("use_attestation_challenge MUST be used when the Client Attestation PoP JWT is not using an expected server-provided challenge. When used this error code MUST be accompanied by the OAuth-Client-Attestation-Challenge HTTP header field parameter.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ extraClaims: { challenge: "never-issued-by-this-server" } }),
      });
      expectUseAttestationChallenge(response);
    });

    it("use_fresh_attestation MUST be used when the Client Attestation JWT is deemed to be not fresh enough to be acceptable by the server.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ exp: toEpocTime({ adjusted: -300 }) }),
        popJwt: createPopJwt(),
      });
      expectUseFreshAttestation(response);
    });

    it("invalid_client_attestation MAY be used in addition to the more general invalid_client error code if the attestation or its proof of possession could not be successfully verified.", async () => {
      const attestationFailure = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ signingKey: () => instanceEs256Jwk }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(attestationFailure);

      const popFailure = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ signingKey: () => attesterEs256Jwk }),
      });
      expectInvalidClientAttestation(popFailure);
    });

    it("Presenting no Client Attestation at all stays on the general invalid_client: there is no attestation whose verification could have failed.", async () => {
      const response = await requestTokenWithAttestation({ popJwt: createPopJwt() });
      expectInvalidClient(response);
    });
  });

  describe("7.5. Client Attestation as an OAuth Client Authentication", () => {

    it("authenticates the client at the token endpoint when Client Attestation JWT and Client Attestation PoP JWT are valid", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt(),
      });
      console.log(response.data);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("access_token");
    });

    it("If the token request contains a client_id parameter as per [RFC6749] the Authorization Server MUST verify that the value of this parameter is the same as the client_id value in the sub claim of the Client Attestation.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ sub: "another-client" }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(response);
    });

    it("authenticates the client at endpoints where the client authenticates: Pushed Authorization Request endpoint (RFC 9126).", async () => {
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

    it("authenticates the client at endpoints where the client authenticates: Token Introspection endpoint (RFC 7662).", async () => {
      const tokenResponse = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt(),
      });
      expect(tokenResponse.status).toBe(200);

      const params = new URLSearchParams();
      params.append("token", tokenResponse.data.access_token);
      params.append("client_id", attestedClient.clientId);
      const response = await post({
        url: serverConfig.tokenIntrospectionEndpoint,
        body: params,
        headers: {
          [ATTESTATION_HEADER]: createAttestationJwt(),
          [POP_HEADER]: createPopJwt(),
        },
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("active", true);
    });

    it("Token Introspection endpoint rejects the request when the Client Attestation headers are absent.", async () => {
      const tokenResponse = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt(),
      });
      expect(tokenResponse.status).toBe(200);

      const params = new URLSearchParams();
      params.append("token", tokenResponse.data.access_token);
      params.append("client_id", attestedClient.clientId);
      const response = await post({
        url: serverConfig.tokenIntrospectionEndpoint,
        body: params,
      });
      console.log(response.status, response.data);
      // The introspection endpoint reports client authentication failure as 400 with
      // active:false, matching rfc7662_token_introspection.test.js (#1707), rather than the
      // 401 used by the token endpoint.
      expect(response.status).toBe(400);
      expect(response.data).toHaveProperty("active", false);
      expect(response.data).toHaveProperty("error", "invalid_client");
    });

    it("authenticates the Resource Server at the introspection-extensions endpoint: the Client Attestation headers are the Resource Server's own credentials, not a forwarded artifact.", async () => {
      const tokenResponse = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt(),
      });
      expect(tokenResponse.status).toBe(200);

      const params = new URLSearchParams();
      params.append("token", tokenResponse.data.access_token);
      params.append("client_id", attestedClient.clientId);
      const response = await post({
        url: serverConfig.tokenIntrospectionExtensionsEndpoint,
        body: params,
        headers: {
          [ATTESTATION_HEADER]: createAttestationJwt(),
          [POP_HEADER]: createPopJwt(),
        },
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("active", true);
    });

    it("authenticates the client at endpoints where the client authenticates: Token Revocation endpoint (RFC 7009).", async () => {
      const tokenResponse = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt(),
      });
      expect(tokenResponse.status).toBe(200);

      const params = new URLSearchParams();
      params.append("token", tokenResponse.data.access_token);
      params.append("client_id", attestedClient.clientId);
      const response = await post({
        url: serverConfig.tokenRevocationEndpoint,
        body: params,
        headers: {
          [ATTESTATION_HEADER]: createAttestationJwt(),
          [POP_HEADER]: createPopJwt(),
        },
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(200);
    });
  });

  describe("7.6. Client Attestation as an additional security signal (not implemented yet)", () => {

    xit("An Authorization Server or Resource Server MAY signal a requirement for presenting a Client Attestation via client_attestation_pop_methods_supported. A server MUST NOT include a method it does not accept, and the array MUST NOT be empty when the parameter is present.", async () => {});
  });

  describe("8. Authorization Server and Resource Server Metadata", () => {

    it("The Authorization Server SHOULD communicate support by using the value attest_jwt_client_auth in the token_endpoint_auth_methods_supported. The Authorization Server MUST include client_attestation_signing_alg_values_supported and client_attestation_pop_signing_alg_values_supported in its published metadata if the Client Attestation PoP JWT mechanism is used.", async () => {
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

    xit("The Authorization Server SHOULD communicate support for authentication using a DPoP proof as the PoP by using the value attest_jwt_client_auth_dpop. The Authorization Server MUST include dpop_signing_alg_values_supported if DPoP is used as the Proof of Possession in combined mode.", async () => {});
  });

  describe("9.3. Refresh token binding (not implemented yet)", () => {

    xit("Authorization servers issuing a refresh token in response to a token request using the client attestation mechanism MUST bind the refresh token to the Client Instance and its associated public key. To prove this binding, the Client Instance MUST use the client attestation mechanism when refreshing an access token, and MUST also use the same key that was present in the cnf claim.", async () => {});
  });

  describe("9.4. Binding of OAuth protocol artifacts (not implemented yet)", () => {

    xit("Authorization servers using Attestation-Based Client Authentication are RECOMMENDED to bind relevant protocol artifacts to the Client Instance and its associated public key where possible, and NOT just the client as specified in [RFC6749]. (the authorization_code as specified in Section 4.1 of [RFC6749])", async () => {});

    xit("Examples of these artifacts include but are not limited to: the auth_req_id as specified in section 7.3 [CIBA].", async () => {});
  });

  describe("11.1. Replay Attacks (not implemented yet)", () => {

    xit("An Authorization/Resource Server SHOULD implement measures to detect replay attacks by the Client Instance. (witnessed jti values of the Client Attestation PoP JWT for the validity time window)", async () => {});
  });
  describe("11.2. Client Attestation Protection", () => {

    it("This specification allows both, digital signatures using asymmetric cryptography, and Message Authentication Codes (MAC) to be used to protect Client Attestation JWTs. (idp-server accepts only digital signatures)", async () => {
      // Section 11.2 permits MACs where the Attester and the Authorization Server share a key.
      // idp-server does not: the trust sources it offers are a public JWKS and a registered public
      // key, neither of which can verify a MAC. A MAC-protected attestation is therefore rejected
      // rather than silently trusted.
      const hmacAttestation = createJwt({
        payload: {
          iss: "test-attester",
          sub: attestedClient.clientId,
          exp: toEpocTime({ adjusted: 300 }),
          cnf: { jwk: publicJwkOf(instanceEs256Jwk) },
        },
        secret: "shared-secret-value-for-hmac-signing-test",
        options: { algorithm: "HS256", header: { typ: ATTESTATION_TYP } },
      });
      const response = await requestTokenWithAttestation({
        attestationJwt: hmacAttestation,
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(response);
    });
  });
});
