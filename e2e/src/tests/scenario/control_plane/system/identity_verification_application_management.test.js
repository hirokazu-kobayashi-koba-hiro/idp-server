import { describe, expect, it } from "@jest/globals";
import { get, deletion, post } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import {
  adminServerConfig,
  backendUrl,
  serverConfig,
  federationServerConfig,
  clientSecretPostClient,
} from "../../../testConfig";
import { createFederatedUser, registerFidoUaf } from "../../../../user";

describe("identity verification application management api", () => {

  const baseUrl = `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/identity-verification-applications`;

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

    it("should list identity verification applications with default pagination", async () => {
      const accessToken = await getAccessToken();

      const listResponse = await get({
        url: baseUrl,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List Response:", JSON.stringify(listResponse.data));
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("list");
      expect(listResponse.data).toHaveProperty("total_count");
      expect(Array.isArray(listResponse.data.list)).toBe(true);
      expect(typeof listResponse.data.total_count).toBe("number");
    });

    it("should support pagination with limit and offset", async () => {
      const accessToken = await getAccessToken();

      const listResponse = await get({
        url: `${baseUrl}?limit=5&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
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

    it("should support filtering by status", async () => {
      const accessToken = await getAccessToken();

      const listResponse = await get({
        url: `${baseUrl}?status=approved&limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
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

    it("should support filtering by type", async () => {
      const accessToken = await getAccessToken();

      const listResponse = await get({
        url: `${baseUrl}?type=NonExistentType12345&limit=10&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Type Filter Response:", JSON.stringify(listResponse.data));
      expect(listResponse.status).toBe(200);
      expect(listResponse.data.list).toEqual([]);
      expect(listResponse.data.total_count).toBe(0);
    });

    it("should support time range filtering", async () => {
      const accessToken = await getAccessToken();

      const oneYearAgo = new Date();
      oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1);
      const fromDate = oneYearAgo.toISOString().substring(0, 19).replace("T", " ");

      const listResponse = await get({
        url: `${baseUrl}?from=${encodeURIComponent(fromDate)}&limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Time Range Filter Response:", JSON.stringify(listResponse.data));
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("total_count");
      expect(listResponse.data).toHaveProperty("list");
    });

    it("should support multiple query parameters", async () => {
      const accessToken = await getAccessToken();

      const oneYearAgo = new Date();
      oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1);
      const fromDate = oneYearAgo.toISOString().substring(0, 19).replace("T", " ");

      const listResponse = await get({
        url: `${baseUrl}?from=${encodeURIComponent(fromDate)}&status=approved&limit=5&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Multi Filter Response:", JSON.stringify(listResponse.data));
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("total_count");
      expect(listResponse.data.limit).toBe(5);
      expect(listResponse.data.offset).toBe(0);
    });
  });

  describe("get by id operations", () => {

    it("should get application detail when applications exist", async () => {
      const accessToken = await getAccessToken();

      // First get the list to find an existing application
      const listResponse = await get({
        url: `${baseUrl}?limit=1`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(listResponse.status).toBe(200);

      if (listResponse.data.list.length > 0) {
        const applicationId = listResponse.data.list[0].id;
        const detailResponse = await get({
          url: `${baseUrl}/${applicationId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("Detail Response:", JSON.stringify(detailResponse.data));
        expect(detailResponse.status).toBe(200);
        expect(detailResponse.data.id).toBe(applicationId);
      }
    });

    it("should return 404 for non-existent application", async () => {
      const accessToken = await getAccessToken();

      const response = await get({
        url: `${baseUrl}/00000000-0000-0000-0000-000000000000`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(response.status).toBe(404);
    });
  });

  describe("delete operations", () => {

    it("should support dry-run delete", async () => {
      const accessToken = await getAccessToken();

      // First get the list to find an existing application
      const listResponse = await get({
        url: `${baseUrl}?limit=1`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(listResponse.status).toBe(200);

      if (listResponse.data.list.length > 0) {
        const applicationId = listResponse.data.list[0].id;

        // Dry-run delete
        const dryRunResponse = await deletion({
          url: `${baseUrl}/${applicationId}?dry_run=true`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("Dry-run Delete Response:", dryRunResponse.status, dryRunResponse.data);
        expect(dryRunResponse.status).toBe(200);

        // Verify application still exists
        const verifyResponse = await get({
          url: `${baseUrl}/${applicationId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        expect(verifyResponse.status).toBe(200);
        expect(verifyResponse.data.id).toBe(applicationId);
      }
    });

    it("should actually delete an application when dry_run=false", async () => {
      // Create a real application via the end-user apply flow so the delete is non-vacuous.
      const { user, accessToken: userToken } = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        client: clientSecretPostClient,
        adminClient: clientSecretPostClient,
      });
      await registerFidoUaf({ accessToken: userToken });

      const type = "investment-account-opening";
      const applyUrl = serverConfig.identityVerificationApplyEndpoint
        .replace("{type}", type)
        .replace("{process}", "apply");
      const applyResponse = await post({
        url: applyUrl,
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${userToken}`,
        },
        body: {
          last_name: "john",
          first_name: "mac",
          last_name_kana: "jon",
          first_name_kana: "mac",
          birthdate: "1992-02-12",
          nationality: "JP",
          email_address: "ito.ichiro@gmail.com",
          mobile_phone_number: user.phone_number,
          address: {
            street_address: "test",
            locality: "test",
            region: "test",
            postal_code: "1000001",
            country: "JP",
          },
        },
      });
      console.log("Apply Response:", applyResponse.status, JSON.stringify(applyResponse.data));
      expect(applyResponse.status).toBe(200);
      const applicationId = applyResponse.data.id;

      // The application was created on serverConfig.tenantId; delete it via the system management API.
      const adminToken = await getAccessToken();
      const mgmtUrl = `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/identity-verification-applications/${applicationId}`;

      const deleteResponse = await deletion({
        url: `${mgmtUrl}?dry_run=false`,
        headers: {
          Authorization: `Bearer ${adminToken}`
        }
      });
      console.log("Actual Delete Response:", deleteResponse.status, deleteResponse.data);
      expect([200, 204]).toContain(deleteResponse.status);

      // Verify the application no longer exists
      const verifyResponse = await get({
        url: mgmtUrl,
        headers: {
          Authorization: `Bearer ${adminToken}`
        }
      });
      expect(verifyResponse.status).toBe(404);
    });
  });

  describe("authorization", () => {

    it("should return 401 for unauthenticated request", async () => {
      const response = await get({
        url: baseUrl,
        headers: {}
      });
      expect(response.status).toBe(401);
    });
  });
});
