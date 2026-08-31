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
import crypto from "crypto";
import { deletion, get, postWithJson } from "../../lib/http";
import { requestAuthorizations } from "../../oauth/request";
import {
  getAuthenticationDeviceAuthenticationTransaction,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken,
} from "../../api/oauthClient";
import { onboarding } from "../../api/managementClient";
import {
  createJwt,
  generateECP256JWKSObject,
  generateJti,
  generateRS256KeyPair,
} from "../../lib/jose";
import { toEpocTime } from "../../lib/util";
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
  let passwordValue;

  /** A token from the authorization code grant, the profile's own example of a resource owner grant. */
  let codeToken;
  /** A token from the client credentials grant, where no resource owner is involved. */
  let clientToken;
  /**
   * The authorization code grant's token after one refresh.
   *
   * <p>Redeemed once in setup rather than per test: refresh tokens rotate, so a second test
   * redeeming the same one would fail on the rotation rather than on what it set out to check.
   */
  let refreshedCodeToken;
  /** One token per grant that can issue one, so every requirement is checked against all of them. */
  let passwordToken;
  let jwtBearerToken;
  let cibaToken;

  const redirectUri = "https://app.example.com/callback";
  let userSub;
  let deviceId;
  let deviceSecret;
  const DEFAULT_RESOURCE = "https://default.example.com";
  let opaqueTenantId;
  let opaqueClientId;
  let opaqueClientSecret;
  const ACCOUNT_RESOURCE = "https://api.example.com";
  const MANAGEMENT_RESOURCE = "https://admin.example.com";
  const ACR = "urn:mace:incommon:iap:silver";

  const tokenEndpoint = () => `${backendUrl}/${tenantId}/v1/tokens`;

  /** A device signed assertion for the JWT bearer grant. */
  const deviceAssertion = () =>
    createJwt({
      payload: {
        iss: `device:${deviceId}`,
        sub: userSub,
        aud: issuer,
        jti: generateJti(),
        exp: toEpocTime({ adjusted: 3600 }),
        iat: toEpocTime({ adjusted: 0 }),
      },
      secret: deviceSecret,
      options: { algorithm: "HS256" },
    });

  /** Runs a CIBA flow to completion and returns the token response. */
  const completeCibaFlow = async (scope) => {
    const backchannel = await requestBackchannelAuthentications({
      endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
      clientId,
      clientSecret,
      scope,
      bindingMessage: "rfc9068",
      loginHint: `sub:${userSub},idp:idp-server`,
    });
    expect(backchannel.status).toBe(200);

    const transactions = await getAuthenticationDeviceAuthenticationTransaction(
      {
        endpoint: `${backendUrl}/${tenantId}/v1/authentication-devices/{id}/authentications`,
        deviceId,
        params: { "attributes.auth_req_id": backchannel.data.auth_req_id },
      }
    );
    expect(transactions.status).toBe(200);

    const transaction = transactions.data.list[0];
    const interaction = await postAuthenticationDeviceInteraction({
      endpoint: `${backendUrl}/${tenantId}/v1/authentications/{id}/`,
      flowType: transaction.flow,
      id: transaction.id,
      interactionType: "password-authentication",
      body: { username, password: passwordValue },
    });
    expect(interaction.status).toBe(200);

    return await requestToken({
      endpoint: tokenEndpoint(),
      grantType: "urn:openid:params:grant-type:ciba",
      authReqId: backchannel.data.auth_req_id,
      clientId,
      clientSecret,
    });
  };

  beforeAll(async () => {
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    clientSecret = uuidv4();
    issuer = `${backendUrl}/${tenantId}`;
    username = `rfc9068-${timestamp}@test.example.com`;
    userSub = uuidv4();
    deviceId = uuidv4();
    deviceSecret = crypto.randomBytes(32).toString("base64");
    passwordValue = `Rfc9068${timestamp}!`;

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
            "urn:openid:params:grant-type:ciba",
            "urn:ietf:params:oauth:grant-type:jwt-bearer",
          ],
          backchannel_authentication_endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
          backchannel_token_delivery_modes_supported: ["poll"],
          id_token_signing_alg_values_supported: ["ES256"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          claims_supported: ["sub"],
          extension: {
            access_token_type: "JWT",
            token_signed_key_id: "token_rsa",
            default_resource_indicator: DEFAULT_RESOURCE,
            scope_resource_mapping: {
              [ACCOUNT_RESOURCE]: ["account"],
              [MANAGEMENT_RESOURCE]: ["management"],
            },
          },
        },
        user: {
          sub: userSub,
          provider_id: "idp-server",
          email: username,
          authentication_devices: [
            {
              id: deviceId,
              app_name: "RFC 9068 Test App",
              priority: 1,
              credential_type: "jwt_bearer_symmetric",
              credential_id: uuidv4(),
              credential_payload: {
                secret_value: deviceSecret,
                algorithm: "HS256",
              },
            },
          ],
          email_verified: true,
          raw_password: passwordValue,
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
            "urn:openid:params:grant-type:ciba",
            "urn:ietf:params:oauth:grant-type:jwt-bearer",
          ],
          response_types: ["code"],
          scope: "openid account management profile",
          client_name: "RFC 9068 Client",
          token_endpoint_auth_method: "client_secret_post",
          extension: {
            // The JWT bearer grant resolves the assertion issuer against the client's federations.
            available_federations: [
              {
                issuer: "device",
                type: "device",
                subject_claim_mapping: "sub",
                jwt_bearer_grant_enabled: true,
              },
            ],
          },
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);

    const managementTokenResponse = await requestToken({
      endpoint: tokenEndpoint(),
      grantType: "password",
      username,
      password: passwordValue,
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

    // CIBA resolves the user's device and runs the same password interaction, so it needs its own
    // policy: policies are looked up by flow.
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
    expect(cibaPolicyResponse.status).toBe(201);

    const { authorizationResponse } = await requestAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      authorizeEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      denyEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/deny`,
      clientId,
      responseType: "code",
      state: `rfc9068-${timestamp}`,
      scope: "openid account",
      redirectUri,
      user: { username, password: passwordValue },
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
    const refreshedResponse = await requestToken({
      endpoint: tokenEndpoint(),
      grantType: "refresh_token",
      refreshToken: codeTokenResponse.data.refresh_token,
      clientId,
      clientSecret,
    });
    expect(refreshedResponse.status).toBe(200);
    refreshedCodeToken = decode(refreshedResponse.data.access_token);

    const clientTokenResponse = await requestToken({
      endpoint: tokenEndpoint(),
      grantType: "client_credentials",
      scope: "account",
      clientId,
      clientSecret,
    });
    expect(clientTokenResponse.status).toBe(200);
    clientToken = decode(clientTokenResponse.data.access_token);

    opaqueTenantId = uuidv4();
    opaqueClientId = uuidv4();
    opaqueClientSecret = uuidv4();
    const opaqueOnboarding = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: uuidv4(),
          name: `RFC 9068 Opaque Org ${timestamp}`,
          description: "E2E for #1824",
        },
        tenant: {
          id: opaqueTenantId,
          name: `RFC 9068 Opaque Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
        },
        authorization_server: {
          issuer: `${backendUrl}/${opaqueTenantId}`,
          authorization_endpoint: `${backendUrl}/${opaqueTenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${opaqueTenantId}/v1/tokens`,
          userinfo_endpoint: `${backendUrl}/${opaqueTenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${opaqueTenantId}/v1/jwks`,
          jwks: JSON.stringify(jwks),
          scopes_supported: ["account", "management"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["client_credentials"],
          id_token_signing_alg_values_supported: ["ES256"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          claims_supported: ["sub"],
          extension: {
            // Opaque tokens carry no claims, so the mapping below can never produce an audience.
            // It is configured anyway, because that is the combination that must still issue.
            access_token_type: "opaque",
            scope_resource_mapping: {
              [ACCOUNT_RESOURCE]: ["account"],
              [MANAGEMENT_RESOURCE]: ["management"],
            },
          },
        },
        user: {
          sub: uuidv4(),
          provider_id: "idp-server",
          email: `rfc9068-opaque-${timestamp}@test.example.com`,
          email_verified: true,
          raw_password: `Rfc9068Opaque${timestamp}!`,
        },
        client: {
          client_id: opaqueClientId,
          client_secret: opaqueClientSecret,
          redirect_uris: [redirectUri],
          grant_types: ["client_credentials"],
          response_types: ["code"],
          scope: "account management",
          client_name: "RFC 9068 Opaque Client",
          token_endpoint_auth_method: "client_secret_post",
        },
      },
    });
    expect(opaqueOnboarding.status).toBe(201);

    const passwordTokenResponse = await requestToken({
      endpoint: tokenEndpoint(),
      grantType: "password",
      username,
      password: passwordValue,
      scope: "management",
      clientId,
      clientSecret,
    });
    expect(passwordTokenResponse.status).toBe(200);
    passwordToken = decode(passwordTokenResponse.data.access_token);

    const jwtBearerTokenResponse = await requestToken({
      endpoint: tokenEndpoint(),
      grantType: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: deviceAssertion(),
      scope: "management",
      clientId,
      clientSecret,
    });
    expect(jwtBearerTokenResponse.status).toBe(200);
    jwtBearerToken = decode(jwtBearerTokenResponse.data.access_token);

    const cibaTokenResponse = await completeCibaFlow("openid management");
    expect(cibaTokenResponse.status).toBe(200);
    cibaToken = decode(cibaTokenResponse.data.access_token);

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
      expect(codeToken.header.alg).not.toBe("none");
      expect(clientToken.header.alg).not.toBe("none");
      expect(passwordToken.header.alg).not.toBe("none");
      expect(jwtBearerToken.header.alg).not.toBe("none");
      expect(cibaToken.header.alg).not.toBe("none");
    });

    it("Authorization servers and resource servers conforming to this specification MUST include RS256 (as defined in [RFC7518]) among their supported signature algorithms - RFC 9068 Section 2.1 (#1824)", async () => {
      // The tenant is configured with an RSA token signing key, so this asserts the capability
      // rather than a default: an authorization server that could only sign with EC would not
      // conform, and there is no metadata field that advertises access token signing algorithms.
      expect(codeToken.header.alg).toBe("RS256");
      expect(clientToken.header.alg).toBe("RS256");
      expect(passwordToken.header.alg).toBe("RS256");
      expect(jwtBearerToken.header.alg).toBe("RS256");
      expect(cibaToken.header.alg).toBe("RS256");
    });

    it('JWT access tokens MUST include this media type in the "typ" header parameter ... Therefore, the "typ" value used SHOULD be "at+jwt" - RFC 9068 Section 2.1 (#1824)', async () => {
      expect(codeToken.header.typ).toBe("at+jwt");
      expect(clientToken.header.typ).toBe("at+jwt");
      expect(passwordToken.header.typ).toBe("at+jwt");
      expect(jwtBearerToken.header.typ).toBe("at+jwt");
      expect(cibaToken.header.typ).toBe("at+jwt");
    });
  });

  describe("2.2.  Data Structure", () => {
    it("iss REQUIRED - as defined in Section 4.1.1 of [RFC7519] - RFC 9068 Section 2.2 (#1824)", async () => {
      expect(codeToken.payload.iss).toBe(issuer);
      expect(clientToken.payload.iss).toBe(issuer);
      expect(passwordToken.payload.iss).toBe(issuer);
      expect(jwtBearerToken.payload.iss).toBe(issuer);
      expect(cibaToken.payload.iss).toBe(issuer);
    });

    it("exp REQUIRED - as defined in Section 4.1.4 of [RFC7519] - RFC 9068 Section 2.2 (#1824)", async () => {
      expect(typeof codeToken.payload.exp).toBe("number");
      expect(typeof clientToken.payload.exp).toBe("number");
      expect(typeof passwordToken.payload.exp).toBe("number");
      expect(typeof jwtBearerToken.payload.exp).toBe("number");
      expect(typeof cibaToken.payload.exp).toBe("number");
    });

    it("aud REQUIRED - as defined in Section 4.1.3 of [RFC7519] - RFC 9068 Section 2.2 (#1824)", async () => {
      // The audience names the resource the scopes belong to, never the client: the client is named
      // by client_id. Each grant assembles its own scopes, so each reaches its resource separately.
      expect(codeToken.payload.aud).toBe(ACCOUNT_RESOURCE); // scope: openid account
      expect(clientToken.payload.aud).toBe(ACCOUNT_RESOURCE); // scope: account
      expect(passwordToken.payload.aud).toBe(MANAGEMENT_RESOURCE); // scope: management
      expect(jwtBearerToken.payload.aud).toBe(MANAGEMENT_RESOURCE); // scope: management
      expect(cibaToken.payload.aud).toBe(MANAGEMENT_RESOURCE); // scope: openid management

      expect(codeToken.payload.aud).not.toBe(clientId);
    });

    it('sub REQUIRED ... In cases of access tokens obtained through grants where a resource owner is involved, such as the authorization code grant, the value of "sub" SHOULD correspond to the subject identifier of the resource owner - RFC 9068 Section 2.2 (#1824)', async () => {
      expect(codeToken.payload.sub).toBe(userSub);
      expect(passwordToken.payload.sub).toBe(userSub);
      expect(jwtBearerToken.payload.sub).toBe(userSub);
      expect(cibaToken.payload.sub).toBe(userSub);
    });

    it('sub REQUIRED ... In cases of access tokens obtained through grants where no resource owner is involved, such as the client credentials grant, the value of "sub" SHOULD correspond to an identifier the authorization server uses to indicate the client application - RFC 9068 Section 2.2 (#1824)', async () => {
      expect(clientToken.payload.sub).toBe(clientId);
    });

    it("client_id REQUIRED - as defined in Section 4.3 of [RFC8693] - RFC 9068 Section 2.2 (#1824)", async () => {
      expect(codeToken.payload.client_id).toBe(clientId);
      expect(clientToken.payload.client_id).toBe(clientId);
      expect(passwordToken.payload.client_id).toBe(clientId);
      expect(jwtBearerToken.payload.client_id).toBe(clientId);
      expect(cibaToken.payload.client_id).toBe(clientId);
    });

    it("iat REQUIRED - as defined in Section 4.1.6 of [RFC7519]. This claim identifies the time at which the JWT access token was issued - RFC 9068 Section 2.2 (#1824)", async () => {
      expect(typeof codeToken.payload.iat).toBe("number");
      expect(typeof clientToken.payload.iat).toBe("number");
      expect(typeof passwordToken.payload.iat).toBe("number");
      expect(typeof jwtBearerToken.payload.iat).toBe("number");
      expect(typeof cibaToken.payload.iat).toBe("number");
    });

    it("jti REQUIRED - as defined in Section 4.1.7 of [RFC7519] - RFC 9068 Section 2.2 (#1824)", async () => {
      const identifiers = [
        codeToken.payload.jti,
        clientToken.payload.jti,
        passwordToken.payload.jti,
        jwtBearerToken.payload.jti,
        cibaToken.payload.jti,
      ];

      identifiers.forEach((jti) => expect(typeof jti).toBe("string"));
      // Distinct per token, which is what makes it usable for replay detection.
      expect(new Set(identifiers).size).toBe(identifiers.length);
    });

    it("aud REQUIRED - a refreshed token keeps the audience of the grant it derives from - RFC 9068 Section 2.2 (#1824)", async () => {
      expect(refreshedCodeToken.payload.aud).toBe(codeToken.payload.aud);
    });
  });

  describe("2.2.1.  Authentication Information Claims", () => {
    it("auth_time OPTIONAL - as defined in Section 2 of [OpenID.Core] - RFC 9068 Section 2.2.1 (#1824)", async () => {
      expect(typeof codeToken.payload.auth_time).toBe("number");
      expect(codeToken.payload.auth_time).toBeGreaterThan(0);
      expect(typeof passwordToken.payload.auth_time).toBe("number");
      expect(typeof cibaToken.payload.auth_time).toBe("number");
    });

    it("amr OPTIONAL - as defined in Section 2 of [OpenID.Core] - RFC 9068 Section 2.2.1 (#1824)", async () => {
      expect(codeToken.payload.amr).toContain("password");
      expect(passwordToken.payload.amr).toContain("password");
      expect(cibaToken.payload.amr).toContain("password");
    });

    it("acr OPTIONAL - as defined in Section 2 of [OpenID.Core] - RFC 9068 Section 2.2.1 (#1824)", async () => {
      // The value is resolved from the authentication policy's acr_mapping_rules against the
      // methods actually performed. The authorization code flow and CIBA each run their own
      // authentication transaction, resolved against the policy for their own flow.
      expect(codeToken.payload.acr).toBe(ACR);
      expect(cibaToken.payload.acr).toBe(ACR);
    });

    it("The claims listed in this section MAY be issued in the context of authorization grants involving the resource owner and reflect the types and strength of authentication ... the authentication server enforced - RFC 9068 Section 2.2.1 (#1824)", async () => {
      // No resource owner is involved in the client credentials grant, so there is no
      // authentication for these claims to reflect.
      expect(clientToken.payload.auth_time).toBeUndefined();
      expect(clientToken.payload.amr).toBeUndefined();
      expect(clientToken.payload.acr).toBeUndefined();
    });

    it("Their values are fixed and remain the same across all access tokens that derive from a given authorization response, whether the access token was obtained directly in the response ... or after one or more token exchanges (e.g., obtaining a fresh access token using a refresh token ...) - RFC 9068 Section 2.2.1 (#1824)", async () => {
      // A fresh token, so iat moves; the authentication these claims describe did not happen again.
      expect(refreshedCodeToken.payload.iat).toBeGreaterThanOrEqual(
        codeToken.payload.iat
      );
      expect(refreshedCodeToken.payload.auth_time).toBe(
        codeToken.payload.auth_time
      );
      expect(refreshedCodeToken.payload.amr).toEqual(codeToken.payload.amr);
      expect(refreshedCodeToken.payload.acr).toBe(codeToken.payload.acr);
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
      // openid is deliberately unmapped: it accompanies every OpenID Connect request, so binding
      // it to a resource would make that resource a party to all of them.
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

    it('If the values in the "scope" parameter refer to different default resource indicator values, the authorization server SHOULD reject the request with "invalid_scope" - client credentials grant - RFC 9068 Section 3 (#1824)', async () => {
      // Refused where the scope is decided. This grant carries its own scope, so that is the token
      // request, and the error takes the form that endpoint uses.
      const spanning = await requestToken({
        endpoint: tokenEndpoint(),
        grantType: "client_credentials",
        scope: "account management",
        clientId,
        clientSecret,
      });
      console.log(
        "spanning, client credentials:",
        spanning.status,
        JSON.stringify(spanning.data)
      );

      expect(spanning.status).toBe(400);
      expect(spanning.data.error).toBe("invalid_scope");
    });

    it('If the values in the "scope" parameter refer to different default resource indicator values, the authorization server SHOULD reject the request with "invalid_scope" - resource owner password credentials grant - RFC 9068 Section 3 (#1824)', async () => {
      const spanning = await requestToken({
        endpoint: tokenEndpoint(),
        grantType: "password",
        username,
        password: passwordValue,
        scope: "account management",
        clientId,
        clientSecret,
      });
      console.log(
        "spanning, password:",
        spanning.status,
        JSON.stringify(spanning.data)
      );

      expect(spanning.status).toBe(400);
      expect(spanning.data.error).toBe("invalid_scope");
    });

    it('If the values in the "scope" parameter refer to different default resource indicator values, the authorization server SHOULD reject the request with "invalid_scope" - JWT bearer assertion grant - RFC 9068 Section 3 (#1824)', async () => {
      // The assertion identifies the user, but the scope still comes from the token request, so
      // this grant decides its scope in the same place the other two do.
      const assertion = createJwt({
        payload: {
          iss: `device:${deviceId}`,
          sub: userSub,
          aud: issuer,
          jti: generateJti(),
          exp: toEpocTime({ adjusted: 3600 }),
          iat: toEpocTime({ adjusted: 0 }),
        },
        secret: deviceSecret,
        options: { algorithm: "HS256" },
      });

      const spanning = await requestToken({
        endpoint: tokenEndpoint(),
        grantType: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion,
        scope: "account management",
        clientId,
        clientSecret,
      });
      console.log(
        "spanning, jwt bearer:",
        spanning.status,
        JSON.stringify(spanning.data)
      );

      expect(spanning.status).toBe(400);
      expect(spanning.data.error).toBe("invalid_scope");
    });

    it('If the values in the "scope" parameter refer to different default resource indicator values, the authorization server SHOULD reject the request with "invalid_scope" - CIBA backchannel authentication request - RFC 9068 Section 3 (#1824)', async () => {
      // CIBA decides its scope at the backchannel authentication request, which is that flow's
      // counterpart of the authorization request. The error is a response body because there is no
      // redirect URI to send one to.
      const spanning = await requestBackchannelAuthentications({
        endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
        clientId,
        clientSecret,
        scope: "openid account management",
        bindingMessage: "rfc9068",
        loginHint: `sub:${userSub},idp:idp-server`,
      });
      console.log(
        "spanning, ciba:",
        spanning.status,
        JSON.stringify(spanning.data)
      );

      expect(spanning.status).toBe(400);
      expect(spanning.data.error).toBe("invalid_scope");
    });

    it("rejects an authorization request whose scopes span resources, where the scope is decided - RFC 9068 Section 3 / RFC 6749 Section 4.1.2.1 (#1824)", async () => {
      // Refusing at the token endpoint would fail a grant the client had already been told it had,
      // and invalid_scope at the authorization endpoint is a redirect rather than a response body.
      const response = await requestAuthorizations({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        authorizeEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
        denyEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/deny`,
        clientId,
        responseType: "code",
        state: `spanning-${Date.now()}`,
        scope: "account management",
        redirectUri,
        user: { username, password: passwordValue },
      });
      console.log(
        "spanning at authorization endpoint:",
        JSON.stringify(response.authorizationResponse)
      );

      expect(response.authorizationResponse.error).toBe("invalid_scope");
      expect(response.authorizationResponse.code).toBeFalsy();
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

  describe("Applicability", () => {
    it("issues an opaque access token, which carries no audience because it carries no claims", async () => {
      // The profile applies to JWT access tokens, so the claims this file asserts do not exist on
      // an opaque token. Reporting the audience through introspection is #1826.
      const response = await requestToken({
        endpoint: `${backendUrl}/${opaqueTenantId}/v1/tokens`,
        grantType: "client_credentials",
        scope: "account",
        clientId: opaqueClientId,
        clientSecret: opaqueClientSecret,
      });
      expect(response.status).toBe(200);

      expect(response.data.access_token.split(".").length).toBe(1);
    });

    it("applies the configured resources to an opaque access token as well", async () => {
      // Only the audience is format dependent. A tenant that has declared which scopes belong to
      // which resource has that declaration applied either way, rather than having it ignored for
      // the format that cannot carry the claim.
      const response = await requestToken({
        endpoint: `${backendUrl}/${opaqueTenantId}/v1/tokens`,
        grantType: "client_credentials",
        scope: "account management",
        clientId: opaqueClientId,
        clientSecret: opaqueClientSecret,
      });
      console.log(
        "opaque spanning:",
        response.status,
        JSON.stringify(response.data)
      );

      expect(response.status).toBe(400);
      expect(response.data.error).toBe("invalid_scope");
    });
  });
});
