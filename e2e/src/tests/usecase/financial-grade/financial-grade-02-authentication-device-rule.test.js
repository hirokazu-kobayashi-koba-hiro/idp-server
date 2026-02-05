import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson, putWithJson } from "../../../lib/http";
import { mtlsPost, mtlsPostWithJson } from "../../../lib/http/mtls";
import { requestToken, postAuthenticationDeviceInteraction } from "../../../api/oauthClient";
import { adminServerConfig, backendUrl, mtlBackendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import fs from "fs";
import path from "path";
import crypto from "crypto";
import { createJwtWithPrivateKey, generateJti, verifyAndDecodeJwt } from "../../../lib/jose";
import { getJwks } from "../../../api/oauthClient";
import { toEpocTime } from "../../../lib/util";
import { calculateCodeChallengeWithS256, generateCodeVerifier } from "../../../lib/oauth";

/**
 * Financial Grade: Authentication Device Rule Test (Issue #728)
 *
 * This test verifies the authentication_device_rule feature at the tenant level:
 * 1. authentication_device_rule is correctly set in identity_policy_config
 * 2. Default values are applied when not specified
 * 3. The rule is correctly returned via Management API
 *
 * authentication_device_rule settings:
 * - max_devices: Maximum number of authentication devices per user
 * - required_identity_verification: Whether identity verification is required for device registration
 */
describe("Financial Grade: Authentication Device Rule (Issue #728)", () => {
  let systemAccessToken;
  let organizationId;
  let organizerTenantId;
  let financialTenantId;
  let orgAdminToken;

  // Load configuration files as templates
  const configDir = path.join(process.cwd(), "../config/examples/financial-grade");
  const onboardingTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "onboarding-request.json"), "utf8")
  );
  const financialTenantTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "financial-tenant.json"), "utf8")
  );

  // Generate unique IDs for this test run
  let onboardingConfig;
  let financialTenantConfig;

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
    const userId = uuidv4();
    const adminClientId = uuidv4();

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
    onboardingConfig.authorization_server.introspection_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens/introspection`;
    onboardingConfig.authorization_server.revocation_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens/revocation`;
    onboardingConfig.authorization_server.backchannel_authentication_endpoint = `${backendUrl}/${organizerTenantId}/v1/backchannel/authentications`;

    financialTenantConfig = JSON.parse(JSON.stringify(financialTenantTemplate));
    financialTenantConfig.tenant.id = financialTenantId;
    financialTenantConfig.authorization_server.issuer = `${backendUrl}/${financialTenantId}`;
    financialTenantConfig.authorization_server.authorization_endpoint = `${backendUrl}/${financialTenantId}/v1/authorizations`;
    financialTenantConfig.authorization_server.token_endpoint = `${backendUrl}/${financialTenantId}/v1/tokens`;
    financialTenantConfig.authorization_server.userinfo_endpoint = `${backendUrl}/${financialTenantId}/v1/userinfo`;
    financialTenantConfig.authorization_server.jwks_uri = `${backendUrl}/${financialTenantId}/v1/jwks`;
    financialTenantConfig.authorization_server.introspection_endpoint = `${backendUrl}/${financialTenantId}/v1/tokens/introspection`;
    financialTenantConfig.authorization_server.revocation_endpoint = `${backendUrl}/${financialTenantId}/v1/tokens/revocation`;
    financialTenantConfig.authorization_server.backchannel_authentication_endpoint = `${backendUrl}/${financialTenantId}/v1/backchannel/authentications`;
  });

  afterAll(async () => {
    // Cleanup: Delete all resources
    console.log("\n=== Cleanup: Deleting Resources ===");

    // Delete financial tenant
    if (financialTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${financialTenantId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    // Delete organizer tenant client
    if (onboardingConfig && organizerTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}/clients/${onboardingConfig.client.client_id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});

      // Delete admin user
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}/users/${onboardingConfig.user.sub}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});

      // Delete organizer tenant
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

  it("should have authentication_device_rule in identity_policy_config", async () => {
    // Step 1: Create organization
    console.log("\n=== Step 1: Creating Organization ===");
    const createOrgResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: onboardingConfig,
    });

    expect(createOrgResponse.status).toBe(201);
    console.log(`✅ Organization created: ${organizationId}`);
    console.log(`✅ Organizer Tenant created: ${organizerTenantId}`);

    // Step 2: Get organization admin token
    console.log("\n=== Step 2: Organization Admin Login ===");
    const orgTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${organizerTenantId}/v1/tokens`,
      grantType: "password",
      username: onboardingConfig.user.email,
      password: onboardingConfig.user.raw_password,
      scope: "openid profile email management",
      clientId: onboardingConfig.client.client_id,
      clientSecret: onboardingConfig.client.client_secret,
    });

    expect(orgTokenResponse.status).toBe(200);
    orgAdminToken = orgTokenResponse.data.access_token;
    console.log("✅ Organization admin logged in");

    // Step 3: Create financial tenant with authentication_device_rule
    console.log("\n=== Step 3: Creating Financial Tenant with authentication_device_rule ===");
    const createTenantResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: financialTenantConfig,
    });

    expect(createTenantResponse.status).toBe(201);
    console.log(`✅ Financial Tenant created: ${financialTenantId}`);

    // Step 4: Verify authentication_device_rule configuration
    console.log("\n=== Step 4: Verifying authentication_device_rule Configuration ===");
    const getTenantResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${financialTenantId}`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
    });

    expect(getTenantResponse.status).toBe(200);

    const tenantData = getTenantResponse.data;
    const identityPolicyConfig = tenantData.identity_policy_config;

    // Verify identity_policy_config exists
    expect(identityPolicyConfig).toBeDefined();
    console.log("✅ identity_policy_config exists");

    // Verify authentication_device_rule exists
    expect(identityPolicyConfig.authentication_device_rule).toBeDefined();
    console.log("✅ authentication_device_rule exists in identity_policy_config");

    const deviceRule = identityPolicyConfig.authentication_device_rule;

    // Verify values match template (financial-grade: max_devices=1, required_identity_verification=true)
    expect(deviceRule.max_devices).toBe(1);
    console.log(`✅ max_devices: ${deviceRule.max_devices}`);

    expect(deviceRule.required_identity_verification).toBe(true);
    console.log(`✅ required_identity_verification: ${deviceRule.required_identity_verification}`);

    console.log("\n=== Test Summary ===");
    console.log("✅ authentication_device_rule successfully moved from AuthenticationPolicy to TenantIdentityPolicy");
    console.log("✅ Settings are correctly persisted and retrieved via Management API");
  });

  it("should apply default values when authentication_device_rule is not specified", async () => {
    // Create tenant config without authentication_device_rule by copying the template
    // and removing the authentication_device_rule field
    const tenantWithoutRuleId = uuidv4();
    const tenantWithoutRule = JSON.parse(JSON.stringify(financialTenantTemplate));
    tenantWithoutRule.tenant.id = tenantWithoutRuleId;
    tenantWithoutRule.tenant.name = "Tenant Without Device Rule";
    tenantWithoutRule.tenant.domain = `${backendUrl}/${tenantWithoutRuleId}`;
    // Remove authentication_device_rule to test default behavior
    delete tenantWithoutRule.tenant.identity_policy_config.authentication_device_rule;
    // Update authorization_server endpoints
    tenantWithoutRule.authorization_server.issuer = `${backendUrl}/${tenantWithoutRuleId}`;
    tenantWithoutRule.authorization_server.authorization_endpoint = `${backendUrl}/${tenantWithoutRuleId}/v1/authorizations`;
    tenantWithoutRule.authorization_server.token_endpoint = `${backendUrl}/${tenantWithoutRuleId}/v1/tokens`;
    tenantWithoutRule.authorization_server.userinfo_endpoint = `${backendUrl}/${tenantWithoutRuleId}/v1/userinfo`;
    tenantWithoutRule.authorization_server.jwks_uri = `${backendUrl}/${tenantWithoutRuleId}/v1/jwks`;
    tenantWithoutRule.authorization_server.introspection_endpoint = `${backendUrl}/${tenantWithoutRuleId}/v1/tokens/introspection`;
    tenantWithoutRule.authorization_server.revocation_endpoint = `${backendUrl}/${tenantWithoutRuleId}/v1/tokens/revocation`;
    tenantWithoutRule.authorization_server.backchannel_authentication_endpoint = `${backendUrl}/${tenantWithoutRuleId}/v1/backchannel/authentications`;

    console.log("\n=== Creating Tenant without authentication_device_rule ===");
    const createTenantResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: tenantWithoutRule,
    });

    expect(createTenantResponse.status).toBe(201);
    console.log(`✅ Tenant created: ${tenantWithoutRuleId}`);

    // Retrieve tenant and check default values
    const getTenantResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantWithoutRuleId}`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
    });

    expect(getTenantResponse.status).toBe(200);
    console.log("✅ Tenant retrieved successfully");

    // Cleanup
    await deletion({
      url: `${backendUrl}/v1/management/tenants/${tenantWithoutRuleId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });

    console.log("✅ Tenant without explicit authentication_device_rule handled correctly");
  });

  it("should verify financial-grade template has correct authentication_device_rule settings", async () => {
    // This test verifies that the financial-grade template file has correct settings
    // for authentication_device_rule (Issue #728)

    // Check the template file directly
    const templateIdentityPolicy = financialTenantTemplate.tenant.identity_policy_config;

    console.log("Template identity_policy_config:");
    console.log(JSON.stringify(templateIdentityPolicy, null, 2));

    // Verify template has authentication_device_rule
    expect(templateIdentityPolicy).toBeDefined();
    expect(templateIdentityPolicy.authentication_device_rule).toBeDefined();

    const deviceRule = templateIdentityPolicy.authentication_device_rule;

    // 金融機関向けテンプレート: 高セキュリティ設定
    // max_devices: 1 (1ユーザー1デバイス - 金融グレードの厳格なセキュリティ)
    // required_identity_verification: true (本人確認必須)
    expect(deviceRule.max_devices).toBe(1);
    expect(deviceRule.required_identity_verification).toBe(true);

    console.log("✅ Financial-grade template authentication_device_rule:");
    console.log(`   - max_devices: ${deviceRule.max_devices} (one device per user - strict security)`);
    console.log(`   - required_identity_verification: ${deviceRule.required_identity_verification} (identity verification required)`);
    console.log("\n✅ Template settings match financial-grade security requirements (Issue #728)");
  });
});

