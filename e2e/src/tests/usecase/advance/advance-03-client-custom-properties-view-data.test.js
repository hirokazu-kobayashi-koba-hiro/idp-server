import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import { requestToken, postAuthentication } from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { requestAuthorizations } from "../../../oauth/request";

/**
 * Advance Use Case: Client Custom Properties in Authorization ViewData
 *
 * This test verifies that custom_properties set in client extension configuration
 * are surfaced as client_custom_properties in the authorization view-data API response.
 *
 * Use Case:
 * - Admin sets arbitrary key-value pairs in extension.custom_properties via management API
 * - Authorization screen (SPA) retrieves them via view-data API to customize UI
 * - Examples: app_label, feature_flags, branding config, etc.
 *
 * Test Flow:
 * 1. Create Organization + Tenant + Client (with custom_properties) via onboarding
 * 2. Start authorization flow to get authorization ID
 * 3. Call view-data API and verify client_custom_properties is present
 */
describe("Advance Use Case: Client Custom Properties in Authorization ViewData", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let userEmail;
  let userPassword;

  beforeAll(async () => {
    console.log("\n=== Setting up Client Custom Properties ViewData Test ===\n");

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
    tenantId = uuidv4();
    clientId = uuidv4();
    clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    userEmail = `custom-props-${timestamp}@example.com`;
    userPassword = `TestPass${timestamp}!`;

    const jwksContent = await generateECP256JWKS();

    // Step 1: Create Organization with Tenant and Client via Onboarding
    console.log("Step 1: Creating organization with custom_properties client...");
    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Custom Props Test Org ${timestamp}`,
        description: "Test organization for custom_properties in view-data",
      },
      tenant: {
        id: tenantId,
        name: `Custom Props Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `CP_TEST_${organizationId.substring(0, 8)}`,
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
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["ES256"],
        response_modes_supported: ["query"],
        extension: {
          access_token_type: "JWT",
        },
      },
      user: {
        sub: uuidv4(),
        email: userEmail,
        raw_password: userPassword,
        username: userEmail,
        name: `Custom Props User ${timestamp}`,
        email_verified: true,
      },
      client: {
        client_id: clientId,
        client_name: `Custom Props Client ${timestamp}`,
        client_secret: clientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "password"],
        scope: "openid profile email management",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
        extension: {
          custom_properties: {
            app_label: "my-custom-app",
            feature_flags: { dark_mode: true, beta_features: false },
            max_sessions: 5,
            tags: ["internal", "pilot"],
          },
        },
      },
    };

    const onboardingResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: onboardingRequest,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    if (onboardingResponse.status !== 201) {
      console.error(
        "Onboarding failed:",
        JSON.stringify(onboardingResponse.data, null, 2)
      );
    }
    expect(onboardingResponse.status).toBe(201);
    console.log(`Tenant created: ${tenantId}, Client: ${clientId}`);
  });

  it("should include client_custom_properties in view-data response", async () => {
    console.log("\n=== Verifying custom_properties in view-data ===\n");

    // Start authorization flow with interaction that checks view-data
    let viewDataResponse;

    const result = await requestAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId: clientId,
      responseType: "code",
      state: `state-${Date.now()}`,
      scope: "openid profile email",
      redirectUri: "http://localhost:3000/callback",
      authorizeEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      interaction: async (authId, user) => {
        console.log(`Authorization ID: ${authId}`);

        // Call view-data API
        viewDataResponse = await get({
          url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/view-data`,
        });
        console.log(
          "View data response:",
          JSON.stringify(viewDataResponse.data, null, 2)
        );
        expect(viewDataResponse.status).toBe(200);

        // Authenticate to complete the flow
        const authResponse = await postAuthentication({
          endpoint: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/password-authentication`,
          id: authId,
          body: {
            username: userEmail,
            password: userPassword,
          },
        });
        if (authResponse.status >= 400) {
          console.error("Authentication failed:", authResponse.data);
        }
      },
      user: {
        username: userEmail,
        password: userPassword,
      },
    });

    // Verify view-data contained client_custom_properties
    expect(viewDataResponse).toBeDefined();
    expect(viewDataResponse.status).toBe(200);

    const viewData = viewDataResponse.data;
    expect(viewData).toHaveProperty("client_id", clientId);
    expect(viewData).toHaveProperty("client_custom_properties");

    const customProps = viewData.client_custom_properties;
    expect(customProps.app_label).toBe("my-custom-app");
    expect(customProps.feature_flags).toEqual({
      dark_mode: true,
      beta_features: false,
    });
    expect(customProps.max_sessions).toBe(5);
    expect(customProps.tags).toEqual(["internal", "pilot"]);

    console.log("client_custom_properties verified in view-data response");
  });

  it("should not include client_custom_properties when not configured", async () => {
    console.log(
      "\n=== Verifying no client_custom_properties when not set ===\n"
    );

    // Create a second client WITHOUT custom_properties
    const noCustomClientId = uuidv4();
    const noCustomClientSecret = `no-custom-${crypto.randomBytes(16).toString("hex")}`;

    // Get org admin token with management scope
    const orgTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "openid profile email management",
      clientId: clientId,
      clientSecret: clientSecret,
    });
    expect(orgTokenResponse.status).toBe(200);
    const orgAccessToken = orgTokenResponse.data.access_token;

    // Create client without custom_properties via management API
    const createResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
      body: {
        client_id: noCustomClientId,
        client_name: `No Custom Props Client ${Date.now()}`,
        client_secret: noCustomClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "password"],
        scope: "openid profile email",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    });
    expect(createResponse.status).toBe(201);

    // Start authorization flow with the no-custom-props client
    let viewDataResponse;

    await requestAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId: noCustomClientId,
      responseType: "code",
      state: `state-${Date.now()}`,
      scope: "openid profile email",
      redirectUri: "http://localhost:3000/callback",
      authorizeEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      interaction: async (authId, user) => {
        viewDataResponse = await get({
          url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/view-data`,
        });
        expect(viewDataResponse.status).toBe(200);

        const authResponse = await postAuthentication({
          endpoint: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/password-authentication`,
          id: authId,
          body: {
            username: userEmail,
            password: userPassword,
          },
        });
        if (authResponse.status >= 400) {
          console.error("Authentication failed:", authResponse.data);
        }
      },
      user: {
        username: userEmail,
        password: userPassword,
      },
    });

    expect(viewDataResponse).toBeDefined();
    expect(viewDataResponse.status).toBe(200);
    expect(viewDataResponse.data).not.toHaveProperty(
      "client_custom_properties"
    );
    console.log(
      "Confirmed: no client_custom_properties when not configured"
    );

    // Cleanup
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${noCustomClientId}`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });
  });
});
