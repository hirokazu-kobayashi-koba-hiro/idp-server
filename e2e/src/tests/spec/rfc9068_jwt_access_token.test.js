/**
 * JSON Web Token (JWT) Profile for OAuth 2.0 Access Tokens (RFC 9068).
 *
 * This file is the compliance ledger for the profile: every requirement appears, whether or not it
 * is met, so that "covered", "known gap" and "missing from the ledger" stay distinguishable.
 *
 * @see https://www.rfc-editor.org/rfc/rfc9068.html
 */
import { beforeAll, afterAll, describe, expect, it, xit } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import { deletion, get, postWithJson } from "../../lib/http";
import { requestAuthorizations } from "../../oauth/request";
import { requestToken } from "../../api/oauthClient";
import { onboarding } from "../../api/managementClient";
import { generateECP256JWKSObject, generateRS256KeyPair } from "../../lib/jose";
import { adminServerConfig, backendUrl } from "../testConfig";

const decode = (jwt) => {
  const [header, payload] = jwt.split(".");
  return {
    header: JSON.parse(Buffer.from(header, "base64url").toString()),
    payload: JSON.parse(Buffer.from(payload, "base64url").toString()),
  };
};

describe("JSON Web Token (JWT) Profile for OAuth 2.0 Access Tokens (RFC 9068)", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let issuer;
  let username;
  let password;

  /** A token from the authorization code grant, the profile's own example of a resource owner grant. */
  let codeToken;
  /** A token from the client credentials grant, where no resource owner is involved. */
  let clientToken;
  /** Redeemed once, to check that the authentication information claims do not move. */
  let codeRefreshToken;

  const redirectUri = "https://app.example.com/callback";
  const DEFAULT_RESOURCE = "https://default.example.com";
  const ACCOUNT_RESOURCE = "https://api.example.com";
  const MANAGEMENT_RESOURCE = "https://admin.example.com";
  const ACR = "urn:mace:incommon:iap:silver";

  const tokenEndpoint = () => `${backendUrl}/${tenantId}/v1/tokens`;

  beforeAll(async () => {
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    clientSecret = uuidv4();
    issuer = `${backendUrl}/${tenantId}`;
    username = `rfc9068-${timestamp}@test.example.com`;
    password = `Rfc9068${timestamp}!`;

    // The token signing key is RSA so that the profile's RS256 requirement is exercised directly.
    const ecJwks = await generateECP256JWKSObject();
    const rsa = await generateRS256KeyPair();
    // generateRS256KeyPair returns the JWKs as `privateKey` / `publicKey`, not as `*Jwk`.
    const jwks = {
      keys: [
        ...ecJwks.keys,
        { ...rsa.privateKey, use: "sig", kid: "token_rsa" },
      ],
    };

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
    systemAccessToken = systemTokenResponse.data.access_token;

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `RFC 9068 Org ${timestamp}`,
          description: "E2E for #1824",
        },
        tenant: {
          id: tenantId,
          name: `RFC 9068 Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
        },
        authorization_server: {
          issuer,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: tokenEndpoint(),
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: JSON.stringify(jwks),
          scopes_supported: ["openid", "account", "management", "profile"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: [
            "authorization_code",
            "client_credentials",
            "password",
            "refresh_token",
          ],
          id_token_signing_alg_values_supported: ["ES256"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          claims_supported: ["sub"],
          extension: {
            access_token_type: "JWT",
            token_signed_key_id: "token_rsa",
            default_resource_indicator: DEFAULT_RESOURCE,
            scope_resource_mapping: {
              [ACCOUNT_RESOURCE]: ["openid", "account"],
              [MANAGEMENT_RESOURCE]: ["management"],
            },
          },
        },
        user: {
          sub: uuidv4(),
          provider_id: "idp-server",
          email: username,
          email_verified: true,
          raw_password: password,
        },
        client: {
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uris: ["https://app.example.com/callback"],
          grant_types: [
            "authorization_code",
            "client_credentials",
            "password",
            "refresh_token",
          ],
          response_types: ["code"],
          scope: "openid account management profile",
          client_name: "RFC 9068 Client",
          token_endpoint_auth_method: "client_secret_post",
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);

    const managementTokenResponse = await requestToken({
      endpoint: tokenEndpoint(),
      grantType: "password",
      username,
      password,
      scope: "management",
      clientId,
      clientSecret,
    });
    expect(managementTokenResponse.status).toBe(200);
    const managementAccessToken = managementTokenResponse.data.access_token;
    const managementHeaders = {
      Authorization: `Bearer ${managementAccessToken}`,
    };
    const managementUrl = `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`;

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
                properties: {
                  username: { type: "string" },
                  password: { type: "string" },
                },
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

    // acr_mapping_rules is what turns a performed method into an acr claim (AcrResolver), so the
    // policy is what makes the acr requirement of 2.2.1 observable at all.
    const authenticationPolicyResponse = await postWithJson({
      url: `${managementUrl}/authentication-policies`,
      headers: managementHeaders,
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "password_only",
            priority: 1,
            conditions: {},
            available_methods: ["password"],
            step_definitions: [
              { method: "password", order: 1, requires_user: false },
            ],
            acr_mapping_rules: { [ACR]: ["password"] },
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
    expect(authenticationPolicyResponse.status).toBe(201);

    const { authorizationResponse } = await requestAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      authorizeEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      denyEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/deny`,
      clientId,
      responseType: "code",
      state: `rfc9068-${timestamp}`,
      scope: "openid account",
      redirectUri,
      user: { username, password },
      interaction: async (id, user) => {
        const response = await postWithJson({
          url: `${backendUrl}/${tenantId}/v1/authorizations/${id}/password-authentication`,
          body: { username: user.username, password: user.password },
        });
        expect(response.status).toBe(200);
      },
    });
    expect(authorizationResponse.code).toBeDefined();

    const codeTokenResponse = await requestToken({
      endpoint: tokenEndpoint(),
      grantType: "authorization_code",
      code: authorizationResponse.code,
      redirectUri,
      clientId,
      clientSecret,
    });
    expect(codeTokenResponse.status).toBe(200);
    codeToken = decode(codeTokenResponse.data.access_token);
    codeRefreshToken = codeTokenResponse.data.refresh_token;

    const clientTokenResponse = await requestToken({
      endpoint: tokenEndpoint(),
      grantType: "client_credentials",
      scope: "account",
      clientId,
      clientSecret,
    });
    expect(clientTokenResponse.status).toBe(200);
    clientToken = decode(clientTokenResponse.data.access_token);

    console.log("code token   :", JSON.stringify(codeToken));
    console.log("client token :", JSON.stringify(clientToken));
  }, 120000);

  afterAll(async () => {
    if (systemAccessToken) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  describe("2.1.  Header", () => {
    it('JWT access tokens MUST be signed. ... JWT access tokens MUST NOT use "none" as the signing algorithm - RFC 9068 Section 2.1 (#1824)', async () => {
      expect(codeToken.header.alg).toBeDefined();
      expect(codeToken.header.alg).not.toBe("none");
    });

    it("Authorization servers and resource servers conforming to this specification MUST include RS256 (as defined in [RFC7518]) among their supported signature algorithms - RFC 9068 Section 2.1 (#1824)", async () => {
      // The tenant is configured with an RSA token signing key, so this asserts the capability
      // rather than a default: an authorization server that could only sign with EC would not
      // conform, and there is no metadata field that advertises access token signing algorithms.
      expect(codeToken.header.alg).toBe("RS256");
    });

    it('JWT access tokens MUST include this media type in the "typ" header parameter ... Therefore, the "typ" value used SHOULD be "at+jwt" - RFC 9068 Section 2.1 (#1824)', async () => {
      expect(codeToken.header.typ).toBe("at+jwt");
      expect(clientToken.header.typ).toBe("at+jwt");
    });
  });

  describe("2.2.  Data Structure", () => {
    it("iss REQUIRED - as defined in Section 4.1.1 of [RFC7519] - RFC 9068 Section 2.2 (#1824)", async () => {
      expect(codeToken.payload.iss).toBe(issuer);
      expect(clientToken.payload.iss).toBe(issuer);
    });

    it("exp REQUIRED - as defined in Section 4.1.4 of [RFC7519] - RFC 9068 Section 2.2 (#1824)", async () => {
      expect(typeof codeToken.payload.exp).toBe("number");
      expect(typeof clientToken.payload.exp).toBe("number");
    });

    it("aud REQUIRED - as defined in Section 4.1.3 of [RFC7519] - RFC 9068 Section 2.2 (#1824)", async () => {
      // The audience names the resource, never the client: the client is named by client_id.
      expect(codeToken.payload.aud).toBe(ACCOUNT_RESOURCE);
      expect(codeToken.payload.aud).not.toBe(clientId);
      expect(clientToken.payload.aud).toBe(ACCOUNT_RESOURCE);
    });

    it('sub REQUIRED ... In cases of access tokens obtained through grants where a resource owner is involved, such as the authorization code grant, the value of "sub" SHOULD correspond to the subject identifier of the resource owner - RFC 9068 Section 2.2 (#1824)', async () => {
      expect(codeToken.payload.sub).toBeDefined();
    });

    it('sub REQUIRED ... In cases of access tokens obtained through grants where no resource owner is involved, such as the client credentials grant, the value of "sub" SHOULD correspond to an identifier the authorization server uses to indicate the client application - RFC 9068 Section 2.2 (#1824)', async () => {
      expect(clientToken.payload.sub).toBe(clientId);
    });

    it("client_id REQUIRED - as defined in Section 4.3 of [RFC8693] - RFC 9068 Section 2.2 (#1824)", async () => {
      expect(codeToken.payload.client_id).toBe(clientId);
      expect(clientToken.payload.client_id).toBe(clientId);
    });

    it("iat REQUIRED - as defined in Section 4.1.6 of [RFC7519]. This claim identifies the time at which the JWT access token was issued - RFC 9068 Section 2.2 (#1824)", async () => {
      expect(typeof codeToken.payload.iat).toBe("number");
      expect(typeof clientToken.payload.iat).toBe("number");
    });

    it("jti REQUIRED - as defined in Section 4.1.7 of [RFC7519] - RFC 9068 Section 2.2 (#1824)", async () => {
      expect(codeToken.payload.jti).toBeDefined();
      expect(clientToken.payload.jti).toBeDefined();
      expect(codeToken.payload.jti).not.toBe(clientToken.payload.jti);
    });
  });

  describe("2.2.1.  Authentication Information Claims", () => {
    it("auth_time OPTIONAL - as defined in Section 2 of [OpenID.Core] - RFC 9068 Section 2.2.1 (#1824)", async () => {
      expect(typeof codeToken.payload.auth_time).toBe("number");
      expect(codeToken.payload.auth_time).toBeGreaterThan(0);
    });

    it("amr OPTIONAL - as defined in Section 2 of [OpenID.Core] - RFC 9068 Section 2.2.1 (#1824)", async () => {
      expect(Array.isArray(codeToken.payload.amr)).toBe(true);
      expect(codeToken.payload.amr).toContain("password");
    });

    it("acr OPTIONAL - as defined in Section 2 of [OpenID.Core] - RFC 9068 Section 2.2.1 (#1824)", async () => {
      // The value is resolved from the authentication policy's acr_mapping_rules against the
      // methods actually performed (AcrResolver), so this asserts the mapping took effect rather
      // than that some string is present.
      expect(codeToken.payload.acr).toBe(ACR);
    });

    it("The claims listed in this section MAY be issued in the context of authorization grants involving the resource owner and reflect the types and strength of authentication ... the authentication server enforced - RFC 9068 Section 2.2.1 (#1824)", async () => {
      // No resource owner is involved in the client credentials grant, so there is no
      // authentication for these claims to reflect.
      expect(clientToken.payload.auth_time).toBeUndefined();
      expect(clientToken.payload.amr).toBeUndefined();
      expect(clientToken.payload.acr).toBeUndefined();
    });

    it("Their values are fixed and remain the same across all access tokens that derive from a given authorization response, whether the access token was obtained directly in the response ... or after one or more token exchanges (e.g., obtaining a fresh access token using a refresh token ...) - RFC 9068 Section 2.2.1 (#1824)", async () => {
      const refreshed = await requestToken({
        endpoint: tokenEndpoint(),
        grantType: "refresh_token",
        refreshToken: codeRefreshToken,
        clientId,
        clientSecret,
      });
      expect(refreshed.status).toBe(200);

      const refreshedToken = decode(refreshed.data.access_token);

      // A fresh token, so iat moves; the authentication these claims describe did not happen again.
      expect(refreshedToken.payload.iat).toBeGreaterThanOrEqual(
        codeToken.payload.iat
      );
      expect(refreshedToken.payload.auth_time).toBe(
        codeToken.payload.auth_time
      );
      expect(refreshedToken.payload.amr).toEqual(codeToken.payload.amr);
      expect(refreshedToken.payload.acr).toBe(codeToken.payload.acr);
    });
  });

  describe("2.2.3.  Authorization Claims", () => {
    it('If an authorization request includes a scope parameter, the corresponding issued JWT access token SHOULD include a "scope" claim as defined in Section 4.2 of [RFC8693] - RFC 9068 Section 2.2.3 (#1824)', async () => {
      expect(codeToken.payload.scope).toContain("account");
    });

    it('All the individual scope strings in the "scope" claim MUST have meaning for the resources indicated in the "aud" claim - RFC 9068 Section 2.2.3 (#1824)', async () => {
      // The mapping is what makes this hold: a token carries only the scopes that belong to the
      // resource it names. A management scope resolves to another resource, so it cannot appear
      // alongside this audience.
      const configuredScopes = ["openid", "account"];

      expect(codeToken.payload.aud).toBe(ACCOUNT_RESOURCE);
      codeToken.payload.scope
        .split(" ")
        .forEach((scope) => expect(configuredScopes).toContain(scope));
    });
  });

  describe("3.  Requesting a JWT Access Token", () => {
    xit('If the request includes a "resource" parameter (as defined in [RFC8707]), the resulting JWT access token "aud" claim SHOULD have the same value as the "resource" parameter in the request - RFC 9068 Section 3 (#1824)', async () => {
      // resource is accepted but dropped: OAuthRequestValidator excludes it from validation and no
      // downstream type carries it. Tracked as #1826, which needs the parameter persisted so that
      // a refreshed token keeps the same audience.
    });

    it('If the request does not include a "resource" parameter, the authorization server MUST use a default resource indicator in the "aud" claim - RFC 9068 Section 3 (#1824)', async () => {
      // No request here carries resource, so every token has to reach an audience some other way.
      // "profile" is granted to the client but absent from scope_resource_mapping, so it proves the
      // fallback rather than the mapping: nothing points it at a resource, and aud is still present.
      const unmapped = await requestToken({
        endpoint: tokenEndpoint(),
        grantType: "client_credentials",
        scope: "profile",
        clientId,
        clientSecret,
      });
      expect(unmapped.status).toBe(200);

      expect(decode(unmapped.data.access_token).payload.aud).toBe(
        DEFAULT_RESOURCE
      );
      expect(clientToken.payload.aud).toBeDefined();
    });

    it('If a "scope" parameter is present in the request, the authorization server SHOULD use it to infer the value of the default resource indicator to be used in the "aud" claim - RFC 9068 Section 3 (#1824)', async () => {
      // The same client and the same grant, differing only in scope, reach different resources.
      const management = await requestToken({
        endpoint: tokenEndpoint(),
        grantType: "client_credentials",
        scope: "management",
        clientId,
        clientSecret,
      });
      expect(management.status).toBe(200);

      expect(decode(management.data.access_token).payload.aud).toBe(
        MANAGEMENT_RESOURCE
      );
      expect(clientToken.payload.aud).toBe(ACCOUNT_RESOURCE);
    });

    it('If the values in the "scope" parameter refer to different default resource indicator values, the authorization server SHOULD reject the request with "invalid_scope" - RFC 9068 Section 3 (#1824)', async () => {
      // Resolving one of them instead would mint a token whose audience names a resource that some
      // of its scopes were never meant for.
      const spanning = await requestToken({
        endpoint: tokenEndpoint(),
        grantType: "client_credentials",
        scope: "account management",
        clientId,
        clientSecret,
      });
      console.log(
        "spanning resources:",
        spanning.status,
        JSON.stringify(spanning.data)
      );

      expect(spanning.status).toBe(400);
      expect(spanning.data.error).toBe("invalid_scope");
    });
  });

  describe("4.  Validating JWT Access Tokens", () => {
    it('Authorization servers SHOULD use OAuth 2.0 Authorization Server Metadata [RFC8414] to advertise to resource servers their signing keys via "jwks_uri" and what "iss" claim value to expect via the "issuer" metadata value - RFC 9068 Section 4 (#1824)', async () => {
      const configuration = await get({
        url: `${backendUrl}/${tenantId}/.well-known/openid-configuration`,
      });
      expect(configuration.status).toBe(200);
      expect(configuration.data.issuer).toBe(codeToken.payload.iss);

      const jwks = await get({ url: configuration.data.jwks_uri });
      expect(jwks.status).toBe(200);
      expect(
        jwks.data.keys.some((key) => key.kid === codeToken.header.kid)
      ).toBe(true);
    });

    // The remaining requirements of this section bind the resource server, not the authorization
    // server. idp-server resolves its own access tokens by looking them up
    // (OAuthTokenQueryRepository), never by validating the JWT, so they are not exercised here.
  });

  describe("5.  Security Considerations", () => {
    it('To prevent cross-JWT confusion, authorization servers MUST use a distinct identifier as an "aud" claim value to uniquely identify access tokens issued by the same issuer for distinct resources - RFC 9068 Section 5 (#1824)', async () => {
      // Same issuer, same client, different resources: the audiences have to differ, or a token
      // minted for one resource is accepted by the other.
      const management = await requestToken({
        endpoint: tokenEndpoint(),
        grantType: "client_credentials",
        scope: "management",
        clientId,
        clientSecret,
      });
      expect(management.status).toBe(200);
      const managementToken = decode(management.data.access_token);

      expect(managementToken.payload.iss).toBe(codeToken.payload.iss);
      expect(managementToken.payload.aud).not.toBe(codeToken.payload.aud);
    });
  });
});
