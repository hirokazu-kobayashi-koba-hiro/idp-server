import { deletion, get, postWithJson } from "../http";
import { requestToken } from "../../api/oauthClient";
import { backendUrl } from "../../tests/testConfig";

/**
 * Provisions an operator that holds exactly one admin permission (Issue #1898).
 *
 * A management API declares what it requires in a `Map` inside a default `getRequiredPermissions`,
 * and nothing downstream restates it. A role carrying `idp:*` satisfies whatever is in that map, so
 * an API asking for the wrong permission answers 200 to every administrator an E2E provisions and
 * the mistake never surfaces. Asking with one permission at a time is what makes the map readable
 * from outside.
 */

const ORG_SCOPE = "org-management account management";

/** Permission name -> id, for one tenant. Ids are per-tenant rows, not constants. */
export async function fetchPermissionIds(
  managementToken,
  organizationId,
  tenantId
) {
  const response = await get({
    url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/permissions?limit=300`,
    headers: { Authorization: `Bearer ${managementToken}` },
  });
  if (response.status !== 200) {
    throw new Error(
      `failed to list permissions: ${response.status} ${JSON.stringify(
        response.data
      )}`
    );
  }
  return new Map(
    response.data.list.map((permission) => [permission.name, permission.id])
  );
}

/**
 * Creates a role holding only `permissionName`, a user holding only that role, and returns a token
 * for them.
 *
 * The user is assigned to the organization and the tenant. `OrganizationAccessVerifier` checks
 * membership before permissions, so without the assignment every call answers
 * `organization_access_denied` and the permission is never consulted — a green test that proves
 * nothing.
 */
export async function createOperatorWithPermission({
  managementToken,
  organizationId,
  tenantId,
  clientId,
  clientSecret,
  permissionName,
  permissionId,
  label,
}) {
  const suffix = `${label}-${Date.now()}-${Math.random()
    .toString(36)
    .slice(2, 8)}`;
  const roleName = `perm-probe-${suffix}`;
  const email = `perm-probe-${suffix}@example.com`;
  const password = "PermProbe_pass1!";
  const base = `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`;
  const headers = { Authorization: `Bearer ${managementToken}` };

  const roleResponse = await postWithJson({
    url: `${base}/roles`,
    headers,
    body: {
      name: roleName,
      description: `holds only ${permissionName}`,
      permissions: [permissionId],
    },
  });
  if (roleResponse.status !== 201) {
    throw new Error(
      `failed to create role for ${permissionName}: ${
        roleResponse.status
      } ${JSON.stringify(roleResponse.data)}`
    );
  }
  const roleId = roleResponse.data.result.id;

  const userResponse = await postWithJson({
    url: `${base}/users`,
    headers,
    body: {
      provider_id: "idp-server",
      name: email,
      email,
      email_verified: true,
      raw_password: password,
      status: "REGISTERED",
      assigned_organizations: [organizationId],
      assigned_tenants: [tenantId],
      roles: [{ role_id: roleId, role_name: roleName }],
    },
  });
  if (userResponse.status !== 201) {
    throw new Error(
      `failed to create user for ${permissionName}: ${
        userResponse.status
      } ${JSON.stringify(userResponse.data)}`
    );
  }
  const sub = userResponse.data.result.sub;

  const tokenResponse = await requestToken({
    endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
    grantType: "password",
    username: email,
    password,
    scope: ORG_SCOPE,
    clientId,
    clientSecret,
  });
  if (tokenResponse.status !== 200) {
    throw new Error(
      `failed to issue token for ${permissionName}: ${
        tokenResponse.status
      } ${JSON.stringify(tokenResponse.data)}`
    );
  }

  return {
    permissionName,
    roleId,
    sub,
    accessToken: tokenResponse.data.access_token,
  };
}

/** Removes what `createOperatorWithPermission` left behind. Never throws. */
export async function deleteOperators(
  managementToken,
  organizationId,
  tenantId,
  operators
) {
  const base = `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`;
  const headers = { Authorization: `Bearer ${managementToken}` };
  for (const operator of operators) {
    try {
      await deletion({ url: `${base}/users/${operator.sub}`, headers });
      await deletion({ url: `${base}/roles/${operator.roleId}`, headers });
    } catch (e) {
      console.log(`cleanup failed for ${operator.permissionName}:`, e.message);
    }
  }
}
