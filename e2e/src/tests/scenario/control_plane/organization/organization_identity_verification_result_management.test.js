import { describe, expect, test, beforeAll } from "@jest/globals";
import { get } from "../../../../lib/http";
import { backendUrl } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";
import {
  validateIdentityVerificationResult,
  validateListResponse,
} from "../../../../lib/schemaValidation";

describe("Organization Identity Verification Result Management API Test", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";

  const baseUrl = `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-results`;

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

  test("should list results with default pagination", async () => {
    const response = await get({
      url: baseUrl,
      headers: { Authorization: authHeader },
    });

    expect(response.status).toBe(200);
    expect(response.data).toHaveProperty("list");
    expect(response.data).toHaveProperty("total_count");
    expect(Array.isArray(response.data.list)).toBe(true);
    expect(response.data.limit).toBe(20);
    expect(response.data.offset).toBe(0);

    const validation = validateListResponse(
      response.data,
      validateIdentityVerificationResult
    );
    if (!validation.valid) {
      console.log("Schema validation errors:", validation.errors);
    }
    expect(validation.valid).toBe(true);
  });

  test("should support pagination with limit and offset", async () => {
    const response = await get({
      url: `${baseUrl}?limit=5&offset=0`,
      headers: { Authorization: authHeader },
    });

    expect(response.status).toBe(200);
    expect(response.data.limit).toBe(5);
    expect(response.data.offset).toBe(0);
    expect(response.data.list.length).toBeLessThanOrEqual(5);
  });

  test("should return empty list for non-matching type filter", async () => {
    const response = await get({
      url: `${baseUrl}?type=NonExistentType12345&limit=10&offset=0`,
      headers: { Authorization: authHeader },
    });

    expect(response.status).toBe(200);
    expect(response.data.list).toEqual([]);
    expect(response.data.total_count).toBe(0);
  });

  test("should support filtering by source", async () => {
    const response = await get({
      url: `${baseUrl}?source=application&limit=10`,
      headers: { Authorization: authHeader },
    });

    expect(response.status).toBe(200);
    if (response.data.list.length > 0) {
      response.data.list.forEach((result) => {
        expect(result.source).toBe("application");
      });
    }
  });

  test("should return 404 for non-existent result", async () => {
    const response = await get({
      url: `${baseUrl}/00000000-0000-0000-0000-000000000000`,
      headers: { Authorization: authHeader },
    });

    expect(response.status).toBe(404);
    expect(response.data).toHaveProperty("error");
    expect(response.data).toHaveProperty("error_description");
  });

  test("should get result detail when results exist", async () => {
    const listResponse = await get({
      url: `${baseUrl}?limit=1`,
      headers: { Authorization: authHeader },
    });
    expect(listResponse.status).toBe(200);

    if (listResponse.data.list.length > 0) {
      const resultId = listResponse.data.list[0].id;
      const detailResponse = await get({
        url: `${baseUrl}/${resultId}`,
        headers: { Authorization: authHeader },
      });
      expect(detailResponse.status).toBe(200);
      expect(detailResponse.data.id).toBe(resultId);
    }
  });
});
