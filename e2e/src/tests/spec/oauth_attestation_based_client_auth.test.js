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
 *   attest_jwt_client_auth and the trusted attester JWKS (client_attestation_jwks)
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
      client_attestation_jwks: JSON.stringify({
        keys: [publicJwkOf(attesterEs256Jwk), publicJwkOf(attesterEs384Jwk)],
      }),
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
      expectInvalidClient(response);
    });

    it("sub REQUIRED. The sub (subject) claim MUST specify client_id value of the OAuth Client.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ sub: null }),
        popJwt: createPopJwt(),
      });
      expectInvalidClient(response);
    });

    it("exp REQUIRED. The Authorization Server MUST reject any JWT with an expiration time that has passed.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ exp: toEpocTime({ adjusted: -300 }) }),
        popJwt: createPopJwt(),
      });
      expectInvalidClient(response);
    });

    it("cnf REQUIRED. The cnf (confirmation) claim MUST specify a key conforming to [RFC7800] that is used by the Client Instance to generate the Client Attestation PoP JWT.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ cnf: null }),
        popJwt: createPopJwt(),
      });
      expectInvalidClient(response);
    });

    it("The key MUST be expressed using the \"jwk\" representation. (cnf without jwk is rejected)", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({
          cnf: { jkt: "NzbLsXh8uDCcd-6MNwXF4W_7noWXFZAfHkxZsRGC9Xs" },
        }),
        popJwt: createPopJwt(),
      });
      expectInvalidClient(response);
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
      expectInvalidClient(response);
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
      expectInvalidClient(response);
    });

    it("aud REQUIRED. When the JWT is presented to an Authorization Server, the [RFC8414] issuer identifier URL of the Authorization Server MUST be used.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ aud: "https://other-as.example.com" }),
      });
      expectInvalidClient(response);
    });

    it("jti REQUIRED. The jti (JWT identifier) claim MUST specify a unique identifier for the Client Attestation PoP.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ jti: null }),
      });
      expectInvalidClient(response);
    });

    it("iat REQUIRED. The iat (issued at) claim MUST specify the time at which the Client Attestation PoP was issued. (outside the acceptable window is rejected)", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ iat: toEpocTime({ adjusted: -600 }) }),
      });
      expectInvalidClient(response);
    });
  });

  describe("6. Challenges (not implemented yet)", () => {

    xit("6.1. The Authorization Server or Resource Server MAY offer a challenge endpoint for Clients to fetch Challenges. It MUST signal support by including the metadata entry challenge_endpoint.", async () => {});

    xit("6.1. The response contains attestation_challenge. The Authorization Server MUST make the response uncacheable by adding a Cache-Control header field including the value no-store.", async () => {});

    xit("6.2. The Authorization Server MAY provide a fresh Challenge with any HTTP response using the OAuth-Client-Attestation-Challenge HTTP header field.", async () => {});

    xit("challenge OPTIONAL. If the Authorization Server offers a challenge endpoint, the Client MUST retrieve a challenge and MUST use this challenge in the Client Attestation PoP JWT.", async () => {});
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
      expectInvalidClient(response);
    });

    it("4. The signature of the Client Attestation JWT verifies with the public key of a known and trusted Client Attester.", async () => {
      const untrustedAttesterJwk = await generateSigningJwk("ES256", "attester-es256");
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ signingKey: () => untrustedAttesterJwk }),
        popJwt: createPopJwt(),
      });
      expectInvalidClient(response);
    });

    it("5. The key contained in the cnf claim of the Client Attestation JWT is not a private key.", async () => {
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt({ cnf: () => ({ jwk: instanceEs256Jwk }) }),
        popJwt: createPopJwt(),
      });
      expectInvalidClient(response);
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
      expectInvalidClient(response);
    });

    it("4. The signature of the Client Attestation PoP JWT verifies with the public key contained in the cnf claim of the Client Attestation JWT.", async () => {
      const anotherInstanceJwk = await generateSigningJwk("ES256", "instance-es256");
      const response = await requestTokenWithAttestation({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ signingKey: () => anotherInstanceJwk }),
      });
      expectInvalidClient(response);
    });

    xit("5. If the server provided a challenge value to the client, the challenge claim is present in the Client Attestation PoP JWT and matches the server-provided challenge value.", async () => {});

    xit("8. If the Client received a challenge through the Authorization Server's challenge endpoint or within previous responses, it MUST match the challenge claim of the Client Attestation PoP JWT.", async () => {});

    xit("9. Depending on the security requirements of the deployment, additional checks to guarantee replay protection for the Client Attestation PoP JWT might need to be applied.", async () => {});
  });

  describe("7.3. DPoP Combined Mode (not implemented yet)", () => {

    xit("1. There is no OAuth-Client-Attestation-PoP HTTP request header field present in the request.", async () => {});

    xit("2. There is precisely one DPoP HTTP request header field present in the request.", async () => {});

    xit("3. Validate the DPoP proof in accordance with [RFC9449].", async () => {});

    xit("4. The public key in the jwk header parameter of the DPoP proof MUST be identical to the public key in the cnf claim of the Client Attestation JWT.", async () => {});

    xit("5. If the Client received a challenge, it MUST match the nonce payload claim of the DPoP proof.", async () => {});
  });

  describe("7.4. Errors (dedicated error codes are not implemented yet; invalid_client per RFC 6749 is returned)", () => {

    xit("use_attestation_challenge MUST be used when the Client Attestation PoP JWT is not using an expected server-provided challenge. When used this error code MUST be accompanied by the OAuth-Client-Attestation-Challenge HTTP header field parameter.", async () => {});

    xit("use_fresh_attestation MUST be used when the Client Attestation JWT is deemed to be not fresh enough to be acceptable by the server.", async () => {});

    xit("invalid_client_attestation MAY be used in addition to the more general invalid_client error code if the attestation or its proof of possession could not be successfully verified.", async () => {});
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
      expectInvalidClient(response);
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

  describe("11.1. Replay Attacks (not implemented yet)", () => {

    xit("An Authorization/Resource Server SHOULD implement measures to detect replay attacks by the Client Instance. (witnessed jti values of the Client Attestation PoP JWT for the validity time window)", async () => {});
  });
});
