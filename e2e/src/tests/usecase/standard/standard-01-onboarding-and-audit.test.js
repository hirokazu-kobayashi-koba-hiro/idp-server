import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { sleep } from "../../../lib/util";

/**
 * Standard Use Case: Complete Onboarding Flow with Audit Log Verification
 *
 * This test demonstrates a complete organizational workflow:
 * 1. Create organization via Onboarding API
 * 2. Login with organization admin
 * 3. Create business client
 * 4. Verify all operations are recorded in audit logs
 */
describe("Standard Use Case: Onboarding Flow with Audit Log Tracking", () => {
  let systemAccessToken;

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
  });

  it("should complete full onboarding flow and verify audit logs", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const clientId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@test-org.example.com`;
    const orgAdminPassword = `TestOrgPass${timestamp}!`;
    const orgClientSecret = `org-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    console.log("\n=== Step 1: Create Organization via Onboarding API ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Test Organization ${timestamp}`,
        description: `E2E test organization created at ${new Date().toISOString()}`,
      },
      tenant: {
        id: tenantId,
        name: `Test Organizer Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `ORG_SESSION_${organizationId.substring(0, 8)}`,
          use_secure_cookie: false,
        },
        cors_config: {
          allow_origins: [backendUrl],
        },
        security_event_log_config: {
          format: "structured_json",
          stage: "processed",
          include_user_id: true,
          include_user_ex_sub: true,
          include_client_id: true,
          include_ip: true,
          persistence_enabled: true,
          include_detail: true,
        },
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantId}`,
        authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        token_endpoint_auth_signing_alg_values_supported: ["RS256", "ES256"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwksContent,
        grant_types_supported: ["authorization_code", "refresh_token", "password"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management"],
        response_types_supported: ["code"],
        response_modes_supported: ["query", "fragment"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        claims_parameter_supported: true,
        token_introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
        token_revocation_endpoint: `${backendUrl}/${tenantId}/v1/tokens/revocation`,
        backchannel_authentication_endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
        extension: {
          access_token_type: "JWT",
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          access_token_duration: 3600,
          id_token_duration: 3600,
          refresh_token_duration: 86400,
        },
      },
      user: {
        sub: userId,
        provider_id: "idp-server",
        name: orgAdminEmail,
        email: orgAdminEmail,
        email_verified: true,
        raw_password: orgAdminPassword,
      },
      client: {
        client_id: clientId,
        client_id_alias: `test-org-client-${timestamp}`,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management",
        client_name: `Test Organization Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const createResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: {
        Authorization: `Bearer ${systemAccessToken}`,
      },
      body: onboardingRequest,
    });

    console.log(`✅ Organization created: ${organizationId}`);
    expect(createResponse.status).toBe(201);
    expect(createResponse.data.dry_run).toBe(false);
    expect(createResponse.data.organization.id).toBe(organizationId);
    expect(createResponse.data.tenant.id).toBe(tenantId);
    expect(createResponse.data.user).toBeDefined();
    expect(createResponse.data.user.sub).toBe(userId);
    expect(createResponse.data.user.email).toBe(orgAdminEmail);
    expect(createResponse.data.client).toBeDefined();
    expect(createResponse.data.client.client_id).toBe(clientId);

    console.log("\n=== Step 2: Login with Organization Admin ===");

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: orgAdminEmail,
      password: orgAdminPassword,
      scope: "openid profile email management",
      clientId: clientId,
      clientSecret: orgClientSecret,
    });

    console.log(tokenResponse.data);
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data.access_token).toBeDefined();
    expect(tokenResponse.data.scope).toContain("management");
    console.log(`✅ Organization admin logged in: ${orgAdminEmail}`);

    const orgAccessToken = tokenResponse.data.access_token;

    await sleep(1000);

    const onboardingAuditLogsResponse = await get({
      url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/audit-logs?limit=1&type=onboarding`,
      headers: {
        Authorization: `Bearer ${systemAccessToken}`,
      },
    });

    console.log(JSON.stringify(onboardingAuditLogsResponse.data, null , 2));
    expect(onboardingAuditLogsResponse.status).toBe(200);
    expect(onboardingAuditLogsResponse.data).toHaveProperty("total_count");
    expect(onboardingAuditLogsResponse.data.list[0].type).toEqual("onboarding");

    console.log("\n=== Step 3: Create Business Client ===");

    const businessClientId = uuidv4();
    const businessClientTimestamp = Date.now();

    const createClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
      body: {
        client_id: businessClientId,
        client_name: `Business Client ${businessClientTimestamp}`,
        client_secret: "business-secret-123",
        grant_types: ["authorization_code", "refresh_token"],
        redirect_uris: ["http://localhost:3000/callback"],
        enabled: true,
      },
    });

    console.log(`✅ Business client created: ${businessClientId}`);
    expect(createClientResponse.status).toBe(201);
    expect(createClientResponse.data.result.client_id).toBe(businessClientId);

    console.log("\n=== Step 4: Verify Audit Logs ===");

    // Wait a bit for audit logs to be persisted
    await new Promise((resolve) => setTimeout(resolve, 1000));

    const auditLogsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/audit-logs?target_tenant_id=${tenantId}&limit=100`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });

    console.log(`✅ Retrieved ${auditLogsResponse.data.list.length} audit log entries`);
    expect(auditLogsResponse.status).toBe(200);
    expect(auditLogsResponse.data.list.length).toBe(1);

    // Verify expected operations are logged
    const logTypes = auditLogsResponse.data.list.map((log) => log.type);
    console.log("\nLogged operation types:", [...new Set(logTypes)].sort());

    // Expected operations from our flow
    const expectedOperations = {
      client_create: false, // Business client creation
      // Note: Onboarding creates multiple resources but may log differently
    };

    auditLogsResponse.data.list.forEach((log) => {
      console.log(JSON.stringify(log, null , 2));
      if (log.type === "client" && log.target_resource_action === "POST" && log.outcome_result === "success") {
        expectedOperations.client_create = true;
        console.log(`  ✓ Found client_create log: ${log.client_id}`);
      }
    });

    // Verify at least client creation was logged
    expect(expectedOperations.client_create).toBe(true);

    // Verify audit log structure (Issue #913 improvements)
    const sampleLog = auditLogsResponse.data.list[0];
    expect(sampleLog).toHaveProperty("type");
    expect(sampleLog).toHaveProperty("outcome_result");
    expect(sampleLog).toHaveProperty("created_at");
    expect(sampleLog).toHaveProperty("dry_run");

    console.log("\n=== Step 5: Test Audit Log Search Features (Issue #913) ===");

    // Test: Filter by outcome_result=success
    const successLogsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/audit-logs?outcome_result=success&limit=20`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });

    console.log(`✅ Success logs: ${successLogsResponse.data.list.length} entries`);
    expect(successLogsResponse.status).toBe(200);
    successLogsResponse.data.list.forEach((log) => {
      expect(log.outcome_result).toBe("success");
    });

    // Test: Filter by type
    const clientLogsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/audit-logs?type=client&limit=20`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });

    console.log(`✅ Client management logs: ${clientLogsResponse.data.list.length} entries`);
    expect(clientLogsResponse.status).toBe(200);
    clientLogsResponse.data.list.forEach((log) => {
      expect(log.type).toBe("client");
    });

    // Test: Filter by target_tenant_id (Issue #913)
    const targetTenantLogsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/audit-logs?target_tenant_id=${tenantId}&limit=20`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });

    console.log(`✅ Target tenant logs: ${targetTenantLogsResponse.data.list.length} entries`);
    expect(targetTenantLogsResponse.status).toBe(200);
    expect(targetTenantLogsResponse.data.list.length).toBeGreaterThan(0);

    // Verify expected operations are logged
    const hasCreateClientLog = targetTenantLogsResponse.data.list.some(
      (log) => log.type === "client" && log.target_resource_action === "POST"
    );
    expect(hasCreateClientLog).toBe(true);

    // Verify all logs have the correct target_tenant_id
    targetTenantLogsResponse.data.list.forEach((log) => {
      expect(log.target_tenant_id).toBe(tenantId);
    });

    // Test: Filter by dry_run=false
    const productionLogsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/audit-logs?dry_run=false&limit=20`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });

    console.log(`✅ Production logs (dry_run=false): ${productionLogsResponse.data.list.length} entries`);
    expect(productionLogsResponse.status).toBe(200);
    productionLogsResponse.data.list.forEach((log) => {
      expect(log.dry_run).toBe(false);
    });

    // Test: Combined filters (type + outcome_result + dry_run + target_tenant_id)
    const combinedLogsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/audit-logs?type=client&outcome_result=success&dry_run=false&target_tenant_id=${tenantId}&limit=20`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });

    console.log(`✅ Combined filter logs: ${combinedLogsResponse.data.list.length} entries`);
    expect(combinedLogsResponse.status).toBe(200);
    combinedLogsResponse.data.list.forEach((log) => {
      expect(log.type).toBe("client");
      expect(log.outcome_result).toBe("success");
      expect(log.dry_run).toBe(false);
      expect(log.target_tenant_id).toBe(tenantId);
    });

    // Test: Limit validation
    const limitZeroResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/audit-logs?limit=0`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });
    expect(limitZeroResponse.status).toBe(400);

    const limitMaxResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/audit-logs?limit=1000`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });
    expect(limitMaxResponse.status).toBe(200);

    // Test: Empty string parameter validation (PR #922 fix)
    const emptyTypeResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/audit-logs?type=`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });
    expect(emptyTypeResponse.status).toBe(400);
    expect(emptyTypeResponse.data.error).toBe("invalid_request");
    console.log("✅ Empty type parameter correctly rejected");

    const emptyClientIdResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/audit-logs?client_id=`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });
    expect(emptyClientIdResponse.status).toBe(400);
    expect(emptyClientIdResponse.data.error).toBe("invalid_request");
    console.log("✅ Empty client_id parameter correctly rejected");

    const emptyTargetTenantIdResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/audit-logs?target_tenant_id=`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });
    expect(emptyTargetTenantIdResponse.status).toBe(400);
    expect(emptyTargetTenantIdResponse.data.error).toBe("invalid_request");
    console.log("✅ Empty target_tenant_id parameter correctly rejected");

    console.log("\n=== Step 6: Test Tenant Statistics API ===");

    // Calculate date range for statistics query
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);
    const tomorrow = new Date(today);
    tomorrow.setDate(tomorrow.getDate() + 1);

    const fromDate = yesterday.toISOString().split("T")[0];
    const toDate = tomorrow.toISOString().split("T")[0];

    console.log(`Querying statistics from ${fromDate} to ${toDate}`);

    const statisticsResponse = await get({
      url: `${backendUrl}/v1/management/tenants/${tenantId}/statistics?from=${fromDate}&to=${toDate}`,
      headers: {
        Authorization: `Bearer ${systemAccessToken}`,
      },
    });

    console.log(JSON.stringify(statisticsResponse.data, null, 2));
    expect(statisticsResponse.status).toBe(200);
    expect(statisticsResponse.data).toHaveProperty("tenant_id", tenantId);
    expect(statisticsResponse.data).toHaveProperty("period");
    expect(statisticsResponse.data.period.from).toBe(fromDate);
    expect(statisticsResponse.data.period.to).toBe(toDate);
    expect(statisticsResponse.data).toHaveProperty("daily_statistics");
    expect(Array.isArray(statisticsResponse.data.daily_statistics)).toBe(true);

    console.log(`✅ Retrieved statistics for ${statisticsResponse.data.daily_statistics.length} days`);

    // Verify statistics data structure
    const dailyStat = statisticsResponse.data.daily_statistics[0];
    expect(dailyStat).toHaveProperty("date");
    expect(dailyStat).toHaveProperty("metrics");
    console.log(`  Sample daily stat: ${dailyStat.date} - ${JSON.stringify(dailyStat.metrics)}`);

    console.log("\n=== Step 7: Test Organization-level Tenant Statistics API ===");

    const orgStatisticsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/statistics?from=${fromDate}&to=${toDate}`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });

    console.log(JSON.stringify(orgStatisticsResponse.data, null, 2));
    expect(orgStatisticsResponse.status).toBe(200);
    expect(orgStatisticsResponse.data).toHaveProperty("tenant_id", tenantId);
    expect(orgStatisticsResponse.data).toHaveProperty("period");
    expect(orgStatisticsResponse.data.period.from).toBe(fromDate);
    expect(orgStatisticsResponse.data.period.to).toBe(toDate);
    expect(orgStatisticsResponse.data).toHaveProperty("daily_statistics");
    expect(Array.isArray(orgStatisticsResponse.data.daily_statistics)).toBe(true);

    console.log(`✅ Retrieved org-level statistics for ${orgStatisticsResponse.data.daily_statistics.length} days`);


    console.log("\n=== Cleanup ===");

    // Delete business client
    const deleteClientResponse = await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients/${businessClientId}`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });
    console.log(`🧹 Business client deleted: ${deleteClientResponse.status}`);
    expect(deleteClientResponse.status).toBe(204);

    const deleteTenantResponse = await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });
    console.log(`🧹 Tenant deleted: ${deleteTenantResponse.status}`);

    const deleteOrgResponse = await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: {
        Authorization: `Bearer ${systemAccessToken}`,
      },
    });
    console.log(`🧹 Organization deleted: ${deleteOrgResponse.status}`);

    console.log("\n=== Test Completed ===");
  });

  it("should handle initial-registration errors without transaction pollution (Issue #965)", async () => {
    console.log("\n=== Issue #965: Initial Registration Transaction Bug Test ===");

    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const clientId = uuidv4();
    const existingUserEmail = `existing-${timestamp}@test.example.com`;
    const orgAdminPassword = `TestPass${timestamp}!`;
    const orgClientSecret = `org-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    console.log("\n=== Step 1: Create Organization with Existing User ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Test Org ${timestamp}`,
        description: `E2E test organization for Issue #965 created at ${new Date().toISOString()}`,
      },
      tenant: {
        id: tenantId,
        name: `Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: "TEST_SESSION",
          use_secure_cookie: false,
        },
        cors_config: {
          allow_origins: [backendUrl],
        },
        security_event_log_config: {
          format: "structured_json",
          stage: "processed",
          persistence_enabled: true,
        },
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantId}`,
        authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwksContent,
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        grant_types_supported: ["authorization_code", "refresh_token"],
        scopes_supported: ["openid", "profile", "email"],
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        token_endpoint_auth_methods_supported: ["client_secret_post"],
        claims_parameter_supported: true,
        extension: {
          access_token_type: "JWT",
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
        },
      },
      user: {
        sub: userId,
        provider_id: "idp-server",
        name: existingUserEmail,
        email: existingUserEmail,  // This user will cause conflict later
        email_verified: true,
        raw_password: orgAdminPassword,
      },
      client: {
        client_id: clientId,
        client_id_alias: `test-client-${timestamp}`,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code"],
        scope: "openid profile email",
        client_name: `Test Client ${timestamp}`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const createResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: {
        Authorization: `Bearer ${systemAccessToken}`,
      },
      body: onboardingRequest,
    });

    if (createResponse.status !== 201) {
      console.error("Onboarding failed:", JSON.stringify(createResponse.data, null, 2));
    }
    expect(createResponse.status).toBe(201);
    console.log(`✅ Created organization with existing user: ${existingUserEmail}`);

    console.log("\n=== Step 2: Start Authorization Flow ===");

    // Authorization flow starts with a GET request (OAuth 2.0 spec)
    const authParams = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: "http://localhost:3000/callback",
      scope: "openid profile email",
      state: "test-state",
    });

    const authResponse = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${authParams.toString()}`,
      headers: {},
    });

    // Server should redirect (302) to authentication page with authorization ID
    expect(authResponse.status).toBe(302);
    const location = authResponse.headers.location;
    expect(location).toBeDefined();

    // Extract authorization ID from redirect URL
    // Format: /tenantId/v1/authorizations/authenticate?id={authorizationId}
    const authorizationId = new URL(location, backendUrl).searchParams.get('id');
    expect(authorizationId).toBeDefined();
    console.log(`✅ Authorization started: ${authorizationId}`);

    console.log("\n=== Step 3: First Request - Email Conflict (Should Fail) ===");

    const firstRequest = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authorizationId}/initial-registration`,
      body: {
        email: existingUserEmail,  // Conflicts with existing user
        password: "AnotherPassword123!",
        name: "Conflicting User",
      },
    });

    expect(firstRequest.status).toBe(400);
    expect(firstRequest.data.error).toBe("invalid_request");
    expect(firstRequest.data.error_description).toContain("conflict");
    console.log(`✅ First request correctly rejected with conflict error`);

    console.log("\n=== Step 4: Second Request - Valid Email (Should Succeed) ===");

    // BEFORE FIX (Bug): This would fail with "user not same" error
    // AFTER FIX: This should succeed
    const secondRequest = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authorizationId}/initial-registration`,
      body: {
        email: `new-user-${timestamp}@test.example.com`,  // Unique email
        password: "NewPassword123!",
        name: "New User",
      },
    });

    expect(secondRequest.status).toBe(200);
    expect(secondRequest.data.user).toBeDefined();
    expect(secondRequest.data.user.email).toBe(`new-user-${timestamp}@test.example.com`);
    expect(secondRequest.data.user.name).toBe("New User");
    console.log(`✅ Second request succeeded: ${secondRequest.data.user.email}`);

    // Verify transaction was NOT polluted with existing user data
    expect(secondRequest.data.user.email).not.toBe(existingUserEmail);
    console.log(`✅ Transaction was not polluted - registered NEW user, not existing user`);

    console.log("\n=== Step 5: Test Multiple Failures Without Pollution ===");

    // Start another authorization flow
    const auth2Params = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: "http://localhost:3000/callback",
      scope: "openid profile email",
      state: "test-state-2",
    });

    const auth2Response = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations?${auth2Params.toString()}`,
      headers: {},
    });

    expect(auth2Response.status).toBe(302);
    const location2 = auth2Response.headers.location;
    expect(location2).toBeDefined();
    const auth2Id = new URL(location2, backendUrl).searchParams.get('id');
    expect(auth2Id).toBeDefined();

    // Multiple failed attempts with same conflicting email
    const attempt1 = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${auth2Id}/initial-registration`,
      body: {
        email: existingUserEmail,
        password: "Attempt1Pass!",
        name: "Attempt 1",
      },
    });
    expect(attempt1.status).toBe(400);
    expect(attempt1.data.error_description).toContain("conflict");
    console.log(`✅ Attempt 1 failed with conflict`);

    const attempt2 = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${auth2Id}/initial-registration`,
      body: {
        email: existingUserEmail,
        password: "Attempt2Pass!",
        name: "Attempt 2",
      },
    });
    console.log(JSON.stringify(attempt2.data, null, 2));
    expect(attempt2.status).toBe(400);
    expect(attempt2.data.error_description).toContain("conflict");
    expect(attempt2.data.error_description).not.toContain("not same");  // Should still be conflict, not "user not same"
    console.log(`✅ Attempt 2 failed with conflict (not "user not same")`);

    // Final attempt with valid email
    const attempt3 = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${auth2Id}/initial-registration`,
      body: {
        email: `final-user-${timestamp}@test.example.com`,
        password: "FinalPass123!",
        name: "Final User",
      },
    });
    expect(attempt3.status).toBe(200);
    expect(attempt3.data.user.email).toBe(`final-user-${timestamp}@test.example.com`);
    console.log(`✅ Final attempt succeeded after multiple failures`);

    console.log("\n=== Cleanup ===");

    await deletion({
      url: `${backendUrl}/v1/management/tenants/${tenantId}`,
      headers: {
        Authorization: `Bearer ${systemAccessToken}`,
      },
    }).catch(() => {});

    await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: {
        Authorization: `Bearer ${systemAccessToken}`,
      },
    }).catch(() => {});

    console.log("✅ Issue #965 test completed successfully");
  });
});
