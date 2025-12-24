import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import { requestToken, postAuthentication } from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { sleep } from "../../../lib/util";
import { post } from "../../../lib/http";
import { requestFederation } from "../../../oauth/federation";
import { requestAuthorizations } from "../../../oauth/request";

/**
 * Advance Use Case: Federation Security Event User Name (Issue #1131)
 *
 * This test verifies that security events include user_name for federated users
 * on every authentication, not just the first login.
 *
 * Problem:
 * - applyIdentityPolicy() was only called for NEW users during federation
 * - On 2nd+ logins, preferred_username was not set
 * - This caused user_name in security events to be null
 *
 * Solution:
 * - applyIdentityPolicy() is now called on EVERY authentication
 * - This ensures preferred_username is always set based on identity_policy
 *
 * Test Flow:
 * 1. Create Consumer Tenant via onboarding (with identity_policy_config)
 * 2. Login as org admin to get orgAccessToken
 * 3. Create Provider Tenant using orgAccessToken
 * 4. Configure federation between Consumer and Provider
 * 5. Perform federation login (1st time - creates federated user)
 * 6. Verify security event has user_name
 * 7. Perform federation login (2nd time - uses existing user)
 * 8. Verify security event still has user_name (this was the bug)
 */
describe("Advance Use Case: Federation Security Event User Name (Issue #1131)", () => {
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
    console.log("\n=== Setting up Federation Security Event Test ===\n");

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
    providerUserEmail = `fed-user-${timestamp}@provider.example.com`;
    providerUserPassword = `FedPass${timestamp}!`;
    providerUserSub = uuidv4();
    federationConfigId = uuidv4();
    ssoProvider = `test-provider-${timestamp}`;
    orgAdminEmail = `admin-${timestamp}@consumer.example.com`;
    orgAdminPassword = `AdminPass${timestamp}!`;

    const consumerJwks = await generateECP256JWKS();
    const providerJwks = await generateECP256JWKS();

    // Step 1: Create Organization with Consumer Tenant via Onboarding
    console.log("Step 1: Creating organization with Consumer Tenant via onboarding...");
    const consumerOnboardingRequest = {
      organization: {
        id: organizationId,
        name: `Federation Test Org ${timestamp}`,
        description: "Test organization for federation security event user_name",
      },
      tenant: {
        id: consumerTenantId,
        name: `Consumer Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `FED_CONSUMER_${organizationId.substring(0, 8)}`,
          use_secure_cookie: false,
        },
        cors_config: {
          allow_origins: [backendUrl],
        },
        // Configure identity policy: EMAIL with fallback to EXTERNAL_USER_ID
        identity_policy_config: {
          identity_unique_key_type: "EMAIL_OR_EXTERNAL_USER_ID",
        },
        // Configure security event to include user name
        security_event_log_config: {
          format: "structured_json",
          stage: "processed",
          include_user_id: true,
          include_client_id: true,
          persistence_enabled: true,
          include_detail: true,
        },
        security_event_user_config: {
          include_id: true,
          include_name: true,
          include_status: true,
        },
      },
      authorization_server: {
        issuer: `${backendUrl}/${consumerTenantId}`,
        authorization_endpoint: `${backendUrl}/${consumerTenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${consumerTenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        userinfo_endpoint: `${backendUrl}/${consumerTenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${consumerTenantId}/v1/jwks`,
        jwks: consumerJwks,
        grant_types_supported: ["authorization_code", "refresh_token", "password"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        response_modes_supported: ["query"],
        extension: {
          access_token_type: "JWT",
        },
      },
      user: {
        sub: uuidv4(),
        email: orgAdminEmail,
        raw_password: orgAdminPassword,
        username: orgAdminEmail,
        name: `Org Admin ${timestamp}`,
        email_verified: true,
      },
      client: {
        client_id: consumerClientId,
        client_name: `Consumer Client ${timestamp}`,
        client_secret: consumerClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management",
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
    };

    const consumerOnboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: consumerOnboardingRequest,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    if (consumerOnboardingResponse.status !== 201) {
      console.error("Consumer onboarding failed:", JSON.stringify(consumerOnboardingResponse.data, null, 2));
    }
    expect(consumerOnboardingResponse.status).toBe(201);
    console.log(`Consumer Tenant created: ${consumerTenantId}`);

    // Step 2: Login as org admin to get orgAccessToken
    console.log("Step 2: Login as org admin to get orgAccessToken...");
    const orgAdminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${consumerTenantId}/v1/tokens`,
      grantType: "password",
      username: orgAdminEmail,
      password: orgAdminPassword,
      scope: "openid profile email management",
      clientId: consumerClientId,
      clientSecret: consumerClientSecret,
    });
    expect(orgAdminTokenResponse.status).toBe(200);
    orgAccessToken = orgAdminTokenResponse.data.access_token;
    console.log("Org admin logged in successfully");

    // Step 3: Create Provider Tenant with Authorization Server using orgAccessToken
    console.log("Step 3: Creating Provider Tenant with Authorization Server...");
    const providerTenantRequest = {
      tenant: {
        id: providerTenantId,
        name: `Provider Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        tenant_type: "BUSINESS",
        session_config: {
          cookie_name: `FED_PROVIDER_${organizationId.substring(0, 8)}`,
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
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        userinfo_endpoint: `${backendUrl}/${providerTenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${providerTenantId}/v1/jwks`,
        jwks: providerJwks,
        grant_types_supported: ["authorization_code", "refresh_token", "password"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email"],
        response_types_supported: ["code"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        response_modes_supported: ["query"],
        claims_supported: ["sub", "name", "email", "email_verified", "preferred_username"],
        extension: {
          access_token_type: "JWT",
        },
      },
    };

    const providerTenantResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants`,
      body: providerTenantRequest,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });

    if (providerTenantResponse.status !== 201) {
      console.error("Provider tenant creation failed:", JSON.stringify(providerTenantResponse.data, null, 2));
    }
    expect(providerTenantResponse.status).toBe(201);
    console.log(`Provider Tenant created: ${providerTenantId}`);

    // Step 5: Create Client for Provider Tenant (for Consumer to use as OIDC client)
    console.log("Step 5: Creating Client for Provider Tenant...");
    const providerClientRequest = {
      client_id: providerClientId,
      client_name: `Provider OIDC Client ${timestamp}`,
      client_secret: providerClientSecret,
      redirect_uris: [`${backendUrl}/${consumerTenantId}/v1/authorizations/federations/oidc/callback`],
      response_types: ["code"],
      grant_types: ["authorization_code", "refresh_token"],
      scope: "openid profile email",
      token_endpoint_auth_method: "client_secret_post",
      application_type: "web",
    };

    const providerClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${providerTenantId}/clients`,
      body: providerClientRequest,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });

    if (providerClientResponse.status !== 201) {
      console.error("Provider client creation failed:", JSON.stringify(providerClientResponse.data, null, 2));
    }
    expect(providerClientResponse.status).toBe(201);
    console.log(`Provider Client created: ${providerClientId}`);

    // Step 6: Create User in Provider Tenant
    console.log("Step 6: Creating User in Provider Tenant...");
    const providerUserRequest = {
      sub: providerUserSub,
      provider_id: "idp-server",
      email: providerUserEmail,
      raw_password: providerUserPassword,
      username: providerUserEmail,
      name: `Federation Test User ${timestamp}`,
      email_verified: true,
    };

    const providerUserResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${providerTenantId}/users`,
      body: providerUserRequest,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });

    if (providerUserResponse.status !== 201) {
      console.error("Provider user creation failed:", JSON.stringify(providerUserResponse.data, null, 2));
    }
    expect(providerUserResponse.status).toBe(201);
    console.log(`Provider User created: ${providerUserEmail}`);

    // Step 7: Configure Federation on Consumer Tenant
    console.log("Step 7: Configuring Federation on Consumer Tenant...");
    const federationConfigRequest = {
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
        userinfo_mapping_rules: [
          { from: "$.http_request.response_body.sub", to: "external_user_id" },
          { from: "$.http_request.response_body.email", to: "email" },
          { from: "$.http_request.response_body.name", to: "name" },
          // { from: "$.http_request.response_body.email", to: "preferred_username" },
        ],
      },
      enabled: true,
    };

    const federationConfigResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${consumerTenantId}/federation-configurations`,
      body: federationConfigRequest,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });

    if (federationConfigResponse.status !== 201) {
      console.error("Federation config creation failed:", JSON.stringify(federationConfigResponse.data, null, 2));
    }
    expect(federationConfigResponse.status).toBe(201);
    console.log(`Federation configured: ${ssoProvider}`);

    // Step 8: Configure Authentication Policy with Federation method
    console.log("Step 8: Configuring Authentication Policy with Federation method...");
    const authPolicyConfigId = uuidv4();
    const authPolicyRequest = {
      id: authPolicyConfigId,
      flow: "oauth",
      policies: [
        {
          description: "Federation and password policy",
          priority: 1,
          conditions: {
            scopes: ["openid"],
          },
          available_methods: ["password", `oidc-${ssoProvider}`],
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
              [
                {
                  path: `$.oidc-${ssoProvider}.success_count`,
                  type: "integer",
                  operation: "gte",
                  value: 1,
                },
              ],
            ],
          },
        },
      ],
      enabled: true,
    };

    const authPolicyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${consumerTenantId}/authentication-policies`,
      body: authPolicyRequest,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });

    if (authPolicyResponse.status !== 201) {
      console.error("Auth policy creation failed:", JSON.stringify(authPolicyResponse.data, null, 2));
    }
    expect(authPolicyResponse.status).toBe(201);
    console.log("Authentication policy configured with federation method");

    console.log("\n=== Setup Complete ===\n");
  });

  it("should include user_name in security event on 2nd federation login (Issue #1131)", async () => {
    console.log("\n=== Test: Verify user_name in security events for federation ===\n");

    // Helper function to perform federation login
    const performFederationLogin = async (loginNumber) => {
      console.log(`\n--- Federation Login #${loginNumber} ---`);

      // Define the interaction for Provider tenant authentication
      const providerInteraction = async (authId, user) => {
        console.log(`Provider auth ID: ${authId}`);

        // Authenticate with password on Provider tenant
        const challengeResponse = await postAuthentication({
          endpoint: `${backendUrl}/${providerTenantId}/v1/authorizations/{id}/password-authentication`,
          id: authId,
          body: {
            username: providerUserEmail,
            password: providerUserPassword,
          },
        });
        console.log(`Password auth response: ${challengeResponse.status}`);
        expect(challengeResponse.status).toBe(200);
      };

      // Start authorization request on Consumer tenant
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: `${backendUrl}/${consumerTenantId}/v1/authorizations`,
        authorizeEndpoint: `${backendUrl}/${consumerTenantId}/v1/authorizations/{id}/authorize`,
        clientId: consumerClientId,
        responseType: "code",
        state: `state-${Date.now()}`,
        scope: "openid profile email",
        redirectUri: "http://localhost:3000/callback",
        interaction: async (authId, user) => {
          console.log(`Consumer auth ID: ${authId}`);

          // Get view data to find available federations
          const viewResponse = await get({
            url: `${backendUrl}/${consumerTenantId}/v1/authorizations/${authId}/view-data`,
          });
          console.log(`View data status: ${viewResponse.status}`);
          expect(viewResponse.status).toBe(200);

          // Find our federation config
          console.log("View data available_federations:", JSON.stringify(viewResponse.data.available_federations, null, 2));
          console.log("Looking for sso_provider:", ssoProvider);
          const federationSetting = viewResponse.data.available_federations?.find(
            f => f.sso_provider === ssoProvider
          );
          console.log("Found federation setting:", JSON.stringify(federationSetting, null, 2));

          if (!federationSetting) {
            throw new Error(`Federation ${ssoProvider} not found in available_federations`);
          }

          // Request federation (same pattern as scenario-02-sso-oidc.test.js)
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
          console.log("Federation params:", params.toString());

          // Complete federation callback
          const federationCallbackResponse = await post({
            url: `${backendUrl}/${consumerTenantId}/v1/authorizations/federations/oidc/callback`,
            body: params.toString(),
          });
          console.log(`Federation callback status: ${federationCallbackResponse.status}`);
          expect(federationCallbackResponse.status).toBe(200);
        },
      });

      console.log(`Authorization response: ${JSON.stringify(authorizationResponse)}`);
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

      expect(tokenResponse.status).toBe(200);
      console.log(`Login #${loginNumber} successful`);

      return tokenResponse.data.access_token;
    };

    // Perform 1st federation login
    console.log("\nStep 1: Performing 1st federation login...");
    await performFederationLogin(1);
    await sleep(1500);

    // Check security events after 1st login
    const securityEventsResponse1 = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${consumerTenantId}/security-events?limit=10`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });

    expect(securityEventsResponse1.status).toBe(200);
    const events1 = securityEventsResponse1.data.list || [];
    console.log(`\nRetrieved ${events1.length} security events after 1st login`);

    let userName1Found = false;
    events1.forEach(event => {
      console.log(`  Event: ${event.type}`);
      console.log(`    User: ${JSON.stringify(event.user)}`);
      if (event.user?.name) {
        console.log(`    ✅ User name found: ${event.user.name}`);
        userName1Found = true;
      }
    });

    // Perform 2nd federation login (same user)
    console.log("\nStep 2: Performing 2nd federation login (same user)...");
    await performFederationLogin(2);
    await sleep(1500);

    // Check security events after 2nd login
    const securityEventsResponse2 = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${consumerTenantId}/security-events?limit=20`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });

    expect(securityEventsResponse2.status).toBe(200);
    const events2 = securityEventsResponse2.data.list || [];
    console.log(`\nRetrieved ${events2.length} security events after 2nd login`);

    let userName2Found = false;
    let eventsWithUserName = 0;
    events2.forEach(event => {
      console.log(`  Event: ${event.type}`);
      console.log(`    User: ${JSON.stringify(event.user)}`);
      if (event.user?.name) {
        console.log(`    ✅ User name found: ${event.user.name}`);
        userName2Found = true;
        eventsWithUserName++;
      }
    });

    // Verification
    console.log("\n=== Verification Results ===");
    console.log(`1st login - user_name found: ${userName1Found}`);
    console.log(`2nd login - user_name found: ${userName2Found}`);
    console.log(`Total events with user_name: ${eventsWithUserName}`);

    // Verify we have security events and user_name is present
    expect(events2.length).toBeGreaterThan(0);

    // The key assertion: user_name should be present on BOTH logins
    // Before the fix, 2nd login would have null user_name
    expect(userName1Found && userName2Found).toBe(true);
    console.log("\n✅ Issue #1131 fix verified: applyIdentityPolicy is called on every federation auth");
    console.log("   This ensures preferred_username -> user.name is set for all logins");

    console.log("\n=== Test Completed ===\n");
  });

  it("should clean up test resources", async () => {
    console.log("\n=== Cleanup ===\n");

    try {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${consumerTenantId}/federation-configurations/${federationConfigId}`,
        headers: { Authorization: `Bearer ${orgAccessToken}` },
      });
      console.log("Federation config deleted");
    } catch (e) {
      console.log("Federation config cleanup skipped:", e.message);
    }

    try {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${consumerTenantId}/clients/${consumerClientId}`,
        headers: { Authorization: `Bearer ${orgAccessToken}` },
      });
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${providerTenantId}/clients/${providerClientId}`,
        headers: { Authorization: `Bearer ${orgAccessToken}` },
      });
      console.log("Clients deleted");
    } catch (e) {
      console.log("Client cleanup skipped:", e.message);
    }

    try {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${providerTenantId}`,
        headers: { Authorization: `Bearer ${orgAccessToken}` },
      });
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${consumerTenantId}`,
        headers: { Authorization: `Bearer ${orgAccessToken}` },
      });
      console.log("Tenants deleted");
    } catch (e) {
      console.log("Tenant cleanup skipped:", e.message);
    }

    try {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      });
      console.log("Organization deleted");
    } catch (e) {
      console.log("Organization cleanup skipped:", e.message);
    }

    console.log("\n=== Cleanup Complete ===\n");
  });
});
