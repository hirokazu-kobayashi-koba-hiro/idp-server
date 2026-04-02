import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, get, postWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  postAuthentication,
  authorize,
  getUserinfo,
} from "../../../api/oauthClient";
import { generateECP256JWKS, verifyAndDecodeJwt } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
  createBearerHeader,
} from "../../../lib/util";

/**
 * Standard Use Case: Basic Authorization Code Flow
 *
 * verify.sh (login-password-only) の基本フローを E2E Jest に移植。
 * CI/CD パイプラインで常時回帰テストできるようにする。
 *
 * カバー範囲 (GAP-ANALYSIS A-01):
 * - Discovery Endpoint
 * - Authorization Request (302 redirect)
 * - User Registration (initial-registration)
 * - Consent Grant (authorize)
 * - Token Exchange (authorization_code)
 * - UserInfo Endpoint
 * - Token Refresh
 * - ID Token 検証 (issuer, sub, claims)
 */
describe("Standard Use Case: Basic Authorization Code Flow", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let mgmtAccessToken;
  const redirectUri =
    "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

  beforeAll(async () => {
    // Get system admin token
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

    // Setup: Create organization + tenant + client
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    const userId = uuidv4();
    clientId = uuidv4();
    clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}@basic-flow.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    const onboardingResponse = await onboarding({
      body: {
        organization: {
          id: organizationId,
          name: `Basic Flow Test Org ${timestamp}`,
          description: "E2E test for basic authorization code flow",
        },
        tenant: {
          id: tenantId,
          name: `Basic Flow Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL",
          },
          session_config: {
            cookie_name: `BASIC_SESSION_${organizationId.substring(0, 8)}`,
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
            "given_name",
            "family_name",
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
          sub: userId,
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
          grant_types: [
            "authorization_code",
            "refresh_token",
            "password",
          ],
          scope: "openid profile email management org-management",
          client_name: "Basic Flow Test Client",
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

    // Get management token for authentication config registration
    const mgmtTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "management org-management",
      clientId: clientId,
      clientSecret: clientSecret,
    });
    expect(mgmtTokenResponse.status).toBe(200);
    mgmtAccessToken = mgmtTokenResponse.data.access_token;

    // Register password authentication config
    const passwordAuthConfig = await postWithJson({
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
            execution: { function: "password_verification" },
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
    expect(passwordAuthConfig.status).toBe(201);

    // Register initial-registration config
    const initialRegConfig = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "initial-registration",
        attributes: {},
        metadata: {},
        interactions: {
          "initial-registration": {
            request: {
              schema: {
                type: "object",
                required: ["email", "password", "name"],
                properties: {
                  name: { type: "string", maxLength: 255 },
                  email: { type: "string", format: "email", maxLength: 255 },
                  password: { type: "string", maxLength: 64, minLength: 8 },
                  given_name: { type: "string", maxLength: 255 },
                  family_name: { type: "string", maxLength: 255 },
                  phone_number: { type: "string", maxLength: 255 },
                },
              },
            },
          },
        },
      },
    });
    expect(initialRegConfig.status).toBe(201);
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

  it("should complete full authorization code flow: Discovery → Registration → Token → UserInfo → Refresh", async () => {
    console.log("\n=== Step 1: Discovery Endpoint ===");

    const discoveryResponse = await get({
      url: `${backendUrl}/${tenantId}/.well-known/openid-configuration`,
    });
    expect(discoveryResponse.status).toBe(200);

    const discovery = discoveryResponse.data;
    expect(discovery.issuer).toBe(`${backendUrl}/${tenantId}`);
    expect(discovery.authorization_endpoint).toBeDefined();
    expect(discovery.token_endpoint).toBeDefined();
    expect(discovery.userinfo_endpoint).toBeDefined();
    expect(discovery.jwks_uri).toBeDefined();
    expect(discovery.grant_types_supported).toContain("authorization_code");
    expect(discovery.grant_types_supported).toContain("refresh_token");
    expect(discovery.scopes_supported).toContain("openid");
    expect(discovery.scopes_supported).toContain("profile");
    expect(discovery.scopes_supported).toContain("email");
    console.log("Discovery endpoint validated");

    console.log("\n=== Step 2: Authorization Request ===");

    const state = `basic-flow-${Date.now()}`;
    const authResponse = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId: clientId,
      responseType: "code",
      state: state,
      scope: "openid profile email",
      redirectUri: redirectUri,
    });
    expect(authResponse.status).toBe(302);
    expect(authResponse.headers.location).toBeDefined();

    const { params } = convertNextAction(authResponse.headers.location);
    const authorizationId = params.get("id");
    expect(authorizationId).toBeDefined();
    console.log(`Authorization ID: ${authorizationId}`);

    console.log("\n=== Step 3: User Registration (initial-registration) ===");

    const testEmail = `verify-${Date.now()}@basic-flow.example.com`;
    const testPassword = "VerifyPass_123!";
    const testName = "Verify User";

    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authorizationId}/initial-registration`,
      body: {
        email: testEmail,
        password: testPassword,
        name: testName,
      },
    });
    expect(registrationResponse.status).toBe(200);
    expect(registrationResponse.data.user).toBeDefined();
    expect(registrationResponse.data.user.sub).toBeDefined();
    const userSub = registrationResponse.data.user.sub;
    console.log(`User registered: sub=${userSub}`);

    console.log("\n=== Step 4: Authorize (consent grant) ===");

    const authorizeResponse = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authorizationId,
      body: {},
    });
    expect(authorizeResponse.status).toBe(200);
    expect(authorizeResponse.data.redirect_uri).toBeDefined();

    const authResult = convertToAuthorizationResponse(
      authorizeResponse.data.redirect_uri
    );
    expect(authResult.code).toBeDefined();
    expect(authResult.state).toBe(state);
    console.log("Authorization code obtained, state parameter matches");

    console.log("\n=== Step 5: Token Exchange ===");

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: authResult.code,
      redirectUri: redirectUri,
      clientId: clientId,
      clientSecret: clientSecret,
    });
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data.access_token).toBeDefined();
    expect(tokenResponse.data.id_token).toBeDefined();
    expect(tokenResponse.data.refresh_token).toBeDefined();
    expect(tokenResponse.data.token_type).toBe("Bearer");
    expect(tokenResponse.data.expires_in).toBeGreaterThan(0);

    const accessToken = tokenResponse.data.access_token;
    const idToken = tokenResponse.data.id_token;
    const refreshToken = tokenResponse.data.refresh_token;
    console.log("Token exchange successful");

    console.log("\n=== Step 6: ID Token Verification ===");

    // Fetch JWKS for verification
    const jwksResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/jwks`,
    });
    expect(jwksResponse.status).toBe(200);

    const { header, payload } = verifyAndDecodeJwt({
      jwt: idToken,
      jwks: jwksResponse.data,
    });
    expect(header.alg).toBe("ES256");
    expect(payload.iss).toBe(`${backendUrl}/${tenantId}`);
    expect(payload.sub).toBe(userSub);
    expect(payload.aud).toBeDefined();
    expect(payload.exp).toBeGreaterThan(Math.floor(Date.now() / 1000));
    expect(payload.iat).toBeDefined();
    console.log("ID Token verified: issuer, sub, exp valid");

    console.log("\n=== Step 7: UserInfo Endpoint ===");

    const userinfoResponse = await getUserinfo({
      endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
      authorizationHeader: createBearerHeader(accessToken),
    });
    expect(userinfoResponse.status).toBe(200);
    expect(userinfoResponse.data.sub).toBe(userSub);
    expect(userinfoResponse.data.email).toBe(testEmail);
    expect(userinfoResponse.data.name).toBe(testName);
    console.log(
      `UserInfo: sub=${userinfoResponse.data.sub}, email=${userinfoResponse.data.email}, name=${userinfoResponse.data.name}`
    );

    console.log("\n=== Step 8: Token Refresh ===");

    const refreshResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "refresh_token",
      refreshToken: refreshToken,
      clientId: clientId,
      clientSecret: clientSecret,
    });
    expect(refreshResponse.status).toBe(200);
    expect(refreshResponse.data.access_token).toBeDefined();
    expect(refreshResponse.data.access_token).not.toBe(accessToken);
    console.log("Token refresh successful: new access token obtained");

    // Verify new access token works with UserInfo
    const newUserinfoResponse = await getUserinfo({
      endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
      authorizationHeader: createBearerHeader(
        refreshResponse.data.access_token
      ),
    });
    expect(newUserinfoResponse.status).toBe(200);
    expect(newUserinfoResponse.data.sub).toBe(userSub);
    console.log("New access token works with UserInfo");

    console.log("\n=== Step 9: Re-login with Password ===");

    // Start new authorization flow and login with registered password
    const state2 = `relogin-${Date.now()}`;
    const authResponse2 = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId: clientId,
      responseType: "code",
      state: state2,
      scope: "openid profile email",
      redirectUri: redirectUri,
      prompt: "login",
    });
    expect(authResponse2.status).toBe(302);
    const { params: params2 } = convertNextAction(
      authResponse2.headers.location
    );
    const authId2 = params2.get("id");

    const loginResult = await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId2,
      body: { username: testEmail, password: testPassword },
    });
    expect(loginResult.status).toBe(200);
    console.log("Re-login with registered password successful");

    // Complete flow
    const authorizeResponse2 = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId2,
      body: {},
    });
    expect(authorizeResponse2.status).toBe(200);
    const authResult2 = convertToAuthorizationResponse(
      authorizeResponse2.data.redirect_uri
    );
    expect(authResult2.code).toBeDefined();

    const tokenResponse2 = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: authResult2.code,
      redirectUri: redirectUri,
      clientId: clientId,
      clientSecret: clientSecret,
    });
    expect(tokenResponse2.status).toBe(200);

    const userinfoResponse2 = await getUserinfo({
      endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
      authorizationHeader: createBearerHeader(
        tokenResponse2.data.access_token
      ),
    });
    expect(userinfoResponse2.status).toBe(200);
    expect(userinfoResponse2.data.sub).toBe(userSub);
    expect(userinfoResponse2.data.email).toBe(testEmail);
    console.log("Re-login flow complete: same user confirmed via UserInfo");

    console.log("\n=== Test Completed Successfully ===");
  });

  it("should reject wrong password and succeed with correct password", async () => {
    console.log("\n=== Password Validation Test ===");

    // Register a new user first
    const state = `pwd-test-${Date.now()}`;
    const authResponse = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId: clientId,
      responseType: "code",
      state: state,
      scope: "openid profile email",
      redirectUri: redirectUri,
    });
    expect(authResponse.status).toBe(302);
    const { params } = convertNextAction(authResponse.headers.location);
    const authId = params.get("id");

    const testEmail = `pwd-test-${Date.now()}@basic-flow.example.com`;
    const testPassword = "CorrectPass_1!";

    const regResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: { email: testEmail, password: testPassword, name: "Password Test User" },
    });
    expect(regResponse.status).toBe(200);

    // Complete registration flow
    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId,
      body: {},
    });
    expect(authorizeResp.status).toBe(200);
    const result = convertToAuthorizationResponse(authorizeResp.data.redirect_uri);
    await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: result.code,
      redirectUri: redirectUri,
      clientId: clientId,
      clientSecret: clientSecret,
    });

    // Try wrong password
    const state2 = `pwd-wrong-${Date.now()}`;
    const authResponse2 = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId: clientId,
      responseType: "code",
      state: state2,
      scope: "openid profile email",
      redirectUri: redirectUri,
    });
    const { params: params2 } = convertNextAction(authResponse2.headers.location);
    const authId2 = params2.get("id");

    const wrongResult = await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId2,
      body: { username: testEmail, password: "WrongPassword!" },
    });
    expect(wrongResult.status).toBe(400);
    console.log("Wrong password correctly rejected");

    // Try correct password (same auth session)
    const correctResult = await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId2,
      body: { username: testEmail, password: testPassword },
    });
    expect(correctResult.status).toBe(200);
    console.log("Correct password accepted after wrong attempt");
  });

  it("should return different claims based on scope", async () => {
    console.log("\n=== Scope-based Claims Test ===");

    // Register user
    const testEmail = `scope-test-${Date.now()}@basic-flow.example.com`;
    const testPassword = "ScopeTest_1!";

    const authResp1 = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId: clientId,
      responseType: "code",
      state: `scope-reg-${Date.now()}`,
      scope: "openid profile email",
      redirectUri: redirectUri,
    });
    const { params: p1 } = convertNextAction(authResp1.headers.location);
    const authId1 = p1.get("id");

    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId1}/initial-registration`,
      body: { email: testEmail, password: testPassword, name: "Scope Test User" },
    });
    const authorizeResp1 = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId1,
      body: {},
    });
    const result1 = convertToAuthorizationResponse(authorizeResp1.data.redirect_uri);
    await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: result1.code,
      redirectUri: redirectUri,
      clientId: clientId,
      clientSecret: clientSecret,
    });

    // Test 1: scope=openid only → sub only
    console.log("Test 1: scope=openid only");
    const authRespMin = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId: clientId,
      responseType: "code",
      state: `scope-min-${Date.now()}`,
      scope: "openid",
      redirectUri: redirectUri,
    });
    const { params: pMin } = convertNextAction(authRespMin.headers.location);
    const authIdMin = pMin.get("id");

    await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authIdMin,
      body: { username: testEmail, password: testPassword },
    });
    const authorizeMin = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authIdMin,
      body: {},
    });
    const resultMin = convertToAuthorizationResponse(authorizeMin.data.redirect_uri);
    const tokenMin = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: resultMin.code,
      redirectUri: redirectUri,
      clientId: clientId,
      clientSecret: clientSecret,
    });

    const userinfoMin = await getUserinfo({
      endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
      authorizationHeader: createBearerHeader(tokenMin.data.access_token),
    });
    expect(userinfoMin.status).toBe(200);
    expect(userinfoMin.data.sub).toBeDefined();
    // With openid only, email and name should not be present
    expect(userinfoMin.data.email).toBeUndefined();
    expect(userinfoMin.data.name).toBeUndefined();
    console.log("scope=openid: sub only, no email/name");

    // Test 2: scope=openid profile email → sub + email + name
    console.log("Test 2: scope=openid profile email");
    const authRespFull = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId: clientId,
      responseType: "code",
      state: `scope-full-${Date.now()}`,
      scope: "openid profile email",
      redirectUri: redirectUri,
    });
    const { params: pFull } = convertNextAction(authRespFull.headers.location);
    const authIdFull = pFull.get("id");

    await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authIdFull,
      body: { username: testEmail, password: testPassword },
    });
    const authorizeFull = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authIdFull,
      body: {},
    });
    const resultFull = convertToAuthorizationResponse(authorizeFull.data.redirect_uri);
    const tokenFull = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: resultFull.code,
      redirectUri: redirectUri,
      clientId: clientId,
      clientSecret: clientSecret,
    });

    const userinfoFull = await getUserinfo({
      endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
      authorizationHeader: createBearerHeader(tokenFull.data.access_token),
    });
    expect(userinfoFull.status).toBe(200);
    expect(userinfoFull.data.sub).toBeDefined();
    expect(userinfoFull.data.email).toBe(testEmail);
    expect(userinfoFull.data.name).toBe("Scope Test User");
    console.log("scope=openid profile email: sub + email + name present");

    console.log("\n=== Scope-based Claims Test Completed ===");
  });
});
