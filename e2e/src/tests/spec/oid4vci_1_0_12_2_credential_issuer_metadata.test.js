/**
 * OpenID for Verifiable Credential Issuance 1.0 (Final) - 12.2. Credential Issuer Metadata
 * https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#section-12.2
 *
 * The tenant's authorization server is also the Credential Issuer: its issuer
 * (https://host/{tenant}) is the Credential Issuer Identifier, and authorization_servers is
 * omitted. Requirements idp-server does not support yet are listed with xit.
 */
import { beforeAll, describe, expect, it, xit } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import { get } from "../../lib/http";
import { onboarding } from "../../api/managementClient";
import { requestToken } from "../../api/oauthClient";
import { adminServerConfig, backendUrl } from "../testConfig";
import { generateECP256JWKS } from "../../lib/jose";

const CREDENTIAL_CONFIGURATION_ID = "identity_credential";

let tenantId;
let credentialIssuer;
let metadataUrl;
let metadata;

/** 12.2.2: the well-known string goes between the host and the path of the identifier. */
const wellKnownUrlOf = (identifier, document) => {
  const url = new URL(identifier);
  return `${url.origin}/.well-known/${document}${url.pathname === "/" ? "" : url.pathname}`;
};

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

  const timestamp = Date.now();
  tenantId = uuidv4();
  credentialIssuer = `${backendUrl}/${tenantId}`;

  const onboardingResponse = await onboarding({
    headers: { Authorization: `Bearer ${tokenResponse.data.access_token}` },
    body: {
      organization: {
        id: uuidv4(),
        name: `OID4VCI Metadata ${timestamp}`,
        description: "OID4VCI 1.0 Section 12.2 Credential Issuer Metadata",
      },
      tenant: {
        id: tenantId,
        name: `OID4VCI Metadata Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        identity_policy_config: { identity_unique_key_type: "EMAIL" },
        session_config: { cookie_name: `VCI_${tenantId.substring(0, 8)}`, use_secure_cookie: false },
        cors_config: { allow_origins: [backendUrl] },
      },
      authorization_server: {
        issuer: credentialIssuer,
        authorization_endpoint: `${credentialIssuer}/v1/authorizations`,
        token_endpoint: `${credentialIssuer}/v1/tokens`,
        jwks_uri: `${credentialIssuer}/v1/jwks`,
        jwks: await generateECP256JWKS(),
        grant_types_supported: ["authorization_code", "password"],
        scopes_supported: ["openid", "management", CREDENTIAL_CONFIGURATION_ID],
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["ES256"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        credential_issuer_metadata: {
          credential_endpoint: `${credentialIssuer}/v1/credentials`,
          nonce_endpoint: `${credentialIssuer}/v1/credentials/nonce`,
          display: [{ name: "Example Issuer", locale: "en-US" }],
          credential_configurations_supported: {
            [CREDENTIAL_CONFIGURATION_ID]: {
              format: "dc+sd-jwt",
              scope: CREDENTIAL_CONFIGURATION_ID,
              vct: "urn:example:identity_credential",
              cryptographic_binding_methods_supported: ["jwk"],
              credential_signing_alg_values_supported: ["ES256"],
              proof_types_supported: { jwt: { proof_signing_alg_values_supported: ["ES256"] } },
              credential_metadata: {
                display: [{ name: "Identity Credential", locale: "en-US" }],
                claims: [{ path: ["given_name"] }, { path: ["family_name"] }],
              },
            },
          },
        },
      },
      user: {
        sub: uuidv4(),
        provider_id: "idp-server",
        email: `admin-${timestamp}@oid4vci-metadata.example.com`,
        email_verified: true,
        raw_password: `VciPass_${timestamp}!`,
      },
      client: {
        client_id: uuidv4(),
        client_secret: `cs-${timestamp}`,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "password"],
        scope: "openid management",
        client_name: "OID4VCI Metadata Management Client",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    },
  });
  expect(onboardingResponse.status).toBe(201);

  metadataUrl = wellKnownUrlOf(credentialIssuer, "openid-credential-issuer");
  const response = await get({ url: metadataUrl, headers: { Accept: "application/json" } });
  expect(response.status).toBe(200);
  metadata = response.data;
});

describe("OpenID for Verifiable Credential Issuance 1.0", () => {
  describe("12.2.  Credential Issuer Metadata", () => {
    describe("12.2.1.  Credential Issuer Identifier", () => {
      it("A Credential Issuer is identified by a case sensitive URL using the https scheme that contains scheme, host and, optionally, port number and path components, but no query or fragment components.", () => {
        const url = new URL(metadata.credential_issuer);
        expect(url.protocol).toBe("https:");
        expect(url.search).toBe("");
        expect(url.hash).toBe("");
      });
    });

    describe("12.2.2.  Credential Issuer Metadata Retrieval", () => {
      it("Credential Issuers publishing metadata MUST make a JSON document available at the path formed by inserting the string /.well-known/openid-credential-issuer into the Credential Issuer Identifier between the host component and the path component, if any.", () => {
        expect(metadataUrl).toBe(`${new URL(credentialIssuer).origin}/.well-known/openid-credential-issuer/${tenantId}`);
        expect(metadata).toHaveProperty("credential_issuer", credentialIssuer);
      });

      it("Communication with the Credential Issuer Metadata Endpoint MUST utilize TLS.", () => {
        expect(new URL(metadataUrl).protocol).toBe("https:");
      });

      it("The Credential Issuer MUST respond with HTTP Status Code 200 and return the Credential Issuer Metadata containing the parameters defined in Section 12.2.4 as either an unsigned JSON document using the media type application/json, or a signed JSON Web Token (JWT) containing the Credential Issuer Metadata in its payload using the media type application/jwt.", async () => {
        const response = await get({ url: metadataUrl, headers: { Accept: "application/json" } });
        expect(response.status).toBe(200);
        expect(response.headers["content-type"]).toMatch(/^application\/json/);
      });

      it("The Credential Issuer MUST support returning metadata in an unsigned form 'application/json' and MAY support returning it in a signed form 'application/jwt'. In all cases the Credential Issuer MUST indicate the media type of the returned Metadata using the HTTP Content-Type header.", async () => {
        // Signed metadata is not supported: a Wallet asking for it gets the unsigned form, labelled as such.
        const response = await get({ url: metadataUrl, headers: { Accept: "application/jwt" } });
        expect(response.status).toBe(200);
        expect(response.headers["content-type"]).toMatch(/^application\/json/);
        expect(response.data).toHaveProperty("credential_issuer", credentialIssuer);
      });

      xit("It is RECOMMENDED for Credential Issuers to respond with a Content-Type matching to the Wallet's requested Accept header when the requested content type is supported.", async () => {});

      xit("It is up to the Credential Issuer whether to: send a subset the metadata containing internationalized display data for one or all of the requested languages and indicate returned languages using the HTTP Content-Language Header, or ignore the Accept-Language Header and send all supported languages or any chosen subset.", async () => {});
    });

    describe("12.2.3.  Signed Metadata", () => {
      xit("alg: REQUIRED. A digital signature algorithm identifier such as per IANA \"JSON Web Signature and Encryption Algorithms\" registry [IANA.JOSE]. It MUST NOT be none or an identifier for a symmetric algorithm (MAC).", async () => {});
      xit("typ: REQUIRED. MUST be openidvci-issuer-metadata+jwt, which explicitly types the key proof JWT as recommended in Section 3.11 of [RFC8725].", async () => {});
      xit("sub: REQUIRED. String matching the Credential Issuer Identifier", async () => {});
      xit("iat: REQUIRED. Integer for the time at which the Credential Issuer Metadata was issued using the syntax defined in [RFC7519].", async () => {});
      xit("All metadata parameters used by the Credential Issuer MUST be added as top-level claims in the JWS payload.", async () => {});
    });

    describe("12.2.4.  Credential Issuer Metadata Parameters", () => {
      it("credential_issuer: REQUIRED. The Credential Issuer's identifier, as defined in Section 12.2.1. The value MUST be identical to the Credential Issuer's identifier value into which the well-known URI string was inserted to create the URL used to retrieve the metadata.", () => {
        expect(metadata.credential_issuer).toBe(credentialIssuer);
      });

      it("authorization_servers: OPTIONAL. ... If this parameter is omitted, the entity providing the Credential Issuer is also acting as the Authorization Server, i.e., the Credential Issuer's identifier is used to obtain the Authorization Server metadata. The actual OAuth 2.0 Authorization Server metadata is obtained from the oauth-authorization-server well-known location as defined in Section 3 of [RFC8414].", async () => {
        expect(metadata).not.toHaveProperty("authorization_servers");

        const response = await get({ url: wellKnownUrlOf(credentialIssuer, "oauth-authorization-server") });
        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("issuer", credentialIssuer);
      });

      it("credential_endpoint: REQUIRED. URL of the Credential Issuer's Credential Endpoint, as defined in Section 8.2. This URL MUST use the https scheme and MAY contain port, path, and query parameter components.", () => {
        expect(new URL(metadata.credential_endpoint).protocol).toBe("https:");
      });

      it("nonce_endpoint: OPTIONAL. URL of the Credential Issuer's Nonce Endpoint, as defined in Section 7. This URL MUST use the https scheme and MAY contain port, path, and query parameter components.", () => {
        expect(new URL(metadata.nonce_endpoint).protocol).toBe("https:");
      });

      xit("deferred_credential_endpoint: OPTIONAL. URL of the Credential Issuer's Deferred Credential Endpoint, as defined in Section 9. This URL MUST use the https scheme and MAY contain port, path, and query parameter components.", async () => {});

      xit("notification_endpoint: OPTIONAL. URL of the Credential Issuer's Notification Endpoint, as defined in Section 11. This URL MUST use the https scheme and MAY contain port, path, and query parameter components.", async () => {});

      xit("credential_request_encryption: OPTIONAL. Object containing information about whether the Credential Issuer supports encryption of the Credential Request on top of TLS.", async () => {});

      xit("credential_response_encryption: OPTIONAL. Object containing information about whether the Credential Issuer supports encryption of the Credential Response on top of TLS.", async () => {});

      xit("batch_credential_issuance: OPTIONAL. ... batch_size: REQUIRED. Integer value specifying the maximum array size for the proofs parameter in a Credential Request. It MUST be 2 or greater.", async () => {});

      it("display: OPTIONAL. A non-empty array of objects, where each object contains display properties of a Credential Issuer for a certain language.", () => {
        expect(metadata.display).toEqual([{ name: "Example Issuer", locale: "en-US" }]);
      });

      describe("credential_configurations_supported: REQUIRED.", () => {
        let configuration;
        beforeAll(() => {
          configuration = metadata.credential_configurations_supported[CREDENTIAL_CONFIGURATION_ID];
        });

        it("credential_configurations_supported: REQUIRED. Object that describes specifics of the Credential that the Credential Issuer supports issuance of. This object contains a list of name/value pairs, where each name is a unique identifier of the supported Credential being described.", () => {
          expect(Object.keys(metadata.credential_configurations_supported)).toEqual([CREDENTIAL_CONFIGURATION_ID]);
        });

        it("format: REQUIRED. A JSON string identifying the format of this Credential", () => {
          expect(configuration).toHaveProperty("format", "dc+sd-jwt");
        });

        it("scope: OPTIONAL. A JSON string identifying the scope value that this Credential Issuer supports for this particular Credential.", () => {
          expect(configuration).toHaveProperty("scope", CREDENTIAL_CONFIGURATION_ID);
        });

        it("credential_signing_alg_values_supported: OPTIONAL. A non-empty array of algorithm identifiers that identify the algorithms that the Issuer uses to sign the issued Credential.", () => {
          expect(configuration.credential_signing_alg_values_supported).toEqual(["ES256"]);
        });

        it("cryptographic_binding_methods_supported: OPTIONAL. A non-empty array of case sensitive strings that identify the representation of the cryptographic key material that the issued Credential is bound to, as defined in Section 8.1. It MUST be present when Cryptographic Key Binding is required for a Credential, and omitted otherwise.", () => {
          expect(configuration.cryptographic_binding_methods_supported).toEqual(["jwk"]);
        });

        it("proof_types_supported: OPTIONAL. Object that describes specifics of the key proof(s) that the Credential Issuer supports. It MUST be present if cryptographic_binding_methods_supported is present, and omitted otherwise.", () => {
          expect(configuration).toHaveProperty("proof_types_supported");
        });

        it("proof_signing_alg_values_supported: REQUIRED. A non-empty array of algorithm identifiers that the Issuer supports for this proof type.", () => {
          expect(configuration.proof_types_supported.jwt.proof_signing_alg_values_supported).toEqual(["ES256"]);
        });

        it("key_attestations_required: OPTIONAL. ... If the Credential Issuer does not require a key attestation, this parameter MUST NOT be present in the metadata.", () => {
          expect(configuration.proof_types_supported.jwt).not.toHaveProperty("key_attestations_required");
        });

        it("credential_metadata: OPTIONAL. Object containing information relevant to the usage and display of issued Credentials.", () => {
          expect(configuration.credential_metadata).toEqual({
            display: [{ name: "Identity Credential", locale: "en-US" }],
            claims: [{ path: ["given_name"] }, { path: ["family_name"] }],
          });
        });
      });

      it("Additional Credential Issuer metadata parameters MAY be defined and used.", () => {
        // What idp-server needs to build a credential is not metadata and is not published.
        expect(Object.keys(metadata).sort()).toEqual(
          ["credential_configurations_supported", "credential_endpoint", "credential_issuer", "display", "nonce_endpoint"],
        );
      });
    });
  });
});
