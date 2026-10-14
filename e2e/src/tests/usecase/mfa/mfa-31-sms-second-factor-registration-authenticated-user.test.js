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
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
  createBearerHeader,
} from "../../../lib/util";

/**
 * MFA Use Case: registering a phone number on the SMS 2nd factor (Issue #1973)
 *
 * With `allow_registration: true` on the 2nd factor, a user without a phone number registers one
 * during the SMS step. The number is written to the user, so the user must be authenticated in
 * this transaction. A user only identified by login_hint is not enough.
 *
 * Setup: Password (1st) + SMS (2nd, requires_user, allow_registration), internal OTP
 * (sender_type=no_action, code read back via the Management API). Each test creates its own user.
 */
describe("MFA Use Case: SMS 2nd factor registers a phone number only for an authenticated user", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let mgmtAccessToken;
  const testPassword = "SmsRegPass_1!";
  const redirectUri =
    "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

  const smsTemplate = {
    subject: "Verification Code",
    body: "Code: {VERIFICATION_CODE}",
  };

  const newPhone = () => `+8190${Math.floor(10000000 + Math.random() * 90000000)}`;

  const startAuthorization = async (loginHint) => {
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `sms-reg-${Date.now()}`,
      scope: "openid profile email phone",
      redirectUri,
      prompt: "login",
      loginHint,
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    return params.get("id");
  };

  const usersUrl = () =>
    `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users`;

  const createUser = async ({ phoneNumber } = {}) => {
    const sub = uuidv4();
    const email = `sms-reg-user-${sub}@mfa-sms-reg.example.com`;
    const createResp = await postWithJson({
      url: usersUrl(),
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        sub,
        provider_id: "idp-server",
        name: "MFA SMS Registration User",
        email,
        email_verified: true,
        ...(phoneNumber ? { phone_number: phoneNumber, phone_number_verified: true } : {}),
        raw_password: testPassword,
      },
    });
    expect(createResp.status).toBe(201);
    return { sub, email };
  };

  const getUser = async (sub) => {
    const resp = await get({
      url: `${usersUrl()}/${sub}`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
    });
    expect(resp.status).toBe(200);
    return resp.data;
  };

  const readVerificationCode = async (authId) => {
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
    return interactionResp.data.payload.verification_code;
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
    clientId = uuidv4();
    clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}@mfa-sms-reg.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    const onboardingResponse = await onboarding({
      body: {
        organization: {
          id: organizationId,
          name: `MFA SMS Registration Org ${timestamp}`,
          description: "E2E test for SMS 2nd factor registration",
        },
        tenant: {
          id: tenantId,
          name: `MFA SMS Registration Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL",
          },
          session_config: {
            cookie_name: `MFA_SMS_REG_${organizationId.substring(0, 8)}`,
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
          grant_types_supported: ["authorization_code", "refresh_token", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "phone", "management", "org-management"],
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
          scope: "openid profile email phone management org-management",
          client_name: "MFA SMS Registration Test Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
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

    const configurationsUrl = `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`;

    const passwordAuthResp = await postWithJson({
      url: configurationsUrl,
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
            response: { body_mapping_rules: [] },
          },
        },
      },
    });
    expect(passwordAuthResp.status).toBe(201);

    const smsAuthResp = await postWithJson({
      url: configurationsUrl,
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

    const authPolicyResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: {
        id: uuidv4(),
        flow: "oauth",
        enabled: true,
        policies: [
          {
            description: "mfa_password_sms_registration",
            priority: 1,
            conditions: {},
            available_methods: ["password", "sms"],
            step_definitions: [
              {
                method: "password",
                order: 1,
                requires_user: false,
                user_identity_source: "username",
              },
              {
                method: "sms",
                order: 2,
                requires_user: true,
                allow_registration: true,
              },
            ],
            success_conditions: {
              any_of: [
                [
                  {
                    path: "$.password-authentication.success_count",
                    type: "integer",
                    operation: "gte",
                    value: 1,
                  },
                  {
                    path: "$.sms-authentication.success_count",
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

  it("does not register a phone number for a user only identified by login_hint", async () => {
    const user = await createUser();
    const authId = await startAuthorization(`email:${user.email}`);

    const challengeResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/sms-authentication-challenge`,
      body: { phone_number: newPhone(), template: "registration" },
    });

    expect(challengeResp.status).toBe(400);
    expect(challengeResp.data.error).toBe("unauthorized");
    expect(challengeResp.data.error_description).toBe(
      "User must be authenticated before registering a phone number."
    );
    expect((await getUser(user.sub)).phone_number).toBeUndefined();
  });

  it("sends the code to a user who already has a phone number, even if only identified by login_hint", async () => {
    const phone = newPhone();
    const user = await createUser({ phoneNumber: phone });
    const authId = await startAuthorization(`email:${user.email}`);

    const challengeResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/sms-authentication-challenge`,
      body: { template: "authentication" },
    });

    expect(challengeResp.status).toBe(200);
    expect((await getUser(user.sub)).phone_number).toBe(phone);
  });

  it("registers a phone number after the 1st factor succeeds", async () => {
    const user = await createUser();
    const authId = await startAuthorization(`email:${user.email}`);

    const passwordResp = await postAuthentication({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/password-authentication`,
      id: authId,
      body: { username: user.email, password: testPassword },
    });
    expect(passwordResp.status).toBe(200);

    const phone = newPhone();
    const challengeResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/sms-authentication-challenge`,
      body: { phone_number: phone, template: "registration" },
    });
    expect(challengeResp.status).toBe(200);

    const smsAuthResp = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/sms-authentication`,
      body: { verification_code: await readVerificationCode(authId) },
    });
    expect(smsAuthResp.status).toBe(200);

    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId,
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

    const userinfoResp = await getUserinfo({
      endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
      authorizationHeader: createBearerHeader(tokenResp.data.access_token),
    });
    expect(userinfoResp.status).toBe(200);
    expect(userinfoResp.data.email).toBe(user.email);
    expect(userinfoResp.data.phone_number).toBe(phone);
  });
});
