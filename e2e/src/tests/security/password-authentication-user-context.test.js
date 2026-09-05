import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { requestToken } from "../../api/oauthClient";
import { backendUrl, clientSecretPostClient, mockApiBaseUrl, serverConfig } from "../testConfig";
import { postWithJson, get, deletion } from "../../lib/http";
import { faker } from "@faker-js/faker";
import { v4 as uuidv4 } from "uuid";
import { requestAuthorizations } from "../../oauth/request";
import { generateRS256KeyPair } from "../../lib/jose";

/**
 * Issue #1862: the `execution` of a password interaction must receive the same allow-listed
 * `$.user.*` projection that `external-api` already sends (#1439) and that `user_resolve` already
 * receives (#1767).
 *
 * Without it a 2nd-factor `http_request` can only build an external lookup key from what the
 * client submitted, so there is no way for the configuration to check that the key belongs to the
 * authenticated user. The counterpart for `external-api` lives in
 * external-api-authentication-2nd-factor-bypass.test.js.
 *
 * Prerequisites: Mockoon at host.docker.internal:4000 (POST /e2e/echo-user-context reflects the
 * received body fields back).
 */
describe("Security: password execution receives the authenticated user projection (#1862)", () => {
  let adminAccessToken;
  let tenantId;
  let clientId;
  let clientSecret;
  const redirectUri = "http://localhost:8080/callback";
  const scope = "openid profile email claims:auth_source";

  beforeAll(async () => {
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
    adminAccessToken = adminTokenResponse.data.access_token;

    tenantId = uuidv4();
    const { jwks } = await generateRS256KeyPair();
    const createTenantResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        tenant: {
          id: tenantId,
          name: "Password user context Tenant",
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

    clientId = uuidv4();
    clientSecret = uuidv4();
    const createClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        client_id: clientId,
        client_secret: clientSecret,
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code"],
        response_types: ["code"],
        scope: scope,
        token_endpoint_auth_method: "client_secret_post",
      },
    });
    expect(createClientResponse.status).toBe(201);

    // 2nd-factor password whose execution maps ONLY $.user.* into the external request body. The
    // echo mock reflects what it received, so the assertions prove the projection reached the
    // external call rather than being silently resolved to null.
    const createPasswordConfigResponse = await postWithJson({
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
                url: `${mockApiBaseUrl}/e2e/echo-user-context`,
                method: "POST",
                header_mapping_rules: [
                  { static_value: "application/json", to: "Content-Type" },
                ],
                body_mapping_rules: [
                  { from: "$.user.email", to: "email" },
                  { from: "$.user.sub", to: "user_id" },
                  // Allow-list probes: the projection never exposes these, so the external API
                  // must receive nothing for them (fail-safe regression guard).
                  { from: "$.user.hashed_password", to: "hashed_password" },
                  { from: "$.user.verified_claims", to: "verified_claims" },
                ],
              },
            },
            user_resolve: {
              user_mapping_rules: [
                { static_value: "external_authenticated", to: "custom_properties.auth_source" },
              ],
            },
            response: {
              body_mapping_rules: [
                {
                  from: "$.execution_http_request.response_body.received_email",
                  to: "echoed_email",
                },
                {
                  from: "$.execution_http_request.response_body.received_sub",
                  to: "echoed_sub",
                },
                {
                  from: "$.execution_http_request.response_body.received_hashed_password",
                  to: "echoed_hashed_password",
                },
                {
                  from: "$.execution_http_request.response_body.received_verified_claims",
                  to: "echoed_verified_claims",
                },
              ],
            },
          },
        },
      },
    });
    expect(createPasswordConfigResponse.status).toBe(201);

    // email (1st factor), no_action mode: establishes the authenticated user.
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
          },
        ],
      },
    });
    expect(createPolicyResponse.status).toBe(201);
  }, 60000);

  /**
   * Runs email (1st factor) then password (2nd factor) and returns the password response plus the
   * userinfo of the resulting session, so a test can compare what the external API received
   * against the identity the server actually established.
   */
  const runFlow = async (extraPasswordBody = {}) => {
    const userEmail = faker.internet.email();
    const testPassword = "PasswordUserContext123!";
    let passwordResponse;

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

      passwordResponse = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/authorizations/${id}/password-authentication`,
        body: { username: userEmail, password: testPassword, ...extraPasswordBody },
      });
      expect(passwordResponse.status).toBe(200);
    };

    const { authorizationResponse } = await requestAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      authorizeEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      denyEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/deny`,
      clientId: clientId,
      responseType: "code",
      state: `state_${Date.now()}`,
      scope: scope,
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
    expect(userInfoResponse.status).toBe(200);

    return { userEmail, passwordResponse, userInfo: userInfoResponse.data };
  };

  afterAll(async () => {
    if (!adminAccessToken || !tenantId) {
      return;
    }
    // The client, the authentication configurations and the policy cascade with the tenant, so
    // deleting it leaves the shared organization clean. The users created by runFlow() outlive it:
    // idp_user has no FK to tenant (#832).
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
    }).catch(() => {});
  });

  it("should forward the authenticated user ($.user.*) to the password execution on a 2nd factor", async () => {
    const { userEmail, passwordResponse, userInfo } = await runFlow();

    // The external API received the identity the 1st factor established, without the client
    // having to resend it.
    expect(passwordResponse.data.echoed_email).toBe(userEmail);
    expect(passwordResponse.data.echoed_sub).toBe(userInfo.sub);
  }, 90000);

  it("should NOT egress allow-list-excluded user fields ($.user.hashed_password / $.user.verified_claims)", async () => {
    const { userEmail, passwordResponse } = await runFlow();

    expect(passwordResponse.data.echoed_email).toBe(userEmail); // control: egress works
    // Excluded fields resolve to nothing in the projection, so no real value is egressed. The
    // mapping writes a null for the missing source path (echoed back as "" or "null"); either way
    // the actual secret never leaves.
    expect(["", "null", null, undefined]).toContain(
      passwordResponse.data.echoed_hashed_password
    );
    expect(["", "null", null, undefined]).toContain(
      passwordResponse.data.echoed_verified_claims
    );
  }, 90000);

  it("should NOT let the caller request body spoof $.user (top-level, server-injected)", async () => {
    // An injected "user" object lands under $.request_body.user and never overwrites the
    // server-injected top-level $.user.
    const { userEmail, passwordResponse } = await runFlow({
      user: { email: "attacker@evil.example.com", sub: "attacker-sub" },
    });

    expect(passwordResponse.data.echoed_email).toBe(userEmail);
    expect(passwordResponse.data.echoed_sub).not.toBe("attacker-sub");
  }, 90000);
});
