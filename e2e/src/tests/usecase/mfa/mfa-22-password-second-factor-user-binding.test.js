import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, postWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  postAuthentication,
  authorize,
} from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
} from "../../../lib/util";

/**
 * Security: Password 2nd factor must bind to the authenticated (session) user.
 *
 * Issue #1396: In a 2nd-factor password step (`requires_user: true`), the executor used to look up
 * the user by the request-supplied username (`findByPreferredUsername`) and verify the password
 * against THAT user, while the interactor authenticated as `transaction.user()` (the 1st-factor
 * user). The two could diverge → an attacker who passed the 1st factor as Alice could satisfy the
 * password step with a DIFFERENT account's (Bob's) credentials = identifier switching / MFA
 * binding bypass.
 *
 * Fix: for 2nd factor, the password is verified against the authenticated session user (the request
 * username is rebound to the session user). Mirrors EmailAuthenticationChallengeInteractor.
 *
 * MFA policy: Email OTP (1st) + Password (2nd). identity_unique_key_type = EMAIL.
 *
 * Prerequisites: Mock server (Mockoon) at host.docker.internal:4000 (email challenge endpoints).
 */
describe("Security: Password 2nd factor user binding (Issue #1396)", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let mgmtAccessToken;
  const redirectUri =
    "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

  const timestamp = Date.now();
  const aliceEmail = `alice-${timestamp}@mfa-bind.example.com`;
  const alicePassword = "AlicePass_1!";
  const bobEmail = `bob-${timestamp}@mfa-bind.example.com`;
  const bobPassword = "BobPass_2!";

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

    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}@mfa-bind.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `MFA Bind Test Org ${timestamp}`,
          description: "E2E test for password 2nd factor user binding (#1396)",
        },
        tenant: {
          id: tenantId,
          name: `MFA Bind Test Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: {
            cookie_name: `MFA_BIND_${organizationId.substring(0, 8)}`,
            use_secure_cookie: false,
          },
          cors_config: { allow_origins: [backendUrl] },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          grant_types_supported: ["authorization_code", "refresh_token", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management"],
          claims_supported: ["sub", "iss", "auth_time", "acr", "name", "email", "email_verified"],
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
          grant_types: ["authorization_code", "refresh_token", "password"],
          scope: "openid profile email management",
          client_name: "MFA Bind Test Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);

    const mgmtTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: adminEmail,
      password: adminPassword,
      scope: "management",
      clientId,
      clientSecret,
    });
    expect(mgmtTokenResponse.status).toBe(200);
    mgmtAccessToken = mgmtTokenResponse.data.access_token;

    // initial-registration config (used to create test users)
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

    // password authentication config
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
            execution: { function: "password_verification" },
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

    // email authentication config (http_request to mock server, returns verification_code)
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "email",
        attributes: {},
        metadata: {
          type: "external",
          description: "Email authentication via mock",
          sender: "test@example.com",
          transaction_id_param: "transaction_id",
          verification_code_param: "verification_code",
          templates: {
            authentication: { subject: "Verification Code", body: "Code: {VERIFICATION_CODE}" },
          },
          retry_count_limitation: 5,
          expire_seconds: 300,
        },
        interactions: {
          "email-authentication-challenge": {
            request: {
              schema: {
                type: "object",
                properties: { email: { type: "string" }, template: { type: "string" } },
              },
            },
            execution: {
              function: "http_request",
              http_request: {
                url: "http://host.docker.internal:4000/email-authentication-challenge",
                method: "POST",
                oauth_authorization: {
                  type: "password",
                  token_endpoint: "http://host.docker.internal:4000/token",
                  client_id: "your-client-id",
                  username: "username",
                  password: "password",
                  scope: "application",
                },
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }],
              },
              http_request_store: {
                key: "email-authentication-challenge",
                interaction_mapping_rules: [
                  { from: "$.response_body.transaction_id", to: "transaction_id" },
                ],
              },
            },
            response: {
              body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }],
            },
          },
          "email-authentication": {
            request: {
              schema: {
                type: "object",
                properties: { verification_code: { type: "string" } },
              },
            },
            execution: {
              function: "http_request",
              previous_interaction: { key: "email-authentication-challenge" },
              http_request: {
                url: "http://host.docker.internal:4000/email-authentication",
                method: "POST",
                oauth_authorization: {
                  type: "password",
                  token_endpoint: "http://host.docker.internal:4000/token",
                  client_id: "your-client-id",
                  username: "username",
                  password: "password",
                  scope: "application",
                },
                header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
                body_mapping_rules: [{ from: "$.request_body", to: "*" }],
              },
            },
            response: {
              body_mapping_rules: [{ from: "$.execution_http_request.response_body", to: "*" }],
            },
          },
        },
      },
    });

    // MFA policy: Email (1st) + Password (2nd, requires_user)
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "mfa_email_then_password",
            priority: 1,
            conditions: {},
            available_methods: ["password", "email", "initial-registration"],
            step_definitions: [
              { method: "email", order: 1, requires_user: false, user_identity_source: "email" },
              { method: "password", order: 2, requires_user: true },
            ],
            success_conditions: {
              any_of: [
                [
                  { path: "$.email-authentication.success_count", type: "integer", operation: "gte", value: 1 },
                  { path: "$.password-authentication.success_count", type: "integer", operation: "gte", value: 1 },
                ],
                [
                  { path: "$.initial-registration.success_count", type: "integer", operation: "gte", value: 1 },
                ],
              ],
            },
          },
        ],
      },
    });

    // Register Alice and Bob (distinct passwords)
    await registerUser(aliceEmail, alicePassword, "Alice");
    await registerUser(bobEmail, bobPassword, "Bob");
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

  async function registerUser(email, password, name) {
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `reg-${name}-${Date.now()}`,
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
    expect(regResp.status).toBe(200);

    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId,
      body: {},
    });
    const result = convertToAuthorizationResponse(authorizeResp.data.redirect_uri);
    await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: result.code,
      redirectUri,
      clientId,
      clientSecret,
    });
  }

  /** Start a fresh login flow and complete the email (1st) factor as the given user. */
  async function startLoginAndEmailFirstFactor(email) {
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `login-${Date.now()}`,
      scope: "openid profile email",
      redirectUri,
      prompt: "login",
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    const challengeResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication-challenge`,
      body: { email, template: "authentication" },
    });
    expect(challengeResp.status).toBe(200);
    const verificationCode = challengeResp.data.verification_code || "123456";

    const emailAuthResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication`,
      body: { verification_code: verificationCode },
    });
    expect(emailAuthResp.status).toBe(200);

    return authId;
  }

  function passwordSecondFactor(authId, username, password) {
    return postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId,
      body: { username, password },
    });
  }

  it("should REJECT password 2nd factor when credentials belong to a different user (identifier switching)", async () => {
    // 1st factor authenticates as Alice
    const authId = await startLoginAndEmailFirstFactor(aliceEmail);

    // Attacker submits Bob's (valid) credentials at the 2nd factor while the session is Alice.
    // Before #1396 fix: executor verified Bob and the flow proceeded as Alice (bypass).
    // After fix: the password is verified against the session user (Alice) → Bob's password fails.
    const resp = passwordSecondFactor(authId, bobEmail, bobPassword);
    const result = await resp;
    console.log("identifier-switching 2nd factor:", result.status, JSON.stringify(result.data));

    // Must fail with a credential mismatch: the submitted password is verified against the
    // SESSION user's (Alice's) hash, not the submitted username's (Bob's). Asserting the exact
    // status + error code (not merely !=200) proves the binding, not an unrelated 4xx/5xx.
    expect(result.status).toBe(400);
    expect(result.data.error).toBe("invalid_credentials");
  });

  it("should ALLOW password 2nd factor with the authenticated user's own credentials", async () => {
    const authId = await startLoginAndEmailFirstFactor(aliceEmail);

    const result = await passwordSecondFactor(authId, aliceEmail, alicePassword);
    console.log("legit 2nd factor:", result.status, JSON.stringify(result.data));

    expect(result.status).toBe(200);
  });

  it("should REJECT password 2nd factor when no 1st factor was completed (no authenticated user)", async () => {
    // Start a fresh flow but skip the email 1st factor; call the password (2nd factor) directly.
    // The early guard must reject before verification (requires_user && !hasUser).
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `login-noprior-${Date.now()}`,
      scope: "openid profile email",
      redirectUri,
      prompt: "login",
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    const result = await passwordSecondFactor(authId, aliceEmail, alicePassword);
    console.log("2nd factor without 1st factor:", result.status, JSON.stringify(result.data));

    expect(result.status).toBe(400);
    expect(result.data.error).toBe("user_not_found");
  });
});
