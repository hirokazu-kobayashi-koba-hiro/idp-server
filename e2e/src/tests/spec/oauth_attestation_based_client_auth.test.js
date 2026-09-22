/**
 * draft-ietf-oauth-attestation-based-client-auth-11:
 * OAuth 2.0 Attestation-Based Client Authentication (attest_jwt_client_auth)
 *
 * The Client Attester issues a Client Attestation JWT binding the Client
 * Instance Key (cnf.jwk). The Client Instance proves possession of that key
 * with a Client Attestation PoP JWT on each request. The Authorization Server
 * verifies both JWTs.
 *
 * Numbering follows draft-11, which moved several sections from draft-10:
 * 9.3 -> 10.3, 9.4 -> 10.4, 9.8 -> 10.8, 11.1 -> 12.1, 11.2 -> 12.2. A ledger
 * whose numbers have drifted cannot be compared against the document, so the
 * renumbering is carried here rather than left for whoever reads it next.
 *
 * Sections covered in sibling files rather than here, because they are about
 * which trust source a deployment picks (10.8) rather than the exchange:
 *   oauth_attestation_registered_instance_key.test.js  self-signed model
 *   oauth_attestation_x5c.test.js                      certificate chain model
 *   usecase/abca/abca-01-attester-jwks.test.js         configured JWKS model
 *
 * Informative sections carry no entries: 1-3 (introduction, conventions,
 * terminology), 14 (relation to RATS), 16 (references).
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
 * @see https://www.ietf.org/archive/id/draft-ietf-oauth-attestation-based-client-auth-11.html
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
let otherInstanceEs256Jwk;
let refreshableClient;
let instanceEs384Jwk;
let attestedClient;
/**
 * A second attested client sharing the same Client Attester, standing in for a client the
 * attacker does not hold credentials for. Used to check that the client whose configuration is
 * loaded and the client the Client Attestation authenticates can never be two different clients.
 */
let otherAttestedClient;

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
/**
 * @param reason the server's stated cause, asserted because the status and the error code alone
 *   do not say which check refused. Every case in this file produces invalid_client_attestation,
 *   so without this a test named after one requirement passes when a different one rejects the
 *   request — which is how a test stops guarding what its name claims.
 */
const expectInvalidClientAttestation = (response, reason) => {
  expect(response.status).toBe(401);
  expect(response.data).toHaveProperty("error", "invalid_client_attestation");
  expect(response.data.error_description).toContain(`reason=${reason}`);
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
  // A second Client Instance of the same client: same attester, different cnf key.
  otherInstanceEs256Jwk = await generateSigningJwk("ES256", "instance-es256-other");
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

  // a second client of the same Attester: same trust source, different client_id
  const otherClientId = uuidv4();
  const otherRegistrationResponse = await postWithJson({
    url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients`,
    headers: managementHeaders,
    body: {
      client_id: otherClientId,
      client_name: "Attestation Based Client Auth Test Client (other)",
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
  expect(otherRegistrationResponse.status).toBe(201);

  // Section 10.3 needs a grant that issues a refresh token; client_credentials does not.
  const refreshableClientId = uuidv4();
  const refreshableRegistrationResponse = await postWithJson({
    url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients`,
    headers: managementHeaders,
    body: {
      client_id: refreshableClientId,
      client_name: "Attestation Based Client Auth Test Client (refreshable)",
      token_endpoint_auth_method: "attest_jwt_client_auth",
      extension: {
        client_attestation_trust_source: "attester_jwks",
        client_attestation_attester_jwks: JSON.stringify({
          keys: [publicJwkOf(attesterEs256Jwk), publicJwkOf(attesterEs384Jwk)],
        }),
      },
      grant_types: ["password", "refresh_token"],
      redirect_uris: ["http://localhost:3000/callback"],
      response_types: ["code"],
      scope: "openid account management",
      enabled: true,
    },
  });
  expect(refreshableRegistrationResponse.status).toBe(201);
  refreshableClient = { clientId: refreshableClientId };
  otherAttestedClient = { clientId: otherClientId };
});

