import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  postAuthentication,
} from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { convertNextAction, sleep } from "../../../lib/util";

/**
 * Standard Use Case: Account Lock changes User Status to LOCKED
 *
 * This test verifies that when authentication policy lock_conditions are met,
 * the user's status is automatically changed to LOCKED via UserLifecycleEvent:
 *
 * 1. Create tenant with authentication policy that has lock_conditions (failure_count >= 3)
 * 2. Fail authentication 3 times to trigger lock_conditions
 * 3. Verify user status is changed to LOCKED via Management API
 * 4. Verify locked user cannot authenticate even with correct password
 */
describe("Standard Use Case: Account Lock User Status", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let userId;
  let clientId;
  let clientSecret;
  let userEmail;
  let userPassword;
  let mgmtAccessToken;

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
    userId = uuidv4();
    clientId = uuidv4();
    userEmail = `user-${timestamp}@account-lock-test.example.com`;
    userPassword = `TestPass_${timestamp}!`;
    clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: {
        organization: {
          id: organizationId,
          name: `Account Lock Test Org ${timestamp}`,
          description: "Test organization for account lock user status",
        },
        tenant: {
          id: tenantId,
          name: `Account Lock Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          session_config: {
            cookie_name: `LOCK_SESSION_${organizationId.substring(0, 8)}`,
            use_secure_cookie: false,
          },
          cors_config: {
            allow_origins: [backendUrl],
          },
          security_event_log_config: {
            persistence_enabled: true,
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
          grant_types_supported: ["authorization_code", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: [
            "openid",
            "profile",
            "email",
            "management",
            "org-management",
          ],
          response_types_supported: ["code"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["RS256", "ES256"],
          response_modes_supported: ["query"],
          extension: {
            access_token_type: "JWT",
          },
        },
        user: {
          sub: userId,
          email: userEmail,
          raw_password: userPassword,
          username: userEmail,
        },
        client: {
          client_id: clientId,
          client_name: "Account Lock Test Client",
          client_secret: clientSecret,
          redirect_uris: [
            "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
          ],
          response_types: ["code"],
          grant_types: ["authorization_code", "password"],
          scope: "openid profile email management org-management",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    expect(onboardingResponse.status).toBe(201);

    // Get management token
    const mgmtTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "management org-management",
      clientId: clientId,
      clientSecret: clientSecret,
    });
    expect(mgmtTokenResponse.status).toBe(200);
    mgmtAccessToken = mgmtTokenResponse.data.access_token;

    // Register password authentication config
    const authConfigResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "password",
        attributes: {},
        metadata: { type: "password", description: "Password authentication" },
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
            pre_hook: {},
            execution: {
              function: "password_verification",
            },
            post_hook: {},
            response: {
              body_mapping_rules: [
                { from: "$.user_id", to: "user_id" },
                { from: "$.username", to: "username" },
                { from: "$.error", to: "error" },
                { from: "$.error_description", to: "error_description" },
              ],
            },
          },
        },
      },
    });
    expect(authConfigResponse.status).toBe(201);

    // Register authentication policy with lock_conditions (failure_count >= 3)
    const authPolicyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "password_with_lock",
            priority: 1,
            conditions: {},
            available_methods: ["password"],
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
            failure_conditions: {
              any_of: [
                [
                  {
                    path: "$.password-authentication.failure_count",
                    type: "integer",
                    operation: "gte",
                    value: 3,
                  },
                ],
              ],
            },
            lock_conditions: {
              any_of: [
                [
                  {
                    path: "$.password-authentication.failure_count",
                    type: "integer",
                    operation: "gte",
                    value: 3,
                  },
                ],
              ],
            },
          },
        ],
      },
    });
    expect(authPolicyResponse.status).toBe(201);
  });

  afterAll(async () => {
    if (mgmtAccessToken) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${clientId}`,
        headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      });
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
        headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      });
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      });
    }
  });

  async function startAuthorizationFlow() {
    const authResponse = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId: clientId,
      responseType: "code",
      state: `lock-test-${Date.now()}`,
      scope: "openid profile",
      redirectUri:
        "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
    });
    expect(authResponse.status).toBe(302);
    const { params } = convertNextAction(authResponse.headers.location);
    return params.get("id");
  }

  async function attemptPasswordAuth(authId, username, password) {
    return await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId,
      body: { username, password },
    });
  }

  async function getUserStatus() {
    const response = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${userId}`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    });
    return response;
  }

  it("should change user status to LOCKED when lock_conditions are met", async () => {
    console.log("\n=== Account Lock User Status Test ===\n");

    // Step 1: Verify initial user status is active
    console.log("Step 1: Verify initial user status");
    const initialUserResponse = await getUserStatus();
    expect(initialUserResponse.status).toBe(200);
    const initialStatus = initialUserResponse.data.status;
    console.log(`  Initial user status: ${initialStatus}`);
    expect(["REGISTERED", "INITIALIZED"]).toContain(initialStatus);

    // Step 2: Fail authentication 3 times within the same authorization flow
    // lock_conditions are evaluated per AuthenticationTransaction, so failures must accumulate in one flow
    console.log("\nStep 2: Fail authentication 3 times in same authorization flow");
    const lockAuthId = await startAuthorizationFlow();
    console.log(lockAuthId);
    for (let i = 1; i <= 3; i++) {
      const result = await attemptPasswordAuth(
        lockAuthId,
        userEmail,
        "wrong-password"
      );
      console.log(
        `  Attempt ${i}: status=${result.status}, error=${result.data?.error || "N/A"}`
      );
      expect(result.status).toBe(400);
    }

    // Step 3: Wait for async UserLifecycleEvent processing
    console.log("\nStep 3: Wait for UserLifecycleEvent processing");
    await sleep(2000);

    // Step 4: Verify user status is LOCKED via Management API
    console.log("\nStep 4: Verify user status is LOCKED via Management API");
    const lockedUserResponse = await getUserStatus();
    expect(lockedUserResponse.status).toBe(200);
    const lockedStatus = lockedUserResponse.data.status;
    console.log(`  User status after lock: ${lockedStatus}`);
    expect(lockedStatus).toBe("LOCKED");

    // TODO: Step 5 - Verify locked user cannot authenticate with correct password
    // This requires implementing user status check during authentication interaction.
    // See: https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/1377

    console.log("\n=== Account Lock User Status Test Completed ===");
  }, 30000);
});
