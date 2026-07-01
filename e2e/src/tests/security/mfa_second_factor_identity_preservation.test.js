import { describe, expect, it } from "@jest/globals";
import { requestToken } from "../../api/oauthClient";
import { backendUrl, clientSecretPostClient, mockApiBaseUrl, serverConfig } from "../testConfig";
import { postWithJson, get } from "../../lib/http";
import { faker } from "@faker-js/faker";
import { v4 as uuidv4 } from "uuid";
import { requestAuthorizations } from "../../oauth/request";
import { generateRS256KeyPair } from "../../lib/jose";

/**
 * Security regression for Issue #1497 / #1515 (CWE-287, identity switching).
 *
 * The MFA 2nd-factor `user_resolve` must only enrich the already authenticated user with
 * non-identifying attributes. It must NOT let attacker-controlled input (submitted on the 2nd
 * factor) overwrite the identity established by the 1st factor. Here the 2nd-factor password
 * config maps `email` / `preferred_username` from extra request-body fields; the fix drops those
 * targets, so the session identity stays the 1st-factor user while custom_properties still enrich.
 *
 * Prerequisites: idp-server built WITH the #1515 fix + Mockoon at host.docker.internal:4000
 * (POST /auth/password echoes email from the submitted username).
 */
describe("Security: MFA 2nd factor cannot swap session identity (#1497/#1515)", () => {
  it("keeps the 1st-factor identity and ignores identity fields mapped on the 2nd factor", async () => {
    // Step 1: admin token
    const adminTokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "password",
      username: serverConfig.oauth.username,
      password: serverConfig.oauth.password,
      scope: clientSecretPostClient.scope,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(adminTokenResponse.status).toBe(200);
    const adminAccessToken = adminTokenResponse.data.access_token;

    // Step 2: tenant
    const tenantId = uuidv4();
    const { jwks } = await generateRS256KeyPair();
    const createTenantResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        tenant: {
          id: tenantId,
          name: "MFA identity preservation Tenant",
          domain: backendUrl,
          authorization_provider: "idp-server",
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/.well-known/jwks.json`,
          jwks: jwks,
          scopes_supported: ["openid", "profile", "email", "claims:auth_source"],
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
            custom_claims_scope_mapping: true,
          },
        },
      },
    });
    expect(createTenantResponse.status).toBe(201);

    // Step 3: client
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
        scope: "openid profile email claims:auth_source",
        token_endpoint_auth_method: "client_secret_post",
      },
    });
    expect(createClientResponse.status).toBe(201);

    // Step 4: 2nd-factor password config whose user_resolve ADVERSARIALLY maps identity fields
    // from attacker-controlled extra request-body fields, plus a legitimate custom_property.
    const createAuthConfigResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
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
                  { static_value: "application/json", to: "Content-Type" },
                ],
                body_mapping_rules: [
                  { from: "$.request_body.username", to: "username" },
                  { from: "$.request_body.password", to: "password" },
                ],
              },
            },
            user_resolve: {
              user_mapping_rules: [
                // Adversarial: attempt to overwrite the 1st-factor identity from submitted input.
                { from: "$.request_body.injected_email", to: "email" },
                { from: "$.request_body.injected_username", to: "preferred_username" },
                // Legitimate enrichment.
                { static_value: "external_authenticated", to: "custom_properties.auth_source" },
              ],
            },
            response: { body_mapping_rules: [] },
          },
        },
      },
    });
    expect(createAuthConfigResponse.status).toBe(201);

    // Step 5: email (1st factor) config, no_action mode
    const createEmailConfigResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
        type: "email",
        attributes: {},
        metadata: {
          type: "external",
          transaction_id_param: "transaction_id",
          verification_code_param: "verification_code",
        },
        interactions: {
          "email-authentication-challenge": {
            request: { schema: { type: "object", properties: { email: { type: "string" } } } },
            execution: {
              function: "email_authentication_challenge",
              details: {
                function: "no_action",
                sender: "test@gmail.com",
                templates: {
                  registration: { subject: "Verification Code", body: "Code: {VERIFICATION_CODE}" },
                  authentication: { subject: "Verification Code", body: "Code: {VERIFICATION_CODE}" },
                },
                retry_count_limitation: 5,
                expire_seconds: 300,
              },
            },
            response: { body_mapping_rules: [{ from: "$.response_body", to: "*" }] },
          },
          "email-authentication": {
            request: {
              schema: { type: "object", properties: { verification_code: { type: "string" } } },
            },
            execution: { function: "email_authentication" },
            response: { body_mapping_rules: [{ from: "$.response_body", to: "*" }] },
          },
        },
      },
    });
    expect(createEmailConfigResponse.status).toBe(201);

    // Step 6: MFA policy (email -> password)
    const createPolicyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: uuidv4(),
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
                user_identity_source: "email",
              },
              { method: "password", order: 2, requires_user: true, allow_registration: true },
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
                    value: 5,
                  },
                ],
              ],
            },
          },
        ],
      },
    });
    expect(createPolicyResponse.status).toBe(201);

    // Step 7: run the flow. 1st factor establishes the victim identity (userEmail); the 2nd
    // factor password submits injected identity fields that must be ignored.
    const userEmail = faker.internet.email();
    const testPassword = "MfaTestPass123!";
    const injectedEmail = "attacker@evil.example.com";
    const injectedUsername = "attacker";

    const interaction = async (id) => {
      const challengeResponse = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/authorizations/${id}/email-authentication-challenge`,
        body: { email: userEmail, template: "registration" },
      });
      expect(challengeResponse.status).toBe(200);

      const txResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/authentication-transactions?authorization_id=${id}`,
        headers: { Authorization: `Bearer ${adminAccessToken}` },
      });
      const transactionId = txResponse.data.list[0].id;
      const interactionResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
        headers: { Authorization: `Bearer ${adminAccessToken}` },
      });
      const verificationCode = interactionResponse.data.payload.verification_code;

      const emailVerifyResponse = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/authorizations/${id}/email-authentication`,
        body: { verification_code: verificationCode },
      });
      expect(emailVerifyResponse.status).toBe(200);

      // 2nd factor: password with adversarial injected identity fields.
      const passwordResponse = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/authorizations/${id}/password-authentication`,
        body: {
          username: userEmail,
          password: testPassword,
          injected_email: injectedEmail,
          injected_username: injectedUsername,
        },
      });
      console.log("2nd factor response:", passwordResponse.status, JSON.stringify(passwordResponse.data, null, 2));
      expect(passwordResponse.status).toBe(200);
    };

    const { authorizationResponse } = await requestAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      authorizeEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      denyEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/deny`,
      clientId: clientId,
      responseType: "code",
      state: `state_${Date.now()}`,
      scope: "openid profile email claims:auth_source",
      redirectUri: redirectUri,
      user: { email: userEmail, password: testPassword },
      interaction,
    });
    expect(authorizationResponse.code).not.toBeNull();

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      code: authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: redirectUri,
      clientId: clientId,
      clientSecret: clientSecret,
    });
    expect(tokenResponse.status).toBe(200);

    const userInfoResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/userinfo`,
      headers: { Authorization: `Bearer ${tokenResponse.data.access_token}` },
    });
    console.log("UserInfo:", JSON.stringify(userInfoResponse.data, null, 2));
    expect(userInfoResponse.status).toBe(200);

    // SECURITY: the 1st-factor identity is preserved; injected identity fields are ignored.
    expect(userInfoResponse.data.email).toBe(userEmail);
    expect(userInfoResponse.data.email).not.toBe(injectedEmail);
    expect(userInfoResponse.data.preferred_username).not.toBe(injectedUsername);

    // Enrichment still works: the non-identifying custom property is applied.
    expect(userInfoResponse.data.auth_source).toBe("external_authenticated");
  }, 90000);
});
