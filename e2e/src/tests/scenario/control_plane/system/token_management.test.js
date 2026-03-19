import { describe, expect, it } from "@jest/globals";
import { get, deletion } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { adminServerConfig, backendUrl } from "../../../testConfig";

describe("token management api", () => {
  const baseUrl = `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/tokens`;

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
    it("should require user_id or client_id filter", async () => {
      const accessToken = await getAccessToken();

      const listResponse = await get({
        url: baseUrl,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log("No filter Response:", JSON.stringify(listResponse.data));
      expect(listResponse.status).toBe(400);
      expect(listResponse.data).toHaveProperty("error", "invalid_request");
    });

    it("should list tokens with client_id filter and pagination", async () => {
      const accessToken = await getAccessToken();

      const listResponse = await get({
        url: `${baseUrl}?client_id=${adminServerConfig.adminClient.clientId}&limit=5&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
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

    it("should support filtering by client_id and grant_type", async () => {
      const accessToken = await getAccessToken();

      const listResponse = await get({
        url: `${baseUrl}?client_id=${adminServerConfig.adminClient.clientId}&grant_type=password&limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
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

    it("should support filtering by client_id", async () => {
      const accessToken = await getAccessToken();

      const listResponse = await get({
        url: `${baseUrl}?client_id=${adminServerConfig.adminClient.clientId}&limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      console.log(
        "Client ID Filter Response:",
        JSON.stringify(listResponse.data)
      );
      expect(listResponse.status).toBe(200);

      if (listResponse.data.list.length > 0) {
        listResponse.data.list.forEach((token) => {
          expect(token.client_id).toBe(
            adminServerConfig.adminClient.clientId
          );
        });
      }
    });

    it("should support time range filtering with client_id", async () => {
      const accessToken = await getAccessToken();

      const oneYearAgo = new Date();
      oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1);
      const fromDate = oneYearAgo
        .toISOString()
        .substring(0, 19)
        .replace("T", " ");

      const listResponse = await get({
        url: `${baseUrl}?client_id=${adminServerConfig.adminClient.clientId}&from=${encodeURIComponent(fromDate)}&limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
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

    it("should return correct token fields in list response", async () => {
      const accessToken = await getAccessToken();

      const listResponse = await get({
        url: `${baseUrl}?client_id=${adminServerConfig.adminClient.clientId}&limit=1`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
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
        // token value should NOT be exposed
        expect(token).not.toHaveProperty("encrypted_access_token");
        expect(token).not.toHaveProperty("hashed_access_token");
        expect(token).not.toHaveProperty("encrypted_refresh_token");
        expect(token).not.toHaveProperty("hashed_refresh_token");
        console.log("Token fields:", JSON.stringify(token));
      }
    });
  });

  describe("get by id operations", () => {
    it("should get token detail when tokens exist", async () => {
      const accessToken = await getAccessToken();

      const listResponse = await get({
        url: `${baseUrl}?client_id=${adminServerConfig.adminClient.clientId}&limit=1`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      expect(listResponse.status).toBe(200);

      if (listResponse.data.list.length > 0) {
        const tokenId = listResponse.data.list[0].id;
        const detailResponse = await get({
          url: `${baseUrl}/${tokenId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        });
        console.log("Detail Response:", JSON.stringify(detailResponse.data));
        expect(detailResponse.status).toBe(200);
        expect(detailResponse.data.id).toBe(tokenId);
      }
    });

    it("should return 404 for non-existent token", async () => {
      const accessToken = await getAccessToken();

      const response = await get({
        url: `${baseUrl}/00000000-0000-0000-0000-000000000000`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      expect(response.status).toBe(404);
    });
  });

  describe("delete operations", () => {
    it("should support dry-run delete", async () => {
      const accessToken = await getAccessToken();

      const listResponse = await get({
        url: `${baseUrl}?client_id=${adminServerConfig.adminClient.clientId}&limit=1`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      expect(listResponse.status).toBe(200);

      if (listResponse.data.list.length > 0) {
        const tokenId = listResponse.data.list[0].id;

        const dryRunResponse = await deletion({
          url: `${baseUrl}/${tokenId}?dry_run=true`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
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
        expect(dryRunResponse.data.target).toHaveProperty("id", tokenId);

        // Verify token still exists
        const verifyResponse = await get({
          url: `${baseUrl}/${tokenId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        });
        expect(verifyResponse.status).toBe(200);
        expect(verifyResponse.data.id).toBe(tokenId);
      }
    });

    it("should support dry-run delete user tokens", async () => {
      const accessToken = await getAccessToken();

      const listResponse = await get({
        url: `${baseUrl}?client_id=${adminServerConfig.adminClient.clientId}&limit=1`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      expect(listResponse.status).toBe(200);

      if (listResponse.data.list.length > 0) {
        const userId = listResponse.data.list[0].user_id;
        if (userId) {
          const userTokensUrl = `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/users/${userId}/tokens`;

          const dryRunResponse = await deletion({
            url: `${userTokensUrl}?dry_run=true`,
            headers: {
              Authorization: `Bearer ${accessToken}`,
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
          expect(dryRunResponse.data.affected_count).toBeGreaterThan(0);
        }
      }
    });
  });
});
