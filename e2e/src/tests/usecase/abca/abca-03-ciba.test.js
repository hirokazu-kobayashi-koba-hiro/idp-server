/**
 * ABCA Use Case: attest_jwt_client_auth on the CIBA flow
 *
 * spec/oauth_attestation_based_client_auth.test.js checks the draft-11 requirements at the token
 * endpoint. This file runs the same checks where a CIBA client authenticates: the backchannel
 * authentication endpoint, and the token request of the CIBA grant. The sections follow draft-11
 * and carry the same checks as the spec file, so the two can be compared side by side.
 *
 * What is specific to CIBA:
 * - 7.5: without client_id, the client is the sub of the Client Attestation (draft-11 Section 7.5).
 *   The backchannel authentication request has to record that client as the token endpoint does,
 *   or the grant registered on approval and the notification on denial have no client to go by
 *   (Issue #1914)
 * - 10.3: the refresh token issued at the end of the CIBA flow is bound to the Client Instance
 *
 * The tenant is set up to match the test tenant the spec file uses: Client Attestations signed
 * with ES256 or RS256, a challenge endpoint, and Challenges offered but not required.
 *
 * @see https://www.ietf.org/archive/id/draft-ietf-oauth-attestation-based-client-auth-11.html
 */
import { beforeAll, describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import * as jose from "jose";
import { get, post, postWithJson } from "../../../lib/http";
import { onboarding } from "../../../api/managementClient";
import {
  getAuthenticationDeviceAuthenticationTransaction,
  postAuthenticationDeviceInteraction,
  requestToken,
} from "../../../api/oauthClient";
import { adminServerConfig, backendUrl } from "../../testConfig";
import {
  createJwt,
  createJwtWithPrivateKey,
  generateECP256JWKS,
  generateJti,
} from "../../../lib/jose";
import { toEpocTime } from "../../../lib/util";
import { createDPoPProof, generateDPoPKeyPair } from "../../../lib/dpop";

const ATTESTATION_TYP = "oauth-client-attestation+jwt";
const POP_TYP = "oauth-client-attestation-pop+jwt";
const ATTESTATION_HEADER = "OAuth-Client-Attestation";
const POP_HEADER = "OAuth-Client-Attestation-PoP";
const CHALLENGE_HEADER = "OAuth-Client-Attestation-Challenge";
const CIBA_GRANT_TYPE = "urn:openid:params:grant-type:ciba";

let organizationId;
let tenantId;
let issuer;
let backchannelEndpoint;
let tokenEndpoint;
let managementHeaders;
let attestedClientId;
/** A second client of the same Attester, standing in for a client the caller does not hold. */
let otherAttestedClientId;
let attesterEs256Jwk;
let attesterEs384Jwk;
let instanceEs256Jwk;
let otherInstanceEs256Jwk;
let instanceEs384Jwk;
let userSub;
let username;
let password;
let deviceId;

const generateSigningJwk = async (alg, kid) => {
  const { privateKey } = await jose.generateKeyPair(alg, { extractable: true });
  const jwk = await jose.exportJWK(privateKey);
  return { ...jwk, use: "sig", kid, alg };
};

const publicJwkOf = (privateJwk) => {
  const { d, ...publicJwk } = privateJwk;
  return publicJwk;
};

/** Client Attester role: issues the Client Attestation JWT for the instance key. */
const createAttestationJwt = ({
  typ = ATTESTATION_TYP,
  sub = () => attestedClientId,
  exp = toEpocTime({ adjusted: 300 }),
  cnf = () => ({ jwk: publicJwkOf(instanceEs256Jwk) }),
  extraClaims = {},
  signingKey = () => attesterEs256Jwk,
} = {}) => {
  const payload = { iss: "test-attester", exp, ...extraClaims };
  const subValue = typeof sub === "function" ? sub() : sub;
  if (subValue !== null) {
    payload.sub = subValue;
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

/** Client Instance role: signs the Client Attestation PoP JWT with the instance key. */
const createPopJwt = ({
  typ = POP_TYP,
  aud = () => issuer,
  jti = generateJti(),
  iat,
  extraClaims = {},
  signingKey = () => instanceEs256Jwk,
} = {}) => {
  const payload = { aud: typeof aud === "function" ? aud() : aud, ...extraClaims };
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

const fetchChallenge = async () => {
  const response = await postWithJson({
    url: `${issuer}/v1/client-attestation/challenges`,
    body: {},
  });
  expect(response.status).toBe(200);
  return response.data.attestation_challenge;
};

const attestationHeaders = ({ attestationJwt, popJwt }) => ({
  ...(attestationJwt !== undefined && { [ATTESTATION_HEADER]: attestationJwt }),
  ...(popJwt !== undefined && { [POP_HEADER]: popJwt }),
});

/**
 * The backchannel authentication request, authenticated by the Client Attestation.
 *
 * @param clientId the client_id parameter. Omitted by default: under attest_jwt_client_auth the
 *   client is the sub of the Client Attestation (Section 7.5).
 */
const requestBackchannel = async ({
  attestationJwt,
  popJwt,
  clientId,
  extraParams = {},
  extraHeaders = {},
}) => {
  const params = new URLSearchParams({
    scope: "openid account",
    login_hint: `sub:${userSub},idp:idp-server`,
    binding_message: "abca-03",
    ...extraParams,
  });
  if (clientId) {
    params.append("client_id", clientId);
  }
  return await post({
    url: backchannelEndpoint,
    headers: { ...attestationHeaders({ attestationJwt, popJwt }), ...extraHeaders },
    body: params.toString(),
  });
};

const requestBackchannelWithValidAttestation = async () =>
  await requestBackchannel({ attestationJwt: createAttestationJwt(), popJwt: createPopJwt() });

/** The token request of the CIBA grant, with no client_id parameter. */
const requestCibaToken = async ({ authReqId, attestationJwt, popJwt }) =>
  await requestToken({
    endpoint: tokenEndpoint,
    grantType: CIBA_GRANT_TYPE,
    authReqId,
    additionalHeaders: attestationHeaders({ attestationJwt, popJwt }),
  });

const findTransaction = async (authReqId) => {
  const response = await getAuthenticationDeviceAuthenticationTransaction({
    endpoint: `${issuer}/v1/authentication-devices/{id}/authentications`,
    deviceId,
    params: { "attributes.auth_req_id": authReqId },
  });
  expect(response.status).toBe(200);
  expect(response.data.list.length).toBe(1);
  return response.data.list[0];
};

const approveOnDevice = async (authReqId) => {
  const transaction = await findTransaction(authReqId);
  const interaction = await postAuthenticationDeviceInteraction({
    endpoint: `${issuer}/v1/authentications/{id}/`,
    flowType: transaction.flow,
    id: transaction.id,
    interactionType: "password-authentication",
    body: { username, password },
  });
  expect(interaction.status).toBe(200);
};

/**
 * Runs the CIBA flow with the given Client Instance key and returns the token response. Neither
 * request carries client_id.
 */
const completeCibaFlowAs = async (instanceKey) => {
  const credentials = () => ({
    attestationJwt: createAttestationJwt({ cnf: () => ({ jwk: publicJwkOf(instanceKey) }) }),
    popJwt: createPopJwt({ signingKey: () => instanceKey }),
  });
  const backchannel = await requestBackchannel(credentials());
  expect(backchannel.status).toBe(200);
  await approveOnDevice(backchannel.data.auth_req_id);
  return await requestCibaToken({ authReqId: backchannel.data.auth_req_id, ...credentials() });
};

const refreshAs = async (instanceKey, refreshToken) =>
  await requestToken({
    endpoint: tokenEndpoint,
    grantType: "refresh_token",
    refreshToken,
    additionalHeaders: attestationHeaders({
      attestationJwt: createAttestationJwt({ cnf: () => ({ jwk: publicJwkOf(instanceKey) }) }),
      popJwt: createPopJwt({ signingKey: () => instanceKey }),
    }),
  });

const expectAccepted = (response) => {
  expect(response.status).toBe(200);
  expect(response.data).toHaveProperty("auth_req_id");
};

const expectInvalidClient = (response) => {
  expect(response.status).toBe(401);
  expect(response.data).toHaveProperty("error", "invalid_client");
  expect(response.data).not.toHaveProperty("auth_req_id");
};

/** @param reason the server's stated cause, so that the check the test names is the one that refused */
const expectInvalidClientAttestation = (response, reason) => {
  expect(response.status).toBe(401);
  expect(response.data).toHaveProperty("error", "invalid_client_attestation");
  expect(response.data.error_description).toContain(`reason=${reason}`);
};

const expectUseAttestationChallenge = (response) => {
  expect(response.status).toBe(400);
  expect(response.data).toHaveProperty("error", "use_attestation_challenge");
  expect(response.headers[CHALLENGE_HEADER.toLowerCase()]).toBeDefined();
};

const expectUseFreshAttestation = (response) => {
  expect(response.status).toBe(401);
  expect(response.data).toHaveProperty("error", "use_fresh_attestation");
};

beforeAll(async () => {
  const systemTokenResponse = await requestToken({
    endpoint: adminServerConfig.tokenEndpoint,
    grantType: "password",
    username: adminServerConfig.oauth.username,
    password: adminServerConfig.oauth.password,
    scope: adminServerConfig.adminClient.scope,
    clientId: adminServerConfig.adminClient.clientId,
    clientSecret: adminServerConfig.adminClient.clientSecret,
  });
  expect(systemTokenResponse.status).toBe(200);

  const timestamp = Date.now();
  organizationId = uuidv4();
  tenantId = uuidv4();
  issuer = `${backendUrl}/${tenantId}`;
  backchannelEndpoint = `${issuer}/v1/backchannel/authentications`;
  tokenEndpoint = `${issuer}/v1/tokens`;
  attestedClientId = uuidv4();
  otherAttestedClientId = uuidv4();
  userSub = uuidv4();
  deviceId = uuidv4();
  username = `abca-03-${timestamp}@test.example.com`;
  password = `Abca03${timestamp}!`;
  attesterEs256Jwk = await generateSigningJwk("ES256", "attester-es256");
  attesterEs384Jwk = await generateSigningJwk("ES384", "attester-es384");
  instanceEs256Jwk = await generateSigningJwk("ES256", "instance-es256");
  otherInstanceEs256Jwk = await generateSigningJwk("ES256", "instance-es256-other");
  instanceEs384Jwk = await generateSigningJwk("ES384", "instance-es384");
  const managementClientId = uuidv4();
  const managementClientSecret = `secret-${uuidv4()}`;

  const onboardingResponse = await onboarding({
    headers: { Authorization: `Bearer ${systemTokenResponse.data.access_token}` },
    body: {
      organization: {
        id: organizationId,
        name: `ABCA CIBA ${timestamp}`,
        description: "ABCA use case: attest_jwt_client_auth on the CIBA flow",
      },
      tenant: {
        id: tenantId,
        name: `ABCA CIBA Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
      },
      authorization_server: {
        issuer,
        authorization_endpoint: `${issuer}/v1/authorizations`,
        token_endpoint: tokenEndpoint,
        userinfo_endpoint: `${issuer}/v1/userinfo`,
        jwks_uri: `${issuer}/v1/jwks`,
        jwks: await generateECP256JWKS(),
        backchannel_authentication_endpoint: backchannelEndpoint,
        backchannel_token_delivery_modes_supported: ["poll", "ping"],
        token_endpoint_auth_methods_supported: ["client_secret_post", "attest_jwt_client_auth"],
        // The same policy as the test tenant the spec file runs against.
        client_attestation_signing_alg_values_supported: ["ES256", "RS256"],
        client_attestation_pop_signing_alg_values_supported: ["ES256", "RS256"],
        challenge_endpoint: `${issuer}/v1/client-attestation/challenges`,
        grant_types_supported: ["authorization_code", "password", "refresh_token", CIBA_GRANT_TYPE],
        scopes_supported: ["openid", "account", "management"],
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["ES256"],
        claims_supported: ["sub"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        extension: { access_token_type: "JWT", client_attestation_challenge_required: false },
      },
      user: {
        sub: userSub,
        provider_id: "idp-server",
        email: username,
        email_verified: true,
        raw_password: password,
        authentication_devices: [{ id: deviceId, app_name: "ABCA CIBA Test App", priority: 1 }],
      },
      client: {
        client_id: managementClientId,
        client_secret: managementClientSecret,
        client_name: "ABCA CIBA Management Client",
        redirect_uris: ["https://app.example.com/callback"],
        grant_types: ["password"],
        response_types: ["code"],
        scope: "openid management",
        token_endpoint_auth_method: "client_secret_post",
      },
    },
  });
  expect(onboardingResponse.status).toBe(201);

  const managementTokenResponse = await requestToken({
    endpoint: tokenEndpoint,
    grantType: "password",
    username,
    password,
    scope: "management",
    clientId: managementClientId,
    clientSecret: managementClientSecret,
  });
  expect(managementTokenResponse.status).toBe(200);
  managementHeaders = { Authorization: `Bearer ${managementTokenResponse.data.access_token}` };
  const managementUrl = `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`;

  // Ping mode, so that a denial is delivered to the client as an error notification.
  for (const clientId of [attestedClientId, otherAttestedClientId]) {
    const clientResponse = await postWithJson({
      url: `${managementUrl}/clients`,
      headers: managementHeaders,
      body: {
        client_id: clientId,
        client_name: `ABCA CIBA Client ${clientId.substring(0, 8)}`,
        token_endpoint_auth_method: "attest_jwt_client_auth",
        grant_types: [CIBA_GRANT_TYPE, "refresh_token"],
        redirect_uris: ["https://app.example.com/callback"],
        response_types: ["code"],
        scope: "openid account",
        backchannel_token_delivery_mode: "ping",
        backchannel_client_notification_endpoint: "http://mockoon:4000/ciba/callback",
        extension: {
          client_attestation_trust_source: "attester_jwks",
          client_attestation_attester_jwks: JSON.stringify({
            keys: [publicJwkOf(attesterEs256Jwk), publicJwkOf(attesterEs384Jwk)],
          }),
        },
      },
    });
    expect(clientResponse.status).toBe(201);
  }

  const authenticationConfigResponse = await postWithJson({
    url: `${managementUrl}/authentication-configurations`,
    headers: managementHeaders,
    body: {
      id: uuidv4(),
      type: "password",
      attributes: {},
      metadata: { type: "password" },
      interactions: {
        "password-authentication": {
          request: {
            schema: {
              type: "object",
              properties: { username: { type: "string" }, password: { type: "string" } },
              required: ["username", "password"],
            },
          },
          execution: { function: "password_verification" },
          response: { body_mapping_rules: [] },
        },
      },
    },
  });
  expect(authenticationConfigResponse.status).toBe(201);

  const cibaPolicyResponse = await postWithJson({
    url: `${managementUrl}/authentication-policies`,
    headers: managementHeaders,
    body: {
      id: uuidv4(),
      flow: "ciba",
      enabled: true,
      policies: [
        {
          description: "password_only",
          priority: 1,
          conditions: {},
          available_methods: ["password"],
          step_definitions: [{ method: "password", order: 1, requires_user: false }],
          // Without failure conditions a denial on the device does not end the transaction, as in
          // the test tenant's CIBA policy.
          failure_conditions: {
            any_of: [
              [
                {
                  path: "$.password-authentication.failure_count",
                  type: "integer",
                  operation: "gte",
                  value: 5,
                },
              ],
            ],
          },
          success_conditions: {
            any_of: [
              [
                {
                  path: "$.password-authentication.success_count",
                  type: "integer",
                  operation: "gte",
                  value: 1,
                },
              ],
            ],
          },
        },
      ],
    },
  });
  expect(cibaPolicyResponse.status).toBe(201);
}, 120000);

describe("ABCA Use Case: attest_jwt_client_auth on the CIBA flow", () => {
  describe("4. Client Attestation JWT", () => {
    it("typ REQUIRED. The typ (JWT type) header MUST be oauth-client-attestation+jwt.", async () => {
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt({ typ: "JWT" }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(
        response,
        "client attestation jwt typ header must be 'oauth-client-attestation+jwt'"
      );
    });

    it("sub REQUIRED. The sub (subject) claim MUST specify the client_id value of the OAuth Client.", async () => {
      // With no client_id parameter the sub is also what names the client, so a request that
      // carries neither cannot be attributed to any client.
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt({ sub: null }),
        popJwt: createPopJwt(),
        clientId: attestedClientId,
      });
      expectInvalidClientAttestation(response, "client attestation jwt must contain sub claim");
    });

    it("exp REQUIRED. The Authorization Server MUST reject any JWT with an expiration time that has passed.", async () => {
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt({ exp: toEpocTime({ adjusted: -300 }) }),
        popJwt: createPopJwt(),
      });
      expectUseFreshAttestation(response);
    });

    it("cnf REQUIRED. The cnf (confirmation) claim MUST specify a key conforming to [RFC7800].", async () => {
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt({ cnf: null }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(response, "client attestation jwt must contain cnf claim");
    });

    it("The key MUST be expressed using the \"jwk\" representation. (cnf without jwk is rejected)", async () => {
      const response = await requestBackchannel({
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
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt({
          extraClaims: { wallet_name: "test-wallet", "urn:example:attestation_ext": true },
        }),
        popJwt: createPopJwt({ extraClaims: { "urn:example:pop_ext": "ignored" } }),
      });
      expectAccepted(response);
    });
  });

  describe("5.1. Client Attestation PoP JWT", () => {
    it("typ REQUIRED. The typ (JWT type) header MUST be oauth-client-attestation-pop+jwt.", async () => {
      const response = await requestBackchannel({
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
        payload: { aud: issuer, jti: generateJti() },
        secret: "shared-secret-value-for-hmac-signing-test",
        options: { algorithm: "HS256", header: { typ: POP_TYP } },
      });
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt(),
        popJwt: hmacPop,
      });
      expectInvalidClientAttestation(
        response,
        "client attestation pop jwt must be signed with an asymmetric algorithm"
      );
    });

    it("aud REQUIRED. When the JWT is presented to an Authorization Server, the [RFC8414] issuer identifier URL of the Authorization Server MUST be used.", async () => {
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ aud: "https://other-as.example.com" }),
      });
      expectInvalidClientAttestation(
        response,
        "client attestation pop jwt aud claim must be the issuer identifier URL of the authorization server"
      );
    });

    it("jti REQUIRED. The jti (JWT identifier) claim MUST specify a unique identifier for the Client Attestation PoP.", async () => {
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ jti: null }),
      });
      expectInvalidClientAttestation(response, "client attestation pop jwt must contain jti claim");
    });

    it("iat REQUIRED. The iat (issued at) claim MUST specify the time at which the Client Attestation PoP was issued. (outside the acceptable window is rejected)", async () => {
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ iat: toEpocTime({ adjusted: -600 }) }),
      });
      expectInvalidClientAttestation(
        response,
        "client attestation pop jwt iat claim is outside the acceptable time window"
      );
    });
  });

  describe("6. Challenges", () => {
    it("Whether a Challenge may be used in more than one Client Attestation PoP JWT is determined by the local policy. (one Challenge covers the backchannel request and its polling)", async () => {
      const challenge = await fetchChallenge();
      const credentials = () => ({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ extraClaims: { challenge } }),
      });
      const backchannel = await requestBackchannel(credentials());
      expectAccepted(backchannel);
      // The poll before approval authenticates with the same Challenge and is only refused as pending.
      const pending = await requestCibaToken({
        authReqId: backchannel.data.auth_req_id,
        ...credentials(),
      });
      expect(pending.status).toBe(400);
      expect(pending.data.error).toBe("authorization_pending");
    });

    it("If they are provided, the Client MUST include the Challenge in the proof of possession.", async () => {
      const challenge = await fetchChallenge();
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ extraClaims: { challenge } }),
      });
      expectAccepted(response);
    });

    it("A server that uses Challenges: MUST provide a Challenge when returning an use_attestation_challenge error defined in Section 7.4", async () => {
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ extraClaims: { challenge: "never-issued-by-this-server" } }),
      });
      expectUseAttestationChallenge(response);
    });
  });

  describe("6.1. Providing Challenges in Errors", () => {
    it("An Authorization Server that rejects the Challenge contained in the Client Attestation PoP JWT MUST respond with an HTTP 400 (Bad Request) status code and the error code use_attestation_challenge. The response MUST include a fresh Challenge in the OAuth-Client-Attestation-Challenge HTTP header field.", async () => {
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ extraClaims: { challenge: "never-issued-by-this-server" } }),
      });
      expectUseAttestationChallenge(response);
    });
  });

  describe("7. Verification and Processing", () => {
    it("If the request contains an OAuth-Client-Attestation header field and a DPoP proof, but no OAuth-Client-Attestation-PoP header field, and the Authorization Server does not support attest_jwt_client_auth_dpop, it MUST reject the request.", async () => {
      const dpopKey = await generateDPoPKeyPair();
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt({ cnf: () => ({ jwk: dpopKey.publicJwk }) }),
        extraHeaders: {
          DPoP: await createDPoPProof({
            privateKey: dpopKey.privateKey,
            publicJwk: dpopKey.publicJwk,
            htu: backchannelEndpoint,
          }),
        },
      });
      expectInvalidClient(response);
      expect(response.data.error_description).toContain(POP_HEADER);
    });
  });

  describe("7.1. Verification: Client Attestation JWT", () => {
    it("1. There is precisely one OAuth-Client-Attestation HTTP request header field containing a Client Attestation JWT. (absence is rejected)", async () => {
      const response = await requestBackchannel({ popJwt: createPopJwt(), clientId: attestedClientId });
      expectInvalidClient(response);
    });

    it("1. There is precisely one OAuth-Client-Attestation HTTP request header field. (multiple header fields are rejected)", async () => {
      const response = await requestBackchannel({
        attestationJwt: [createAttestationJwt(), createAttestationJwt()],
        popJwt: createPopJwt(),
        clientId: attestedClientId,
      });
      expectInvalidClient(response);
    });

    it("3. The alg JOSE Header Parameter contains a registered algorithm, is not none, is supported by the application, and is acceptable per local policy. (alg outside client_attestation_signing_alg_values_supported is rejected)", async () => {
      const response = await requestBackchannel({
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
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt({ signingKey: () => untrustedAttesterJwk }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(
        response,
        "client attestation jwt validation failed: invalid signature"
      );
    });

    it("5. The key contained in the cnf claim of the Client Attestation JWT is not a private key.", async () => {
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt({ cnf: () => ({ jwk: instanceEs256Jwk }) }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(
        response,
        "client attestation jwt cnf.jwk must not contain a private key"
      );
    });
  });

  describe("7.2. Verification: Client Attestation PoP JWT", () => {
    it("1. There is precisely one OAuth-Client-Attestation-PoP HTTP request header field containing a Client Attestation PoP JWT. (absence is rejected)", async () => {
      const response = await requestBackchannel({ attestationJwt: createAttestationJwt() });
      expectInvalidClient(response);
    });

    it("1. There is precisely one OAuth-Client-Attestation-PoP HTTP request header field. (multiple header fields are rejected)", async () => {
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt(),
        popJwt: [createPopJwt(), createPopJwt()],
      });
      expectInvalidClient(response);
    });

    it("3. The alg JOSE Header Parameter is acceptable per local policy. (alg outside client_attestation_pop_signing_alg_values_supported is rejected)", async () => {
      const response = await requestBackchannel({
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
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ signingKey: () => anotherInstanceJwk }),
      });
      expectInvalidClientAttestation(
        response,
        "client attestation pop jwt signature verification failed with the client instance key (cnf.jwk)"
      );
    });

    it("4. A Client Attestation JWT captured from a legitimate instance cannot be paired with a PoP signed by another key.", async () => {
      const attackerJwk = await generateSigningJwk("ES256", "attacker-key");
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ signingKey: () => attackerJwk }),
      });
      expectInvalidClientAttestation(
        response,
        "client attestation pop jwt signature verification failed with the client instance key (cnf.jwk)"
      );
    });

    it("5. The challenge claim of the Client Attestation PoP JWT MUST match a provided challenge. (challenge endpoint)", async () => {
      const challenge = await fetchChallenge();
      const accepted = await requestBackchannel({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ extraClaims: { challenge } }),
      });
      expectAccepted(accepted);
      const mismatched = await requestBackchannel({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ extraClaims: { challenge: `${challenge}-tampered` } }),
      });
      expectUseAttestationChallenge(mismatched);
    });

    it("5. The challenge claim of the Client Attestation PoP JWT MUST match a provided challenge. (previous responses)", async () => {
      const rejected = await requestBackchannel({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ extraClaims: { challenge: "never-issued-by-this-server" } }),
      });
      expectUseAttestationChallenge(rejected);
      const handedBack = rejected.headers[CHALLENGE_HEADER.toLowerCase()];
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ extraClaims: { challenge: handedBack } }),
      });
      expectAccepted(response);
    });
  });

  describe("7.4. Errors", () => {
    it("use_attestation_challenge MUST be used when the Client Attestation PoP JWT is not using an expected server-provided challenge, accompanied by a fresh Challenge in the OAuth-Client-Attestation-Challenge HTTP header field.", async () => {
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ extraClaims: { challenge: "never-issued-by-this-server" } }),
      });
      expectUseAttestationChallenge(response);
    });

    it("use_fresh_attestation MUST be used when the Client Attestation JWT is deemed to be not fresh enough to be acceptable by the server.", async () => {
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt({ exp: toEpocTime({ adjusted: -300 }) }),
        popJwt: createPopJwt(),
      });
      expectUseFreshAttestation(response);
    });

    it("invalid_client_attestation MAY be used if the attestation or its proof of possession could not be successfully verified.", async () => {
      const attestationFailure = await requestBackchannel({
        attestationJwt: createAttestationJwt({ signingKey: () => instanceEs256Jwk }),
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(
        attestationFailure,
        "client attestation jwt validation failed: invalid signature"
      );
      const popFailure = await requestBackchannel({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt({ signingKey: () => attesterEs256Jwk }),
      });
      expectInvalidClientAttestation(
        popFailure,
        "client attestation pop jwt signature verification failed with the client instance key (cnf.jwk)"
      );
    });

    it("Presenting no Client Attestation at all stays on the general invalid_client.", async () => {
      const response = await requestBackchannel({ popJwt: createPopJwt(), clientId: attestedClientId });
      expectInvalidClient(response);
    });
  });

  describe("7.5. Client Attestation as an OAuth Client Authentication", () => {
    it("authenticates the client at the backchannel authentication endpoint when Client Attestation JWT and Client Attestation PoP JWT are valid", async () => {
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt(),
        clientId: attestedClientId,
      });
      expectAccepted(response);
    });

    it("client_id OPTIONAL. The client is identified by the sub claim of the Client Attestation: a CIBA flow that never sends client_id is approved, issues a token, and records the grant under that client (Issue #1914).", async () => {
      const tokenResponse = await completeCibaFlowAs(instanceEs256Jwk);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");

      // The grant is recorded by the client the backchannel request recorded. Without the client,
      // it is not found under it.
      const grants = await get({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/grants`,
        headers: managementHeaders,
        params: { client_id: attestedClientId, user_id: userSub },
      });
      expect(grants.status).toBe(200);
      expect(grants.data.total_count).toBe(1);
    }, 120000);

    it("client_id OPTIONAL. A denial on the device in ping mode reaches the client the sub claim names, and the token request is refused (Issue #1914).", async () => {
      const backchannel = await requestBackchannelWithValidAttestation();
      expectAccepted(backchannel);

      // The denial notifies the client, which is looked up by the client the request recorded.
      const transaction = await findTransaction(backchannel.data.auth_req_id);
      const deny = await postAuthenticationDeviceInteraction({
        endpoint: `${issuer}/v1/authentications/{id}/`,
        flowType: transaction.flow,
        id: transaction.id,
        interactionType: "authentication-device-deny",
        body: {},
      });
      expect(deny.status).toBe(200);

      const tokenResponse = await requestCibaToken({
        authReqId: backchannel.data.auth_req_id,
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt(),
      });
      expect(tokenResponse.status).toBe(400);
      expect(tokenResponse.data.error).toBe("access_denied");
    }, 120000);

    it("If the request contains a client_id parameter the Authorization Server MUST verify that the value of this parameter is the same as the client_id value in the sub claim of the Client Attestation.", async () => {
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt({ sub: "another-client" }),
        popJwt: createPopJwt(),
        clientId: attestedClientId,
      });
      expectInvalidClientAttestation(
        response,
        "client attestation jwt sub claim must be the client_id of the client"
      );
    });

    it("authenticates the client at the token request of the CIBA grant, and rejects it when the Client Attestation headers are absent.", async () => {
      const backchannel = await requestBackchannelWithValidAttestation();
      expectAccepted(backchannel);
      const unauthenticated = await requestCibaToken({ authReqId: backchannel.data.auth_req_id });
      expect(unauthenticated.status).toBe(401);
      expect(unauthenticated.data).toHaveProperty("error", "invalid_client");
      const authenticated = await requestCibaToken({
        authReqId: backchannel.data.auth_req_id,
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt(),
      });
      expect(authenticated.status).toBe(400);
      expect(authenticated.data.error).toBe("authorization_pending");
    });

    it("The client whose configuration is loaded and the client the sub claim names are the same client: a client_assertion naming another client does not split them.", async () => {
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt(),
        extraParams: {
          client_assertion: createJwtWithPrivateKey({
            payload: { iss: otherAttestedClientId, sub: otherAttestedClientId },
            privateKey: attesterEs256Jwk,
            algorithm: "ES256",
          }),
          client_assertion_type: "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
        },
      });
      expect(response.status).toBe(401);
      expect(response.data).not.toHaveProperty("auth_req_id");
    });

    it("The client whose configuration is loaded and the client the sub claim names are the same client: HTTP Basic naming another client does not split them.", async () => {
      const basic = Buffer.from(`${otherAttestedClientId}:unused`).toString("base64");
      const response = await requestBackchannel({
        attestationJwt: createAttestationJwt(),
        popJwt: createPopJwt(),
        extraHeaders: { Authorization: `Basic ${basic}` },
      });
      expect(response.status).toBe(401);
      expect(response.data).not.toHaveProperty("auth_req_id");
    });
  });

  describe("10.2. Reuse of a Client Attestation JWT", () => {
    it("A Client Attestation JWT stays usable for its whole lifetime: only the PoP JWT is created per request.", async () => {
      const attestationJwt = createAttestationJwt();
      const first = await requestBackchannel({ attestationJwt, popJwt: createPopJwt() });
      const second = await requestBackchannel({ attestationJwt, popJwt: createPopJwt() });
      expectAccepted(first);
      expectAccepted(second);
    });
  });

  describe("10.3. Refresh token binding", () => {
    it("The refresh token issued at the end of the CIBA flow is bound to the Client Instance: the instance that obtained it can refresh.", async () => {
      const issued = await completeCibaFlowAs(instanceEs256Jwk);
      expect(issued.status).toBe(200);
      expect(issued.data).toHaveProperty("refresh_token");
      const refreshed = await refreshAs(instanceEs256Jwk, issued.data.refresh_token);
      expect(refreshed.status).toBe(200);
      expect(refreshed.data).toHaveProperty("access_token");
    }, 120000);

    it("The Client Instance MUST use the same key that was present in the cnf claim of the Client Attestation that was used when the refresh token was issued. (another instance of the same client is refused)", async () => {
      const issued = await completeCibaFlowAs(instanceEs256Jwk);
      expect(issued.status).toBe(200);
      const refreshed = await refreshAs(otherInstanceEs256Jwk, issued.data.refresh_token);
      expect(refreshed.status).toBe(400);
      expect(refreshed.data.error).toBe("invalid_grant");
      expect(refreshed.data.error_description).toBe(
        "the refresh token was issued to a different client instance of this client"
      );
    }, 120000);

    it("security: the binding survives rotation, so the refresh token handed back is bound to the same instance.", async () => {
      const issued = await completeCibaFlowAs(instanceEs256Jwk);
      expect(issued.status).toBe(200);
      const rotated = await refreshAs(instanceEs256Jwk, issued.data.refresh_token);
      expect(rotated.status).toBe(200);
      expect(rotated.data).toHaveProperty("refresh_token");
      const stolen = await refreshAs(otherInstanceEs256Jwk, rotated.data.refresh_token);
      expect(stolen.status).toBe(400);
      expect(stolen.data.error).toBe("invalid_grant");
      expect(stolen.data.error_description).toBe(
        "the refresh token was issued to a different client instance of this client"
      );
    }, 120000);
  });

  describe("12.2. Client Attestation Protection", () => {
    it("MACs are allowed by the specification to protect Client Attestation JWTs. (idp-server accepts only digital signatures)", async () => {
      const hmacAttestation = createJwt({
        payload: {
          iss: "test-attester",
          sub: attestedClientId,
          exp: toEpocTime({ adjusted: 300 }),
          cnf: { jwk: publicJwkOf(instanceEs256Jwk) },
        },
        secret: "shared-secret-value-for-hmac-signing-test",
        options: { algorithm: "HS256", header: { typ: ATTESTATION_TYP } },
      });
      const response = await requestBackchannel({
        attestationJwt: hmacAttestation,
        popJwt: createPopJwt(),
      });
      expectInvalidClientAttestation(
        response,
        "client attestation jwt validation failed: The secret length must be at least 256 bits"
      );
    });
  });
});
