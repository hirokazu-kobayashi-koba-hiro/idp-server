import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, get, postWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  authorize,
} from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { convertNextAction } from "../../../lib/util";

/**
 * Issue #1395 / #1667 (Email side) + #1669.
 *
 * - #1395: EmailAuthenticationInteractor must convert UserTooManyFoundResultException (thrown by
 *   resolveUser -> findByEmail when several users share an email) into 400 invalid_request, not 500.
 * - #1669: InitialRegistrationInteractor must dedupe by the tenant's unique key (preferred_username),
 *   not a hardcoded email. With identity_unique_key_type=PHONE two users may share an email, so
 *   self-registration of the second shared-email user must succeed (it used to 400-conflict).
 *
 * This test exercises both: it self-registers two users with the SAME email and DISTINCT phones
 * (only possible once #1669 is fixed), then drives a 1st-factor email login whose verification hits
 * findByEmail -> multiple users -> 400 (the #1395 fix).
 *
 * Email is the 1st factor (no step definition => requires_user=false => email-based identification),
 * internal OTP (details.function=no_action so the code is read back via the Management API).
 */
describe("MFA Use Case: Email verification returns 400 (not 500) when email maps to many users (Issue #1395 / #1669)", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let mgmtAccessToken;
  const sharedEmail = `shared-${Date.now()}@mfa-email-toomany.example.com`;
  const redirectUri =
    "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

  const emailTemplate = {
    subject: "[ID Verification] Your login email confirmation code",
    body: "Hello,\n\nPlease enter the following verification code:\n\n【{VERIFICATION_CODE}】\n\nThis code will expire in {EXPIRE_SECONDS} seconds.\n\n– IDP Support",
  };

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
    const adminEmail = `admin-${timestamp}@mfa-email-toomany.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;
    // PHONE uniqueness => preferred_username is derived from the phone, so admin must log in by phone.
    const adminPhone = `+8190${Math.floor(10000000 + Math.random() * 90000000)}`;

    const onboardingResponse = await onboarding({
      body: {
        organization: {
          id: organizationId,
          name: `MFA Email TooMany Org ${timestamp}`,
          description: "E2E test for Email verification too-many users (#1395/#1667/#1669)",
        },
        tenant: {
          id: tenantId,
          name: `MFA Email TooMany Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: {
            // PHONE uniqueness lets two users share one email (their phones differ).
            identity_unique_key_type: "PHONE",
          },
          session_config: {
            cookie_name: `MFA_EMAIL_TOOMANY_${organizationId.substring(0, 8)}`,
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
            "phone",
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
          phone_number: adminPhone,
          raw_password: adminPassword,
        },
        client: {
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uris: [redirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code", "refresh_token", "password"],
          scope: "openid profile email phone management org-management",
          client_name: "MFA Email TooMany Test Client",
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
      username: adminPhone,
      password: adminPassword,
      scope: "management org-management",
      clientId,
      clientSecret,
    });
    expect(mgmtTokenResponse.status).toBe(200);
    mgmtAccessToken = mgmtTokenResponse.data.access_token;

    // initial-registration config (phone_number required so each user gets a distinct unique key)
    const initialRegResp = await postWithJson({
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
                required: ["email", "password", "name", "phone_number"],
                properties: {
                  name: { type: "string", maxLength: 255 },
                  email: { type: "string", format: "email", maxLength: 255 },
                  password: { type: "string", maxLength: 64, minLength: 8 },
                  phone_number: { type: "string", maxLength: 255 },
                },
              },
            },
          },
        },
      },
    });
    expect(initialRegResp.status).toBe(201);

    // Email authentication config: internal OTP + no_action sender (code readable via Management API)
    const emailAuthResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "email",
        attributes: {},
        metadata: {
          type: "no-action",
          transaction_id_param: "transaction_id",
          verification_code_param: "verification_code",
        },
        interactions: {
          "email-authentication-challenge": {
            request: {
              schema: {
                type: "object",
                properties: {
                  email: { type: "string" },
                  template: { type: "string" },
                },
              },
            },
            pre_hook: {},
            execution: {
              function: "email_authentication_challenge",
              details: {
                function: "no_action",
                sender: "noreply@test.example.com",
                templates: {
                  registration: emailTemplate,
                  authentication: emailTemplate,
                },
                retry_count_limitation: 5,
                expire_seconds: 300,
              },
            },
            post_hook: {},
            response: {
              body_mapping_rules: [{ from: "$.response_body", to: "*" }],
            },
          },
          "email-authentication": {
            request: {
              schema: {
                type: "object",
                properties: {
                  verification_code: { type: "string" },
                },
              },
            },
            execution: {
              function: "email_authentication",
            },
            response: {
              body_mapping_rules: [{ from: "$.response_body", to: "*" }],
            },
          },
        },
      },
    });
    expect(emailAuthResp.status).toBe(201);

    // Authentication policy: email as 1st factor (no step definition => email-based identification)
    const authPolicyResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "email_first_factor_too_many_1395",
            priority: 1,
            conditions: {},
            available_methods: ["email", "initial-registration"],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.email-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                ],
                [
                  {
                    path: "$.initial-registration.success_count",
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
    expect(authPolicyResp.status).toBe(201);

    // Register two users that share the same email but have distinct phone numbers (unique key).
    // The second shared-email registration succeeds only because of the #1669 fix (dedupe by
    // preferred_username/phone, not email).
    for (const label of ["one", "two"]) {
      const distinctPhone = `+8190${Math.floor(10000000 + Math.random() * 90000000)}`;
      const regAuthResp = await getAuthorizations({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        clientId,
        responseType: "code",
        state: `reg-${label}-${timestamp}`,
        scope: "openid profile email phone",
        redirectUri,
      });
      expect(regAuthResp.status).toBe(302);
      const { params } = convertNextAction(regAuthResp.headers.location);
      const regAuthId = params.get("id");

      const regResp = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/authorizations/${regAuthId}/initial-registration`,
        body: {
          email: sharedEmail,
          password: "EmailTooManyPass_1!",
          name: `Email TooMany User ${label}`,
          phone_number: distinctPhone,
        },
      });
      expect(regResp.status).toBe(200);

      await authorize({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
        id: regAuthId,
        body: {},
      });
    }
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

  it("returns 400 invalid_request (not 500) when the verified email matches multiple users", async () => {
    // Step 1: Start a fresh email-first authorization flow
    const state = `mfa-email-toomany-${Date.now()}`;
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state,
      scope: "openid profile email",
      redirectUri,
      prompt: "login",
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");
    expect(authId).toBeDefined();

    // Step 2: Email challenge with the shared email (challenge does NOT resolve the user, so it succeeds)
    const challengeResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication-challenge`,
      body: {
        email: sharedEmail,
        template: "authentication",
      },
    });
    expect(challengeResp.status).toBe(200);

    // Step 3: Read the internally-generated OTP via the Management API
    const txnListResp = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-transactions?authorization_id=${authId}`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    });
    expect(txnListResp.status).toBe(200);
    const transactionId = txnListResp.data.list[0].id;

    const interactionResp = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    });
    expect(interactionResp.status).toBe(200);
    const verificationCode = interactionResp.data.payload.verification_code;
    expect(verificationCode).toBeDefined();

    // Step 4: Verify with the CORRECT OTP. Execution succeeds, so resolveUser runs findByEmail,
    // which matches both users -> UserTooManyFoundResultException.
    // Before #1395 this escaped as a 500; it must now be a 400 invalid_request.
    const emailAuthResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/email-authentication`,
      body: {
        verification_code: verificationCode,
      },
    });

    expect(emailAuthResp.status).toBe(400);
    expect(emailAuthResp.data.error).toBe("invalid_request");
    expect(emailAuthResp.data.error_description).toContain(
      "too many users found for email"
    );
  });
});
