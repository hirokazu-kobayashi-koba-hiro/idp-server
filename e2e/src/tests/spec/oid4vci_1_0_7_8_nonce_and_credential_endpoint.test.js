/**
 * OpenID for Verifiable Credential Issuance 1.0 (Final)
 *   7. Nonce Endpoint
 *   8. Credential Endpoint
 * https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#section-7
 *
 * The tenant issues one dc+sd-jwt credential configuration, requested by scope. The access token is
 * obtained with the password grant to keep the test about the Credential Endpoint; the HAIP flow
 * (PAR, DPoP, Client Attestation) is covered by the OIDF conformance plan
 * oid4vci-1_0-issuer-haip-test-plan (oidc-conformance-suite/oid4vci-haip). Requirements idp-server
 * does not support yet are listed with xit.
 */
import { beforeAll, describe, expect, it, xit } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import * as jose from "jose";
import crypto from "crypto";
import { post, postWithJson } from "../../lib/http";
import { onboarding } from "../../api/managementClient";
import { requestToken } from "../../api/oauthClient";
import { adminServerConfig, backendUrl } from "../testConfig";
import { generateECP256JWKS } from "../../lib/jose";
import { enablePasswordLogin } from "../../lib/clientInstance";

const CREDENTIAL_CONFIGURATION_ID = "identity_credential";
const VCT = "urn:example:identity_credential";

let tenantId;
let credentialIssuer;
let nonceEndpoint;
let credentialEndpoint;
let clientId;
let accessToken;
let userEmail;

const fetchNonce = async () => {
  const response = await post({ url: nonceEndpoint });
  expect(response.status).toBe(200);
  return response.data.c_nonce;
};

/** A jwt key proof (Appendix F.1) for a fresh holder key. */
const createProof = async ({ nonce, audience = credentialIssuer, holder = undefined } = {}) => {
  const { publicKey, privateKey } = holder ?? (await jose.generateKeyPair("ES256", { extractable: true }));
  const jwk = await jose.exportJWK(publicKey);
  const builder = new jose.SignJWT({ nonce })
    .setProtectedHeader({ alg: "ES256", typ: "openid4vci-proof+jwt", jwk })
    .setIssuer(clientId)
    .setAudience(audience)
    .setIssuedAt();
  return { proof: await builder.sign(privateKey), jwk };
};

const requestCredential = async (body, token = accessToken) =>
  await postWithJson({
    url: credentialEndpoint,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body,
  });

/** Splits an SD-JWT (RFC 9901) into its issuer-signed JWT and decoded disclosures. */
const parseSdJwt = (sdJwt) => {
  const [issuerSignedJwt, ...rest] = sdJwt.split("~");
  const disclosures = rest.filter((part) => part !== "");
  return {
    header: jose.decodeProtectedHeader(issuerSignedJwt),
    payload: jose.decodeJwt(issuerSignedJwt),
    disclosures: disclosures.map((disclosure) => ({
      digest: crypto.createHash("sha256").update(disclosure).digest("base64url"),
      value: JSON.parse(Buffer.from(disclosure, "base64url").toString()),
    })),
    endsWithSeparator: sdJwt.endsWith("~"),
  };
};

