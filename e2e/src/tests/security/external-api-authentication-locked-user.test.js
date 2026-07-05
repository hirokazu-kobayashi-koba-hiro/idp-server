import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, postWithJson, patchWithJson } from "../../lib/http";
import {
  requestToken,
  getAuthorizations,
  authorize,
  getUserinfo,
} from "../../api/oauthClient";
import { generateECP256JWKS } from "../../lib/jose";
import { adminServerConfig, backendUrl, mockApiBaseUrl } from "../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
  createBearerHeader,
} from "../../lib/util";

/**
 * Security: External API Authentication denies non-active users
 *
 * Verifies that AuthenticationUserStatusGuard (#1377) applies to the
 * external-api-authentication interactor: a user whose status is LOCKED
 * must be denied (403 access_denied) even when the external API itself
 * authenticates the credential successfully.
 *
 * Flow:
 * 1. Authenticate via password_verify interaction and complete the flow
 *    (user is provisioned from the external API response)
 * 2. Change the user status to LOCKED via Management API
 * 3. Re-authenticate with the same (valid) credentials
 *    -> interaction must be rejected with 403 access_denied
 */
describe("Security: External API Authentication denies non-active users", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let mgmtAccessToken;
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
    const adminEmail = `admin-${timestamp}@ext-api-lock.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: {
        organization: {
          id: organizationId,
          name: `External API Auth Locked User Test Org ${timestamp}`,
          description: "E2E test for external API auth non-active user denial",
        },
        tenant: {
          id: tenantId,
          name: `External API Auth Locked User Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL_OR_EXTERNAL_USER_ID",
          },
          session_config: {
            cookie_name: `EXT_LOCK_${organizationId.substring(0, 8)}`,
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
          client_name: "External API Auth Locked User Test Client",
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

    const authConfigResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "external-api-authentication",
        attributes: {},
        metadata: {
          description: "External API authentication for locked user test",
        },
        interactions: {
          password_verify: {
            request: {
              schema: {
                type: "object",
                required: ["interaction", "username", "password"],
                properties: {
                  interaction: { type: "string" },
                  username: { type: "string", minLength: 1 },
                  password: { type: "string", minLength: 1 },
                },
              },
            },
            execution: {
              function: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/auth/password`,
                method: "POST",
                header_mapping_rules: [
                  { static_value: "application/json", to: "Content-Type" },
                ],
                body_mapping_rules: [
                  { from: "$.request_body.username", to: "username" },
                  { from: "$.request_body.password", to: "password" },
                ],
              },
            },
            user_resolve: {
              user_mapping_rules: [
                {
                  from: "$.execution_http_request.response_body.user_id",
                  to: "external_user_id",
                },
                {
                  from: "$.execution_http_request.response_body.email",
                  to: "email",
                },
                {
                  from: "$.execution_http_request.response_body.name",
                  to: "name",
                },
                { static_value: "mock-external-api", to: "provider_id" },
              ],
            },
            response: {
              body_mapping_rules: [
                {
                  from: "$.execution_http_request.response_body.user_id",
                  to: "user_id",
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

  async function startAuthFlow() {
    const state = `ext-api-lock-${Date.now()}`;
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
    return params.get("id");
  }

  async function callExternalApiAuth(authId, body) {
    return await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/external-api-authentication`,
      body,
    });
  }

  it("should deny external-api authentication with 403 when user status is LOCKED", async () => {
    const userEmail = `locked-user-${Date.now()}@example.com`;
    const userPassword = "ExternalPass123!";

    // Step 1: First login provisions the user from the external API response
    const firstAuthId = await startAuthFlow();
    const firstLoginResp = await callExternalApiAuth(firstAuthId, {
      interaction: "password_verify",
      username: userEmail,
      password: userPassword,
    });
    expect(firstLoginResp.status).toBe(200);

    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: firstAuthId,
      body: {},
    });
    expect(authorizeResp.status).toBe(200);
    const authorizationResponse = convertToAuthorizationResponse(
      authorizeResp.data.redirect_uri
    );

    const tokenResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: authorizationResponse.code,
      redirectUri,
      clientId,
      clientSecret,
    });
    expect(tokenResp.status).toBe(200);

    const userinfoResp = await getUserinfo({
      endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
      authorizationHeader: createBearerHeader(tokenResp.data.access_token),
    });
    expect(userinfoResp.status).toBe(200);
    const userSub = userinfoResp.data.sub;
    console.log(`Provisioned user sub: ${userSub}`);

    // Step 2: Lock the user via Management API
    const changeStatusResp = await patchWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${userSub}`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: { status: "LOCKED" },
    });
    expect(changeStatusResp.status).toBe(200);
    expect(changeStatusResp.data.diff).toHaveProperty("status");
    console.log("User status changed to LOCKED");

    // Step 3: Re-authenticate with the same valid credentials
    // The external API succeeds, but AuthenticationUserStatusGuard must deny.
    const secondAuthId = await startAuthFlow();
    const secondLoginResp = await callExternalApiAuth(secondAuthId, {
      interaction: "password_verify",
      username: userEmail,
      password: userPassword,
    });
    console.log(
      `Re-authentication while LOCKED: status=${secondLoginResp.status}, error=${secondLoginResp.data?.error}`
    );
    expect(secondLoginResp.status).toBe(403);
    expect(secondLoginResp.data.error).toBe("access_denied");
    expect(secondLoginResp.data.error_description).toBe(
      "The user is not active."
    );

    // Step 4: The authorization must not proceed to code issuance
    const deniedAuthorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: secondAuthId,
      body: {},
    });
    expect(deniedAuthorizeResp.status).not.toBe(200);
  }, 30000);
});
