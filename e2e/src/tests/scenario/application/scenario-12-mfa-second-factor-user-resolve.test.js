import { describe, expect, it } from "@jest/globals";
import { requestToken } from "../../../api/oauthClient";
import { backendUrl, clientSecretPostClient, mockApiBaseUrl, serverConfig } from "../../testConfig";
import { post, postWithJson, get } from "../../../lib/http";
import { faker } from "@faker-js/faker";
import { v4 as uuidv4 } from "uuid";
import { requestAuthorizations } from "../../../oauth/request";
import { generateRS256KeyPair } from "../../../lib/jose";

/**
 * Issue #1497: MFA 2段目の PasswordAuthenticationInteractor で user_resolve が実行されることを検証
 *
 * テストフロー:
 * 1. テナント作成（認証ポリシー: email → password MFA）
 * 2. password 認証に外部認証 + user_mapping_rules（custom_properties マッピング）を設定
 * 3. email で初回登録（1st factor）
 * 4. password で外部認証（2nd factor）→ user_resolve で custom_properties がマージされること
 * 5. UserInfo で custom_properties が含まれることを検証
 */
describe("Issue #1497: MFA 2nd Factor user_resolve", () => {

  it("should apply user_mapping_rules on 2nd factor password authentication", async () => {
    console.log("\n=== Starting MFA 2nd Factor user_resolve Test ===\n");

    // Step 1: Get admin access token
    const adminTokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "password",
      username: serverConfig.oauth.username,
      password: serverConfig.oauth.password,
      scope: clientSecretPostClient.scope,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret
    });
    expect(adminTokenResponse.status).toBe(200);
    const adminAccessToken = adminTokenResponse.data.access_token;

    // Step 2: Create tenant
    const tenantId = uuidv4();
    const { jwks } = await generateRS256KeyPair();

    const tenantData = {
      tenant: {
        id: tenantId,
        name: "MFA user_resolve Test Tenant",
        domain: backendUrl,
        authorization_provider: "idp-server"
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantId}`,
        authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/.well-known/jwks.json`,
        jwks: jwks,
        scopes_supported: ["openid", "profile", "email", "claims:auth_source", "claims:external_verified"],
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        grant_types_supported: ["authorization_code"],
        token_endpoint_auth_methods_supported: ["client_secret_post"],
        id_token_signing_alg_values_supported: ["RS256"],
        claims_supported: ["sub", "name", "email", "email_verified", "preferred_username"],
        extension: {
          access_token_type: "JWT",
          access_token_duration: 3600,
          id_token_duration: 3600,
          access_token_user_custom_properties: true,
          custom_claims_scope_mapping: true
        }
      }
    };

    const createTenantResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: tenantData
    });
    expect(createTenantResponse.status).toBe(201);
    console.log(`Tenant created: ${tenantId}`);

    // Step 3: Register client
    const clientId = uuidv4();
    const clientSecret = uuidv4();
    const redirectUri = "http://localhost:8080/callback";

    const createClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        client_id: clientId,
        client_secret: clientSecret,
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code"],
        response_types: ["code"],
        scope: "openid profile email claims:auth_source claims:external_verified",
        token_endpoint_auth_method: "client_secret_post"
      }
    });
    expect(createClientResponse.status).toBe(201);
    console.log(`Client registered: ${clientId}`);

    // Step 4: Configure external password authentication with user_resolve
    const authConfigId = uuidv4();

    const authenticationConfig = {
      id: authConfigId,
      type: "password",
      attributes: {},
      interactions: {
        "password-authentication": {
          execution: {
            function: "http_request",
            http_request: {
              url: `${mockApiBaseUrl}/auth/password`,
              method: "POST",
              header_mapping_rules: [
                { "static_value": "application/json", "to": "Content-Type" }
              ],
              body_mapping_rules: [
                { "from": "$.request_body.username", "to": "username" },
                { "from": "$.request_body.password", "to": "password" }
              ]
            }
          },
          user_resolve: {
            user_mapping_rules: [
              { "from": "$.execution_http_request.response_body.user_id", "to": "external_user_id" },
              { "from": "$.execution_http_request.response_body.email", "to": "email" },
              { "from": "$.execution_http_request.response_body.name", "to": "name" },
              { "static_value": "mockoon", "to": "provider_id" },
              // custom_properties にマッピング: 外部認証の応答データを格納
              { "static_value": "external_authenticated", "to": "custom_properties.auth_source" },
              { "from": "$.execution_http_request.response_body.authenticated", "to": "custom_properties.external_verified" }
            ]
          },
          response: {
            body_mapping_rules: [
              { "from": "$.execution_http_request.response_body.user_id", "to": "user_id" }
            ]
          }
        }
      }
    };

    const createAuthConfigResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: authenticationConfig
    });
    console.log("Auth config response:", createAuthConfigResponse.status, JSON.stringify(createAuthConfigResponse.data, null, 2));
    expect(createAuthConfigResponse.status).toBe(201);
    console.log(`Authentication config created: ${authConfigId}`);

    // Step 5: Configure email authentication (no_action mode)
    const emailConfigId = uuidv4();

    const emailConfig = {
      id: emailConfigId,
      type: "email",
      attributes: {},
      metadata: {
        type: "external",
        transaction_id_param: "transaction_id",
        verification_code_param: "verification_code"
      },
      interactions: {
        "email-authentication-challenge": {
          request: {
            schema: {
              type: "object",
              properties: {
                email: { type: "string" }
              }
            }
          },
          execution: {
            function: "email_authentication_challenge",
            details: {
              function: "no_action",
              sender: "test@gmail.com",
              templates: {
                registration: {
                  subject: "Verification Code",
                  body: "Code: {VERIFICATION_CODE}"
                },
                authentication: {
                  subject: "Verification Code",
                  body: "Code: {VERIFICATION_CODE}"
                }
              },
              retry_count_limitation: 5,
              expire_seconds: 300
            }
          },
          response: {
            body_mapping_rules: [
              { "from": "$.response_body", "to": "*" }
            ]
          }
        },
        "email-authentication": {
          request: {
            schema: {
              type: "object",
              properties: {
                verification_code: { type: "string" }
              }
            }
          },
          execution: {
            function: "email_authentication"
          },
          response: {
            body_mapping_rules: [
              { "from": "$.response_body", "to": "*" }
            ]
          }
        }
      }
    };

    const createEmailConfigResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: emailConfig
    });
    console.log("Email config response:", createEmailConfigResponse.status);
    expect(createEmailConfigResponse.status).toBe(201);
    console.log(`Email authentication config created: ${emailConfigId}`);

    // Step 6: Configure MFA authentication policy (email → password)
    const policyId = uuidv4();

    const authenticationPolicy = {
      id: policyId,
      flow: "oauth",
      enabled: true,
      policies: [
        {
          description: "mfa_email_then_password",
          priority: 100,
          conditions: { scopes: ["openid"] },
          available_methods: ["email", "password", "initial-registration"],
          step_definitions: [
            {
              method: "email",
              order: 1,
              requires_user: false,
              allow_registration: true,
              user_identity_source: "email"
            },
            {
              method: "password",
              order: 2,
              requires_user: true,
              allow_registration: true
            }
          ],
          success_conditions: {
            any_of: [
              [
                { path: "$.password-authentication.success_count", type: "integer", operation: "gte", value: 1 }
              ],
              [
                { path: "$.initial-registration.success_count", type: "integer", operation: "gte", value: 1 }
              ]
            ]
          },
          failure_conditions: {
            any_of: [
              [
                { path: "$.password-authentication.failure_count", type: "integer", operation: "gte", value: 5 }
              ]
            ]
          }
        }
      ]
    };

    const createPolicyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: authenticationPolicy
    });
    console.log("Policy response:", createPolicyResponse.status, JSON.stringify(createPolicyResponse.data, null, 2));
    expect(createPolicyResponse.status).toBe(201);
    console.log(`Authentication policy created: ${policyId}`);

    // Step 6: Authorization flow - email (1st factor) + password (2nd factor)
    const userEmail = faker.internet.email();
    const testPassword = "MfaTestPass123!";

    const interaction = async (id) => {
      // 1st factor: email authentication challenge
      const challengeResponse = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/authorizations/${id}/email-authentication-challenge`,
        body: { email: userEmail, template: "registration" }
      });
      console.log("Email challenge response:", challengeResponse.status, challengeResponse.data);
      expect(challengeResponse.status).toBe(200);

      // Get verification_code via Management API
      const txResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/authentication-transactions?authorization_id=${id}`,
        headers: { Authorization: `Bearer ${adminAccessToken}` }
      });
      const transactionId = txResponse.data.list[0].id;

      const interactionResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
        headers: { Authorization: `Bearer ${adminAccessToken}` }
      });
      const verificationCode = interactionResponse.data.payload.verification_code;
      console.log("Verification code obtained via Management API");

      // 1st factor: email verification
      const emailVerifyResponse = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/authorizations/${id}/email-authentication`,
        body: { verification_code: verificationCode }
      });
      console.log("Email verify response:", emailVerifyResponse.status, emailVerifyResponse.data);
      expect(emailVerifyResponse.status).toBe(200);

      // 2nd factor: password authentication (external service + user_resolve)
      const passwordResponse = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/authorizations/${id}/password-authentication`,
        body: { username: userEmail, password: testPassword }
      });
      console.log("Password auth response:", passwordResponse.status, JSON.stringify(passwordResponse.data, null, 2));
      expect(passwordResponse.status).toBe(200);
    };

    const { authorizationResponse } = await requestAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      authorizeEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      denyEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/deny`,
      clientId: clientId,
      responseType: "code",
      state: `state_${Date.now()}`,
      scope: "openid profile email claims:auth_source claims:external_verified",
      redirectUri: redirectUri,
      user: { email: userEmail, password: testPassword },
      interaction,
    });

    expect(authorizationResponse.code).not.toBeNull();
    console.log("Authorization code obtained");

    // Step 7: Token exchange
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      code: authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: redirectUri,
      clientId: clientId,
      clientSecret: clientSecret,
    });
    console.log("Token response:", tokenResponse.status);
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data).toHaveProperty("access_token");

    // Step 8: Verify UserInfo includes custom_properties from 2nd factor user_resolve
    const userInfoResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/userinfo`,
      headers: { Authorization: `Bearer ${tokenResponse.data.access_token}` },
    });

    console.log("UserInfo response:", JSON.stringify(userInfoResponse.data, null, 2));
    expect(userInfoResponse.status).toBe(200);
    expect(userInfoResponse.data).toHaveProperty("sub");
    expect(userInfoResponse.data).toHaveProperty("email", userEmail);

    // custom_properties が 2nd factor の user_resolve でマージされ、claims:* scope で出力されていること
    expect(userInfoResponse.data).toHaveProperty("auth_source", "external_authenticated");
    expect(userInfoResponse.data).toHaveProperty("external_verified", true);

    console.log("custom_properties merged from 2nd factor user_resolve and exposed via claims:* scope");
    console.log("=== MFA 2nd Factor user_resolve Test Complete ===\n");
  }, 90000);

});
