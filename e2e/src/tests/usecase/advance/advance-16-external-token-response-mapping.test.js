import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, postWithJson, putWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  postAuthentication,
  authorize,
  getUserinfo,
} from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl, mockApiBaseUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
  createBearerHeader,
} from "../../../lib/util";

/**
 * Advance Use Case: external-token authentication applies response.body_mapping_rules (#1696)
 *
 * Before the fix, ExternalTokenAuthenticationInteractor built a fixed
 * `{"user": user.toMinimalizedMap()}` envelope and ignored the configured
 * `response.body_mapping_rules`. This test drives the external-token interaction
 * end-to-end and asserts that fields derived from the external IdP responses
 * (here: the Mockoon /user/details payload) are surfaced in the interaction
 * response, while the legacy `user` envelope is retained.
 *
 * Prerequisites:
 * - idp-server running with the #1696 fix (docker compose up -d --build)
 * - Mock server (Mockoon) at host.docker.internal:4000 with
 *   POST /user/overview -> { id, email } and POST /user/details ->
 *   { birthdate, zoneinfo, locale, phone_number, role }
 */
describe("Advance Use Case: external-token response.body_mapping_rules (#1696)", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let mgmtAccessToken;
  let externalTokenConfigId;
  const redirectUri =
    "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

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
    systemAccessToken = tokenResponse.data.access_token;

    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    const adminUserId = uuidv4();
    clientId = uuidv4();
    clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}@ext-token.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;
    externalTokenConfigId = uuidv4();

    const onboardingResponse = await onboarding({
      body: {
        organization: {
          id: organizationId,
          name: `External Token Test Org ${timestamp}`,
          description: "E2E test for external-token response mapping (#1696)",
        },
        tenant: {
          id: tenantId,
          name: `External Token Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL_OR_EXTERNAL_USER_ID",
          },
          session_config: {
            cookie_name: `EXT_TOKEN_${organizationId.substring(0, 8)}`,
            use_secure_cookie: false,
          },
          cors_config: {
            allow_origins: [backendUrl],
          },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          grant_types_supported: [
            "authorization_code",
            "refresh_token",
            "password",
          ],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: [
            "openid",
            "profile",
            "email",
            "management",
            "org-management",
          ],
          claims_supported: [
            "sub",
            "iss",
            "auth_time",
            "acr",
            "name",
            "email",
            "email_verified",
            "birthdate",
            "phone_number",
          ],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: {
            access_token_type: "JWT",
          },
        },
        user: {
          sub: adminUserId,
          provider_id: "idp-server",
          name: "Admin User",
          email: adminEmail,
          email_verified: true,
          raw_password: adminPassword,
        },
        client: {
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uris: [redirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code", "refresh_token", "password"],
          scope: "openid profile email management org-management",
          client_name: "External Token Test Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    if (onboardingResponse.status !== 201) {
      console.error(
        "Onboarding failed:",
        JSON.stringify(onboardingResponse.data, null, 2)
      );
    }
    expect(onboardingResponse.status).toBe(201);

    const mgmtTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "management org-management",
      clientId,
      clientSecret,
    });
    expect(mgmtTokenResponse.status).toBe(200);
    mgmtAccessToken = mgmtTokenResponse.data.access_token;

    // external-token auth config: two chained calls (overview -> details), then
    // user_resolve plus the response.body_mapping_rules under test (#1696).
    const authConfigResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: externalTokenConfigId,
        type: "external-token",
        attributes: { service_name: "mock" },
        interactions: {
          "external-token": {
            request: {
              schema: {
                type: "object",
                properties: { access_token: { type: "string" } },
              },
            },
            execution: {
              function: "http_requests",
              http_requests: [
                {
                  url: `${mockApiBaseUrl}/user/overview`,
                  method: "POST",
                  header_mapping_rules: [
                    {
                      from: "$.request_body.access_token",
                      to: "x-token",
                      convertType: "string",
                      functions: [
                        { name: "format", args: { template: "Bearer {{value}}" } },
                      ],
                    },
                  ],
                  body_mapping_rules: [{ from: "$.request_body", to: "*" }],
                },
                {
                  url: `${mockApiBaseUrl}/user/details`,
                  method: "POST",
                  header_mapping_rules: [
                    { static_value: "test-client-id", to: "x-client-id" },
                  ],
                  body_mapping_rules: [
                    {
                      from: "$.execution_http_requests[0].response_body.id",
                      to: "user_id",
                    },
                  ],
                },
              ],
            },
            user_resolve: {
              user_mapping_rules: [
                { static_value: "mock-provider", to: "provider_id" },
                {
                  from: "$.execution_http_requests[0].response_body.id",
                  to: "external_user_id",
                },
                {
                  from: "$.execution_http_requests[0].response_body.email",
                  to: "email",
                },
                {
                  from: "$.execution_http_requests[1].response_body.birthdate",
                  to: "birthdate",
                },
                {
                  from: "$.execution_http_requests[1].response_body.phone_number",
                  to: "phone_number",
                },
              ],
            },
            response: {
              body_mapping_rules: [
                {
                  from: "$.execution_http_requests[1].response_body.role",
                  to: "role",
                },
                {
                  from: "$.execution_http_requests[1].response_body.birthdate",
                  to: "birthdate",
                },
                {
                  from: "$.execution_http_requests[0].response_body.email",
                  to: "external_email",
                },
              ],
            },
          },
        },
      },
    });
    if (authConfigResp.status !== 201) {
      console.error(
        "Auth config failed:",
        JSON.stringify(authConfigResp.data, null, 2)
      );
    }
    expect(authConfigResp.status).toBe(201);

    const policyResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "external_token_allow_registration",
            priority: 1,
            conditions: {},
            available_methods: ["external-token"],
            step_definitions: [
              {
                method: "external-token",
                order: 1,
                requires_user: false,
                allow_registration: true,
                user_identity_source: "email",
              },
            ],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.external-token.success_count",
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
    if (policyResp.status !== 201) {
      console.error(
        "Auth policy failed:",
        JSON.stringify(policyResp.data, null, 2)
      );
    }
    expect(policyResp.status).toBe(201);
  });

  afterAll(async () => {
    if (mgmtAccessToken) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
        headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      }).catch(() => {});
    }
    if (systemAccessToken) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  it("surfaces response.body_mapping_rules fields while keeping the user envelope", async () => {
    const state = `ext-token-${Date.now()}`;
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state,
      scope: "openid profile email",
      redirectUri,
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    const loginResp = await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/external-token`,
      id: authId,
      body: { access_token: "dummy-external-access-token" },
    });
    console.log(
      "external-token interaction response:",
      loginResp.status,
      JSON.stringify(loginResp.data, null, 2)
    );
    expect(loginResp.status).toBe(200);

    // #1696: fields mapped from the external responses are now present.
    expect(loginResp.data.role).toBe("Administrator");
    expect(loginResp.data.birthdate).toBe("2000-02-02");
    expect(loginResp.data.external_email).toBeDefined();

    // Backward compatibility: the minimalized user envelope is retained.
    expect(loginResp.data.user).toBeDefined();
    expect(loginResp.data.user.sub).toBeDefined();

    // The flow still completes to a token and userinfo.
    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId,
      body: {},
    });
    expect(authorizeResp.status).toBe(200);
    const result = convertToAuthorizationResponse(authorizeResp.data.redirect_uri);

    const tokenResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: result.code,
      redirectUri,
      clientId,
      clientSecret,
    });
    expect(tokenResp.status).toBe(200);
    expect(tokenResp.data.access_token).toBeDefined();

    const userinfoResp = await getUserinfo({
      endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
      authorizationHeader: createBearerHeader(tokenResp.data.access_token),
    });
    expect(userinfoResp.status).toBe(200);
    expect(userinfoResp.data.sub).toBeDefined();
  });

  it("returns only the user envelope when no response.body_mapping_rules are configured", async () => {
    // Update the existing config to one with no response.body_mapping_rules.
    const authConfigResp = await putWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations/${externalTokenConfigId}`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: externalTokenConfigId,
        type: "external-token",
        attributes: { service_name: "mock" },
        interactions: {
          "external-token": {
            request: {
              schema: {
                type: "object",
                properties: { access_token: { type: "string" } },
              },
            },
            execution: {
              function: "http_requests",
              http_requests: [
                {
                  url: `${mockApiBaseUrl}/user/overview`,
                  method: "POST",
                  header_mapping_rules: [
                    {
                      from: "$.request_body.access_token",
                      to: "x-token",
                      convertType: "string",
                      functions: [
                        { name: "format", args: { template: "Bearer {{value}}" } },
                      ],
                    },
                  ],
                  body_mapping_rules: [{ from: "$.request_body", to: "*" }],
                },
              ],
            },
            user_resolve: {
              user_mapping_rules: [
                { static_value: "mock-provider", to: "provider_id" },
                {
                  from: "$.execution_http_requests[0].response_body.id",
                  to: "external_user_id",
                },
                {
                  from: "$.execution_http_requests[0].response_body.email",
                  to: "email",
                },
              ],
            },
          },
        },
      },
    });
    expect([200, 201]).toContain(authConfigResp.status);

    const state = `ext-token-legacy-${Date.now()}`;
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state,
      scope: "openid profile email",
      redirectUri,
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    const loginResp = await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/external-token`,
      id: authId,
      body: { access_token: "dummy-external-access-token" },
    });
    expect(loginResp.status).toBe(200);
    // Unchanged behavior: only the user envelope is returned.
    expect(loginResp.data.user).toBeDefined();
    expect(Object.keys(loginResp.data)).toEqual(["user"]);
  });
});
