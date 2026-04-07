import { describe, expect, it, beforeAll } from "@jest/globals";
import { get, postWithJson, post } from "../../../lib/http";
import { requestToken, inspectToken, postAuthentication } from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { requestFederation } from "../../../oauth/federation";
import { requestAuthorizations } from "../../../oauth/request";

/**
 * Advance Use Case: SSO Credentials in Access Token Custom Claims
 *
 * Verifies that when access_token_sso_credentials=true and access_token_type=opaque,
 * the SSO provider's access token is included as a custom claim in the access token,
 * accessible via Token Introspection.
 *
 * Use Case: Migration pattern where a legacy IdP remains in use and idp-server provides
 * FIDO authentication / eKYC. The backend retrieves the legacy access token via Introspection.
 *
 * Test Flow:
 * 1. Create Consumer Tenant (opaque AT, access_token_sso_credentials=true)
 * 2. Create Provider Tenant (acts as legacy IdP)
 * 3. Configure federation with store_credentials=true
 * 4. Perform federation login (SSO credentials stored)
 * 5. Token Introspection → verify sso_provider + sso_access_token claims
 */
describe("Advance Use Case: SSO Credentials in Access Token (opaque AT + Introspection)", () => {
  let systemAccessToken;
  let orgAccessToken;
  let organizationId;
  let consumerTenantId;
  let providerTenantId;
  let consumerClientId;
  let consumerClientSecret;
  let providerClientId;
  let providerClientSecret;
  let providerUserEmail;
  let providerUserPassword;
  let providerUserSub;
  let federationConfigId;
  let ssoProvider;
  let orgAdminEmail;
  let orgAdminPassword;

  beforeAll(async () => {
    console.log("\n=== Setting up SSO Credentials Access Token Test ===\n");

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

    const timestamp = Date.now();
    organizationId = uuidv4();
    consumerTenantId = uuidv4();
    providerTenantId = uuidv4();
    consumerClientId = uuidv4();
    providerClientId = uuidv4();
    consumerClientSecret = `consumer-secret-${crypto.randomBytes(16).toString("hex")}`;
    providerClientSecret = `provider-secret-${crypto.randomBytes(16).toString("hex")}`;
    providerUserEmail = `sso-user-${timestamp}@provider.example.com`;
    providerUserPassword = `SsoPass${timestamp}!`;
    providerUserSub = uuidv4();
    federationConfigId = uuidv4();
    ssoProvider = `sso-provider-${timestamp}`;
    orgAdminEmail = `admin-${timestamp}@sso-consumer.example.com`;
    orgAdminPassword = `AdminPass${timestamp}!`;

    const consumerJwks = await generateECP256JWKS();
    const providerJwks = await generateECP256JWKS();

    // Step 1: Create Organization with Consumer Tenant (opaque AT + SSO credentials)
    console.log("Step 1: Creating organization with Consumer Tenant (opaque AT)...");
    const consumerOnboardingResponse = await onboarding({
      body: {
        organization: {
          id: organizationId,
          name: `SSO Credentials Test Org ${timestamp}`,
          description: "Test: SSO credentials in opaque access token",
        },
        tenant: {
          id: consumerTenantId,
          name: `SSO Consumer ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: {
            identity_unique_key_type: "EMAIL_OR_EXTERNAL_USER_ID",
          },
          session_config: {
            cookie_name: `SSO_C_${organizationId.substring(0, 8)}`,
            use_secure_cookie: false,
          },
          cors_config: {
            allow_origins: [backendUrl],
          },
        },
        authorization_server: {
          issuer: `${backendUrl}/${consumerTenantId}`,
          authorization_endpoint: `${backendUrl}/${consumerTenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${consumerTenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          userinfo_endpoint: `${backendUrl}/${consumerTenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${consumerTenantId}/v1/jwks`,
          jwks: consumerJwks,
          grant_types_supported: ["authorization_code", "refresh_token", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management", "org-management"],
          claims_supported: [
            "sub", "iss", "auth_time", "acr",
            "name", "given_name", "family_name",
            "email", "email_verified",
          ],
          response_types_supported: ["code"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          response_modes_supported: ["query"],
          extension: {
            access_token_type: "opaque",
            access_token_sso_credentials: true,
          },
        },
        user: {
          sub: uuidv4(),
          email: orgAdminEmail,
          raw_password: orgAdminPassword,
          username: orgAdminEmail,
          name: `SSO Admin ${timestamp}`,
          email_verified: true,
        },
        client: {
          client_id: consumerClientId,
          client_name: `SSO Consumer Client ${timestamp}`,
          client_secret: consumerClientSecret,
          redirect_uris: ["http://localhost:3000/callback"],
          response_types: ["code"],
          grant_types: ["authorization_code", "refresh_token", "password"],
          scope: "openid profile email management org-management",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
          extension: {
            available_federations: [
              {
                id: federationConfigId,
                type: "oidc",
                sso_provider: ssoProvider,
                auto_selected: true,
              },
            ],
          },
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    if (consumerOnboardingResponse.status !== 201) {
      console.error("Consumer onboarding failed:", JSON.stringify(consumerOnboardingResponse.data, null, 2));
    }
    expect(consumerOnboardingResponse.status).toBe(201);
    console.log(`Consumer Tenant created: ${consumerTenantId}`);

    // Step 2: Login as org admin
    console.log("Step 2: Login as org admin...");
    const orgAdminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${consumerTenantId}/v1/tokens`,
      grantType: "password",
      username: orgAdminEmail,
      password: orgAdminPassword,
      scope: "openid profile email management org-management",
      clientId: consumerClientId,
      clientSecret: consumerClientSecret,
    });
    expect(orgAdminTokenResponse.status).toBe(200);
    orgAccessToken = orgAdminTokenResponse.data.access_token;

    // Step 3: Create Provider Tenant
    console.log("Step 3: Creating Provider Tenant...");
    const providerTenantResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants`,
      body: {
        tenant: {
          id: providerTenantId,
          name: `SSO Provider ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          tenant_type: "BUSINESS",
          session_config: {
            cookie_name: `SSO_P_${organizationId.substring(0, 8)}`,
            use_secure_cookie: false,
          },
          cors_config: {
            allow_origins: [backendUrl],
          },
        },
        authorization_server: {
          issuer: `${backendUrl}/${providerTenantId}`,
          authorization_endpoint: `${backendUrl}/${providerTenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${providerTenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          userinfo_endpoint: `${backendUrl}/${providerTenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${providerTenantId}/v1/jwks`,
          jwks: providerJwks,
          grant_types_supported: ["authorization_code", "refresh_token", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email"],
          claims_supported: ["sub", "name", "email", "email_verified"],
          response_types_supported: ["code"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          response_modes_supported: ["query"],
          extension: {
            access_token_type: "opaque",
          },
        },
      },
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });

    if (providerTenantResponse.status !== 201) {
      console.error("Provider tenant creation failed:", JSON.stringify(providerTenantResponse.data, null, 2));
    }
    expect(providerTenantResponse.status).toBe(201);
    console.log(`Provider Tenant created: ${providerTenantId}`);

    // Step 4: Create Client on Provider Tenant
    console.log("Step 4: Creating Client on Provider Tenant...");
    const providerClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${providerTenantId}/clients`,
      body: {
        client_id: providerClientId,
        client_name: `SSO Provider Client ${timestamp}`,
        client_secret: providerClientSecret,
        redirect_uris: [`${backendUrl}/${consumerTenantId}/v1/authorizations/federations/oidc/callback`],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token"],
        scope: "openid profile email",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });

    if (providerClientResponse.status !== 201) {
      console.error("Provider client creation failed:", JSON.stringify(providerClientResponse.data, null, 2));
    }
    expect(providerClientResponse.status).toBe(201);

    // Step 5: Create User on Provider Tenant
    console.log("Step 5: Creating User on Provider Tenant...");
    const providerUserResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${providerTenantId}/users`,
      body: {
        sub: providerUserSub,
        provider_id: "idp-server",
        email: providerUserEmail,
        raw_password: providerUserPassword,
        username: providerUserEmail,
        name: `SSO Test User ${timestamp}`,
        email_verified: true,
      },
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });

    if (providerUserResponse.status !== 201) {
      console.error("Provider user creation failed:", JSON.stringify(providerUserResponse.data, null, 2));
    }
    expect(providerUserResponse.status).toBe(201);

    // Step 6: Configure Federation on Consumer Tenant (with store_credentials=true)
    console.log("Step 6: Configuring Federation with store_credentials=true...");
    const federationConfigResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${consumerTenantId}/federation-configurations`,
      body: {
        id: federationConfigId,
        type: "oidc",
        sso_provider: ssoProvider,
        payload: {
          type: "standard",
          provider: "standard",
          issuer: `${backendUrl}/${providerTenantId}`,
          issuer_name: ssoProvider,
          authorization_endpoint: `${backendUrl}/${providerTenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${providerTenantId}/v1/tokens`,
          userinfo_endpoint: `${backendUrl}/${providerTenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${providerTenantId}/v1/jwks`,
          scopes_supported: ["openid", "profile", "email"],
          client_id: providerClientId,
          client_secret: providerClientSecret,
          redirect_uri: `${backendUrl}/${consumerTenantId}/v1/authorizations/federations/oidc/callback`,
          store_credentials: true,
          userinfo_mapping_rules: [
            { from: "$.http_request.response_body.sub", to: "external_user_id" },
            { from: "$.http_request.response_body.email", to: "email" },
            { from: "$.http_request.response_body.name", to: "name" },
          ],
        },
        enabled: true,
      },
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });

    if (federationConfigResponse.status !== 201) {
      console.error("Federation config creation failed:", JSON.stringify(federationConfigResponse.data, null, 2));
    }
    expect(federationConfigResponse.status).toBe(201);

    // Step 7: Configure Authentication Policy
    console.log("Step 7: Configuring Authentication Policy...");
    const authPolicyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${consumerTenantId}/authentication-policies`,
      body: {
        id: uuidv4(),
        flow: "oauth",
        policies: [
          {
            description: "Federation policy for SSO credentials test",
            priority: 1,
            conditions: { scopes: ["openid"] },
            available_methods: ["password", `oidc-${ssoProvider}`],
            success_conditions: {
              any_of: [
                [{ path: "$.password-authentication.success_count", type: "integer", operation: "gte", value: 1 }],
                [{ path: `$.oidc-${ssoProvider}.success_count`, type: "integer", operation: "gte", value: 1 }],
              ],
            },
          },
        ],
        enabled: true,
      },
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });

    if (authPolicyResponse.status !== 201) {
      console.error("Auth policy creation failed:", JSON.stringify(authPolicyResponse.data, null, 2));
    }
    expect(authPolicyResponse.status).toBe(201);

    console.log("\n=== Setup Complete ===\n");
  }, 60000);

  it("should include sso_provider and sso_access_token in introspection response", async () => {
    console.log("\n=== Test: SSO Credentials in Token Introspection ===\n");

    // Perform federation login
    const providerInteraction = async (authId) => {
      const challengeResponse = await postAuthentication({
        endpoint: `${backendUrl}/${providerTenantId}/v1/authorizations/{id}/password-authentication`,
        id: authId,
        body: {
          username: providerUserEmail,
          password: providerUserPassword,
        },
      });
      console.log(`Provider password auth: ${challengeResponse.status}`);
      expect(challengeResponse.status).toBe(200);
    };

    const { authorizationResponse } = await requestAuthorizations({
      endpoint: `${backendUrl}/${consumerTenantId}/v1/authorizations`,
      authorizeEndpoint: `${backendUrl}/${consumerTenantId}/v1/authorizations/{id}/authorize`,
      clientId: consumerClientId,
      responseType: "code",
      state: `state-${Date.now()}`,
      scope: "openid profile email",
      redirectUri: "http://localhost:3000/callback",
      interaction: async (authId) => {
        const viewResponse = await get({
          url: `${backendUrl}/${consumerTenantId}/v1/authorizations/${authId}/view-data`,
        });
        expect(viewResponse.status).toBe(200);

        const federationSetting = viewResponse.data.available_federations?.find(
          f => f.sso_provider === ssoProvider
        );
        expect(federationSetting).toBeDefined();

        const { params } = await requestFederation({
          url: backendUrl,
          authSessionId: authId,
          authSessionTenantId: consumerTenantId,
          type: federationSetting.type,
          providerName: federationSetting.sso_provider,
          federationTenantId: providerTenantId,
          user: { email: providerUserEmail },
          interaction: providerInteraction,
        });

        const federationCallbackResponse = await post({
          url: `${backendUrl}/${consumerTenantId}/v1/authorizations/federations/oidc/callback`,
          body: params.toString(),
        });
        console.log(`Federation callback: ${federationCallbackResponse.status}`);
        expect(federationCallbackResponse.status).toBe(200);
      },
    });

    console.log("Authorization response:", JSON.stringify(authorizationResponse));
    expect(authorizationResponse.code).toBeDefined();

    // Exchange code for tokens
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${consumerTenantId}/v1/tokens`,
      code: authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: "http://localhost:3000/callback",
      clientId: consumerClientId,
      clientSecret: consumerClientSecret,
    });

    console.log("Token response:", JSON.stringify(tokenResponse.data, null, 2));
    expect(tokenResponse.status).toBe(200);

    const accessToken = tokenResponse.data.access_token;
    // Opaque token should not be a JWT (no dots)
    expect(accessToken.split(".").length).toBeLessThan(3);

    // Token Introspection
    const introspectionResponse = await inspectToken({
      endpoint: `${backendUrl}/${consumerTenantId}/v1/tokens/introspection`,
      token: accessToken,
      clientId: consumerClientId,
      clientSecret: consumerClientSecret,
    });

    console.log("Introspection response:", JSON.stringify(introspectionResponse.data, null, 2));
    expect(introspectionResponse.status).toBe(200);
    expect(introspectionResponse.data.active).toBe(true);

    // Verify SSO credentials are included
    expect(introspectionResponse.data).toHaveProperty("sso_provider");
    expect(introspectionResponse.data).toHaveProperty("sso_access_token");
    expect(typeof introspectionResponse.data.sso_provider).toBe("string");
    expect(typeof introspectionResponse.data.sso_access_token).toBe("string");
    expect(introspectionResponse.data.sso_access_token.length).toBeGreaterThan(0);

    console.log(`SSO Provider: ${introspectionResponse.data.sso_provider}`);
    console.log(`SSO Access Token present: ${introspectionResponse.data.sso_access_token.length > 0}`);
  }, 60000);
});
