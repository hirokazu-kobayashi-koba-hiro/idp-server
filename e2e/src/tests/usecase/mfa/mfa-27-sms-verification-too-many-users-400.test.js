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
 * Issue #1395: SmsAuthenticationInteractor must convert UserTooManyFoundResultException
 * (thrown by resolveUser -> findByPhone when several users share a phone number) into a
 * 400 invalid_request, instead of letting it escape as a 500.
 *
 * Why the existing mfa-02 "too many" case does NOT cover this: it submits a WRONG OTP, so
 * the execution fails first and the interactor returns before reaching resolveUser (the
 * too-many only surfaces inside the guarded tryResolveUserForLogging helper). The #1395 path
 * needs a SUCCESSFUL OTP verification so resolveUser actually runs findByPhone.
 *
 * Setup: SMS as the 1st factor (requires_user=false => phone-based identification), internal
 * OTP (sender_type=no_action so the code is generated inside idp-server and read back via the
 * Management API), and two users registered under the SAME phone number (EMAIL uniqueness lets
 * two distinct emails share a phone). Verifying with the correct OTP then triggers findByPhone
 * returning >1 user.
 */
describe("MFA Use Case: SMS verification returns 400 (not 500) when phone maps to many users (Issue #1395)", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let mgmtAccessToken;
  const sharedPhone = `+8190${Math.floor(10000000 + Math.random() * 90000000)}`;
  const redirectUri =
    "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

  const smsTemplate = {
    subject: "[ID Verification] Your login sms confirmation code",
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
    const adminEmail = `admin-${timestamp}@mfa-sms-toomany.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    const onboardingResponse = await onboarding({
      body: {
        organization: {
          id: organizationId,
          name: `MFA SMS TooMany Org ${timestamp}`,
          description: "E2E test for SMS verification too-many users (#1395)",
        },
        tenant: {
          id: tenantId,
          name: `MFA SMS TooMany Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: {
            // EMAIL uniqueness lets two users share a phone number.
            identity_unique_key_type: "EMAIL",
          },
          session_config: {
            cookie_name: `MFA_SMS_TOOMANY_${organizationId.substring(0, 8)}`,
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
          raw_password: adminPassword,
        },
        client: {
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uris: [redirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code", "refresh_token", "password"],
          scope: "openid profile email phone management org-management",
          client_name: "MFA SMS TooMany Test Client",
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

    // initial-registration config (phone_number captured at registration time)
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
                required: ["email", "password", "name"],
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

    // SMS authentication config: internal OTP + no_action sender (code readable via Management API)
    const smsAuthResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        type: "sms",
        attributes: {},
        metadata: {
          type: "internal",
          transaction_id_param: "transaction_id",
          verification_code_param: "verification_code",
        },
        interactions: {
          "sms-authentication-challenge": {
            request: {
              schema: {
                type: "object",
                properties: {
                  phone_number: { type: "string" },
                  template: { type: "string" },
                },
              },
            },
            pre_hook: {},
            execution: {
              function: "sms_authentication_challenge",
              details: {
                sender_type: "no_action",
                templates: {
                  registration: smsTemplate,
                  authentication: smsTemplate,
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
          "sms-authentication": {
            request: {
              schema: {
                type: "object",
                properties: {
                  verification_code: { type: "string" },
                },
              },
            },
            execution: {
              function: "sms_authentication",
              details: {
                retry_count_limitation: 5,
                expire_seconds: 300,
              },
            },
          },
        },
      },
    });
    expect(smsAuthResp.status).toBe(201);

    // Authentication policy: SMS as 1st factor (phone-based identification) + registration
    const authPolicyResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "sms_first_factor_too_many_1395",
            priority: 1,
            conditions: {},
            available_methods: ["sms", "initial-registration"],
            step_definitions: [
              {
                method: "sms",
                order: 1,
                requires_user: false,
              },
            ],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.sms-authentication.success_count",
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

    // Register two users that share the same phone number.
    for (const label of ["one", "two"]) {
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
          email: `sms-toomany-${label}-${timestamp}@example.com`,
          password: "SmsTooManyPass_1!",
          name: `SMS TooMany User ${label}`,
          phone_number: sharedPhone,
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

  it("returns 400 invalid_request (not 500) when the verified phone number matches multiple users", async () => {
    // Step 1: Start a fresh SMS-first authorization flow
    const state = `mfa-sms-toomany-${Date.now()}`;
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state,
      scope: "openid profile email phone",
      redirectUri,
      prompt: "login",
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");
    expect(authId).toBeDefined();

    // Step 2: SMS challenge with the shared phone (challenge does NOT resolve the user, so it succeeds)
    const challengeResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/sms-authentication-challenge`,
      body: {
        phone_number: sharedPhone,
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
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-interactions/${transactionId}/sms-authentication-challenge`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    });
    expect(interactionResp.status).toBe(200);
    const verificationCode = interactionResp.data.payload.verification_code;
    expect(verificationCode).toBeDefined();

    // Step 4: Verify with the CORRECT OTP. Execution succeeds, so resolveUser runs findByPhone,
    // which matches both users -> UserTooManyFoundResultException.
    // Before #1395 this escaped as a 500; it must now be a 400 invalid_request.
    const smsAuthResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/sms-authentication`,
      body: {
        verification_code: verificationCode,
      },
    });

    expect(smsAuthResp.status).toBe(400);
    expect(smsAuthResp.data.error).toBe("invalid_request");
    expect(smsAuthResp.data.error_description).toContain(
      "too many users found for phone number"
    );
  });
});
