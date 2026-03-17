import { describe, expect, test, beforeAll } from "@jest/globals";
import { deletion, get } from "../../../../lib/http";
import { backendUrl } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";

describe("Organization Identity Verification Application Management API Test", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";

  const baseUrl = `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-applications`;

  let authHeader;

  beforeAll(async () => {
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      scope: "org-management account management",
      clientId: "org-client",
      clientSecret: "org-client-001"
    });
    authHeader = `Bearer ${tokenResponse.data.access_token}`;
  });

  test("should list identity verification applications with default pagination", async () => {
    const listResponse = await get({
      url: baseUrl,
      headers: {
        Authorization: authHeader,
      },
    });

    console.log("List Response:", JSON.stringify(listResponse.data));
    expect(listResponse.status).toBe(200);
    expect(listResponse.data).toHaveProperty("list");
    expect(listResponse.data).toHaveProperty("total_count");
    expect(Array.isArray(listResponse.data.list)).toBe(true);
    expect(typeof listResponse.data.total_count).toBe("number");
  });

  test("should support pagination with limit and offset", async () => {
    const listResponse = await get({
      url: `${baseUrl}?limit=5&offset=0`,
      headers: {
        Authorization: authHeader,
      },
    });

    console.log("Pagination Response:", JSON.stringify(listResponse.data));
    expect(listResponse.status).toBe(200);
    expect(listResponse.data).toHaveProperty("list");
    expect(listResponse.data).toHaveProperty("total_count");
    expect(listResponse.data).toHaveProperty("limit");
    expect(listResponse.data).toHaveProperty("offset");
    expect(listResponse.data.limit).toBe(5);
    expect(listResponse.data.offset).toBe(0);
    expect(listResponse.data.list.length).toBeLessThanOrEqual(5);
  });

  test("should support filtering by status", async () => {
    const listResponse = await get({
      url: `${baseUrl}?status=approved&limit=10`,
      headers: {
        Authorization: authHeader,
      },
    });

    console.log("Status Filter Response:", JSON.stringify(listResponse.data));
    expect(listResponse.status).toBe(200);
    expect(listResponse.data).toHaveProperty("total_count");

    if (listResponse.data.list.length > 0) {
      listResponse.data.list.forEach(app => {
        expect(app.status).toBe("approved");
      });
    }
  });

  test("should support filtering by type", async () => {
    const listResponse = await get({
      url: `${baseUrl}?type=NonExistentType12345&limit=10&offset=0`,
      headers: {
        Authorization: authHeader,
      },
    });

    console.log("Type Filter Response:", JSON.stringify(listResponse.data));
    expect(listResponse.status).toBe(200);
    expect(listResponse.data.list).toEqual([]);
    expect(listResponse.data.total_count).toBe(0);
  });

  test("should support time range filtering", async () => {
    const oneYearAgo = new Date();
    oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1);
    const fromDate = oneYearAgo.toISOString().substring(0, 19).replace("T", " ");

    const listResponse = await get({
      url: `${baseUrl}?from=${encodeURIComponent(fromDate)}&limit=10`,
      headers: {
        Authorization: authHeader,
      },
    });

    console.log("Time Range Filter Response:", JSON.stringify(listResponse.data));
    expect(listResponse.status).toBe(200);
    expect(listResponse.data).toHaveProperty("total_count");
    expect(listResponse.data).toHaveProperty("list");
  });

  test("should support multiple query parameters", async () => {
    const oneYearAgo = new Date();
    oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1);
    const fromDate = oneYearAgo.toISOString().substring(0, 19).replace("T", " ");

    const listResponse = await get({
      url: `${baseUrl}?from=${encodeURIComponent(fromDate)}&status=approved&limit=5&offset=0`,
      headers: {
        Authorization: authHeader,
      },
    });

    console.log("Multi Filter Response:", JSON.stringify(listResponse.data));
    expect(listResponse.status).toBe(200);
    expect(listResponse.data).toHaveProperty("total_count");
    expect(listResponse.data.limit).toBe(5);
    expect(listResponse.data.offset).toBe(0);
  });

  test("should get application detail when applications exist", async () => {
    const listResponse = await get({
      url: `${baseUrl}?limit=1`,
      headers: {
        Authorization: authHeader,
      },
    });
    expect(listResponse.status).toBe(200);

    if (listResponse.data.list.length > 0) {
      const applicationId = listResponse.data.list[0].id;
      const detailResponse = await get({
        url: `${baseUrl}/${applicationId}`,
        headers: {
          Authorization: authHeader,
        },
      });
      console.log("Detail Response:", JSON.stringify(detailResponse.data));
      expect(detailResponse.status).toBe(200);
      expect(detailResponse.data.id).toBe(applicationId);
    }
  });

  test("should return 404 for non-existent application", async () => {
    const response = await get({
      url: `${baseUrl}/00000000-0000-0000-0000-000000000000`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(response.status).toBe(404);
  });

  test("should support dry-run delete", async () => {
    const listResponse = await get({
      url: `${baseUrl}?limit=1`,
      headers: {
        Authorization: authHeader,
      },
    });
    expect(listResponse.status).toBe(200);

    if (listResponse.data.list.length > 0) {
      const applicationId = listResponse.data.list[0].id;

      // Dry-run delete
      const dryRunResponse = await deletion({
        url: `${baseUrl}/${applicationId}?dry_run=true`,
        headers: {
          Authorization: authHeader,
        },
      });
      console.log("Dry-run Delete Response:", dryRunResponse.status, dryRunResponse.data);
      expect(dryRunResponse.status).toBe(200);

      // Verify application still exists
      const verifyResponse = await get({
        url: `${baseUrl}/${applicationId}`,
        headers: {
          Authorization: authHeader,
        },
      });
      expect(verifyResponse.status).toBe(200);
      expect(verifyResponse.data.id).toBe(applicationId);
    }
  });
});
