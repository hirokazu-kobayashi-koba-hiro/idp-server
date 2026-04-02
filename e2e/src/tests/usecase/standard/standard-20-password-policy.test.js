import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, postWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  authorize,
} from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { convertNextAction, convertToAuthorizationResponse } from "../../../lib/util";

/**
 * Standard Use Case: Password Policy Enforcement
 *
 * EXPERIMENTS-basics.md Experiment 1 を E2E Jest に移植。
 *
 * カバー範囲 (GAP-ANALYSIS B-01):
 * - min_length 制約でパスワードが短すぎると拒否される
 * - require_uppercase / require_number 制約
 * - 要件を満たすパスワードは受理される
 */
describe("Standard Use Case: Password Policy Enforcement", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
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
    clientId = uuidv4();
    clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}@pwd-policy.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    // Create tenant with strict password policy
    const onboardingResponse = await onboarding({
      body: {
        organization: {
          id: organizationId,
          name: `Password Policy Test Org ${timestamp}`,
          description: "E2E test for password policy enforcement",
        },
        tenant: {
          id: tenantId,
          name: `Password Policy Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL",
            password_policy: {
              min_length: 12,
              require_uppercase: true,
              require_lowercase: true,
              require_number: true,
            },
          },
          session_config: {
            cookie_name: `PWD_POL_${organizationId.substring(0, 8)}`,
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
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: { access_token_type: "JWT" },
        },
        user: {
          sub: uuidv4(),
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
          grant_types: ["authorization_code", "password"],
          scope: "openid profile email management org-management",
          client_name: "Password Policy Test Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    if (onboardingResponse.status !== 201) {
      console.error("Onboarding failed:", JSON.stringify(onboardingResponse.data, null, 2));
    }
    expect(onboardingResponse.status).toBe(201);

    // Get management token + register auth configs
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
    const mgmtAccessToken = mgmtTokenResponse.data.access_token;

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
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
                properties: { username: { type: "string" }, password: { type: "string" } },
                required: ["username", "password"],
              },
            },
            pre_hook: {},
            execution: { function: "password_verification" },
            post_hook: {},
            response: {
              body_mapping_rules: [
                { from: "$.user_id", to: "user_id" },
                { from: "$.error", to: "error" },
                { from: "$.error_description", to: "error_description" },
              ],
            },
          },
        },
      },
    });

    await postWithJson({
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
                },
              },
            },
          },
        },
      },
    });
  });

  afterAll(async () => {
    if (systemAccessToken) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  /** Helper: start auth flow and attempt registration */
  async function startAuthAndRegister(email, password, name) {
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `pwd-policy-${Date.now()}`,
      scope: "openid profile email",
      redirectUri,
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    const regResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: { email, password, name },
    });
    return { authId, status: regResp.status, data: regResp.data };
  }

  it("should reject passwords that violate min_length policy (12 chars)", async () => {
    console.log("\n=== Password Policy: min_length=12 ===");

    // 8 chars lowercase only → reject (too short + missing uppercase + missing number)
    const result1 = await startAuthAndRegister(
      `short-${Date.now()}@test.com`,
      "password",
      "Short Password User"
    );
    expect(result1.status).toBe(400);
    console.log("'password' (8 chars, lowercase only): rejected");

    // 11 chars with mixed case + number → reject (still too short)
    const result2 = await startAuthAndRegister(
      `eleven-${Date.now()}@test.com`,
      "AbcDefg123!",
      "Eleven Char User"
    );
    expect(result2.status).toBe(400);
    console.log("'AbcDefg123!' (11 chars): rejected");
  });

  it("should reject passwords missing required character types", async () => {
    console.log("\n=== Password Policy: require_uppercase + require_number ===");

    // 12+ chars but no uppercase → reject
    const result1 = await startAuthAndRegister(
      `nouppercase-${Date.now()}@test.com`,
      "longpassword1!",
      "No Uppercase User"
    );
    expect(result1.status).toBe(400);
    console.log("'longpassword1!' (no uppercase): rejected");

    // 12+ chars but no number → reject
    const result2 = await startAuthAndRegister(
      `nonumber-${Date.now()}@test.com`,
      "LongPasswordHere!",
      "No Number User"
    );
    expect(result2.status).toBe(400);
    console.log("'LongPasswordHere!' (no number): rejected");
  });

  it("should accept passwords that satisfy all policy requirements", async () => {
    console.log("\n=== Password Policy: Valid Password ===");

    // 13 chars with uppercase + lowercase + number → accept
    const result = await startAuthAndRegister(
      `valid-${Date.now()}@test.com`,
      "StrongPass123!",
      "Valid Password User"
    );
    expect(result.status).toBe(200);
    expect(result.data.user).toBeDefined();
    console.log("'StrongPass123!' (13 chars, upper+lower+num): accepted");

    // Complete the flow to persist user
    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: result.authId,
      body: {},
    });
    expect(authorizeResp.status).toBe(200);
    const authResult = convertToAuthorizationResponse(authorizeResp.data.redirect_uri);
    const tokenResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: authResult.code,
      redirectUri,
      clientId,
      clientSecret,
    });
    expect(tokenResp.status).toBe(200);
    console.log("Full flow completed with valid password");
  });
});