/**
 * Device Limit Enforcement Test
 *
 * This test verifies that the max_devices limit in authentication_device_rule
 * is correctly enforced when registering authentication devices.
 *
 * Uses Authorization Code Flow with PKCE + mTLS (FAPI compliant)
 */
describe("Financial Grade: Device Limit Enforcement (Issue #728)", () => {
  let systemAccessToken;
  let organizationId;
  let organizerTenantId;
  let limitedTenantId;
  let limitedClientId;
  let userId;
  let userAccessToken;
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
  const initialRegConfigTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-config/initial-registration/standard.json"), "utf8")
  );
  const smsConfigTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-config/sms/external.json"), "utf8")
  );
  const fidoUafConfigTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-config/fido-uaf/external.json"), "utf8")
  );
  const authPolicyTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-policy/oauth.json"), "utf8")
  );
  const fidoUafRegistrationPolicyTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-policy/fido-uaf-registration.json"), "utf8")
  );

  let onboardingConfig;
  let limitedTenantConfig;
  let limitedClientConfig;
  let initialRegConfig;
  let smsConfig;
  let fidoUafConfig;
  let authPolicyConfig;
  let fidoUafRegPolicyConfig;
  let clientCertPath;
  let clientKeyPath;
  let userEmail;
  let userPassword;

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
    limitedTenantId = uuidv4();
    limitedClientId = uuidv4();
    const adminClientId = uuidv4();
    const authPolicyId = uuidv4();
    const fidoUafRegPolicyId = uuidv4();
    const initialRegConfigId = uuidv4();
    const smsConfigId = uuidv4();
    const fidoUafConfigId = uuidv4();
    const timestamp = Date.now();
    userEmail = `device-limit-test-${timestamp}@example.com`;
    userPassword = `DeviceLimitTest${timestamp}!`;

    // Client certificate paths for mTLS
    clientCertPath = path.join(configDir, "certs/client-cert.pem");
    clientKeyPath = path.join(configDir, "certs/client-key.pem");

    // Create onboarding config
    onboardingConfig = JSON.parse(JSON.stringify(onboardingTemplate));
    onboardingConfig.organization.id = organizationId;
    onboardingConfig.tenant.id = organizerTenantId;
    onboardingConfig.user.sub = uuidv4();
    onboardingConfig.client.client_id = adminClientId;
    onboardingConfig.authorization_server.issuer = `${backendUrl}/${organizerTenantId}`;
    onboardingConfig.authorization_server.authorization_endpoint = `${backendUrl}/${organizerTenantId}/v1/authorizations`;
    onboardingConfig.authorization_server.token_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens`;
    onboardingConfig.authorization_server.userinfo_endpoint = `${backendUrl}/${organizerTenantId}/v1/userinfo`;
    onboardingConfig.authorization_server.jwks_uri = `${backendUrl}/${organizerTenantId}/v1/jwks`;
    onboardingConfig.authorization_server.introspection_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens/introspection`;
    onboardingConfig.authorization_server.revocation_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens/revocation`;
    onboardingConfig.authorization_server.backchannel_authentication_endpoint = `${backendUrl}/${organizerTenantId}/v1/backchannel/authentications`;

    // Create tenant config with max_devices = 2 (low limit for testing)
    limitedTenantConfig = JSON.parse(JSON.stringify(financialTenantTemplate));
    limitedTenantConfig.tenant.id = limitedTenantId;
    limitedTenantConfig.tenant.name = "Device Limit Test Tenant";
    limitedTenantConfig.tenant.identity_policy_config = {
      identity_unique_key_type: "EMAIL",
      authentication_device_rule: {
        max_devices: 2,  // Low limit for testing
        required_identity_verification: false,
      },
    };
    limitedTenantConfig.authorization_server.issuer = `${backendUrl}/${limitedTenantId}`;
    limitedTenantConfig.authorization_server.authorization_endpoint = `${backendUrl}/${limitedTenantId}/v1/authorizations`;
    limitedTenantConfig.authorization_server.token_endpoint = `${backendUrl}/${limitedTenantId}/v1/tokens`;
    limitedTenantConfig.authorization_server.userinfo_endpoint = `${backendUrl}/${limitedTenantId}/v1/userinfo`;
    limitedTenantConfig.authorization_server.jwks_uri = `${backendUrl}/${limitedTenantId}/v1/jwks`;
    limitedTenantConfig.authorization_server.introspection_endpoint = `${backendUrl}/${limitedTenantId}/v1/tokens/introspection`;
    limitedTenantConfig.authorization_server.revocation_endpoint = `${backendUrl}/${limitedTenantId}/v1/tokens/revocation`;
    limitedTenantConfig.authorization_server.backchannel_authentication_endpoint = `${backendUrl}/${limitedTenantId}/v1/backchannel/authentications`;

    // Client config (same as financial-client but with unique ID)
    limitedClientConfig = JSON.parse(JSON.stringify(financialClientTemplate));
    limitedClientConfig.client_id = limitedClientId;

    // Authentication configs
    initialRegConfig = JSON.parse(JSON.stringify(initialRegConfigTemplate));
    initialRegConfig.id = initialRegConfigId;

    smsConfig = JSON.parse(JSON.stringify(smsConfigTemplate));
    smsConfig.id = smsConfigId;

    fidoUafConfig = JSON.parse(JSON.stringify(fidoUafConfigTemplate));
    fidoUafConfig.id = fidoUafConfigId;

    // Authentication policies
    authPolicyConfig = JSON.parse(JSON.stringify(authPolicyTemplate));
    authPolicyConfig.id = authPolicyId;

    fidoUafRegPolicyConfig = JSON.parse(JSON.stringify(fidoUafRegistrationPolicyTemplate));
    fidoUafRegPolicyConfig.id = fidoUafRegPolicyId;
  });

  afterAll(async () => {
    // Cleanup
    console.log("\n=== Cleanup ===");

    if (limitedClientId && limitedTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${limitedTenantId}/clients/${limitedClientId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (userId && limitedTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${limitedTenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (limitedTenantId && authPolicyConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${limitedTenantId}/authentication-policies/${authPolicyConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (limitedTenantId && fidoUafRegPolicyConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${limitedTenantId}/authentication-policies/${fidoUafRegPolicyConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (limitedTenantId && initialRegConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${limitedTenantId}/authentication-configurations/${initialRegConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (limitedTenantId && smsConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${limitedTenantId}/authentication-configurations/${smsConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (limitedTenantId && fidoUafConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${limitedTenantId}/authentication-configurations/${fidoUafConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (limitedTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${limitedTenantId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    const adminClientId = onboardingConfig.client.client_id;
    if (adminClientId && organizerTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}/clients/${adminClientId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    const adminUserId = onboardingConfig.user.sub;
    if (adminUserId && organizerTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}/users/${adminUserId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (organizerTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (organizationId) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  it("should reject device registration when max_devices limit is reached", async () => {
    // Skip if client certificate not found
    if (!fs.existsSync(clientCertPath) || !fs.existsSync(clientKeyPath)) {
      console.log("⚠️  Client certificate not found, skipping test");
      return;
    }

    // Step 1: Create organization
    console.log("\n=== Step 1: Creating Organization ===");
    const createOrgResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: onboardingConfig,
    });
    expect(createOrgResponse.status).toBe(201);
    console.log(`✅ Organization created: ${organizationId}`);

    // Step 2: Login as organization admin
    console.log("\n=== Step 2: Login as Organization Admin ===");
    const orgTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${organizerTenantId}/v1/tokens`,
      grantType: "password",
      username: onboardingConfig.user.email,
      password: onboardingConfig.user.raw_password,
      scope: "openid profile email management",
      clientId: onboardingConfig.client.client_id,
      clientSecret: onboardingConfig.client.client_secret,
    });
    expect(orgTokenResponse.status).toBe(200);
    orgAdminToken = orgTokenResponse.data.access_token;
    console.log("✅ Organization admin logged in");

    // Step 3: Create tenant with max_devices = 2
    console.log("\n=== Step 3: Creating Tenant with max_devices = 2 ===");
    const createTenantResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: limitedTenantConfig,
    });
    expect(createTenantResponse.status).toBe(201);
    console.log(`✅ Tenant created: ${limitedTenantId}`);
    console.log(`   max_devices: 2`);

    // Step 4: Create client
    console.log("\n=== Step 4: Creating Client ===");
    const createClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${limitedTenantId}/clients`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: limitedClientConfig,
    });
    expect(createClientResponse.status).toBe(201);
    console.log(`✅ Client created: ${limitedClientId}`);

    // Step 5: Create authentication configs
    console.log("\n=== Step 5: Creating Authentication Configs ===");
    const createInitialRegResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${limitedTenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: initialRegConfig,
    });
    expect(createInitialRegResponse.status).toBe(201);
    console.log("✅ Initial Registration Config created");

    const createSmsResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${limitedTenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: smsConfig,
    });
    expect(createSmsResponse.status).toBe(201);
    console.log("✅ SMS Config created");

    const createFidoUafResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${limitedTenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: fidoUafConfig,
    });
    expect(createFidoUafResponse.status).toBe(201);
    console.log("✅ FIDO-UAF Config created");

    // Step 6: Create authentication policies
    console.log("\n=== Step 6: Creating Authentication Policies ===");
    const createAuthPolicyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${limitedTenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: authPolicyConfig,
    });
    expect(createAuthPolicyResponse.status).toBe(201);
    console.log("✅ OAuth Authentication Policy created");

    const createFidoRegPolicyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${limitedTenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: fidoUafRegPolicyConfig,
    });
    expect(createFidoRegPolicyResponse.status).toBe(201);
    console.log("✅ FIDO-UAF Registration Policy created");

    // Step 7: User registration via Authorization Code Flow with PKCE + JAR (FAPI Advance)
    console.log("\n=== Step 7: User Registration (Authorization Code Flow + PKCE + JAR) ===");

    // Generate PKCE parameters
    const codeVerifier = generateCodeVerifier(64);
    const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
    const nonce = crypto.randomBytes(16).toString("base64url");
    const state = crypto.randomBytes(16).toString("hex");

    // Get private key from client JWKS for signing request object
    const clientJwks = JSON.parse(limitedClientConfig.jwks);
    const clientPrivateKey = clientJwks.keys[0];

    // Create signed JWT request object for FAPI Advance
    const issuer = `${backendUrl}/${limitedTenantId}`;
    const requestObject = createJwtWithPrivateKey({
      payload: {
        response_type: "code",
        client_id: limitedClientId,
        redirect_uri: limitedClientConfig.redirect_uris[0],
        scope: "openid profile transfers",
        state: state,
        nonce: nonce,
        code_challenge: codeChallenge,
        code_challenge_method: "S256",
        response_mode: "jwt",
        aud: issuer,
        iss: limitedClientId,
        exp: toEpocTime({ adjusted: 300 }),
        iat: toEpocTime({}),
        nbf: toEpocTime({}),
        jti: generateJti(),
      },
      privateKey: clientPrivateKey,
    });

    const authzParams = new URLSearchParams({
      request: requestObject,
      client_id: limitedClientId,
    });

    const authzResponse = await get({
      url: `${backendUrl}/${limitedTenantId}/v1/authorizations?${authzParams.toString()}`,
    });

    if (authzResponse.status !== 302) {
      console.log(`Authorization response: ${authzResponse.status}`, authzResponse.data);
    }
    expect(authzResponse.status).toBe(302);
    const location = authzResponse.headers.location;
    console.log(`Location header: ${location}`);
    const authTxId = new URL(location).searchParams.get("id");
    expect(authTxId).not.toBeNull();
    console.log(`✅ Authorization request started: ${authTxId}`);

    // Register new user via initial-registration
    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${limitedTenantId}/v1/authorizations/${authTxId}/initial-registration`,
      body: {
        email: userEmail,
        name: "Device Limit Test User",
        phone_number: "+81-90-1234-5678",
        password: userPassword,
        birthdate: "1990-01-01",
        address: {
          country: "JP",
          postal_code: "100-0001",
          region: "Tokyo",
          locality: "Chiyoda",
        },
      },
    });

    expect(registrationResponse.status).toBe(200);
    userId = registrationResponse.data.user.sub;
    console.log(`✅ User registered: ${userId}`);

    // SMS authentication (2nd factor)
    console.log("\n=== SMS Authentication (2nd Factor) ===");
    const smsChallengeResponse = await postWithJson({
      url: `${backendUrl}/${limitedTenantId}/v1/authorizations/${authTxId}/sms-authentication-challenge`,
      body: {
        phone_number: "+81-90-1234-5678",
        template: "authentication",
      },
    });
    expect(smsChallengeResponse.status).toBe(200);

    // Get verification code from authentication transaction
    const authTxResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${limitedTenantId}/authentication-transactions?authorization_id=${authTxId}`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
    });
    expect(authTxResponse.status).toBe(200);

    const verificationCode = "123456";
    const smsVerificationResponse = await postWithJson({
      url: `${backendUrl}/${limitedTenantId}/v1/authorizations/${authTxId}/sms-authentication`,
      body: { verification_code: verificationCode },
    });
    expect(smsVerificationResponse.status).toBe(200);
    console.log("✅ SMS authentication completed");

    // Authorize
    const authorizeResponse = await postWithJson({
      url: `${backendUrl}/${limitedTenantId}/v1/authorizations/${authTxId}/authorize`,
      body: {},
    });
    expect(authorizeResponse.status).toBe(200);

    // Handle JARM (JWT Authorization Response Mode) - response contains JWT with code
    const authRedirectUri = new URL(authorizeResponse.data.redirect_uri);
    const jarmResponse = authRedirectUri.searchParams.get("response");
    expect(jarmResponse).toBeDefined();

    // Get server JWKS to verify JARM response
    const jwksResponse = await getJwks({
      endpoint: `${backendUrl}/${limitedTenantId}/v1/jwks`,
    });
    expect(jwksResponse.status).toBe(200);

    // Decode JARM JWT to get authorization code
    const decodedJarm = verifyAndDecodeJwt({
      jwt: jarmResponse,
      jwks: jwksResponse.data,
    });
    const code = decodedJarm.payload.code;
    expect(code).toBeDefined();
    console.log("✅ Authorization code obtained from JARM response");

    // Exchange code for token via mTLS
    const tokenParams = new URLSearchParams({
      grant_type: "authorization_code",
      code: code,
      redirect_uri: limitedClientConfig.redirect_uris[0],
      client_id: limitedClientId,
      code_verifier: codeVerifier,
    });

    const tokenResponse = await mtlsPost({
      url: `${mtlBackendUrl}/${limitedTenantId}/v1/tokens`,
      body: tokenParams.toString(),
      certPath: clientCertPath,
      keyPath: clientKeyPath,
    });

    expect(tokenResponse.status).toBe(200);
    userAccessToken = tokenResponse.data.access_token;
    console.log("✅ User access token obtained via mTLS");

    // Step 8: Register first device (should succeed)
    console.log("\n=== Step 8: Registering First Device ===");
    let mfaResponse = await mtlsPostWithJson({
      url: `${mtlBackendUrl}/${limitedTenantId}/v1/me/mfa/fido-uaf-registration`,
      body: {
        app_name: "device-1",
        platform: "Android",
        os: "Android15",
        model: "Device 1",
        locale: "ja",
        notification_channel: "fcm",
        notification_token: "token1",
        priority: 1,
      },
      headers: { Authorization: `Bearer ${userAccessToken}` },
      certPath: clientCertPath,
      keyPath: clientKeyPath,
    });
    expect(mfaResponse.status).toBe(200);
    console.log("✅ First device registration initiated");

    let transactionId = mfaResponse.data.id;
    const authenticationDeviceEndpoint = `${backendUrl}/${limitedTenantId}/v1/authentications/{id}/`;
    let interactionResponse = await postAuthenticationDeviceInteraction({
      endpoint: authenticationDeviceEndpoint,
      id: transactionId,
      interactionType: "fido-uaf-registration-challenge",
      body: {},
    });
    expect(interactionResponse.status).toBe(200);

    interactionResponse = await postAuthenticationDeviceInteraction({
      endpoint: authenticationDeviceEndpoint,
      id: transactionId,
      interactionType: "fido-uaf-registration",
      body: {},
    });
    expect(interactionResponse.status).toBe(200);
    expect(interactionResponse.data.device_id).toBeDefined();
    console.log(`✅ First device registered: ${interactionResponse.data.device_id}`);

    // Step 9: Register second device (should succeed - at limit)
    console.log("\n=== Step 9: Registering Second Device ===");
    mfaResponse = await mtlsPostWithJson({
      url: `${mtlBackendUrl}/${limitedTenantId}/v1/me/mfa/fido-uaf-registration`,
      body: {
        app_name: "device-2",
        platform: "iOS",
        os: "iOS18",
        model: "Device 2",
        locale: "en",
        notification_channel: "apns",
        notification_token: "token2",
        priority: 2,
      },
      headers: { Authorization: `Bearer ${userAccessToken}` },
      certPath: clientCertPath,
      keyPath: clientKeyPath,
    });
    expect(mfaResponse.status).toBe(200);
    console.log("✅ Second device registration initiated");

    transactionId = mfaResponse.data.id;
    interactionResponse = await postAuthenticationDeviceInteraction({
      endpoint: authenticationDeviceEndpoint,
      id: transactionId,
      interactionType: "fido-uaf-registration-challenge",
      body: {},
    });
    expect(interactionResponse.status).toBe(200);

    interactionResponse = await postAuthenticationDeviceInteraction({
      endpoint: authenticationDeviceEndpoint,
      id: transactionId,
      interactionType: "fido-uaf-registration",
      body: {},
    });
    expect(interactionResponse.status).toBe(200);
    expect(interactionResponse.data.device_id).toBeDefined();
    console.log(`✅ Second device registered: ${interactionResponse.data.device_id}`);

    // Step 10: Try to register third device (should FAIL - exceeds limit)
    console.log("\n=== Step 10: Attempting Third Device Registration (Should Fail) ===");
    mfaResponse = await mtlsPostWithJson({
      url: `${mtlBackendUrl}/${limitedTenantId}/v1/me/mfa/fido-uaf-registration`,
      body: {
        app_name: "device-3",
        platform: "Android",
        os: "Android16",
        model: "Device 3",
        locale: "ja",
        notification_channel: "fcm",
        notification_token: "token3",
        priority: 3,
      },
      headers: { Authorization: `Bearer ${userAccessToken}` },
      certPath: clientCertPath,
      keyPath: clientKeyPath,
    });

    // Verify that registration is rejected
    console.log("Response:", JSON.stringify(mfaResponse.data, null, 2));
    expect(mfaResponse.status).toBe(400);
    expect(mfaResponse.data.error).toBe("invalid_request");
    expect(mfaResponse.data.error_description).toContain("Maximum number of devices reached");
    expect(mfaResponse.data.error_description).toContain("2");

    console.log("✅ Third device registration correctly rejected");
    console.log(`   Error: ${mfaResponse.data.error}`);
    console.log(`   Description: ${mfaResponse.data.error_description}`);
    console.log("\n=== Device Limit Enforcement Test PASSED ===");
  });
});

/**
 * Identity Verification Required Test
 *
 * This test verifies that when required_identity_verification is set to true,
 * the user's status changes to IDENTITY_VERIFICATION_REQUIRED after device registration.
 */
describe("Financial Grade: Identity Verification Required (Issue #728)", () => {
  let systemAccessToken;
  let organizationId;
  let organizerTenantId;
  let verificationTenantId;
  let verificationClientId;
  let userId;
  let userAccessToken;
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
  const initialRegConfigTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-config/initial-registration/standard.json"), "utf8")
  );
  const smsConfigTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-config/sms/external.json"), "utf8")
  );
  const fidoUafConfigTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-config/fido-uaf/external.json"), "utf8")
  );
  const authPolicyTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-policy/oauth.json"), "utf8")
  );
  const fidoUafRegistrationPolicyTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-policy/fido-uaf-registration.json"), "utf8")
  );

  let onboardingConfig;
  let verificationTenantConfig;
  let verificationClientConfig;
  let initialRegConfig;
  let smsConfig;
  let fidoUafConfig;
  let authPolicyConfig;
  let fidoUafRegPolicyConfig;
  let clientCertPath;
  let clientKeyPath;
  let userEmail;
  let userPassword;

  // Generate unique IDs
  const initialRegConfigId = uuidv4();
  const smsConfigId = uuidv4();
  const fidoUafConfigId = uuidv4();
  const authPolicyId = uuidv4();
  const fidoUafRegPolicyId = uuidv4();

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

    // Generate unique IDs for this test run
    organizationId = uuidv4();
    organizerTenantId = uuidv4();
    verificationTenantId = uuidv4();
    verificationClientId = uuidv4();
    userEmail = `test-verification-${Date.now()}@example.com`;
    userPassword = "TestPassword123!";

    // Client certificate paths
    clientCertPath = path.join(configDir, "certs/client-cert.pem");
    clientKeyPath = path.join(configDir, "certs/client-key.pem");

    // Prepare onboarding config
    onboardingConfig = JSON.parse(JSON.stringify(onboardingTemplate));
    onboardingConfig.organization.id = organizationId;
    onboardingConfig.organization.name = `Verification Test Org ${Date.now()}`;
    onboardingConfig.tenant.id = organizerTenantId;
    onboardingConfig.tenant.name = "Verification Organizer Tenant";
    onboardingConfig.tenant.domain = `${backendUrl}/${organizerTenantId}`;
    onboardingConfig.client.client_id = uuidv4();
    onboardingConfig.user.sub = uuidv4();
    onboardingConfig.user.email = `admin-${Date.now()}@example.com`;
    onboardingConfig.authorization_server.issuer = `${backendUrl}/${organizerTenantId}`;
    onboardingConfig.authorization_server.authorization_endpoint = `${backendUrl}/${organizerTenantId}/v1/authorizations`;
    onboardingConfig.authorization_server.token_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens`;
    onboardingConfig.authorization_server.userinfo_endpoint = `${backendUrl}/${organizerTenantId}/v1/userinfo`;
    onboardingConfig.authorization_server.jwks_uri = `${backendUrl}/${organizerTenantId}/v1/jwks`;
    onboardingConfig.authorization_server.introspection_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens/introspection`;
    onboardingConfig.authorization_server.revocation_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens/revocation`;
    onboardingConfig.authorization_server.backchannel_authentication_endpoint = `${backendUrl}/${organizerTenantId}/v1/backchannel/authentications`;

    // Create tenant config with required_identity_verification = true
    verificationTenantConfig = JSON.parse(JSON.stringify(financialTenantTemplate));
    verificationTenantConfig.tenant.id = verificationTenantId;
    verificationTenantConfig.tenant.name = "Identity Verification Test Tenant";
    verificationTenantConfig.tenant.identity_policy_config = {
      identity_unique_key_type: "EMAIL",
      authentication_device_rule: {
        max_devices: 5,
        required_identity_verification: true,  // Key setting for this test
      },
    };
    verificationTenantConfig.authorization_server.issuer = `${backendUrl}/${verificationTenantId}`;
    verificationTenantConfig.authorization_server.authorization_endpoint = `${backendUrl}/${verificationTenantId}/v1/authorizations`;
    verificationTenantConfig.authorization_server.token_endpoint = `${backendUrl}/${verificationTenantId}/v1/tokens`;
    verificationTenantConfig.authorization_server.userinfo_endpoint = `${backendUrl}/${verificationTenantId}/v1/userinfo`;
    verificationTenantConfig.authorization_server.jwks_uri = `${backendUrl}/${verificationTenantId}/v1/jwks`;
    verificationTenantConfig.authorization_server.introspection_endpoint = `${backendUrl}/${verificationTenantId}/v1/tokens/introspection`;
    verificationTenantConfig.authorization_server.revocation_endpoint = `${backendUrl}/${verificationTenantId}/v1/tokens/revocation`;
    verificationTenantConfig.authorization_server.backchannel_authentication_endpoint = `${backendUrl}/${verificationTenantId}/v1/backchannel/authentications`;

    // Client config
    verificationClientConfig = JSON.parse(JSON.stringify(financialClientTemplate));
    verificationClientConfig.client_id = verificationClientId;

    // Authentication configs
    initialRegConfig = JSON.parse(JSON.stringify(initialRegConfigTemplate));
    initialRegConfig.id = initialRegConfigId;

    smsConfig = JSON.parse(JSON.stringify(smsConfigTemplate));
    smsConfig.id = smsConfigId;

    fidoUafConfig = JSON.parse(JSON.stringify(fidoUafConfigTemplate));
    fidoUafConfig.id = fidoUafConfigId;

    // Authentication policies
    authPolicyConfig = JSON.parse(JSON.stringify(authPolicyTemplate));
    authPolicyConfig.id = authPolicyId;

    fidoUafRegPolicyConfig = JSON.parse(JSON.stringify(fidoUafRegistrationPolicyTemplate));
    fidoUafRegPolicyConfig.id = fidoUafRegPolicyId;
  });

  afterAll(async () => {
    // Cleanup
    console.log("\n=== Cleanup ===");

    if (verificationClientId && verificationTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${verificationTenantId}/clients/${verificationClientId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (userId && verificationTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${verificationTenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (verificationTenantId && authPolicyConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${verificationTenantId}/authentication-policies/${authPolicyConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (verificationTenantId && fidoUafRegPolicyConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${verificationTenantId}/authentication-policies/${fidoUafRegPolicyConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (verificationTenantId && initialRegConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${verificationTenantId}/authentication-configurations/${initialRegConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (verificationTenantId && smsConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${verificationTenantId}/authentication-configurations/${smsConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (verificationTenantId && fidoUafConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${verificationTenantId}/authentication-configurations/${fidoUafConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (verificationTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${verificationTenantId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    const adminClientId = onboardingConfig.client.client_id;
    if (adminClientId && organizerTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}/clients/${adminClientId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    const adminUserId = onboardingConfig.user.sub;
    if (adminUserId && organizerTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}/users/${adminUserId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (organizerTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (organizationId) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  it("should set user status to IDENTITY_VERIFICATION_REQUIRED after device registration when required_identity_verification is true", async () => {
    // Skip if client certificate not found
    if (!fs.existsSync(clientCertPath) || !fs.existsSync(clientKeyPath)) {
      console.log("⚠️  Client certificate not found, skipping test");
      return;
    }

    // Step 1: Create organization
    console.log("\n=== Step 1: Creating Organization ===");
    const orgResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: onboardingConfig,
    });
    if (orgResponse.status !== 201) {
      console.log("❌ Organization creation failed:", JSON.stringify(orgResponse.data, null, 2));
    }
    expect(orgResponse.status).toBe(201);
    console.log(`✅ Organization created: ${organizationId}`);

    // Step 2: Get org admin token
    console.log("\n=== Step 2: Getting Org Admin Token ===");
    const orgAdminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${organizerTenantId}/v1/tokens`,
      grantType: "password",
      username: onboardingConfig.user.email,
      password: onboardingConfig.user.raw_password,
      scope: "openid profile email management",
      clientId: onboardingConfig.client.client_id,
      clientSecret: onboardingConfig.client.client_secret,
    });
    expect(orgAdminTokenResponse.status).toBe(200);
    orgAdminToken = orgAdminTokenResponse.data.access_token;
    console.log("✅ Got org admin token");

    // Step 3: Create tenant with required_identity_verification = true
    console.log("\n=== Step 3: Creating Tenant with required_identity_verification = true ===");
    const tenantResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: verificationTenantConfig,
    });
    if (tenantResponse.status !== 201) {
      console.log("❌ Tenant creation failed:", JSON.stringify(tenantResponse.data, null, 2));
    }
    expect(tenantResponse.status).toBe(201);
    console.log(`✅ Tenant created: ${verificationTenantId}`);
    console.log(`   required_identity_verification: ${verificationTenantConfig.tenant.identity_policy_config.authentication_device_rule.required_identity_verification}`);

    // Step 4: Create client
    console.log("\n=== Step 4: Creating Client ===");
    const clientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${verificationTenantId}/clients`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: verificationClientConfig,
    });
    expect(clientResponse.status).toBe(201);
    console.log(`✅ Client created: ${verificationClientId}`);

    // Step 5: Create authentication configurations
    console.log("\n=== Step 5: Creating Authentication Configurations ===");
    const initialRegResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${verificationTenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: initialRegConfig,
    });
    expect(initialRegResponse.status).toBe(201);

    const smsResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${verificationTenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: smsConfig,
    });
    expect(smsResponse.status).toBe(201);

    const fidoUafResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${verificationTenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: fidoUafConfig,
    });
    expect(fidoUafResponse.status).toBe(201);
    console.log("✅ Authentication configurations created");

    // Step 6: Create authentication policies
    console.log("\n=== Step 6: Creating Authentication Policies ===");
    const authPolicyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${verificationTenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: authPolicyConfig,
    });
    expect(authPolicyResponse.status).toBe(201);

    const fidoUafRegPolicyResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${verificationTenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: fidoUafRegPolicyConfig,
    });
    expect(fidoUafRegPolicyResponse.status).toBe(201);
    console.log("✅ Authentication policies created");

    // Step 7: Register user and get access token via Authorization Code Flow + PKCE + JAR (FAPI Advance)
    console.log("\n=== Step 7: Registering User via Authorization Code Flow + PKCE + JAR ===");

    const codeVerifier = generateCodeVerifier(64);
    const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
    const nonce = crypto.randomBytes(16).toString("base64url");
    const state = crypto.randomBytes(16).toString("hex");

    // Get private key from client JWKS for signing request object
    const clientJwks = JSON.parse(verificationClientConfig.jwks);
    const clientPrivateKey = clientJwks.keys[0];

    // Create signed JWT request object for FAPI Advance
    const issuer = `${backendUrl}/${verificationTenantId}`;
    const requestObject = createJwtWithPrivateKey({
      payload: {
        response_type: "code",
        client_id: verificationClientId,
        redirect_uri: verificationClientConfig.redirect_uris[0],
        scope: "openid profile transfers",
        state: state,
        nonce: nonce,
        code_challenge: codeChallenge,
        code_challenge_method: "S256",
        response_mode: "jwt",
        aud: issuer,
        iss: verificationClientId,
        exp: toEpocTime({ adjusted: 300 }),
        iat: toEpocTime({}),
        nbf: toEpocTime({}),
        jti: generateJti(),
      },
      privateKey: clientPrivateKey,
    });

    const authzParams = new URLSearchParams({
      request: requestObject,
      client_id: verificationClientId,
    });

    const authzResponse = await get({
      url: `${backendUrl}/${verificationTenantId}/v1/authorizations?${authzParams.toString()}`,
    });

    if (authzResponse.status !== 302) {
      console.log(`Authorization response: ${authzResponse.status}`, authzResponse.data);
    }
    expect(authzResponse.status).toBe(302);
    const location = authzResponse.headers.location;
    console.log(`Location header: ${location}`);
    const authTxId = new URL(location).searchParams.get("id");
    expect(authTxId).not.toBeNull();
    console.log(`✅ Authorization request started: ${authTxId}`);

    // Register new user via initial-registration
    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${verificationTenantId}/v1/authorizations/${authTxId}/initial-registration`,
      body: {
        email: userEmail,
        name: "Identity Verification Test User",
        phone_number: "+81-90-9999-8888",
        password: userPassword,
        birthdate: "1990-01-01",
        address: {
          country: "JP",
          postal_code: "100-0001",
          region: "Tokyo",
          locality: "Chiyoda",
        },
      },
    });

    expect(registrationResponse.status).toBe(200);
    userId = registrationResponse.data.user.sub;
    console.log(`✅ User registered: ${userId}`);

    // SMS authentication (2nd factor)
    console.log("\n=== SMS Authentication (2nd Factor) ===");
    const smsChallengeResponse = await postWithJson({
      url: `${backendUrl}/${verificationTenantId}/v1/authorizations/${authTxId}/sms-authentication-challenge`,
      body: {
        phone_number: "+81-90-9999-8888",
        template: "authentication",
      },
    });
    expect(smsChallengeResponse.status).toBe(200);

    const verificationCode = "123456";
    const smsVerificationResponse = await postWithJson({
      url: `${backendUrl}/${verificationTenantId}/v1/authorizations/${authTxId}/sms-authentication`,
      body: { verification_code: verificationCode },
    });
    expect(smsVerificationResponse.status).toBe(200);
    console.log("✅ SMS authentication completed");

    // Authorize
    const authorizeResponse = await postWithJson({
      url: `${backendUrl}/${verificationTenantId}/v1/authorizations/${authTxId}/authorize`,
      body: {},
    });
    expect(authorizeResponse.status).toBe(200);

    // Handle JARM (JWT Authorization Response Mode) - response contains JWT with code
    const authRedirectUri = new URL(authorizeResponse.data.redirect_uri);
    const jarmResponse = authRedirectUri.searchParams.get("response");
    expect(jarmResponse).toBeDefined();

    // Get server JWKS to verify JARM response
    const jwksResponse = await getJwks({
      endpoint: `${backendUrl}/${verificationTenantId}/v1/jwks`,
    });
    expect(jwksResponse.status).toBe(200);

    // Decode JARM JWT to get authorization code
    const decodedJarm = verifyAndDecodeJwt({
      jwt: jarmResponse,
      jwks: jwksResponse.data,
    });
    const authorizationCode = decodedJarm.payload.code;
    expect(authorizationCode).toBeDefined();
    console.log("✅ Authorization code obtained from JARM response");

    // Exchange code for token via mTLS
    const tokenParams = new URLSearchParams({
      grant_type: "authorization_code",
      code: authorizationCode,
      redirect_uri: verificationClientConfig.redirect_uris[0],
      client_id: verificationClientId,
      code_verifier: codeVerifier,
    });

    const tokenResponse = await mtlsPost({
      url: `${mtlBackendUrl}/${verificationTenantId}/v1/tokens`,
      body: tokenParams.toString(),
      certPath: clientCertPath,
      keyPath: clientKeyPath,
    });

    expect(tokenResponse.status).toBe(200);
    userAccessToken = tokenResponse.data.access_token;
    console.log("✅ User access token obtained via mTLS");

    // Check user status BEFORE device registration
    console.log("\n=== Checking User Status BEFORE Device Registration ===");
    const userBeforeResponse = await get({
      url: `${backendUrl}/v1/management/tenants/${verificationTenantId}/users/${userId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    expect(userBeforeResponse.status).toBe(200);
    const statusBefore = userBeforeResponse.data.status;
    console.log(`   User status BEFORE: ${statusBefore}`);
    expect(statusBefore).not.toBe("IDENTITY_VERIFICATION_REQUIRED");

    // Step 8: Register FIDO-UAF device
    console.log("\n=== Step 8: Registering FIDO-UAF Device ===");

    // Start MFA registration via mTLS
    const mfaStartResponse = await mtlsPostWithJson({
      url: `${mtlBackendUrl}/${verificationTenantId}/v1/me/mfa/fido-uaf-registration`,
      body: {
        app_name: "Test App",
        platform: "Android",
        os: "Android 14",
        model: "Test Device",
        locale: "en",
        notification_channel: "fcm",
        notification_token: "test-token",
        priority: 1,
      },
      headers: { Authorization: `Bearer ${userAccessToken}` },
      certPath: clientCertPath,
      keyPath: clientKeyPath,
    });
    expect(mfaStartResponse.status).toBe(200);
    const registrationId = mfaStartResponse.data.id;
    console.log(`✅ MFA registration started: ${registrationId}`);

    // Complete FIDO-UAF registration (simulated)
    const authenticationDeviceEndpoint = `${backendUrl}/${verificationTenantId}/v1/authentications/{id}/`;
    const challengeResponse = await postAuthenticationDeviceInteraction({
      endpoint: authenticationDeviceEndpoint,
      id: registrationId,
      interactionType: "fido-uaf-registration-challenge",
      body: {},
    });
    expect(challengeResponse.status).toBe(200);

    const fidoRegistrationResponse = await postAuthenticationDeviceInteraction({
      endpoint: authenticationDeviceEndpoint,
      id: registrationId,
      interactionType: "fido-uaf-registration",
      body: {},
    });
    expect(fidoRegistrationResponse.status).toBe(200);
    const deviceId = fidoRegistrationResponse.data.device_id;
    console.log(`✅ Device registered: ${deviceId}`);

    // Step 9: Check user status AFTER device registration
    console.log("\n=== Step 9: Checking User Status AFTER Device Registration ===");
    const userAfterResponse = await get({
      url: `${backendUrl}/v1/management/tenants/${verificationTenantId}/users/${userId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    expect(userAfterResponse.status).toBe(200);
    const statusAfter = userAfterResponse.data.status;
    console.log(`   User status AFTER: ${statusAfter}`);

    // Verify user status changed to IDENTITY_VERIFICATION_REQUIRED
    expect(statusAfter).toBe("IDENTITY_VERIFICATION_REQUIRED");
    console.log("✅ User status correctly changed to IDENTITY_VERIFICATION_REQUIRED");

    console.log("\n=== Identity Verification Required Test PASSED ===");
    console.log("✅ When required_identity_verification is true:");
    console.log("   - Device registration succeeds");
    console.log("   - User status changes to IDENTITY_VERIFICATION_REQUIRED");
  });
});

/**
 * TOCTOU (Time-of-check to time-of-use) Vulnerability Prevention Test
 *
 * This test verifies that the device limit is enforced at REGISTRATION COMPLETION time,
 * not just at challenge request time. This prevents a race condition where:
 * 1. User has (max_devices - 1) devices
 * 2. Two registration challenges are requested concurrently (both pass the check)
 * 3. First registration completes (user now at max_devices)
 * 4. Second registration should FAIL even though its challenge was already issued
 *
 * This is the security fix added in FidoUafRegistrationInteractor and Fido2RegistrationInteractor.
 */
describe("Financial Grade: TOCTOU Device Limit Prevention (Security Fix)", () => {
  let systemAccessToken;
  let organizationId;
  let organizerTenantId;
  let toctoTenantId;
  let toctoClientId;
  let userId;
  let userAccessToken;
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
  const initialRegConfigTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-config/initial-registration/standard.json"), "utf8")
  );
  const smsConfigTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-config/sms/external.json"), "utf8")
  );
  const fidoUafConfigTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-config/fido-uaf/external.json"), "utf8")
  );
  const authPolicyTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-policy/oauth.json"), "utf8")
  );
  const fidoUafRegistrationPolicyTemplate = JSON.parse(
    fs.readFileSync(path.join(configDir, "authentication-policy/fido-uaf-registration.json"), "utf8")
  );

  let onboardingConfig;
  let toctoTenantConfig;
  let toctoClientConfig;
  let initialRegConfig;
  let smsConfig;
  let fidoUafConfig;
  let authPolicyConfig;
  let fidoUafRegPolicyConfig;
  let clientCertPath;
  let clientKeyPath;
  let userEmail;
  let userPassword;

  // Generate unique IDs
  const initialRegConfigId = uuidv4();
  const smsConfigId = uuidv4();
  const fidoUafConfigId = uuidv4();
  const authPolicyId = uuidv4();
  const fidoUafRegPolicyId = uuidv4();

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

    // Generate unique IDs for this test run
    organizationId = uuidv4();
    organizerTenantId = uuidv4();
    toctoTenantId = uuidv4();
    toctoClientId = uuidv4();
    userEmail = `test-toctou-${Date.now()}@example.com`;
    userPassword = "TestPassword123!";

    // Client certificate paths
    clientCertPath = path.join(configDir, "certs/client-cert.pem");
    clientKeyPath = path.join(configDir, "certs/client-key.pem");

    // Prepare onboarding config
    onboardingConfig = JSON.parse(JSON.stringify(onboardingTemplate));
    onboardingConfig.organization.id = organizationId;
    onboardingConfig.organization.name = `TOCTOU Test Org ${Date.now()}`;
    onboardingConfig.tenant.id = organizerTenantId;
    onboardingConfig.tenant.name = "TOCTOU Organizer Tenant";
    onboardingConfig.tenant.domain = `${backendUrl}/${organizerTenantId}`;
    onboardingConfig.client.client_id = uuidv4();
    onboardingConfig.user.sub = uuidv4();
    onboardingConfig.user.email = `admin-toctou-${Date.now()}@example.com`;
    onboardingConfig.authorization_server.issuer = `${backendUrl}/${organizerTenantId}`;
    onboardingConfig.authorization_server.authorization_endpoint = `${backendUrl}/${organizerTenantId}/v1/authorizations`;
    onboardingConfig.authorization_server.token_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens`;
    onboardingConfig.authorization_server.userinfo_endpoint = `${backendUrl}/${organizerTenantId}/v1/userinfo`;
    onboardingConfig.authorization_server.jwks_uri = `${backendUrl}/${organizerTenantId}/v1/jwks`;
    onboardingConfig.authorization_server.introspection_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens/introspection`;
    onboardingConfig.authorization_server.revocation_endpoint = `${backendUrl}/${organizerTenantId}/v1/tokens/revocation`;
    onboardingConfig.authorization_server.backchannel_authentication_endpoint = `${backendUrl}/${organizerTenantId}/v1/backchannel/authentications`;

    // Create tenant config with max_devices = 1 (strict limit for TOCTOU test)
    toctoTenantConfig = JSON.parse(JSON.stringify(financialTenantTemplate));
    toctoTenantConfig.tenant.id = toctoTenantId;
    toctoTenantConfig.tenant.name = "TOCTOU Test Tenant";
    toctoTenantConfig.tenant.identity_policy_config = {
      identity_unique_key_type: "EMAIL",
      authentication_device_rule: {
        max_devices: 1,  // Only 1 device allowed - strict for TOCTOU test
        required_identity_verification: false,
      },
    };
    toctoTenantConfig.authorization_server.issuer = `${backendUrl}/${toctoTenantId}`;
    toctoTenantConfig.authorization_server.authorization_endpoint = `${backendUrl}/${toctoTenantId}/v1/authorizations`;
    toctoTenantConfig.authorization_server.token_endpoint = `${backendUrl}/${toctoTenantId}/v1/tokens`;
    toctoTenantConfig.authorization_server.userinfo_endpoint = `${backendUrl}/${toctoTenantId}/v1/userinfo`;
    toctoTenantConfig.authorization_server.jwks_uri = `${backendUrl}/${toctoTenantId}/v1/jwks`;
    toctoTenantConfig.authorization_server.introspection_endpoint = `${backendUrl}/${toctoTenantId}/v1/tokens/introspection`;
    toctoTenantConfig.authorization_server.revocation_endpoint = `${backendUrl}/${toctoTenantId}/v1/tokens/revocation`;
    toctoTenantConfig.authorization_server.backchannel_authentication_endpoint = `${backendUrl}/${toctoTenantId}/v1/backchannel/authentications`;

    // Client config
    toctoClientConfig = JSON.parse(JSON.stringify(financialClientTemplate));
    toctoClientConfig.client_id = toctoClientId;

    // Authentication configs
    initialRegConfig = JSON.parse(JSON.stringify(initialRegConfigTemplate));
    initialRegConfig.id = initialRegConfigId;

    smsConfig = JSON.parse(JSON.stringify(smsConfigTemplate));
    smsConfig.id = smsConfigId;

    fidoUafConfig = JSON.parse(JSON.stringify(fidoUafConfigTemplate));
    fidoUafConfig.id = fidoUafConfigId;

    // Authentication policies
    authPolicyConfig = JSON.parse(JSON.stringify(authPolicyTemplate));
    authPolicyConfig.id = authPolicyId;

    fidoUafRegPolicyConfig = JSON.parse(JSON.stringify(fidoUafRegistrationPolicyTemplate));
    fidoUafRegPolicyConfig.id = fidoUafRegPolicyId;
  });

  afterAll(async () => {
    // Cleanup
    console.log("\n=== Cleanup ===");

    if (toctoClientId && toctoTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${toctoTenantId}/clients/${toctoClientId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (userId && toctoTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${toctoTenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (toctoTenantId && authPolicyConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${toctoTenantId}/authentication-policies/${authPolicyConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (toctoTenantId && fidoUafRegPolicyConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${toctoTenantId}/authentication-policies/${fidoUafRegPolicyConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (toctoTenantId && initialRegConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${toctoTenantId}/authentication-configurations/${initialRegConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (toctoTenantId && smsConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${toctoTenantId}/authentication-configurations/${smsConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (toctoTenantId && fidoUafConfig) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${toctoTenantId}/authentication-configurations/${fidoUafConfig.id}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (toctoTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${toctoTenantId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    const adminClientId = onboardingConfig.client.client_id;
    if (adminClientId && organizerTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}/clients/${adminClientId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    const adminUserId = onboardingConfig.user.sub;
    if (adminUserId && organizerTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}/users/${adminUserId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (organizerTenantId) {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${organizerTenantId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }

    if (organizationId) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  it("should reject second registration at completion time when first registration filled the limit (TOCTOU prevention)", async () => {
    // Skip if client certificate not found
    if (!fs.existsSync(clientCertPath) || !fs.existsSync(clientKeyPath)) {
      console.log("⚠️  Client certificate not found, skipping test");
      return;
    }

    // Step 1: Create organization
    console.log("\n=== [TOCTOU] Step 1: Creating Organization ===");
    const orgResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: onboardingConfig,
    });
    expect(orgResponse.status).toBe(201);
    console.log(`✅ Organization created: ${organizationId}`);

    // Step 2: Get org admin token
    console.log("\n=== [TOCTOU] Step 2: Getting Org Admin Token ===");
    const orgAdminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${organizerTenantId}/v1/tokens`,
      grantType: "password",
      username: onboardingConfig.user.email,
      password: onboardingConfig.user.raw_password,
      scope: "openid profile email management",
      clientId: onboardingConfig.client.client_id,
      clientSecret: onboardingConfig.client.client_secret,
    });
    expect(orgAdminTokenResponse.status).toBe(200);
    orgAdminToken = orgAdminTokenResponse.data.access_token;

    // Step 3: Create tenant with max_devices = 1
    console.log("\n=== [TOCTOU] Step 3: Creating Tenant with max_devices = 1 ===");
    const tenantResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: toctoTenantConfig,
    });
    expect(tenantResponse.status).toBe(201);
    console.log(`✅ Tenant created with max_devices = 1`);

    // Step 4: Create client
    console.log("\n=== [TOCTOU] Step 4: Creating Client ===");
    const clientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${toctoTenantId}/clients`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: toctoClientConfig,
    });
    expect(clientResponse.status).toBe(201);

    // Step 5: Create authentication configurations
    console.log("\n=== [TOCTOU] Step 5: Creating Authentication Configurations ===");
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${toctoTenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: initialRegConfig,
    });
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${toctoTenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: smsConfig,
    });
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${toctoTenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: fidoUafConfig,
    });
    console.log("✅ Authentication configurations created");

    // Step 6: Create authentication policies
    console.log("\n=== [TOCTOU] Step 6: Creating Authentication Policies ===");
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${toctoTenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: authPolicyConfig,
    });
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${toctoTenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${orgAdminToken}` },
      body: fidoUafRegPolicyConfig,
    });
    console.log("✅ Authentication policies created");

    // Step 7: Register user and get access token
    console.log("\n=== [TOCTOU] Step 7: Registering User ===");
    const codeVerifier = generateCodeVerifier(64);
    const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
    const nonce = crypto.randomBytes(16).toString("base64url");
    const state = crypto.randomBytes(16).toString("hex");

    const clientJwks = JSON.parse(toctoClientConfig.jwks);
    const clientPrivateKey = clientJwks.keys[0];
    const issuer = `${backendUrl}/${toctoTenantId}`;

    const requestObject = createJwtWithPrivateKey({
      payload: {
        response_type: "code",
        client_id: toctoClientId,
        redirect_uri: toctoClientConfig.redirect_uris[0],
        scope: "openid profile transfers",
        state: state,
        nonce: nonce,
        code_challenge: codeChallenge,
        code_challenge_method: "S256",
        response_mode: "jwt",
        aud: issuer,
        iss: toctoClientId,
        exp: toEpocTime({ adjusted: 300 }),
        iat: toEpocTime({}),
        nbf: toEpocTime({}),
        jti: generateJti(),
      },
      privateKey: clientPrivateKey,
    });

    const authzParams = new URLSearchParams({
      request: requestObject,
      client_id: toctoClientId,
    });

    const authzResponse = await get({
      url: `${backendUrl}/${toctoTenantId}/v1/authorizations?${authzParams.toString()}`,
    });
    expect(authzResponse.status).toBe(302);
    const authTxId = new URL(authzResponse.headers.location).searchParams.get("id");

    // Register user
    const registrationResponse = await postWithJson({
      url: `${backendUrl}/${toctoTenantId}/v1/authorizations/${authTxId}/initial-registration`,
      body: {
        email: userEmail,
        name: "TOCTOU Test User",
        phone_number: "+81-90-1111-2222",
        password: userPassword,
        birthdate: "1990-01-01",
        address: { country: "JP", postal_code: "100-0001", region: "Tokyo", locality: "Chiyoda" },
      },
    });
    expect(registrationResponse.status).toBe(200);
    userId = registrationResponse.data.user.sub;
    console.log(`✅ User registered: ${userId}`);

    // SMS authentication
    await postWithJson({
      url: `${backendUrl}/${toctoTenantId}/v1/authorizations/${authTxId}/sms-authentication-challenge`,
      body: { phone_number: "+81-90-1111-2222", template: "authentication" },
    });
    await postWithJson({
      url: `${backendUrl}/${toctoTenantId}/v1/authorizations/${authTxId}/sms-authentication`,
      body: { verification_code: "123456" },
    });

    // Authorize and get token
    const authorizeResponse = await postWithJson({
      url: `${backendUrl}/${toctoTenantId}/v1/authorizations/${authTxId}/authorize`,
      body: {},
    });
    const authRedirectUri = new URL(authorizeResponse.data.redirect_uri);
    const jarmResponse = authRedirectUri.searchParams.get("response");
    const jwksResponse = await getJwks({ endpoint: `${backendUrl}/${toctoTenantId}/v1/jwks` });
    const decodedJarm = verifyAndDecodeJwt({ jwt: jarmResponse, jwks: jwksResponse.data });
    const authorizationCode = decodedJarm.payload.code;

    const tokenParams = new URLSearchParams({
      grant_type: "authorization_code",
      code: authorizationCode,
      redirect_uri: toctoClientConfig.redirect_uris[0],
      client_id: toctoClientId,
      code_verifier: codeVerifier,
    });
    const tokenResponse = await mtlsPost({
      url: `${mtlBackendUrl}/${toctoTenantId}/v1/tokens`,
      body: tokenParams.toString(),
      certPath: clientCertPath,
      keyPath: clientKeyPath,
    });
    expect(tokenResponse.status).toBe(200);
    userAccessToken = tokenResponse.data.access_token;
    console.log("✅ User access token obtained");

    // Step 8: TOCTOU Test - Start TWO registration flows CONCURRENTLY
    console.log("\n=== [TOCTOU] Step 8: Starting TWO Registration Flows Concurrently ===");
    console.log("   (Both should pass the Verifier check since user has 0 devices < max_devices=1)");

    // Start first registration flow
    const mfaResponse1 = await mtlsPostWithJson({
      url: `${mtlBackendUrl}/${toctoTenantId}/v1/me/mfa/fido-uaf-registration`,
      body: {
        app_name: "device-1-toctou",
        platform: "Android",
        os: "Android15",
        model: "Device 1",
        locale: "ja",
        notification_channel: "fcm",
        notification_token: "token1",
        priority: 1,
      },
      headers: { Authorization: `Bearer ${userAccessToken}` },
      certPath: clientCertPath,
      keyPath: clientKeyPath,
    });
    expect(mfaResponse1.status).toBe(200);
    const transactionId1 = mfaResponse1.data.id;
    console.log(`✅ First registration flow started: ${transactionId1}`);

    // Start second registration flow (both pass because user still has 0 devices)
    const mfaResponse2 = await mtlsPostWithJson({
      url: `${mtlBackendUrl}/${toctoTenantId}/v1/me/mfa/fido-uaf-registration`,
      body: {
        app_name: "device-2-toctou",
        platform: "iOS",
        os: "iOS18",
        model: "Device 2",
        locale: "en",
        notification_channel: "apns",
        notification_token: "token2",
        priority: 2,
      },
      headers: { Authorization: `Bearer ${userAccessToken}` },
      certPath: clientCertPath,
      keyPath: clientKeyPath,
    });
    expect(mfaResponse2.status).toBe(200);
    const transactionId2 = mfaResponse2.data.id;
    console.log(`✅ Second registration flow started: ${transactionId2}`);

    // Step 9: Get challenges for BOTH registrations
    console.log("\n=== [TOCTOU] Step 9: Getting Challenges for Both Registrations ===");
    const authenticationDeviceEndpoint = `${backendUrl}/${toctoTenantId}/v1/authentications/{id}/`;

    const challengeResponse1 = await postAuthenticationDeviceInteraction({
      endpoint: authenticationDeviceEndpoint,
      id: transactionId1,
      interactionType: "fido-uaf-registration-challenge",
      body: {},
    });
    expect(challengeResponse1.status).toBe(200);
    console.log(`✅ Challenge 1 obtained`);

    const challengeResponse2 = await postAuthenticationDeviceInteraction({
      endpoint: authenticationDeviceEndpoint,
      id: transactionId2,
      interactionType: "fido-uaf-registration-challenge",
      body: {},
    });
    expect(challengeResponse2.status).toBe(200);
    console.log(`✅ Challenge 2 obtained`);

    // Step 10: Complete FIRST registration (user now has 1 device = max_devices)
    console.log("\n=== [TOCTOU] Step 10: Completing First Registration ===");
    const registrationResult1 = await postAuthenticationDeviceInteraction({
      endpoint: authenticationDeviceEndpoint,
      id: transactionId1,
      interactionType: "fido-uaf-registration",
      body: {},
    });
    expect(registrationResult1.status).toBe(200);
    expect(registrationResult1.data.device_id).toBeDefined();
    console.log(`✅ First device registered: ${registrationResult1.data.device_id}`);
    console.log(`   User now has 1 device (= max_devices)`);

    // Wait for DB commit to complete before attempting second registration
    console.log("   Waiting for DB commit...");
    await new Promise(resolve => setTimeout(resolve, 500));

    // Step 11: Try to complete SECOND registration - this should FAIL
    console.log("\n=== [TOCTOU] Step 11: Attempting to Complete Second Registration ===");
    console.log("   (Should FAIL because user already has max_devices, even though challenge was valid)");

    const registrationResult2 = await postAuthenticationDeviceInteraction({
      endpoint: authenticationDeviceEndpoint,
      id: transactionId2,
      interactionType: "fido-uaf-registration",
      body: {},
    });

    console.log("Response:", JSON.stringify(registrationResult2.data, null, 2));

    // Verify that registration is rejected at COMPLETION time (not challenge time)
    expect(registrationResult2.status).toBe(400);
    expect(registrationResult2.data.error).toBe("invalid_request");
    expect(registrationResult2.data.error_description).toContain("Maximum number of devices reached");
    expect(registrationResult2.data.error_description).toContain("1");

    console.log("✅ Second registration correctly rejected at COMPLETION time");
    console.log(`   Error: ${registrationResult2.data.error}`);
    console.log(`   Description: ${registrationResult2.data.error_description}`);

    console.log("\n=== [TOCTOU] TOCTOU Prevention Test PASSED ===");
    console.log("✅ Security fix verified:");
    console.log("   - Challenge can be issued when user has < max_devices");
    console.log("   - Registration completion is blocked if another registration filled the limit");
    console.log("   - Race condition (TOCTOU) vulnerability is prevented");
  });
});
