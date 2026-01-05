import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import { requestToken, getAuthorizations, postAuthentication, authorize } from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { convertNextAction, convertToAuthorizationResponse } from "../../../lib/util";

/**
 * Standard Use Case: Session Switch Policy (Issue #1171)
 *
 * This test verifies the session switch policy behavior:
 * - STRICT: Different user authentication is rejected (must logout first)
 * - SWITCH_ALLOWED: Old session is terminated, new session is created (default)
 * - Same user re-authentication: Session is reused (all policies)
 */
describe("Standard Use Case: Session Switch Policy (Issue #1171)", () => {
  let systemAccessToken;

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
  });

  describe("STRICT policy", () => {

    it("should reject different user authentication when policy is STRICT", async () => {
      const timestamp = Date.now();
      const organizationId = uuidv4();
      const tenantId = uuidv4();
      const user1Id = uuidv4();
      const user2Id = uuidv4();
      const clientId = uuidv4();
      const user1Email = `user1-${timestamp}@strict-policy.example.com`;
      const user2Email = `user2-${timestamp}@strict-policy.example.com`;
      const user1Password = `TestPass1_${timestamp}!`;
      const user2Password = `TestPass2_${timestamp}!`;
      const clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
      const jwksContent = await generateECP256JWKS();

      console.log("\n=== STRICT Policy Test: Different User Should Be Rejected ===\n");

      // Step 1: Create Organization with STRICT session switch policy
      console.log("Step 1: Create Organization with STRICT session switch policy");

      const onboardingRequest = {
        organization: {
          id: organizationId,
          name: `Strict Policy Test Org ${timestamp}`,
          description: "Test organization for STRICT session switch policy",
        },
        tenant: {
          id: tenantId,
          name: `Strict Policy Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          session_config: {
            cookie_name: `STRICT_SESSION_${organizationId.substring(0, 8)}`,
            use_secure_cookie: false,
            switch_policy: "STRICT",
          },
          cors_config: {
            allow_origins: [backendUrl],
          },
          security_event_log_config: {
            format: "structured_json",
            stage: "processed",
            include_user_id: true,
            include_client_id: true,
            persistence_enabled: true,
          },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          grant_types_supported: ["authorization_code", "refresh_token", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management", "org-management", "session:read", "session:delete"],
          response_types_supported: ["code"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["RS256", "ES256"],
          token_introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
          token_revocation_endpoint: `${backendUrl}/${tenantId}/v1/tokens/revocation`,
          response_modes_supported: ["query"],
          extension: {
            access_token_type: "JWT",
          },
        },
        user: {
          sub: user1Id,
          email: user1Email,
          raw_password: user1Password,
          username: user1Email,
        },
        client: {
          client_id: clientId,
          client_name: "Strict Policy Test Client",
          client_secret: clientSecret,
          redirect_uris: ["https://www.certification.openid.net/test/a/idp_oidc_basic/callback"],
          response_types: ["code"],
          grant_types: ["authorization_code", "refresh_token", "password"],
          scope: "openid profile email management org-management session:read session:delete",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      };

      const onboardingResponse = await postWithJson({
        url: `${backendUrl}/v1/management/onboarding`,
        body: onboardingRequest,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      });

      expect(onboardingResponse.status).toBe(201);
      console.log("Organization created with STRICT switch_policy");

      // Step 2: Create second user
      console.log("\nStep 2: Create second user");

      const mgmtTokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "password",
        username: user1Email,
        password: user1Password,
        scope: "org-management management session:read session:delete",
        clientId: clientId,
        clientSecret: clientSecret,
      });
      expect(mgmtTokenResponse.status).toBe(200);
      const mgmtAccessToken = mgmtTokenResponse.data.access_token;

      const createUser2Response = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users`,
        body: {
          sub: user2Id,
          provider_id: "idp-server",
          email: user2Email,
          raw_password: user2Password,
          name: `User2 ${timestamp}`,
        },
        headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      });
      expect(createUser2Response.status).toBe(201);
      console.log("Second user created:", user2Email);

      // Step 3: User 1 logs in via authorization flow (creates OPSession)
      console.log("\nStep 3: User 1 logs in via authorization flow");

      const authResponse1 = await getAuthorizations({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        clientId: clientId,
        responseType: "code",
        state: "user1-login-strict-test",
        scope: "openid profile",
        redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
      });
      expect(authResponse1.status).toBe(302);

      const { params: params1 } = convertNextAction(authResponse1.headers.location);
      const authId1 = params1.get("id");

      const authResult1 = await postAuthentication({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
        id: authId1,
        body: { username: user1Email, password: user1Password },
      });
      expect(authResult1.status).toBe(200);

      const authorizeResponse1 = await authorize({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
        id: authId1,
        body: {},
      });
      expect(authorizeResponse1.status).toBe(200);

      const result1 = convertToAuthorizationResponse(authorizeResponse1.data.redirect_uri);
      expect(result1.code).toBeDefined();
      console.log("User 1 logged in successfully, authorization code obtained");

      // Step 4: Verify User 1 can get authorization code with prompt=none (SSO) BEFORE token exchange
      // This tests that AuthorizationGranted is registered at authorize time, not token time
      console.log("\nStep 4: Verify User 1 can use SSO with prompt=none (BEFORE token exchange)");

      const ssoAuthResponse = await getAuthorizations({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        clientId: clientId,
        responseType: "code",
        state: "user1-sso-strict-test",
        scope: "openid profile",
        redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
        prompt: "none",
      });
      // prompt=none with valid session and AuthorizationGranted should redirect with code
      expect(ssoAuthResponse.status).toBe(302);
      const ssoRedirectUri = ssoAuthResponse.headers.location;
      expect(ssoRedirectUri).toContain("code=");
      expect(ssoRedirectUri).not.toContain("error=");
      console.log("User 1 SSO with prompt=none succeeded (before token exchange!)");

      // Step 5: User 2 tries to log in (should fail with STRICT policy)
      console.log("\nStep 5: User 2 tries to log in (should fail with STRICT policy)");

      const authResponse2 = await getAuthorizations({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        clientId: clientId,
        responseType: "code",
        state: "user2-login-strict-test",
        scope: "openid profile",
        redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
      });
      expect(authResponse2.status).toBe(302);

      const { params: params2 } = convertNextAction(authResponse2.headers.location);
      const authId2 = params2.get("id");

      // User 2 authenticates with their credentials
      // With STRICT policy, different user authentication should be rejected at authentication step
      const authResult2 = await postAuthentication({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
        id: authId2,
        body: { username: user2Email, password: user2Password },
      });

      // STRICT policy rejects at authentication step with 403 Forbidden
      expect(authResult2.status).toBe(403);
      expect(authResult2.data.error_description).toContain("Different user authenticated");
      console.log("User 2 authentication correctly rejected with STRICT policy");

      console.log("\n=== STRICT Policy Test Completed ===");

      // Cleanup
      console.log("\nCleanup...");
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${user2Id}`,
        headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      });
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
    });

  });

  describe("SWITCH_ALLOWED policy (default)", () => {

    it("should terminate old session and create new when different user authenticates", async () => {
      const timestamp = Date.now();
      const organizationId = uuidv4();
      const tenantId = uuidv4();
      const user1Id = uuidv4();
      const user2Id = uuidv4();
      const clientId = uuidv4();
      const user1Email = `user1-${timestamp}@switch-policy.example.com`;
      const user2Email = `user2-${timestamp}@switch-policy.example.com`;
      const user1Password = `TestPass1_${timestamp}!`;
      const user2Password = `TestPass2_${timestamp}!`;
      const clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
      const jwksContent = await generateECP256JWKS();

      console.log("\n=== SWITCH_ALLOWED Policy Test: Session Should Be Switched ===\n");

      // Step 1: Create Organization with SWITCH_ALLOWED session switch policy
      console.log("Step 1: Create Organization with SWITCH_ALLOWED session switch policy");

      const onboardingRequest = {
        organization: {
          id: organizationId,
          name: `Switch Policy Test Org ${timestamp}`,
          description: "Test organization for SWITCH_ALLOWED session switch policy",
        },
        tenant: {
          id: tenantId,
          name: `Switch Policy Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          session_config: {
            cookie_name: `SWITCH_SESSION_${organizationId.substring(0, 8)}`,
            use_secure_cookie: false,
            switch_policy: "SWITCH_ALLOWED",
          },
          cors_config: {
            allow_origins: [backendUrl],
          },
          security_event_log_config: {
            format: "structured_json",
            stage: "processed",
            include_user_id: true,
            include_client_id: true,
            persistence_enabled: true,
          },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          grant_types_supported: ["authorization_code", "refresh_token", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management", "org-management", "session:read", "session:delete"],
          response_types_supported: ["code"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["RS256", "ES256"],
          token_introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
          token_revocation_endpoint: `${backendUrl}/${tenantId}/v1/tokens/revocation`,
          response_modes_supported: ["query"],
          extension: {
            access_token_type: "JWT",
          },
        },
        user: {
          sub: user1Id,
          email: user1Email,
          raw_password: user1Password,
          username: user1Email,
        },
        client: {
          client_id: clientId,
          client_name: "Switch Policy Test Client",
          client_secret: clientSecret,
          redirect_uris: ["https://www.certification.openid.net/test/a/idp_oidc_basic/callback"],
          response_types: ["code"],
          grant_types: ["authorization_code", "refresh_token", "password"],
          scope: "openid profile email management org-management session:read session:delete",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      };

      const onboardingResponse = await postWithJson({
        url: `${backendUrl}/v1/management/onboarding`,
        body: onboardingRequest,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      });

      expect(onboardingResponse.status).toBe(201);
      console.log("Organization created with SWITCH_ALLOWED switch_policy");

      // Step 2: Create second user
      console.log("\nStep 2: Create second user");

      const mgmtTokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "password",
        username: user1Email,
        password: user1Password,
        scope: "org-management management session:read session:delete",
        clientId: clientId,
        clientSecret: clientSecret,
      });
      expect(mgmtTokenResponse.status).toBe(200);
      const mgmtAccessToken = mgmtTokenResponse.data.access_token;

      const createUser2Response = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users`,
        body: {
          sub: user2Id,
          provider_id: "idp-server",
          email: user2Email,
          raw_password: user2Password,
          name: `User2 ${timestamp}`,
        },
        headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      });
      expect(createUser2Response.status).toBe(201);
      console.log("Second user created:", user2Email);

      // Step 3: User 1 logs in via authorization flow (creates OPSession)
      console.log("\nStep 3: User 1 logs in via authorization flow");

      const authResponse1 = await getAuthorizations({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        clientId: clientId,
        responseType: "code",
        state: "user1-login-switch-test",
        scope: "openid profile",
        redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
      });
      expect(authResponse1.status).toBe(302);

      const { params: params1 } = convertNextAction(authResponse1.headers.location);
      const authId1 = params1.get("id");

      await postAuthentication({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
        id: authId1,
        body: { username: user1Email, password: user1Password },
      });

      const authorizeResponse1 = await authorize({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
        id: authId1,
        body: {},
      });
      expect(authorizeResponse1.status).toBe(200);
      console.log("User 1 logged in successfully");

      // Get User 1's sessions
      const user1SessionsResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${user1Id}/sessions`,
        headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      });
      expect(user1SessionsResponse.status).toBe(200);
      const user1SessionCount = user1SessionsResponse.data.list.length;
      console.log(`User 1 has ${user1SessionCount} session(s)`);

      // Step 4: User 2 logs in (should succeed, User 1's session should be terminated)
      console.log("\nStep 4: User 2 logs in (should succeed with SWITCH_ALLOWED)");

      const authResponse2 = await getAuthorizations({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        clientId: clientId,
        responseType: "code",
        state: "user2-login-switch-test",
        scope: "openid profile",
        redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
      });
      expect(authResponse2.status).toBe(302);

      const { params: params2 } = convertNextAction(authResponse2.headers.location);
      const authId2 = params2.get("id");

      await postAuthentication({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
        id: authId2,
        body: { username: user2Email, password: user2Password },
      });

      const authorizeResponse2 = await authorize({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
        id: authId2,
        body: {},
      });

      // With SWITCH_ALLOWED, the authorization should succeed
      expect(authorizeResponse2.status).toBe(200);
      console.log("User 2 logged in successfully");

      // Verify User 1's session was terminated (check session count)
      const user1SessionsAfterResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${user1Id}/sessions`,
        headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      });
      expect(user1SessionsAfterResponse.status).toBe(200);
      const user1SessionCountAfter = user1SessionsAfterResponse.data.list.length;
      console.log(`User 1 now has ${user1SessionCountAfter} session(s) (was ${user1SessionCount})`);

      // User 1's session should have been terminated
      expect(user1SessionCountAfter).toBeLessThan(user1SessionCount);
      console.log("User 1's session was correctly terminated due to user switch");

      console.log("\n=== SWITCH_ALLOWED Policy Test Completed Successfully ===");

      // Cleanup
      console.log("\nCleanup...");
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${user2Id}`,
        headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      });
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
    });

  });

  describe("Same user re-authentication (all policies)", () => {

    it("should reuse existing session when same user re-authenticates", async () => {
      const timestamp = Date.now();
      const organizationId = uuidv4();
      const tenantId = uuidv4();
      const userId = uuidv4();
      const clientId = uuidv4();
      const userEmail = `user-${timestamp}@reuse-session.example.com`;
      const userPassword = `TestPass_${timestamp}!`;
      const clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
      const jwksContent = await generateECP256JWKS();

      console.log("\n=== Same User Re-authentication Test: Session Should Be Reused ===\n");

      // Step 1: Create Organization
      console.log("Step 1: Create Organization");

      const onboardingRequest = {
        organization: {
          id: organizationId,
          name: `Session Reuse Test Org ${timestamp}`,
          description: "Test organization for session reuse",
        },
        tenant: {
          id: tenantId,
          name: `Session Reuse Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          session_config: {
            cookie_name: `REUSE_SESSION_${organizationId.substring(0, 8)}`,
            use_secure_cookie: false,
            switch_policy: "SWITCH_ALLOWED",
          },
          cors_config: {
            allow_origins: [backendUrl],
          },
          security_event_log_config: {
            format: "structured_json",
            stage: "processed",
            include_user_id: true,
            include_client_id: true,
            persistence_enabled: true,
          },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          grant_types_supported: ["authorization_code", "refresh_token", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management", "org-management", "session:read", "session:delete"],
          response_types_supported: ["code"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["RS256", "ES256"],
          token_introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
          token_revocation_endpoint: `${backendUrl}/${tenantId}/v1/tokens/revocation`,
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
          client_name: "Session Reuse Test Client",
          client_secret: clientSecret,
          redirect_uris: ["https://www.certification.openid.net/test/a/idp_oidc_basic/callback"],
          response_types: ["code"],
          grant_types: ["authorization_code", "refresh_token", "password"],
          scope: "openid profile email management org-management session:read session:delete",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      };

      const onboardingResponse = await postWithJson({
        url: `${backendUrl}/v1/management/onboarding`,
        body: onboardingRequest,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      });

      expect(onboardingResponse.status).toBe(201);
      console.log("Organization created");

      // Get management token
      const mgmtTokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "password",
        username: userEmail,
        password: userPassword,
        scope: "management session:read session:delete",
        clientId: clientId,
        clientSecret: clientSecret,
      });
      expect(mgmtTokenResponse.status).toBe(200);
      const mgmtAccessToken = mgmtTokenResponse.data.access_token;

      // Step 2: First login
      console.log("\nStep 2: First login");

      const authResponse1 = await getAuthorizations({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        clientId: clientId,
        responseType: "code",
        state: "first-login-reuse-test",
        scope: "openid profile",
        redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
      });
      expect(authResponse1.status).toBe(302);

      const { params: params1 } = convertNextAction(authResponse1.headers.location);
      const authId1 = params1.get("id");

      await postAuthentication({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
        id: authId1,
        body: { username: userEmail, password: userPassword },
      });

      const authorizeResponse1 = await authorize({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
        id: authId1,
        body: {},
      });
      expect(authorizeResponse1.status).toBe(200);
      console.log("First login successful");

      // Get session info after first login
      const sessionsAfterFirst = await get({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${userId}/sessions`,
        headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      });
      expect(sessionsAfterFirst.status).toBe(200);
      const sessionCountAfterFirst = sessionsAfterFirst.data.list.length;
      const firstSessionId = sessionsAfterFirst.data.list[0]?.id;
      console.log(`Sessions after first login: ${sessionCountAfterFirst}, Session ID: ${firstSessionId}`);

      // Step 3: Same user re-authenticates
      console.log("\nStep 3: Same user re-authenticates");

      const authResponse2 = await getAuthorizations({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        clientId: clientId,
        responseType: "code",
        state: "second-login-reuse-test",
        scope: "openid profile",
        redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
      });
      expect(authResponse2.status).toBe(302);

      const { params: params2 } = convertNextAction(authResponse2.headers.location);
      const authId2 = params2.get("id");

      await postAuthentication({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
        id: authId2,
        body: { username: userEmail, password: userPassword },
      });

      const authorizeResponse2 = await authorize({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
        id: authId2,
        body: {},
      });
      expect(authorizeResponse2.status).toBe(200);
      console.log("Second login successful");

      // Get session info after second login
      const sessionsAfterSecond = await get({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${userId}/sessions`,
        headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      });
      expect(sessionsAfterSecond.status).toBe(200);
      const sessionCountAfterSecond = sessionsAfterSecond.data.list.length;
      const secondSessionId = sessionsAfterSecond.data.list[0]?.id;
      console.log(`Sessions after second login: ${sessionCountAfterSecond}, Session ID: ${secondSessionId}`);

      // Verify session was reused (same count, same ID)
      expect(sessionCountAfterSecond).toBe(sessionCountAfterFirst);
      expect(secondSessionId).toBe(firstSessionId);
      console.log("Session was correctly reused (same ID, no new session created)");

      console.log("\n=== Same User Re-authentication Test Completed Successfully ===");

      // Cleanup
      console.log("\nCleanup...");
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
    });

  });

});
