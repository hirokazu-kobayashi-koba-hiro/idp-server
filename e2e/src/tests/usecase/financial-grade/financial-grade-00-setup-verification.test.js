import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson, putWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import fs from "fs";
import path from "path";

/**
 * Financial Grade Setup Verification Test
 *
 * This test verifies the setup process using financial-grade configuration files:
 * 1. Create organization and financial tenant via config files
 * 2. Create financial client with self_signed_tls_client_auth
 * 3. Verify FAPI compliance (MTLS, scopes, policies)
 * 4. Test basic MTLS authentication
 */
describe("Financial Grade: Setup Verification", () => {
  let systemAccessToken;
  let organizationId;
  let organizerTenantId;
  let financialTenantId;
  let financialClientId;
  let orgAdminToken;

  // Load configuration files as templates
  const configDir = path.join(process.cwd(), "../config/examples/financial-grade");
  const onboardingTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "onboarding-request.json"), "utf8")
  );
  const financialTenantTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "financial-tenant.json"), "utf8")
  );
  const financialClientTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "financial-client.json"), "utf8")
  );
  const authPolicyTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-policy/oauth.json"), "utf8")
  );

  // Generate unique IDs for this test run
  let onboardingConfig;
  let financialTenantConfig;
  let financialClientConfig;
  let authPolicyConfig;

  beforeAll(async () => {
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

    // Generate unique IDs
    organizationId = uuidv4();
    organizerTenantId = uuidv4();
    financialTenantId = uuidv4();
    financialClientId = uuidv4();
    const userId = uuidv4();
    const adminClientId = uuidv4();
    const authPolicyId = uuidv4();

    // Create unique configs by replacing IDs
    onboardingConfig = JSON.parse(JSON.stringify(onboardingTemplate));
    onboardingConfig.organization.id = organizationId;
    onboardingConfig.tenant.id = organizerTenantId;
    onboardingConfig.user.sub = userId;
    onboardingConfig.client.client_id = adminClientId;
    onboardingConfig.authorization_server.issuer = `${backendUrl}/${organizerTenantId}`;
    onboardingConfig.authorization_server.authorization_endpoint = `${backendUrl}/${organizerTenantId}/v1/authorizations`;
    onboardingConfig.authorization_server.token_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens`;
    onboardingConfig.authorization_server.userinfo_endpoint = `${backendUrl}/${organizerTenantId}/v1/userinfo`;
    onboardingConfig.authorization_server.jwks_uri = `${backendUrl}/${organizerTenantId}/v1/jwks`;
    onboardingConfig.authorization_server.token_introspection_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens/introspection`;
    onboardingConfig.authorization_server.token_revocation_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens/revocation`;
    onboardingConfig.authorization_server.backchannel_authentication_endpoint = `${backendUrl}/${organizerTenantId}/v1/backchannel/authentications`;

    financialTenantConfig = JSON.parse(JSON.stringify(financialTenantTemplate));
    financialTenantConfig.tenant.id = financialTenantId;
    financialTenantConfig.authorization_server.issuer = `${backendUrl}/${financialTenantId}`;
    financialTenantConfig.authorization_server.authorization_endpoint = `${backendUrl}/${financialTenantId}/v1/authorizations`;
    financialTenantConfig.authorization_server.token_endpoint = `${backendUrl}/${financialTenantId}/v1/tokens`;
    financialTenantConfig.authorization_server.userinfo_endpoint = `${backendUrl}/${financialTenantId}/v1/userinfo`;
    financialTenantConfig.authorization_server.jwks_uri = `${backendUrl}/${financialTenantId}/v1/jwks`;
    financialTenantConfig.authorization_server.token_introspection_endpoint = `${backendUrl}/${financialTenantId}/v1/tokens/introspection`;
    financialTenantConfig.authorization_server.token_revocation_endpoint = `${backendUrl}/${financialTenantId}/v1/tokens/revocation`;
    financialTenantConfig.authorization_server.backchannel_authentication_endpoint = `${backendUrl}/${financialTenantId}/v1/backchannel/authentications`;

    financialClientConfig = JSON.parse(JSON.stringify(financialClientTemplate));
    financialClientConfig.client_id = financialClientId;

    authPolicyConfig = JSON.parse(JSON.stringify(authPolicyTemplate));
    authPolicyConfig.id = authPolicyId;
  });

  afterAll(async () => {
    // Cleanup: Delete all resources
    console.log("\n=== Cleanup: Deleting Resources ===");

    // Delete financial client
    if (financialClientId && financialTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${financialTenantId}/clients/${financialClientId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    // Delete authentication policy
    if (financialTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${financialTenantId}/authentication-policies/${authPolicyConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    // Delete financial tenant
    if (financialTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${financialTenantId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    // Delete admin client
    const adminClientId = onboardingConfig.client.client_id;
    if (adminClientId && organizerTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}/clients/${adminClientId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    // Delete user
    const userId = onboardingConfig.user.sub;
    if (userId && organizerTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    // Delete organizer tenant
    if (organizerTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    // Delete organization
    if (organizationId) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  it("Step 1: Create organization via Onboarding API", async () => {
    console.log("\n=== Step 1: Creating Financial Grade Organization ===");

    const createResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: {
        Authorization: `Bearer ${systemAccessToken}`,
      },
      body: onboardingConfig,
    });

    console.log(`✅ Organization created: ${organizationId}`);
    console.log(`✅ Organizer Tenant created: ${organizerTenantId}`);

    expect(createResponse.status).toBe(201);
    expect(createResponse.data.organization.id).toBe(organizationId);
    expect(createResponse.data.tenant.id).toBe(organizerTenantId);
    expect(createResponse.data.user.email).toBe(onboardingConfig.user.email);
    expect(createResponse.data.client.client_id).toBe(onboardingConfig.client.client_id);
  });

  it("Step 2: Login with organization admin", async () => {
    console.log("\n=== Step 2: Organization Admin Login ===");

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${organizerTenantId}/v1/tokens`,
      grantType: "password",
      username: onboardingConfig.user.email,
      password: onboardingConfig.user.raw_password,
      scope: "openid profile email management",
      clientId: onboardingConfig.client.client_id,
      clientSecret: onboardingConfig.client.client_secret,
    });

    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data.access_token).toBeDefined();
    expect(tokenResponse.data.scope).toContain("management");

    orgAdminToken = tokenResponse.data.access_token;
    console.log(`✅ Organization admin logged in`);
  });

  it("Step 3: Create financial tenant", async () => {
    console.log("\n=== Step 3: Creating Financial Tenant ===");

    const createResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants`,
      headers: {
        Authorization: `Bearer ${orgAdminToken}`,
      },
      body: financialTenantConfig,
    });

    expect(createResponse.status).toBe(201);
    expect(createResponse.data.result.id).toBe(financialTenantId);

    console.log(`✅ Financial Tenant created: ${financialTenantId}`);
  });

  it("Step 4: Verify FAPI compliance settings", async () => {
    console.log("\n=== Step 4: Verifying FAPI Compliance ===");

    // Get authorization server config via Management API
    const authServerResponse = await get({
      url: `${backendUrl}/v1/management/tenants/${financialTenantId}/authorization-server`,
      headers: {
        Authorization: `Bearer ${systemAccessToken}`,
      },
    });

    expect(authServerResponse.status).toBe(200);
    const authServer = authServerResponse.data;

    // Verify FAPI scopes
    expect(authServer.extension.fapi_baseline_scopes).toEqual(["read", "account"]);
    expect(authServer.extension.fapi_advance_scopes).toEqual(["write", "transfers"]);
    expect(authServer.extension.required_identity_verification_scopes).toEqual(["transfers"]);

    // Verify token settings
    expect(authServer.extension.access_token_duration).toBe(900); // 15 minutes
    expect(authServer.extension.id_token_strict_mode).toBe(true);
    expect(authServer.extension.refresh_token_duration).toBe(2592000); // 30 days

    // Verify auth methods
    expect(authServer.token_endpoint_auth_methods_supported).toContain("private_key_jwt");
    expect(authServer.token_endpoint_auth_methods_supported).toContain("tls_client_auth");
    expect(authServer.token_endpoint_auth_methods_supported).toContain("self_signed_tls_client_auth");

    // Verify subject type
    expect(authServer.subject_types_supported).toContain("pairwise");

    console.log(`✅ FAPI Baseline Scopes: ${authServer.extension.fapi_baseline_scopes.join(", ")}`);
    console.log(`✅ FAPI Advance Scopes: ${authServer.extension.fapi_advance_scopes.join(", ")}`);
    console.log(`✅ Access Token Duration: ${authServer.extension.access_token_duration}s`);
    console.log(`✅ ID Token Strict Mode: ${authServer.extension.id_token_strict_mode}`);
  });

  it("Step 5: Create financial client with MTLS", async () => {
    console.log("\n=== Step 5: Creating Financial Client (MTLS) ===");

    const createResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${financialTenantId}/clients`,
      headers: {
        Authorization: `Bearer ${orgAdminToken}`,
      },
      body: financialClientConfig,
    });

    expect(createResponse.status).toBe(201);
    expect(createResponse.data.result.client_id).toBe(financialClientId);
    expect(createResponse.data.result.token_endpoint_auth_method).toBe("self_signed_tls_client_auth");
    expect(createResponse.data.result.tls_client_certificate_bound_access_tokens).toBe(true);

    console.log(`✅ Financial Client created: ${financialClientId}`);
    console.log(`✅ Auth Method: self_signed_tls_client_auth`);
    console.log(`✅ MTLS Token Binding: enabled`);
  });

  it("Step 6: Create authentication policy", async () => {
    console.log("\n=== Step 6: Creating Authentication Policy ===");

    const createResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${financialTenantId}/authentication-policies`,
      headers: {
        Authorization: `Bearer ${orgAdminToken}`,
      },
      body: authPolicyConfig,
    });

    expect(createResponse.status).toBe(201);
    expect(createResponse.data.result.id).toBe(authPolicyConfig.id);
    expect(createResponse.data.result.flow).toBe("oauth");

    console.log(`✅ Authentication Policy created: ${authPolicyConfig.id}`);
    console.log(`✅ Flow: ${createResponse.data.result.flow}`);
  });

  it("Step 7: Verify Discovery endpoint", async () => {
    console.log("\n=== Step 7: Verifying Discovery Endpoint ===");

    const discoveryResponse = await get({
      url: `${backendUrl}/${financialTenantId}/.well-known/openid-configuration`,
    });

    expect(discoveryResponse.status).toBe(200);
    const discovery = discoveryResponse.data;

    // Verify FAPI compliance
    expect(discovery.token_endpoint_auth_methods_supported).toContain("self_signed_tls_client_auth");
    expect(discovery.subject_types_supported).toContain("pairwise");
    expect(discovery.request_object_signing_alg_values_supported).toBeDefined();

    console.log(`✅ Discovery endpoint accessible`);
    console.log(`✅ FAPI-compliant auth methods supported`);
  });

  it("Step 8: Test MTLS authentication with certificate", async () => {
    console.log("\n=== Step 8: Testing MTLS Authentication ===");

    // Load client certificate
    const certPath = path.join(configDir, "certs/client-cert.pem");

    if (!fs.existsSync(certPath)) {
      console.log(`⚠️  Certificate not found at ${certPath}, skipping MTLS test`);
      return;
    }

    const clientCert = fs.readFileSync(certPath, "utf8");
    const encodedCert = clientCert.replace(/\n/g, "%0A");

    // Test token request with invalid authorization code
    // (Should authenticate client successfully but reject invalid code)
    const tokenResponse = await postWithJson({
      url: `${backendUrl}/${financialTenantId}/v1/tokens`,
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        "x-ssl-cert": encodedCert,
      },
      body: new URLSearchParams({
        grant_type: "authorization_code",
        code: "invalid_code_for_testing",
        redirect_uri: "http://localhost:3000/callback/",
        client_id: financialClientId,
      }).toString(),
      isFormData: true,
    });

    // Should get 400 invalid_grant (not 401 invalid_client)
    // This proves client authentication succeeded
    expect(tokenResponse.status).toBe(400);
    expect(tokenResponse.data.error).toBe("invalid_grant");

    console.log(`✅ MTLS client authentication successful`);
    console.log(`✅ Client authenticated via self_signed_tls_client_auth`);
  });

  it("Step 9: Test without certificate (should fail)", async () => {
    console.log("\n=== Step 9: Testing Without Certificate ===");

    // Test token request without certificate
    const tokenResponse = await postWithJson({
      url: `${backendUrl}/${financialTenantId}/v1/tokens`,
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: new URLSearchParams({
        grant_type: "authorization_code",
        code: "invalid_code_for_testing",
        redirect_uri: "http://localhost:3000/callback/",
        client_id: financialClientId,
      }).toString(),
      isFormData: true,
    });

    // Should get 401 invalid_client (certificate required)
    expect(tokenResponse.status).toBe(401);
    expect(tokenResponse.data.error).toBe("invalid_client");
    expect(tokenResponse.data.error_description).toContain("client_cert");

    console.log(`✅ Correctly rejected request without certificate`);
  });
});
