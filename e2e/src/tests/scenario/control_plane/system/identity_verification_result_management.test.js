import { describe, expect, it } from "@jest/globals";
import { get } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { adminServerConfig, backendUrl, serverConfig } from "../../../testConfig";
import {
  isUUID,
  isISODateTime,
  validateIdentityVerificationResult,
  validateListResponse,
} from "../../../../lib/schemaValidation";

describe("identity verification result management api", () => {

  // Use integration test tenant which has data from IDA integration tests
  const tenantId = serverConfig.tenantId;
  const baseUrl = `${backendUrl}/v1/management/tenants/${tenantId}/identity-verification-results`;

  const getAccessToken = async () => {
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
    return tokenResponse.data.access_token;
  };

  describe("list operations", () => {

    it("should list results with default pagination", async () => {
      const accessToken = await getAccessToken();

      const response = await get({
        url: baseUrl,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      console.log("List Response:", JSON.stringify(response.data));
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("list");
      expect(response.data).toHaveProperty("total_count");
      expect(Array.isArray(response.data.list)).toBe(true);
      expect(typeof response.data.total_count).toBe("number");
      expect(response.data.limit).toBe(20);
      expect(response.data.offset).toBe(0);
    });

    it("should support pagination with limit and offset", async () => {
      const accessToken = await getAccessToken();

      const response = await get({
        url: `${baseUrl}?limit=5&offset=0`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(response.status).toBe(200);
      expect(response.data.limit).toBe(5);
      expect(response.data.offset).toBe(0);
      expect(response.data.list.length).toBeLessThanOrEqual(5);
    });

    it("should support filtering by type", async () => {
      const accessToken = await getAccessToken();

      const response = await get({
        url: `${baseUrl}?type=NonExistentType12345&limit=10&offset=0`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(response.status).toBe(200);
      expect(response.data.list).toEqual([]);
      expect(response.data.total_count).toBe(0);
    });

    it("should support filtering by source", async () => {
      const accessToken = await getAccessToken();

      const response = await get({
        url: `${baseUrl}?source=application&limit=10`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("total_count");

      if (response.data.list.length > 0) {
        response.data.list.forEach(result => {
          expect(result.source).toBe("application");
        });
      }
    });

    it("should support time range filtering", async () => {
      const accessToken = await getAccessToken();

      const oneYearAgo = new Date();
      oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1);
      const fromDate = oneYearAgo.toISOString().substring(0, 19).replace("T", " ");

      const response = await get({
        url: `${baseUrl}?verified_at_from=${encodeURIComponent(fromDate)}&limit=10`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("total_count");
      expect(response.data).toHaveProperty("list");
    });
  });

  describe("get by id operations", () => {

    it("should get result detail when results exist", async () => {
      const accessToken = await getAccessToken();

      const listResponse = await get({
        url: `${baseUrl}?limit=1`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(listResponse.status).toBe(200);

      if (listResponse.data.list.length > 0) {
        const resultId = listResponse.data.list[0].id;
        const detailResponse = await get({
          url: `${baseUrl}/${resultId}`,
          headers: { Authorization: `Bearer ${accessToken}` },
        });
        expect(detailResponse.status).toBe(200);
        expect(detailResponse.data.id).toBe(resultId);
      }
    });

    it("should return 404 for non-existent result", async () => {
      const accessToken = await getAccessToken();

      const response = await get({
        url: `${baseUrl}/00000000-0000-0000-0000-000000000000`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(response.status).toBe(404);
    });
  });

  describe("schema compliance", () => {

    it("should validate IdentityVerificationResult schema for each item", async () => {
      const accessToken = await getAccessToken();

      const response = await get({
        url: `${baseUrl}?limit=5`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(response.status).toBe(200);

      const validation = validateListResponse(
        response.data,
        validateIdentityVerificationResult
      );
      if (!validation.valid) {
        console.log("Schema validation errors:", validation.errors);
      }
      expect(validation.valid).toBe(true);

      if (response.data.list.length > 0) {
        for (const result of response.data.list) {
          expect(isUUID(result.id)).toBe(true);
          expect(typeof result.type).toBe("string");
          expect(typeof result.tenant_id).toBe("string");
          expect(typeof result.user_id).toBe("string");
          expect(typeof result.source).toBe("string");
          expect(["application", "direct", "manual", "import"]).toContain(result.source);
          expect(isISODateTime(result.verified_at)).toBe(true);
          expect(typeof result.verified_claims).toBe("object");
          console.log(`✅ ${result.id} (type=${result.type}, source=${result.source})`);
        }
      }
    });

    it("should validate list/detail consistency", async () => {
      const accessToken = await getAccessToken();

      const listResponse = await get({
        url: `${baseUrl}?limit=3`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(listResponse.status).toBe(200);

      for (const listItem of listResponse.data.list) {
        const detailResponse = await get({
          url: `${baseUrl}/${listItem.id}`,
          headers: { Authorization: `Bearer ${accessToken}` },
        });
        expect(detailResponse.status).toBe(200);
        expect(detailResponse.data.id).toBe(listItem.id);
        expect(detailResponse.data.type).toBe(listItem.type);
        expect(detailResponse.data.tenant_id).toBe(listItem.tenant_id);
        expect(detailResponse.data.user_id).toBe(listItem.user_id);
        expect(detailResponse.data.source).toBe(listItem.source);
        expect(detailResponse.data.verified_at).toBe(listItem.verified_at);
      }
    });

    it("should return 404 with ErrorResponse schema", async () => {
      const accessToken = await getAccessToken();

      const response = await get({
        url: `${baseUrl}/00000000-0000-0000-0000-000000000000`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(response.status).toBe(404);
      expect(response.data).toHaveProperty("error");
      expect(response.data).toHaveProperty("error_description");
      expect(typeof response.data.error).toBe("string");
      expect(typeof response.data.error_description).toBe("string");
    });
  });
});
