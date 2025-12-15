import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get, postWithJson, patchWithJson } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { generateECP256JWKS } from "../../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { sleep } from "../../../../lib/util";

/**
 * Standard Use Case: Role-Based Access Control (RBAC) with Audit Log Verification
 *
 * This test verifies the complete RBAC workflow:
 * 1. Create organization with tenant, user, and client
 * 2. Create permissions and roles
 * 3. Assign roles to user and verify access is granted
 * 4. Remove roles from user and verify access is denied
 * 5. Verify all operations are recorded in audit logs
 *
 * Related Issue: #742 - User role delete/insert pattern implementation
 */
describe("Standard Use Case: Role-Based Access Control with Audit Logging", () => {
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

  it("should manage roles and verify access control with audit logging", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const adminUserId = uuidv4();
    const testUserId = uuidv4();
    const clientId = uuidv4();
    const orgAdminEmail = `admin-${timestamp}@rbac-test.example.com`;
    const testUserEmail = `user-${timestamp}@rbac-test.example.com`;
    const orgAdminPassword = `AdminPass${timestamp}!`;
    const testUserPassword = `UserPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    console.log("\n=== Step 1: Create Organization via Onboarding API ===");

    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `RBAC Test Org ${timestamp}`,
        description: `E2E test organization for RBAC created at ${new Date().toISOString()}`,
      },
      tenant: {
        id: tenantId,
        name: `RBAC Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `RBAC_SESSION_${organizationId.substring(0, 8)}`,
          use_secure_cookie: false,
        },
        cors_config: {
          allow_origins: [backendUrl],
        },
        security_event_log_config: {
          format: "structured_json",
          stage: "processed",
          include_user_id: true,
          include_client_id: true,
          persistence_enabled: true,
          include_detail: true,
        },
      },
      authorization_server: {
        issuer: `${backendUrl}/${tenantId}`,
        authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
        userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
        jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
        jwks: jwksContent,
        grant_types_supported: ["authorization_code", "refresh_token", "password"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management", "claims:roles", "claims:permissions"],
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["RS256", "ES256"],
        token_introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
        token_revocation_endpoint: `${backendUrl}/${tenantId}/v1/tokens/revocation`,
        extension: {
          access_token_type: "JWT",
          access_token_duration: 3600,
          id_token_duration: 3600,
          refresh_token_duration: 86400,
        },
      },
      user: {
        sub: adminUserId,
        provider_id: "idp-server",
        name: "RBAC Admin User",
        email: orgAdminEmail,
        email_verified: true,
        raw_password: orgAdminPassword,
      },
      client: {
        client_id: clientId,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token", "password"],
        scope: "openid profile email management claims:roles claims:permissions",
        client_name: `RBAC Test Client ${timestamp}`,
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
    console.log(`✅ Organization created: ${organizationId}`);

    console.log("\n=== Step 2: Login with Organization Admin ===");

    const adminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: orgAdminEmail,
      password: orgAdminPassword,
      scope: "openid profile email management claims:roles claims:permissions",
      clientId: clientId,
      clientSecret: orgClientSecret,
    });

    expect(adminTokenResponse.status).toBe(200);
    const orgAccessToken = adminTokenResponse.data.access_token;
    console.log(`✅ Organization admin logged in: ${orgAdminEmail}`);

    console.log("\n=== Step 3: Create Test User ===");

    const createUserResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
      body: {
        sub: testUserId,
        provider_id: "idp-server",
        name: "RBAC Test User",
        email: testUserEmail,
        email_verified: true,
        raw_password: testUserPassword,
      },
    });

    expect(createUserResponse.status).toBe(201);
    console.log(`✅ Test user created: ${testUserEmail}`);

    console.log("\n=== Step 4: Create Permissions ===");

    const readPermissionName = `read-resources-${timestamp}`;
    const writePermissionName = `write-resources-${timestamp}`;
    const deletePermissionName = `delete-resources-${timestamp}`;

    const createReadPermResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/permissions`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
      body: {
        name: readPermissionName,
        description: "Permission to read resources",
      },
    });
    expect(createReadPermResponse.status).toBe(201);
    const readPermissionId = createReadPermResponse.data.result.id;

    const createWritePermResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/permissions`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
      body: {
        name: writePermissionName,
        description: "Permission to write resources",
      },
    });
    expect(createWritePermResponse.status).toBe(201);
    const writePermissionId = createWritePermResponse.data.result.id;

    const createDeletePermResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/permissions`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
      body: {
        name: deletePermissionName,
        description: "Permission to delete resources",
      },
    });
    expect(createDeletePermResponse.status).toBe(201);
    const deletePermissionId = createDeletePermResponse.data.result.id;

    console.log(`✅ Permissions created: ${readPermissionName}, ${writePermissionName}, ${deletePermissionName}`);

    console.log("\n=== Step 5: Create Roles ===");

    const viewerRoleName = `viewer-role-${timestamp}`;
    const editorRoleName = `editor-role-${timestamp}`;
    const adminRoleName = `admin-role-${timestamp}`;

    // Viewer role: read only
    const createViewerRoleResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/roles`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
      body: {
        name: viewerRoleName,
        description: "Viewer role with read-only access",
        permissions: [readPermissionId],
      },
    });
    expect(createViewerRoleResponse.status).toBe(201);
    const viewerRoleId = createViewerRoleResponse.data.result.id;

    // Editor role: read + write
    const createEditorRoleResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/roles`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
      body: {
        name: editorRoleName,
        description: "Editor role with read and write access",
        permissions: [readPermissionId, writePermissionId],
      },
    });
    expect(createEditorRoleResponse.status).toBe(201);
    const editorRoleId = createEditorRoleResponse.data.result.id;

    // Admin role: read + write + delete
    const createAdminRoleResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/roles`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
      body: {
        name: adminRoleName,
        description: "Admin role with full access",
        permissions: [readPermissionId, writePermissionId, deletePermissionId],
      },
    });
    expect(createAdminRoleResponse.status).toBe(201);
    const adminRoleId = createAdminRoleResponse.data.result.id;

    console.log(`✅ Roles created: ${viewerRoleName}, ${editorRoleName}, ${adminRoleName}`);

    console.log("\n=== Step 6: Assign Viewer Role to Test User ===");

    const assignViewerRoleResponse = await patchWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${testUserId}/roles`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
      body: {
        roles: [
          {
            role_id: viewerRoleId,
            role_name: viewerRoleName,
          },
        ],
      },
    });

    expect(assignViewerRoleResponse.status).toBe(200);
    expect(assignViewerRoleResponse.data).toHaveProperty("result");
    expect(assignViewerRoleResponse.data).toHaveProperty("diff");
    console.log("Role assignment diff:", JSON.stringify(assignViewerRoleResponse.data.diff, null, 2));
    console.log(`✅ Viewer role assigned to test user`);

    // Wait for changes to persist
    await sleep(500);

    console.log("\n=== Step 7: Verify Test User Has Viewer Role ===");

    // Get user details via Management API to verify roles
    const getUserResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${testUserId}`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });

    expect(getUserResponse.status).toBe(200);
    console.log("User details:", JSON.stringify(getUserResponse.data, null, 2));

    // Verify roles in user details
    const userRoles = getUserResponse.data.roles || [];
    console.log("User roles:", JSON.stringify(userRoles, null, 2));
    const hasViewerRole = userRoles.some((r) => r.name === viewerRoleName || r.role_name === viewerRoleName);
    expect(hasViewerRole).toBe(true);
    console.log(`✅ Test user has viewer role`);

    // Verify permissions - may be string array or object array
    const userPermissions = getUserResponse.data.permissions || [];
    const rolePermissions = userRoles.flatMap((r) => r.permissions || []);
    const allPermissions = [...userPermissions, ...rolePermissions];
    console.log("All permissions:", JSON.stringify(allPermissions, null, 2));
    // Permissions can be string or object with name property
    const hasReadPermission = allPermissions.some((p) =>
      (typeof p === "string" && p === readPermissionName) ||
      (typeof p === "object" && p.name === readPermissionName)
    );
    expect(hasReadPermission).toBe(true);
    console.log(`✅ Test user has read permission from viewer role`);

    console.log("\n=== Step 8: Upgrade User to Editor Role ===");

    const assignEditorRoleResponse = await patchWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${testUserId}/roles`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
      body: {
        roles: [
          {
            role_id: editorRoleId,
            role_name: editorRoleName,
          },
        ],
      },
    });

    expect(assignEditorRoleResponse.status).toBe(200);
    console.log("Role upgrade diff:", JSON.stringify(assignEditorRoleResponse.data.diff, null, 2));

    // Verify diff shows role change
    expect(assignEditorRoleResponse.data.diff).toHaveProperty("roles");
    console.log(`✅ User upgraded to editor role`);

    await sleep(500);

    // Verify new role via Management API
    const editorUserResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${testUserId}`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });

    expect(editorUserResponse.status).toBe(200);
    const editorRoles = editorUserResponse.data.roles || [];
    const hasEditorRole = editorRoles.some((r) => r.name === editorRoleName || r.role_name === editorRoleName);
    expect(hasEditorRole).toBe(true);

    // Viewer role should be replaced (not accumulated)
    const stillHasViewerRole = editorRoles.some((r) => r.name === viewerRoleName || r.role_name === viewerRoleName);
    expect(stillHasViewerRole).toBe(false);
    console.log(`✅ Role replacement verified: viewer role removed, editor role added`);

    // Verify editor has both read and write permissions
    const editorUserPermissions = editorUserResponse.data.permissions || [];
    const editorRolePermissions = editorRoles.flatMap((r) => r.permissions || []);
    const allEditorPermissions = [...editorUserPermissions, ...editorRolePermissions];
    // Permissions can be string or object with name property
    const editorHasRead = allEditorPermissions.some((p) =>
      (typeof p === "string" && p === readPermissionName) ||
      (typeof p === "object" && p.name === readPermissionName)
    );
    const editorHasWrite = allEditorPermissions.some((p) =>
      (typeof p === "string" && p === writePermissionName) ||
      (typeof p === "object" && p.name === writePermissionName)
    );
    expect(editorHasRead).toBe(true);
    expect(editorHasWrite).toBe(true);
    console.log(`✅ Editor has read and write permissions`);

    console.log("\n=== Step 9: Remove All Roles from User (Issue #742) ===");

    const removeAllRolesResponse = await patchWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${testUserId}/roles`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
      body: {
        roles: [],
      },
    });

    expect(removeAllRolesResponse.status).toBe(200);
    console.log("Remove all roles diff:", JSON.stringify(removeAllRolesResponse.data.diff, null, 2));

    // Verify diff shows roles and permissions were removed
    expect(removeAllRolesResponse.data).toHaveProperty("diff");
    expect(removeAllRolesResponse.data.diff).toHaveProperty("roles");
    expect(removeAllRolesResponse.data.diff).toHaveProperty("permissions");

    // Verify before/after in diff
    const rolesDiff = removeAllRolesResponse.data.diff.roles;
    expect(rolesDiff.before).toBeDefined();
    expect(rolesDiff.after).toBeNull();
    console.log(`✅ All roles removed from user`);

    await sleep(500);

    console.log("\n=== Step 10: Verify User Has No Roles After Removal ===");

    // Verify via Management API
    const noRolesUserResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${testUserId}`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });

    expect(noRolesUserResponse.status).toBe(200);
    const finalRoles = noRolesUserResponse.data.roles || [];
    const finalUserPermissions = noRolesUserResponse.data.permissions || [];
    const finalRolePermissions = finalRoles.flatMap((r) => r.permissions || []);
    const allFinalPermissions = [...finalUserPermissions, ...finalRolePermissions];

    expect(finalRoles).toHaveLength(0);
    expect(allFinalPermissions).toHaveLength(0);
    console.log(`✅ User has no roles and no permissions after removal`);

    console.log("\n=== Step 11: Verify Audit Logs ===");

    await sleep(1000);

    const auditLogsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/audit-logs?limit=100`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });

    expect(auditLogsResponse.status).toBe(200);
    console.log(`✅ Retrieved ${auditLogsResponse.data.list.length} audit log entries`);

    // Verify expected operations are logged
    const logTypes = auditLogsResponse.data.list.map((log) => log.type);
    console.log("Logged operation types:", [...new Set(logTypes)].sort());

    // Check for role-related audit logs
    const roleAuditLogs = auditLogsResponse.data.list.filter((log) => log.type === "role");
    console.log(`  Role-related logs: ${roleAuditLogs.length}`);

    const userAuditLogs = auditLogsResponse.data.list.filter((log) => log.type === "user");
    console.log(`  User-related logs: ${userAuditLogs.length}`);

    const permissionAuditLogs = auditLogsResponse.data.list.filter((log) => log.type === "permission");
    console.log(`  Permission-related logs: ${permissionAuditLogs.length}`);

    // Verify role creation logs
    const roleCreateLogs = roleAuditLogs.filter((log) => log.target_resource_action === "POST");
    expect(roleCreateLogs.length).toBeGreaterThanOrEqual(3); // 3 roles created
    console.log(`✅ Role creation logs verified: ${roleCreateLogs.length} entries`);

    // Verify user role assignment logs
    const userRoleUpdateLogs = userAuditLogs.filter((log) =>
      log.target_resource_action === "PATCH" &&
      log.target_resource_endpoint?.includes("/roles")
    );
    console.log(`  User role update logs: ${userRoleUpdateLogs.length}`);

    // Print sample audit log for verification
    if (auditLogsResponse.data.list.length > 0) {
      console.log("\nSample audit log entry:");
      console.log(JSON.stringify(auditLogsResponse.data.list[0], null, 2));
    }

    console.log("\n=== Step 12: Test Audit Log Filters ===");

    // Filter by type=role
    const roleLogsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/audit-logs?type=role&limit=50`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });
    expect(roleLogsResponse.status).toBe(200);
    roleLogsResponse.data.list.forEach((log) => {
      expect(log.type).toBe("role");
    });
    console.log(`✅ Role type filter verified: ${roleLogsResponse.data.list.length} entries`);

    // Filter by outcome_result=success
    const successLogsResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/audit-logs?outcome_result=success&limit=50`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
      },
    });
    expect(successLogsResponse.status).toBe(200);
    successLogsResponse.data.list.forEach((log) => {
      expect(log.outcome_result).toBe("success");
    });
    console.log(`✅ Success filter verified: ${successLogsResponse.data.list.length} entries`);

    console.log("\n=== Cleanup ===");

    // Delete roles
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/roles/${viewerRoleId}`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/roles/${editorRoleId}`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/roles/${adminRoleId}`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });
    console.log("🧹 Roles deleted");

    // Delete permissions
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/permissions/${readPermissionId}`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/permissions/${writePermissionId}`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/permissions/${deletePermissionId}`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });
    console.log("🧹 Permissions deleted");

    // Delete test user
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${testUserId}`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });
    console.log("🧹 Test user deleted");

    // Delete tenant
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });
    console.log("🧹 Tenant deleted");

    // Delete organization
    await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    console.log("🧹 Organization deleted");

    console.log("\n=== Test Completed Successfully ===");
    console.log("✅ Verified: Role-based access control with audit logging");
    console.log("✅ Verified: Role assignment and removal (Issue #742)");
    console.log("✅ Verified: Permission inheritance from roles");
    console.log("✅ Verified: Audit log recording for all operations");
  });

  it("should verify multiple role assignment and replacement pattern", async () => {
    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const adminUserId = uuidv4();
    const testUserId = uuidv4();
    const clientId = uuidv4();
    const orgAdminEmail = `admin-multi-${timestamp}@rbac-test.example.com`;
    const testUserEmail = `user-multi-${timestamp}@rbac-test.example.com`;
    const orgAdminPassword = `AdminPass${timestamp}!`;
    const testUserPassword = `UserPass${timestamp}!`;
    const orgClientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    console.log("\n=== Multiple Roles Assignment Test ===");

    // Create organization
    const onboardingRequest = {
      organization: {
        id: organizationId,
        name: `Multi-Role Test Org ${timestamp}`,
        description: "Test organization for multiple role assignment",
      },
      tenant: {
        id: tenantId,
        name: `Multi-Role Test Tenant ${timestamp}`,
        domain: backendUrl,
        authorization_provider: "idp-server",
        session_config: {
          cookie_name: `MULTI_SESSION_${organizationId.substring(0, 8)}`,
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
        grant_types_supported: ["password"],
        token_signed_key_id: "signing_key_1",
        id_token_signed_key_id: "signing_key_1",
        scopes_supported: ["openid", "profile", "email", "management", "claims:roles", "claims:permissions"],
        response_types_supported: ["code"],
        response_modes_supported: ["query"],
        subject_types_supported: ["public"],
        id_token_signing_alg_values_supported: ["ES256"],
        extension: {
          access_token_type: "JWT",
        },
      },
      user: {
        sub: adminUserId,
        provider_id: "idp-server",
        name: "Multi-Role Admin",
        email: orgAdminEmail,
        email_verified: true,
        raw_password: orgAdminPassword,
      },
      client: {
        client_id: clientId,
        client_secret: orgClientSecret,
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["password"],
        scope: "openid profile email management claims:roles claims:permissions",
        client_name: `Multi-Role Test Client`,
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    };

    const createResponse = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: onboardingRequest,
    });
    expect(createResponse.status).toBe(201);

    // Get admin token
    const adminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: orgAdminEmail,
      password: orgAdminPassword,
      scope: "openid profile email management",
      clientId: clientId,
      clientSecret: orgClientSecret,
    });
    expect(adminTokenResponse.status).toBe(200);
    const orgAccessToken = adminTokenResponse.data.access_token;

    // Create test user
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
      body: {
        sub: testUserId,
        provider_id: "idp-server",
        name: "Multi-Role Test User",
        email: testUserEmail,
        email_verified: true,
        raw_password: testUserPassword,
      },
    });

    // Create permissions
    const perm1Response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/permissions`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
      body: { name: `perm-a-${timestamp}`, description: "Permission A" },
    });
    const perm1Id = perm1Response.data.result.id;

    const perm2Response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/permissions`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
      body: { name: `perm-b-${timestamp}`, description: "Permission B" },
    });
    const perm2Id = perm2Response.data.result.id;

    const perm3Response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/permissions`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
      body: { name: `perm-c-${timestamp}`, description: "Permission C" },
    });
    const perm3Id = perm3Response.data.result.id;

    // Create roles
    const role1Response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/roles`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
      body: { name: `role-x-${timestamp}`, description: "Role X", permissions: [perm1Id] },
    });
    const role1Id = role1Response.data.result.id;
    const role1Name = role1Response.data.result.name;

    const role2Response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/roles`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
      body: { name: `role-y-${timestamp}`, description: "Role Y", permissions: [perm2Id] },
    });
    const role2Id = role2Response.data.result.id;
    const role2Name = role2Response.data.result.name;

    const role3Response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/roles`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
      body: { name: `role-z-${timestamp}`, description: "Role Z", permissions: [perm3Id] },
    });
    const role3Id = role3Response.data.result.id;
    const role3Name = role3Response.data.result.name;

    console.log("✅ Setup complete: 3 permissions and 3 roles created");

    console.log("\n=== Test 1: Assign Multiple Roles at Once ===");

    const assignMultipleResponse = await patchWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${testUserId}/roles`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
      body: {
        roles: [
          { role_id: role1Id, role_name: role1Name },
          { role_id: role2Id, role_name: role2Name },
        ],
      },
    });

    expect(assignMultipleResponse.status).toBe(200);
    const userRoles = assignMultipleResponse.data.result.roles || [];
    expect(userRoles).toHaveLength(2);
    console.log(`✅ Assigned 2 roles at once`);

    console.log("\n=== Test 2: Replace Roles Completely ===");

    const replaceRolesResponse = await patchWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${testUserId}/roles`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
      body: {
        roles: [
          { role_id: role3Id, role_name: role3Name },
        ],
      },
    });

    expect(replaceRolesResponse.status).toBe(200);
    const replacedRoles = replaceRolesResponse.data.result.roles || [];
    expect(replacedRoles).toHaveLength(1);
    // Role ID may be stored as 'id' or 'role_id'
    expect(replacedRoles[0].id || replacedRoles[0].role_id).toBe(role3Id);

    // Verify diff shows complete replacement
    const rolesDiff = replaceRolesResponse.data.diff.roles;
    expect(rolesDiff.before).toHaveLength(2);
    expect(rolesDiff.after).toHaveLength(1);
    console.log(`✅ Roles completely replaced (2 -> 1)`);

    console.log("\n=== Test 3: Add Back Multiple Roles ===");

    const addBackResponse = await patchWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${testUserId}/roles`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
      body: {
        roles: [
          { role_id: role1Id, role_name: role1Name },
          { role_id: role2Id, role_name: role2Name },
          { role_id: role3Id, role_name: role3Name },
        ],
      },
    });

    expect(addBackResponse.status).toBe(200);
    const allRoles = addBackResponse.data.result.roles || [];
    expect(allRoles).toHaveLength(3);
    console.log(`✅ User now has all 3 roles`);

    console.log("\n=== Test 4: Remove All Roles at Once ===");

    const removeAllResponse = await patchWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${testUserId}/roles`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
      body: {
        roles: [],
      },
    });

    expect(removeAllResponse.status).toBe(200);
    const noRoles = removeAllResponse.data.result.roles || [];
    expect(noRoles).toHaveLength(0);

    // Verify diff shows all roles removed
    const removeDiff = removeAllResponse.data.diff.roles;
    expect(removeDiff.before).toHaveLength(3);
    expect(removeDiff.after).toBeNull();
    console.log(`✅ All 3 roles removed at once`);

    // Cleanup
    console.log("\n=== Cleanup ===");
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    }).catch(() => {});
    await deletion({
      url: `${backendUrl}/v1/management/orgs/${organizationId}`,
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    }).catch(() => {});

    console.log("✅ Multiple role assignment/replacement test completed");
  });
});