beforeAll(async () => {
  const adminToken = await requestToken({
    endpoint: adminServerConfig.tokenEndpoint,
    grantType: "password",
    username: adminServerConfig.oauth.username,
    password: adminServerConfig.oauth.password,
    scope: adminServerConfig.adminClient.scope,
    clientId: adminServerConfig.adminClient.clientId,
    clientSecret: adminServerConfig.adminClient.clientSecret,
  });
  expect(adminToken.status).toBe(200);
  const adminHeaders = { Authorization: `Bearer ${adminToken.data.access_token}` };

  const timestamp = Date.now();
  tenantId = uuidv4();
  credentialIssuer = `${backendUrl}/${tenantId}`;
  nonceEndpoint = `${credentialIssuer}/v1/credentials/nonce`;
  credentialEndpoint = `${credentialIssuer}/v1/credentials`;
  clientId = uuidv4();
  const clientSecret = `cs-${timestamp}`;
  userEmail = `holder-${timestamp}@oid4vci-credential.example.com`;
  const password = `VciPass_${timestamp}!`;

  const onboardingResponse = await onboarding({
    headers: adminHeaders,
    body: {
      organization: {
        id: uuidv4(),
        name: `OID4VCI Credential ${timestamp}`,
        description: "OID4VCI 1.0 Sections 7 and 8",
      },
      tenant: {
        id: tenantId,
        name: `OID4VCI Credential Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        identity_policy_config: { identity_unique_key_type: "EMAIL" },
        session_config: { cookie_name: `VCC_${tenantId.substring(0, 8)}`, use_secure_cookie: false },
        cors_config: { allow_origins: [backendUrl] },
      },
      authorization_server: {
        issuer: credentialIssuer,
        authorization_endpoint: `${credentialIssuer}/v1/authorizations`,
        token_endpoint: `${credentialIssuer}/v1/tokens`,
        jwks_uri: `${credentialIssuer}/v1/jwks`,
        jwks: await generateECP256JWKS(),
        grant_types_supported: ["authorization_code", "password"],
        token_endpoint_auth_methods_supported: ["client_secret_post"],
        scopes_supported: ["openid", "management", CREDENTIAL_CONFIGURATION_ID],
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["ES256"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        credential_issuer_metadata: {
          credential_endpoint: credentialEndpoint,
          nonce_endpoint: nonceEndpoint,
          credential_configurations_supported: {
            [CREDENTIAL_CONFIGURATION_ID]: {
              format: "dc+sd-jwt",
              scope: CREDENTIAL_CONFIGURATION_ID,
              vct: VCT,
              cryptographic_binding_methods_supported: ["jwk"],
              credential_signing_alg_values_supported: ["ES256"],
              proof_types_supported: { jwt: { proof_signing_alg_values_supported: ["ES256"] } },
            },
          },
        },
        credential_issuance: {
          signing_key_id: "signing_key_1",
          credentials: {
            [CREDENTIAL_CONFIGURATION_ID]: {
              expires_in: 86400,
              claims: [
                { name: "email", from: "$.email" },
                { name: "email_verified", from: "$.email_verified", selectively_disclosable: false },
              ],
            },
          },
        },
      },
      user: {
        sub: uuidv4(),
        provider_id: "idp-server",
        email: userEmail,
        email_verified: true,
        raw_password: password,
      },
      client: {
        client_id: clientId,
        client_secret: clientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "password"],
        scope: `openid management ${CREDENTIAL_CONFIGURATION_ID}`,
        client_name: "OID4VCI Credential Wallet",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    },
  });
  expect(onboardingResponse.status).toBe(201);
  await enablePasswordLogin({ tenantId, headers: adminHeaders });

  const tokenResponse = await requestToken({
    endpoint: `${credentialIssuer}/v1/tokens`,
    grantType: "password",
    username: userEmail,
    password,
    scope: CREDENTIAL_CONFIGURATION_ID,
    clientId,
    clientSecret,
  });
  expect(tokenResponse.status).toBe(200);
  accessToken = tokenResponse.data.access_token;
});

describe("OpenID for Verifiable Credential Issuance 1.0", () => {
  describe("7.  Nonce Endpoint", () => {
    describe("7.1.  Nonce Request", () => {
      it("A request for a nonce is made by sending an HTTP POST request to the URL provided in the nonce_endpoint Credential Issuer Metadata parameter. The Nonce Endpoint is not a protected resource, meaning the Wallet does not need to supply an access token to access it.", async () => {
        const response = await post({ url: nonceEndpoint });
        expect(response.status).toBe(200);
      });
    });

    describe("7.2.  Nonce Response", () => {
      it("c_nonce: REQUIRED. String containing a challenge to be used when creating a proof of possession of the key (see Section 8.2). ... New challenge values MUST be unpredictable.", async () => {
        const first = await fetchNonce();
        const second = await fetchNonce();
        expect(typeof first).toBe("string");
        expect(first).not.toBe(second);
        expect(Buffer.from(first, "base64url").length).toBeGreaterThanOrEqual(32);
      });

      it("Due to the temporal nature of the c_nonce value, the Credential Issuer MUST make the response uncacheable by adding a Cache-Control header field including the value no-store.", async () => {
        const response = await post({ url: nonceEndpoint });
        expect(response.headers["cache-control"]).toContain("no-store");
      });

      xit("The Credential Issuer MAY provide a DPoP nonce in an HTTP header as defined in Section 8.2 of [RFC9449].", async () => {});
    });
  });

  describe("8.  Credential Endpoint", () => {
    describe("8.2.  Credential Request", () => {
      it("credential_configuration_id: REQUIRED if a credential_identifiers parameter was not returned from the Token Response as part of the authorization_details parameter. ... The corresponding object in the credential_configurations_supported map MUST contain one of the value(s) used in the scope parameter in the Authorization Request.", async () => {
        const { proof } = await createProof({ nonce: await fetchNonce() });
        const response = await requestCredential({
          credential_configuration_id: CREDENTIAL_CONFIGURATION_ID,
          proofs: { jwt: [proof] },
        });
        expect(response.status).toBe(200);
      });

      it("credential_identifier: ... When this parameter is used, the credential_configuration_id MUST NOT be present.", async () => {
        const { proof } = await createProof({ nonce: await fetchNonce() });
        const response = await requestCredential({
          credential_identifier: "any",
          credential_configuration_id: CREDENTIAL_CONFIGURATION_ID,
          proofs: { jwt: [proof] },
        });
        expect(response.status).toBe(400);
        expect(response.data.error).toBe("invalid_credential_request");
      });

      it("The proofs parameter MUST be present if the proof_types_supported parameter is present in the credential_configurations_supported parameter of the Issuer metadata for the requested Credential.", async () => {
        const response = await requestCredential({ credential_configuration_id: CREDENTIAL_CONFIGURATION_ID });
        expect(response.status).toBe(400);
        expect(response.data.error).toBe("invalid_proof");
      });

      it("The proof(s) in the proofs parameter MUST incorporate the Credential Issuer Identifier (audience) and, if the Credential Issuer has a Nonce Endpoint, a c_nonce value", async () => {
        const wrongAudience = await createProof({ nonce: await fetchNonce(), audience: "https://another.example.com" });
        const noNonce = await createProof({ nonce: undefined });
        for (const { proof } of [wrongAudience, noNonce]) {
          const response = await requestCredential({
            credential_configuration_id: CREDENTIAL_CONFIGURATION_ID,
            proofs: { jwt: [proof] },
          });
          expect(response.status).toBe(400);
          expect(response.data.error).toBe("invalid_proof");
        }
      });

      it("Additional Credential Request parameters MAY be defined and used. The Credential Issuer MUST ignore any unrecognized parameters.", async () => {
        const { proof } = await createProof({ nonce: await fetchNonce() });
        const response = await requestCredential({
          credential_configuration_id: CREDENTIAL_CONFIGURATION_ID,
          proofs: { jwt: [proof] },
          unknown_parameter: "ignored",
        });
        expect(response.status).toBe(200);
      });

      xit("credential_response_encryption: OPTIONAL. Object containing information for encrypting the Credential Response.", async () => {});
    });

    describe("8.3.  Credential Response", () => {
      it("credentials: ... The number of elements in the credentials array matches the number of keys that the Wallet has provided via the proofs parameter ... Each key provided by the Wallet is used to bind to, at most, one Credential.", async () => {
        const holder = await jose.generateKeyPair("ES256", { extractable: true });
        const { proof, jwk } = await createProof({ nonce: await fetchNonce(), holder });
        const response = await requestCredential({
          credential_configuration_id: CREDENTIAL_CONFIGURATION_ID,
          proofs: { jwt: [proof] },
        });
        expect(response.status).toBe(200);
        expect(response.headers["content-type"]).toMatch(/^application\/json/);
        expect(response.data.credentials).toHaveLength(1);

        const credential = parseSdJwt(response.data.credentials[0].credential);
        expect(credential.endsWithSeparator).toBe(true);
        expect(credential.header.typ).toBe("dc+sd-jwt");
        expect(credential.payload).toMatchObject({ iss: credentialIssuer, vct: VCT, email_verified: true });
        expect(credential.payload.cnf.jwk).toMatchObject({ kty: jwk.kty, crv: jwk.crv, x: jwk.x, y: jwk.y });
        expect(credential.payload._sd_alg).toBe("sha-256");

        // email is selectively disclosable: only its digest is in the payload.
        expect(credential.payload).not.toHaveProperty("email");
        const email = credential.disclosures.find((d) => d.value[1] === "email");
        expect(email.value[2]).toBe(userEmail);
        expect(credential.payload._sd).toContain(email.digest);
      });

      xit("If the Credential Issuer is not able to immediately issue the requested credentials ... the Credential Issuer MUST return a response with a transaction_id parameter. In this case, the Credential Issuer MUST also use the HTTP status code 202 for the response.", async () => {});

      xit("notification_id: OPTIONAL. String identifying one or more Credentials issued in one Credential Response. It MUST be included in the Notification Request as defined in Section 11.1.", async () => {});

      describe("8.3.1.  Credential Error Response", () => {
        describe("8.3.1.1.  Authorization Errors", () => {
          it("If the Credential Request does not contain an Access Token that enables issuance of a requested Credential, the Credential Endpoint returns an authorization error response such as defined in Section 3 of [RFC6750].", async () => {
            const { proof } = await createProof({ nonce: await fetchNonce() });
            const body = { credential_configuration_id: CREDENTIAL_CONFIGURATION_ID, proofs: { jwt: [proof] } };

            const withoutToken = await requestCredential(body, null);
            expect(withoutToken.status).toBe(401);
            expect(withoutToken.headers["www-authenticate"]).toContain("invalid_token");

            const unknownToken = await requestCredential(body, "not-a-token");
            expect(unknownToken.status).toBe(401);
          });
        });

        describe("8.3.1.2.  Credential Request Errors", () => {
          it("invalid_credential_request: The Credential Request is missing a required parameter, includes an unsupported parameter or parameter value, repeats the same parameter, or is otherwise malformed.", async () => {
            const response = await requestCredential({ proofs: { jwt: [] } });
            expect(response.status).toBe(400);
            expect(response.data.error).toBe("invalid_credential_request");
          });

          it("unknown_credential_configuration: Requested Credential Configuration is unknown.", async () => {
            const { proof } = await createProof({ nonce: await fetchNonce() });
            const response = await requestCredential({ credential_configuration_id: "unknown", proofs: { jwt: [proof] } });
            expect(response.status).toBe(400);
            expect(response.data.error).toBe("unknown_credential_configuration");
          });

          it("unknown_credential_identifier: Requested Credential identifier is unknown.", async () => {
            const { proof } = await createProof({ nonce: await fetchNonce() });
            const response = await requestCredential({ credential_identifier: `unknown:${uuidv4()}`, proofs: { jwt: [proof] } });
            expect(response.status).toBe(400);
            expect(response.data.error).toBe("unknown_credential_identifier");
          });

          it("invalid_proof: The proofs parameter in the Credential Request is invalid: (1) if the field is missing, or (2) one of the provided key proofs is invalid, or (3) if at least one of the key proofs does not contain a c_nonce value", async () => {
            const { proof } = await createProof({ nonce: await fetchNonce() });
            const [header, payload] = proof.split(".");
            const forged = `${header}.${payload}.${Buffer.from("forged").toString("base64url")}`;
            const response = await requestCredential({
              credential_configuration_id: CREDENTIAL_CONFIGURATION_ID,
              proofs: { jwt: [forged] },
            });
            expect(response.status).toBe(400);
            expect(response.data.error).toBe("invalid_proof");
          });

          it("invalid_nonce: The proofs parameter in the Credential Request uses an invalid nonce: at least one of the key proofs contains an invalid c_nonce value. The wallet should retrieve a new c_nonce value", async () => {
            const unknown = await createProof({ nonce: "not-issued-by-this-issuer" });
            const unknownResponse = await requestCredential({
              credential_configuration_id: CREDENTIAL_CONFIGURATION_ID,
              proofs: { jwt: [unknown.proof] },
            });
            expect(unknownResponse.status).toBe(400);
            expect(unknownResponse.data.error).toBe("invalid_nonce");

            // A nonce is spent by the request that carries it: replaying the proof fails (Section 13.8).
            const { proof } = await createProof({ nonce: await fetchNonce() });
            const body = { credential_configuration_id: CREDENTIAL_CONFIGURATION_ID, proofs: { jwt: [proof] } };
            expect((await requestCredential(body)).status).toBe(200);
            const replayed = await requestCredential(body);
            expect(replayed.status).toBe(400);
            expect(replayed.data.error).toBe("invalid_nonce");
          });

          it("invalid_encryption_parameters: This error occurs when the encryption parameters in the Credential Request are either invalid or missing.", async () => {
            const { proof } = await createProof({ nonce: await fetchNonce() });
            const response = await requestCredential({
              credential_configuration_id: CREDENTIAL_CONFIGURATION_ID,
              proofs: { jwt: [proof] },
              credential_response_encryption: { enc: "A128GCM", jwk: { kty: "EC" } },
            });
            expect(response.status).toBe(400);
            expect(response.data.error).toBe("invalid_encryption_parameters");
          });

          xit("credential_request_denied: The Credential Request has not been accepted by the Credential Issuer.", async () => {});
        });
      });
    });
  });
});
