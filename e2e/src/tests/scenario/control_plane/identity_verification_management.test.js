import { describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import { get, postWithJson, putWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { clientSecretPostClient, serverConfig, backendUrl } from "../../testConfig";

describe("identity verification configuration management api", () => {

  describe("success pattern", () => {

    it("basic list and get operations", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
        scope: clientSecretPostClient.scope,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Step 1: List identity verification configurations
      const listResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/identity-verification-configurations`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List Response:", JSON.stringify(listResponse.data));
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("list");
      expect(listResponse.data).toHaveProperty("total_count");
      console.log("✅ Identity verification configs listed successfully");

      // Step 2: Get details of the first config if any exist
      if (listResponse.data.list.length > 0) {
        const firstConfigId = listResponse.data.list[0].id;
        const detailResponse = await get({
          url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/identity-verification-configurations/${firstConfigId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("Detail Response:", detailResponse.status, detailResponse.data);
        expect(detailResponse.status).toBe(200);
        expect(detailResponse.data.id).toBe(firstConfigId);
        console.log("✅ Identity verification config detail retrieved successfully");
      }

      console.log("🎉 Basic identity verification config operations test passed!");
    });
  });

  describe("IdentityVerificationQueries and Pagination", () => {

    it("should support basic pagination with total_count", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
        scope: clientSecretPostClient.scope,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Test basic pagination
      const listResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/identity-verification-configurations?limit=5&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Identity Verification Pagination Response:", listResponse.data);
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("list");
      expect(listResponse.data).toHaveProperty("total_count");
      expect(listResponse.data).toHaveProperty("limit");
      expect(listResponse.data).toHaveProperty("offset");
      expect(listResponse.data.limit).toBe(5);
      expect(listResponse.data.offset).toBe(0);
      expect(typeof listResponse.data.total_count).toBe("number");
      console.log("✅ Identity verification basic pagination test passed");
    });

    it("should support filtering by type", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
        scope: clientSecretPostClient.scope,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Test filtering by type
      const typeResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/identity-verification-configurations?type=ekyc_test_provider&limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Identity Verification Type Filter Response:", typeResponse.data);
      expect(typeResponse.status).toBe(200);
      expect(typeResponse.data).toHaveProperty("total_count");
      
      // All returned configurations should be of the specified type
      if (typeResponse.data.list.length > 0) {
        typeResponse.data.list.forEach(config => {
          expect(config.type).toBe("ekyc_test_provider");
        });
      }
      console.log("✅ Identity verification type filtering test passed");
    });

    it("should support limit and offset parameters", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
        scope: clientSecretPostClient.scope,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Test with small limit
      const limitResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/identity-verification-configurations?limit=2&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Identity Verification Limit Response:", limitResponse.data);
      expect(limitResponse.status).toBe(200);
      expect(limitResponse.data).toHaveProperty("total_count");
      expect(limitResponse.data.limit).toBe(2);
      expect(limitResponse.data.offset).toBe(0);
      
      // List should not exceed the limit
      expect(limitResponse.data.list.length).toBeLessThanOrEqual(2);
      console.log("✅ Identity verification limit and offset parameters test passed");
    });

    it("should handle empty results with proper pagination info", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
        scope: clientSecretPostClient.scope,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Test with a query that should return no results
      const emptyResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/identity-verification-configurations?type=NonExistentProvider12345&limit=10&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Identity Verification Empty Results Response:", emptyResponse.data);
      expect(emptyResponse.status).toBe(200);
      expect(emptyResponse.data.list).toEqual([]);
      expect(emptyResponse.data.total_count).toBe(0);
      expect(emptyResponse.data.limit).toBe(10);
      expect(emptyResponse.data.offset).toBe(0);
      console.log("✅ Identity verification empty results test passed");
    });

    it("should support multiple query parameters", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
        scope: clientSecretPostClient.scope,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Test multiple query parameters (using type and limit/offset)
      const multiFilterResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/identity-verification-configurations?type=ekyc_test_provider&limit=5&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Identity Verification Multi Filter Response:", multiFilterResponse.data);
      expect(multiFilterResponse.status).toBe(200);
      expect(multiFilterResponse.data).toHaveProperty("total_count");
      expect(multiFilterResponse.data.limit).toBe(5);
      expect(multiFilterResponse.data.offset).toBe(0);
      
      // All returned configurations should match the type condition
      multiFilterResponse.data.list.forEach(config => {
        expect(config.type).toBe("ekyc_test_provider");
      });
      console.log("✅ Identity verification multiple query parameters test passed");
    });

    it("should support time range filtering", async () => {
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
        scope: clientSecretPostClient.scope,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Test filtering with from/to date range (get configs from the last year)
      const oneYearAgo = new Date();
      oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1);
      const fromDate = oneYearAgo.toISOString().substring(0, 19).replace("T", " ");

      const timeResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/identity-verification-configurations?from=${encodeURIComponent(fromDate)}&limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Identity Verification Time Filter Response:", timeResponse.data);
      expect(timeResponse.status).toBe(200);
      expect(timeResponse.data).toHaveProperty("total_count");
      expect(timeResponse.data).toHaveProperty("limit");
      expect(timeResponse.data).toHaveProperty("offset");
      console.log("✅ Identity verification time range filtering test passed");
    });
  });
});