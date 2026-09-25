import { afterAll, beforeAll, describe, expect, it } from "@jest/globals";
import { get } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { backendUrl } from "../../../testConfig";
import {
  createOperatorWithPermission,
  deleteOperators,
  fetchPermissionIds,
} from "../../../../lib/adminPermission";

/**
 * Which admin permission each organization-level read endpoint actually demands (Issue #1898).
 *
 * Every other control-plane E2E provisions an administrator holding `idp:*`, which satisfies any
 * permission the API asks for. That is why an API requiring the wrong one — the organization
 * authentication *configuration* endpoint requiring the authentication *policy* permission, copied
 * from its sibling interface — answered 200 to every test that existed. The permission name lives
 * only in a `Map` inside a default `getRequiredPermissions`; nothing downstream restates it, so
 * there was no second place for it to disagree with.
 *
 * Each case is asked twice: once by an operator holding only the expected permission, and once
 * by an operator holding only the `decoy` — the permission this endpoint must NOT accept. The decoy
 * is the confusable sibling wherever one exists (config vs policy-config, hook vs hook-config,
 * role vs permission), because a copy-paste lands on the sibling, not on something unrelated.
 *
 * This is a ledger as much as a test: the table is the readable form of what the
 * `getRequiredPermissions` maps say, so a new endpoint that is missing here is visible as a gap.
 */

const ORG_ID = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
const TENANT_ID = "952f6906-3e95-4ed3-86b2-981f90f785f9";
const CLIENT_ID = "org-client";
const CLIENT_SECRET = "org-client-001";

const tenantScoped = (path) =>
  `${backendUrl}/v1/management/organizations/${ORG_ID}/tenants/${TENANT_ID}/${path}`;

const CASES = [
  {
    path: "authentication-configurations",
    required: "idp:authentication-config:read",
    // Issue #1898: this endpoint required the policy permission.
    decoy: "idp:authentication-policy-config:read",
  },
  {
    path: "authentication-policies",
    required: "idp:authentication-policy-config:read",
    decoy: "idp:authentication-config:read",
  },
  {
    path: "security-event-hook-configurations",
    required: "idp:security-event-hook-config:read",
    decoy: "idp:security-event-hook:read",
  },
  {
    path: "security-event-hooks",
    required: "idp:security-event-hook:read",
    decoy: "idp:security-event-hook-config:read",
  },
  {
    path: "identity-verification-configurations",
    required: "idp:identity-verification-config:read",
    decoy: "idp:identity-verification-result:read",
  },
  {
    path: "identity-verification-results",
    required: "idp:identity-verification-result:read",
    decoy: "idp:identity-verification-config:read",
  },
  {
    path: "identity-verification-applications",
    required: "idp:identity-verification-application:read",
    decoy: "idp:identity-verification-result:read",
  },
  { path: "roles", required: "idp:role:read", decoy: "idp:permission:read" },
  {
    path: "permissions",
    required: "idp:permission:read",
    decoy: "idp:role:read",
  },
  { path: "clients", required: "idp:client:read", decoy: "idp:tenant:read" },
  {
    path: "audit-logs",
    required: "idp:audit-log:read",
    decoy: "idp:security-event:read",
  },
  {
    path: "security-events",
    required: "idp:security-event:read",
    decoy: "idp:audit-log:read",
  },
  { path: "federation-configurations", required: "idp:federation-config:read" },
  { path: "grants", required: "idp:grant:read" },
  {
    path: "authentication-interactions",
    required: "idp:authentication-interaction:read",
  },
  {
    path: "authentication-transactions",
    required: "idp:authentication-transaction:read",
  },
  {
    path: "contact-verification-challenges",
    required: "idp:contact-verification-challenge:read",
  },
  // Not a list: the tenant's own authorization server document, gated by the tenant permission.
  {
    path: "authorization-server",
    required: "idp:tenant:read",
    decoy: "idp:client:read",
  },
  // OrgUserManagementApi branches on tenant type. This tenant is ORGANIZER, so it is the admin-user
  // permission here; a PUBLIC tenant would require idp:user:read for the same path.
  { path: "users", required: "idp:admin-user:read", decoy: "idp:user:read" },
];

const DEFAULT_DECOY = "idp:client:read";

describe("organization management api admin permission enforcement", () => {
  let managementToken;
  const operators = new Map();
  const created = [];

  /** One operator per distinct permission, reused across cases. */
  const operatorFor = (permissionName) =>
    operators.get(permissionName).accessToken;

  beforeAll(async () => {
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${TENANT_ID}/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      scope: "org-management account management",
      clientId: CLIENT_ID,
      clientSecret: CLIENT_SECRET,
    });
    expect(tokenResponse.status).toBe(200);
    managementToken = tokenResponse.data.access_token;

    const permissionIds = await fetchPermissionIds(
      managementToken,
      ORG_ID,
      TENANT_ID
    );
    const needed = new Set();
    for (const testCase of CASES) {
      needed.add(testCase.required);
      needed.add(testCase.decoy ?? DEFAULT_DECOY);
    }

    for (const permissionName of needed) {
      const permissionId = permissionIds.get(permissionName);
      // A name that no longer exists is a finding, not a reason to skip: the table would be
      // describing an endpoint gated by something the server does not define.
      expect(permissionId).toBeDefined();
      const operator = await createOperatorWithPermission({
        managementToken,
        organizationId: ORG_ID,
        tenantId: TENANT_ID,
        clientId: CLIENT_ID,
        clientSecret: CLIENT_SECRET,
        permissionName,
        permissionId,
        label: permissionName.replaceAll(":", "-"),
      });
      operators.set(permissionName, operator);
      created.push(operator);
    }
  }, 120000);

  afterAll(async () => {
    await deleteOperators(managementToken, ORG_ID, TENANT_ID, created);
  }, 120000);

  describe("the expected permission is accepted", () => {
    it.each(CASES.map((c) => [c.path, c.required]))(
      "GET %s accepts %s",
      async (path, required) => {
        const response = await get({
          url: tenantScoped(path),
          headers: { Authorization: `Bearer ${operatorFor(required)}` },
        });
        console.log(`${path} with ${required}:`, response.status);
        expect(response.status).toBe(200);
      }
    );
  });

  describe("a different permission is refused", () => {
    it.each(CASES.map((c) => [c.path, c.decoy ?? DEFAULT_DECOY, c.required]))(
      "GET %s refuses %s",
      async (path, decoy, required) => {
        const response = await get({
          url: tenantScoped(path),
          headers: { Authorization: `Bearer ${operatorFor(decoy)}` },
        });
        console.log(
          `${path} with ${decoy}:`,
          response.status,
          JSON.stringify(response.data).slice(0, 140)
        );
        expect(response.status).toBe(403);
        // The message names what was missing, so a refusal for the wrong reason (membership,
        // scope, a tenant that does not exist) does not read as a passing permission check.
        expect(response.data.error_description).toContain(required);
      }
    );
  });

  it("GET organization tenants refuses idp:client:read", async () => {
    // The tenant list sits one level up, outside the per-tenant table above, so it is asked
    // separately. OrgTenantManagementApi requires idp:tenant:read for it.
    const response = await get({
      url: `${backendUrl}/v1/management/organizations/${ORG_ID}/tenants`,
      headers: { Authorization: `Bearer ${operatorFor("idp:client:read")}` },
    });
    console.log(
      "organization tenants with idp:client:read:",
      response.status,
      JSON.stringify(response.data).slice(0, 140)
    );
    expect(response.status).toBe(403);
    expect(response.data.error_description).toContain("idp:tenant:read");
  });
});
