import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, postWithJson } from "../../../lib/http";
import { requestToken, getAuthorizations, postAuthentication } from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { convertNextAction } from "../../../lib/util";

/**
 * Standard Use Case: Password Brute-Force Protection
 *
 * This test verifies that brute-force protection works correctly:
 * - After max_attempts consecutive failures, authentication is rejected with too_many_attempts
 * - After successful authentication, the failure counter is reset
 * - After lockout_duration_seconds, the counter is auto-reset (via Redis TTL)
 */
describe("Standard Use Case: Password Brute-Force Protection", () => {
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

    // Setup: Create tenant with brute-force protection (max_attempts=3, lockout=5s for fast test)
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    userId = uuidv4();
    clientId = uuidv4();
    userEmail = `user-${timestamp}@brute-force-test.example.com`;
    userPassword = `TestPass_${timestamp}!`;
    clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: {
        organization: {
          id: organizationId,
          name: `Brute Force Test Org ${timestamp}`,
          description: "Test organization for brute-force protection",
        },
        tenant: {
          id: tenantId,
          name: `Brute Force Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL",
            password_policy: {
              min_length: 8,
              max_attempts: 3,
              lockout_duration_seconds: 5,
            },
          },
          session_config: {
            cookie_name: `BF_SESSION_${organizationId.substring(0, 8)}`,
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
          grant_types_supported: ["authorization_code", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management", "org-management"],
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
          client_name: "Brute Force Test Client",
          client_secret: clientSecret,
          redirect_uris: ["https://www.certification.openid.net/test/a/idp_oidc_basic/callback"],
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
                  username: { type: "string", description: "Username" },
                  password: { type: "string", description: "Password" },
                  provider_id: { type: "string", description: "Provider ID" },
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
      state: `brute-force-test-${Date.now()}`,
      scope: "openid profile",
      redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
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

  it("should lock out after max_attempts failures and reset after successful auth", async () => {
    console.log("\n=== Brute-Force Protection Test ===\n");

    // Step 1: Fail 3 times (max_attempts=3), each within the limit
    console.log("Step 1: Fail 3 times with wrong password");

    for (let i = 1; i <= 3; i++) {
      const authId = await startAuthorizationFlow();
      const result = await attemptPasswordAuth(authId, userEmail, "wrong-password");
      console.log(`  Attempt ${i}: status=${result.status}, data=${JSON.stringify(result.data)}`);
      expect(result.status).toBe(400);
    }

    // Step 2: 4th attempt should be locked out (even with correct password)
    console.log("\nStep 2: 4th attempt should be locked out");

    const lockedAuthId = await startAuthorizationFlow();
    const lockedResult = await attemptPasswordAuth(lockedAuthId, userEmail, userPassword);
    console.log(`  Attempt 4: status=${lockedResult.status}, data=${JSON.stringify(lockedResult.data)}`);
    expect(lockedResult.status).toBe(400);
    expect(lockedResult.data.error).toBe("too_many_attempts");

    // Step 3: Wait for lockout to expire (lockout_duration_seconds=5)
    console.log("\nStep 3: Wait for lockout to expire (5 seconds)");
    await new Promise((resolve) => setTimeout(resolve, 6000));

    // Step 4: Should succeed after lockout expires
    console.log("\nStep 4: Attempt after lockout expires should succeed");

    const resetAuthId = await startAuthorizationFlow();
    const resetResult = await attemptPasswordAuth(resetAuthId, userEmail, userPassword);
    console.log(`  Result: status=${resetResult.status}, data=${JSON.stringify(resetResult.data)}`);
    expect(resetResult.status).toBe(200);
    expect(resetResult.data.user_id).toBeDefined();

    // Step 5: Verify counter is reset after success - fail again up to limit
    console.log("\nStep 5: Verify counter reset - fail 3 more times");

    for (let i = 1; i <= 3; i++) {
      const authId = await startAuthorizationFlow();
      const result = await attemptPasswordAuth(authId, userEmail, "wrong-password");
      console.log(`  Attempt ${i}: status=${result.status}`);
      expect(result.status).toBe(400);
    }

    // Step 6: Should be locked out again
    console.log("\nStep 6: 4th attempt should be locked out again");

    const lockedAgainAuthId = await startAuthorizationFlow();
    const lockedAgainResult = await attemptPasswordAuth(lockedAgainAuthId, userEmail, userPassword);
    console.log(`  Attempt 4: status=${lockedAgainResult.status}, data=${JSON.stringify(lockedAgainResult.data)}`);
    expect(lockedAgainResult.status).toBe(400);
    expect(lockedAgainResult.data.error).toBe("too_many_attempts");

    console.log("\n=== Brute-Force Protection Test Completed ===");
  }, 30000);
});
