import { describe, expect, test, beforeAll } from "@jest/globals";
import { deletion, get } from "../../../../lib/http";
import { backendUrl } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";

describe("Organization Token Management API Test", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";

  const baseUrl = `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/tokens`;

  let authHeader;

  beforeAll(async () => {
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      scope: "org-management account management",
      clientId: "org-client",
      clientSecret: "org-client-001",
    });
    authHeader = `Bearer ${tokenResponse.data.access_token}`;
  });

  test("should list tokens with default pagination", async () => {
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

  test("should support filtering by grant_type", async () => {
    const listResponse = await get({
      url: `${baseUrl}?grant_type=password&limit=10`,
      headers: {
        Authorization: authHeader,
      },
    });

    console.log(
      "Grant Type Filter Response:",
      JSON.stringify(listResponse.data)
    );
    expect(listResponse.status).toBe(200);
    expect(listResponse.data).toHaveProperty("total_count");

    if (listResponse.data.list.length > 0) {
      listResponse.data.list.forEach((token) => {
        expect(token.grant_type).toBe("password");
      });
    }
  });

  test("should support time range filtering", async () => {
    const oneYearAgo = new Date();
    oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1);
    const fromDate = oneYearAgo
      .toISOString()
      .substring(0, 19)
      .replace("T", " ");

    const listResponse = await get({
      url: `${baseUrl}?from=${encodeURIComponent(fromDate)}&limit=10`,
      headers: {
        Authorization: authHeader,
      },
    });

    console.log(
      "Time Range Filter Response:",
      JSON.stringify(listResponse.data)
    );
    expect(listResponse.status).toBe(200);
    expect(listResponse.data).toHaveProperty("total_count");
    expect(listResponse.data).toHaveProperty("list");
  });

  test("should return correct token fields in list", async () => {
    const listResponse = await get({
      url: `${baseUrl}?limit=1`,
      headers: {
        Authorization: authHeader,
      },
    });
    expect(listResponse.status).toBe(200);

    if (listResponse.data.list.length > 0) {
      const token = listResponse.data.list[0];
      expect(token).toHaveProperty("id");
      expect(token).toHaveProperty("tenant_id");
      expect(token).toHaveProperty("client_id");
      expect(token).toHaveProperty("grant_type");
      expect(token).toHaveProperty("scopes");
      expect(token).toHaveProperty("token_type");
      expect(token).toHaveProperty("access_token_expires_at");
      expect(token).toHaveProperty("has_refresh_token");
      expect(token).toHaveProperty("created_at");
      // token values must NOT be exposed
      expect(token).not.toHaveProperty("encrypted_access_token");
      expect(token).not.toHaveProperty("encrypted_refresh_token");
    }
  });

  test("should get token detail when tokens exist", async () => {
    const listResponse = await get({
      url: `${baseUrl}?limit=1`,
      headers: {
        Authorization: authHeader,
      },
    });
    expect(listResponse.status).toBe(200);

    if (listResponse.data.list.length > 0) {
      const tokenId = listResponse.data.list[0].id;
      const detailResponse = await get({
        url: `${baseUrl}/${tokenId}`,
        headers: {
          Authorization: authHeader,
        },
      });
      console.log("Detail Response:", JSON.stringify(detailResponse.data));
      expect(detailResponse.status).toBe(200);
      expect(detailResponse.data.id).toBe(tokenId);
    }
  });

  test("should return 404 for non-existent token", async () => {
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
      const tokenId = listResponse.data.list[0].id;

      // Dry-run delete
      const dryRunResponse = await deletion({
        url: `${baseUrl}/${tokenId}?dry_run=true`,
        headers: {
          Authorization: authHeader,
        },
      });
      console.log(
        "Dry-run Delete Response:",
        dryRunResponse.status,
        dryRunResponse.data
      );
      expect(dryRunResponse.status).toBe(200);
      expect(dryRunResponse.data).toHaveProperty("dry_run", true);
      expect(dryRunResponse.data).toHaveProperty("target");

      // Verify token still exists
      const verifyResponse = await get({
        url: `${baseUrl}/${tokenId}`,
        headers: {
          Authorization: authHeader,
        },
      });
      expect(verifyResponse.status).toBe(200);
      expect(verifyResponse.data.id).toBe(tokenId);
    }
  });

  test("should support dry-run delete user tokens", async () => {
    const listResponse = await get({
      url: `${baseUrl}?limit=1`,
      headers: {
        Authorization: authHeader,
      },
    });
    expect(listResponse.status).toBe(200);

    if (listResponse.data.list.length > 0) {
      const userId = listResponse.data.list[0].user_id;
      if (userId) {
        const userTokensUrl = `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}/tokens`;

        const dryRunResponse = await deletion({
          url: `${userTokensUrl}?dry_run=true`,
          headers: {
            Authorization: authHeader,
          },
        });
        console.log(
          "Dry-run Delete User Tokens Response:",
          dryRunResponse.status,
          dryRunResponse.data
        );
        expect(dryRunResponse.status).toBe(200);
        expect(dryRunResponse.data).toHaveProperty("dry_run", true);
        expect(dryRunResponse.data).toHaveProperty("user_id", userId);
        expect(dryRunResponse.data).toHaveProperty("affected_count");
        expect(typeof dryRunResponse.data.affected_count).toBe("number");
      }
    }
  });
});