describe("draft-ietf-oauth-attestation-based-client-auth-11: OAuth 2.0 Attestation-Based Client Authentication", () => {

  describe("4. Client Attestation JWT", () => {

    it("typ REQUIRED. The typ (JWT type) header MUST be oauth-client-attestation+jwt.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ typ: "JWT" }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(
        response,
        "client attestation jwt typ header must be 'oauth-client-attestation+jwt'"
      );
    });

    it("sub REQUIRED. The sub (subject) claim MUST specify client_id value of the OAuth Client.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ sub: null }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(
        response,
        "client attestation jwt must contain sub claim"
      );
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
      expectInvalidClientAttestation(
        response,
        "client attestation jwt must contain cnf claim"
      );
    });

    it("The key MUST be expressed using the \"jwk\" representation. (cnf without jwk is rejected)", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({
          cnf: { jkt: "NzbLsXh8uDCcd-6MNwXF4W_7noWXFZAfHkxZsRGC9Xs" },
        }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(
        response,
        "client attestation jwt cnf claim must contain a jwk representation"
      );
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
      expectInvalidClientAttestation(
        response,
        "client attestation pop jwt typ header must be 'oauth-client-attestation-pop+jwt'"
      );
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
      expectInvalidClientAttestation(
        response,
        "client attestation pop jwt must be signed with an asymmetric algorithm"
      );
    });

    it("aud REQUIRED. When the JWT is presented to an Authorization Server, the [RFC8414] issuer identifier URL of the Authorization Server MUST be used.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ aud: "https://other-as.example.com" }),
      });
      expectInvalidClientAttestation(
        response,
        "client attestation pop jwt aud claim must be the issuer identifier URL of the authorization server"
      );
    });

    it("jti REQUIRED. The jti (JWT identifier) claim MUST specify a unique identifier for the Client Attestation PoP.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ jti: null }),
      });
      expectInvalidClientAttestation(
        response,
        "client attestation pop jwt must contain jti claim"
      );
    });

    it("iat REQUIRED. The iat (issued at) claim MUST specify the time at which the Client Attestation PoP was issued. (outside the acceptable window is rejected)", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ iat: toEpocTime({ adjusted: -600 }) }),
      });
      expectInvalidClientAttestation(
        response,
        "client attestation pop jwt iat claim is outside the acceptable time window"
      );
    });
  });

  /**
   * The construction side of DPoP combined mode. 7.3 holds the verification steps; this is the
   * section that says what the client sends, and neither is implemented.
   */
  describe("5.2. Using DPoP as the Proof of Possession (not implemented yet)", () => {
    xit("The DPoP proof MUST adhere to the rules defined in [RFC9449].", async () => {});

    xit("The public key in the jwk header parameter of the DPoP proof MUST match the public key in the cnf claim of the Client Attestation JWT.", async () => {});
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
      expectInvalidClientAttestation(
        response,
        "client attestation jwt alg 'ES384' is not in client_attestation_signing_alg_values_supported"
      );
    });

    it("4. The signature of the Client Attestation JWT verifies with the public key of a known and trusted Client Attester.", async () => {
      const untrustedAttesterJwk = await generateSigningJwk("ES256", "attester-es256");
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ signingKey: () => untrustedAttesterJwk }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(
        response,
        "client attestation jwt validation failed: invalid signature"
      );
    });

    it("5. The key contained in the cnf claim of the Client Attestation JWT is not a private key.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ cnf: () => ({ jwk: instanceEs256Jwk }) }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(
        response,
        "client attestation jwt cnf.jwk must not contain a private key"
      );
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
      expectInvalidClientAttestation(
        response,
        "client attestation pop jwt alg 'ES384' is not in client_attestation_pop_signing_alg_values_supported"
      );
    });

    it("4. The signature of the Client Attestation PoP JWT verifies with the public key contained in the cnf claim of the Client Attestation JWT.", async () => {
      const anotherInstanceJwk = await generateSigningJwk("ES256", "instance-es256");
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ signingKey: () => anotherInstanceJwk }),
      });
      expectInvalidClientAttestation(
        response,
        "client attestation pop jwt signature verification failed with the client instance key (cnf.jwk)"
      );
    });

    it("4. A Client Attestation JWT captured from a legitimate instance cannot be paired with a PoP signed by another key.", async () => {
      // The attacker view of item 4: both headers travel in plain sight, so a captured Client
      // Attestation JWT is not a credential on its own. Possession of the cnf key is what counts.
      const attackerJwk = await generateSigningJwk("ES256", "attacker-key");
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ signingKey: () => attackerJwk }),
      });
      expectInvalidClientAttestation(
        response,
        "client attestation pop jwt signature verification failed with the client instance key (cnf.jwk)"
      );
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
      expectInvalidClientAttestation(
        attestationFailure,
        "client attestation jwt validation failed: invalid signature"
      );

      const popFailure = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ signingKey: () => attesterEs256Jwk }),
      });
      expectInvalidClientAttestation(
        popFailure,
        "client attestation pop jwt signature verification failed with the client instance key (cnf.jwk)"
      );
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

    it("client_id OPTIONAL. The client is identified by the sub claim of the Client Attestation, so a request that omits the parameter authenticates.", async () => {
      // Section 7.5 example: POST /token with grant_type and scope only. RFC 7521 Section 4.2 says
      // the same for assertion authentication — "The client_id is unnecessary for client assertion
      // authentication because the client is identified by the subject of the assertion".
      const params = new URLSearchParams();
      params.append("grant_type", "client_credentials");
      params.append("scope", "account");

      const response = await post({
        url: serverConfig.tokenEndpoint,
        body: params,
        headers: {
          [ATTESTATION_HEADER]: createAttestationJwt(),
          [POP_HEADER]: createPopJwt(),
        },
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("access_token");
    });

    it("If the token request contains a client_id parameter as per [RFC6749] the Authorization Server MUST verify that the value of this parameter is the same as the client_id value in the sub claim of the Client Attestation.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ sub: "another-client" }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(
        response,
        "client attestation jwt sub claim must be the client_id of the client"
      );
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

    it("The client whose configuration is loaded and the client the sub claim names are the same client: a client_assertion naming another client does not split them.", async () => {
      // The server reads client_assertion's iss to decide which client configuration to load, and
      // does so without verifying the signature (RFC 7521 Section 4.2 identifies the client by the
      // assertion's subject, which the assertion's own verifier checks afterwards). If that value
      // and the value the Client Attestation is checked against came from two different
      // resolutions, an attestation the attacker legitimately holds would authenticate it as a
      // client it does not hold — here both clients share an Attester, so every signature checks
      // out and only the identity is wrong.
      const params = new URLSearchParams();
      params.append("grant_type", "client_credentials");
      params.append("scope", "account");
      params.append(
        "client_assertion",
        createJwtWithPrivateKey({
          payload: { iss: otherAttestedClient.clientId, sub: otherAttestedClient.clientId },
          privateKey: attesterEs256Jwk,
          algorithm: "ES256",
        })
      );
      params.append(
        "client_assertion_type",
        "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
      );

      const response = await post({
        url: serverConfig.tokenEndpoint,
        body: params,
        headers: {
          [ATTESTATION_HEADER]: createAttestationJwt(),
          [POP_HEADER]: createPopJwt(),
        },
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(401);
      expect(response.data).not.toHaveProperty("access_token");
    });

    it("The client whose configuration is loaded and the client the sub claim names are the same client: HTTP Basic naming another client does not split them.", async () => {
      // The introspection and revocation endpoints read the Basic credentials before the client_id
      // parameter, so the two must not be allowed to name different clients either.
      const tokenResponse = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt(),
      });
      expect(tokenResponse.status).toBe(200);

      const params = new URLSearchParams();
      params.append("token", tokenResponse.data.access_token);
      params.append("client_id", attestedClient.clientId);

      const basic = Buffer.from(`${otherAttestedClient.clientId}:unused`).toString("base64");
      const response = await post({
        url: serverConfig.tokenIntrospectionEndpoint,
        body: params,
        headers: {
          Authorization: `Basic ${basic}`,
          [ATTESTATION_HEADER]: createAttestationJwt(),
          [POP_HEADER]: createPopJwt(),
        },
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(400);
      expect(response.data).toHaveProperty("active", false);
    });

    it("Token Revocation endpoint rejects the request when the Client Attestation headers are absent, and the token stays valid.", async () => {
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
      });
      console.log(response.status, response.data);
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_client");

      // RFC 7009 Section 2.1 requires the client to authenticate, so an unauthenticated request
      // must not revoke: rejecting after the fact would still have destroyed the token.
      const introspection = await post({
        url: serverConfig.tokenIntrospectionEndpoint,
        body: params,
        headers: {
          [ATTESTATION_HEADER]: createAttestationJwt(),
          [POP_HEADER]: createPopJwt(),
        },
      });
      console.log(introspection.status, introspection.data);
      expect(introspection.status).toBe(200);
      expect(introspection.data).toHaveProperty("active", true);
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

  /**
   * Section 9 is the client's own declaration of what it signs with. idp-server has none of it:
   * the client configuration schema carries no client_attestation_* algorithm parameters, only
   * the idp-server specific trust source fields.
   *
   * The restrictions the section states are satisfied in substance elsewhere — the Authorization
   * Server metadata schema allows only asymmetric algorithms, so neither none nor a MAC can be
   * configured, and ClientAttestationJwtVerifier rejects alg: none outright. What is missing is
   * the client being able to narrow that further, which a profile building on Section 9 would
   * expect. Left as xit rather than removed so the gap stays visible (Issue #1892).
   */
  describe("9. Client Metadata (not implemented yet)", () => {
    xit("token_endpoint_auth_method: the Client indicates support by using the value attest_jwt_client_auth or attest_jwt_client_auth_dpop. (supported; the rest of this section is not)", async () => {});

    xit("client_attestation_signing_alg_values_supported: JSON array containing a list of the JWS [RFC7515] algorithms (alg values) supported for signing the Client Attestation JWT. The value none MUST NOT be present.", async () => {});

    xit("client_attestation_pop_signing_alg_values_supported: JSON array containing a list of the JWS [RFC7515] algorithms (alg values) supported for signing the Client Attestation PoP JWT. The values none and symmetric algorithms MUST NOT be present.", async () => {});

    xit("client_attestation_pop_methods_supported: the Proof of Possession methods the Client supports.", async () => {});
  });

  describe("10.2. Reuse of a Client Attestation JWT", () => {
    it("A Client Attestation JWT stays usable for its whole lifetime: only the PoP JWT is created per request.", async () => {
      // A fresh PoP each time, the same attestation both times. This is what lets a client avoid
      // a round trip to its Attester on every request.
      const attestationJwt = createAttestationJwt();

      const first = await requestTokenWithAttestation({
        attestationJwt,
        popJwt: createPopJwt(),
      });
      const second = await requestTokenWithAttestation({
        attestationJwt,
        popJwt: createPopJwt(),
      });

      expect(first.status).toBe(200);
      expect(second.status).toBe(200);
    }, 120000);
  });

  /**
   * Why client authentication is not enough here: several Client Instances of one application
   * share a client_id, so authenticating as the client says nothing about which instance is
   * refreshing. RFC 9449 Section 5 leaves confidential clients' refresh tokens unbound on the
   * reasoning that client authentication constrains the sender; that reasoning does not carry
   * over, which is why this section exists.
   */
  describe("10.3. Refresh token binding", () => {

    /** Instance A obtains a refresh token; the attestation names A's key in cnf. */
    const passwordGrantAs = async (instanceKey) => {
      const params = new URLSearchParams();
      params.append("grant_type", "password");
      params.append("username", serverConfig.oauth.username);
      params.append("password", serverConfig.oauth.password);
      params.append("scope", "openid account");
      params.append("client_id", refreshableClient.clientId);

      return await post({
        url: serverConfig.tokenEndpoint,
        body: params,
        headers: {
          [ATTESTATION_HEADER]: createAttestationJwt({
            sub: refreshableClient.clientId,
            cnf: () => ({ jwk: publicJwkOf(instanceKey) }),
          }),
          [POP_HEADER]: createPopJwt({ signingKey: () => instanceKey }),
        },
      });
    };

    const refreshAs = async (instanceKey, refreshToken) => {
      const params = new URLSearchParams();
      params.append("grant_type", "refresh_token");
      params.append("refresh_token", refreshToken);
      params.append("client_id", refreshableClient.clientId);

      return await post({
        url: serverConfig.tokenEndpoint,
        body: params,
        headers: {
          [ATTESTATION_HEADER]: createAttestationJwt({
            sub: refreshableClient.clientId,
            cnf: () => ({ jwk: publicJwkOf(instanceKey) }),
          }),
          [POP_HEADER]: createPopJwt({ signingKey: () => instanceKey }),
        },
      });
    };

    it("Authorization servers issuing a refresh token in response to a token request using the client attestation mechanism MUST bind the refresh token to the Client Instance and its associated public key. (the instance that obtained it can refresh)", async () => {
      const issued = await passwordGrantAs(instanceEs256Jwk);
      console.log("password grant with attestation:", issued.status);
      expect(issued.status).toBe(200);
      expect(issued.data).toHaveProperty("refresh_token");

      const refreshed = await refreshAs(instanceEs256Jwk, issued.data.refresh_token);
      console.log("refresh by the same instance:", refreshed.status);

      expect(refreshed.status).toBe(200);
      expect(refreshed.data).toHaveProperty("access_token");
    }, 120000);

    it("the Client Instance MUST use the same key that was present in the cnf claim. (another instance of the same client is refused)", async () => {
      // Both instances authenticate as the same client and pass every other check. Only the
      // binding separates them — this is the case the section exists for.
      const issued = await passwordGrantAs(instanceEs256Jwk);
      expect(issued.status).toBe(200);

      const refreshed = await refreshAs(
        otherInstanceEs256Jwk,
        issued.data.refresh_token
      );
      console.log(
        "refresh by another instance:",
        refreshed.status,
        JSON.stringify(refreshed.data)
      );

      expect(refreshed.status).toBe(400);
      expect(refreshed.data.error).toBe("invalid_grant");
      // The description is asserted because the status alone does not say which check refused:
      // a request that never reached the binding fails the same way.
      expect(refreshed.data.error_description).toBe(
        "the refresh token was issued to a different client instance of this client"
      );
    }, 120000);

    it("security: the binding survives rotation, so the refresh token handed back is bound to the same instance.", async () => {
      // The new token is issued from the credentials of whoever refreshed, and only the bound
      // instance gets that far. A rotation that dropped the binding would reopen the hole one
      // refresh later, which the first two tests would not notice.
      const issued = await passwordGrantAs(instanceEs256Jwk);
      expect(issued.status).toBe(200);

      const rotated = await refreshAs(instanceEs256Jwk, issued.data.refresh_token);
      expect(rotated.status).toBe(200);
      expect(rotated.data).toHaveProperty("refresh_token");

      const stolen = await refreshAs(otherInstanceEs256Jwk, rotated.data.refresh_token);
      console.log("another instance against the rotated token:", stolen.status);

      expect(stolen.status).toBe(400);
      expect(stolen.data.error).toBe("invalid_grant");
      expect(stolen.data.error_description).toBe(
        "the refresh token was issued to a different client instance of this client"
      );
    }, 120000);

  });

  describe("10.4. Binding of OAuth protocol artifacts (not implemented yet)", () => {

    xit("Authorization servers using Attestation-Based Client Authentication are RECOMMENDED to bind relevant protocol artifacts to the Client Instance and its associated public key where possible, and NOT just the client as specified in [RFC6749]. (the authorization_code as specified in Section 4.1 of [RFC6749])", async () => {});

    xit("Examples of these artifacts include but are not limited to: the auth_req_id as specified in section 7.3 [CIBA].", async () => {});
  });

  describe("10.5. Web Server Default Maximum HTTP Header Sizes (not implemented yet)", () => {
    xit("The two JWTs travel in HTTP header fields, so a deployment has to allow header sizes above the common defaults. Nothing here bounds or reports the size.", async () => {});
  });

  describe("10.6. Rotation of Client Instance Key (not implemented yet)", () => {
    xit("A Client Instance that rotates its key obtains a new Client Attestation JWT for the new key. Nothing here covers what happens to artifacts bound to the previous key.", async () => {});
  });

  describe("10.7. Replay Attack Detection (not implemented yet)", () => {
    xit("Implementation guidance for the detection 12.1 recommends. See 12.1 for the requirement itself.", async () => {});
  });

  /**
   * The linkability question. HAIP forbids a Wallet Attestation from carrying an identifier
   * specific to one instance, which is exactly what registered_instance_key sends as kid. See
   * Issue #1887.
   */
  describe("11.1. Client Instance Tracking Across Authorization Servers or Resource Servers (not implemented yet)", () => {
    xit("The same Client Attestation JWT presented to multiple Authorization Servers or Resource Servers allows them to correlate the Client Instance by colluding.", async () => {});
  });

  describe("12.1. Replay Attacks (not implemented yet)", () => {

    xit("An Authorization/Resource Server SHOULD implement measures to detect replay attacks by the Client Instance. (witnessed jti values of the Client Attestation PoP JWT for the validity time window)", async () => {});
  });
  describe("12.2. Client Attestation Protection", () => {

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
      expectInvalidClientAttestation(
        response,
        "client attestation jwt validation failed: The secret length must be at least 256 bits"
      );
    });
  });

  describe("13. Considerations for Profiling this specification (not implemented yet)", () => {
    xit("A profile of this specification MUST define how an Authorization Server or Resource Server determines that the profile applies to a given request.", async () => {});

    xit("All other requirements of this specification continue to apply unchanged unless the profile states otherwise. HAIP is the profile this matters for; see Issue #1887.", async () => {});
  });
});
