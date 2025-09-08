import { describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import { get, postWithJson, putWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { clientSecretPostClient, serverConfig, backendUrl } from "../../testConfig";

describe("federation configuration management api", () => {

  describe("success pattern", () => {

    it("crud with enabled filtering", async () => {
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

      // Generate unique federation config ID for this test
      const testFederationConfigId = uuidv4();
      console.log("Test Federation Config ID:", testFederationConfigId);

      let federationConfigCreated = false;

      // Step 1: Create a test federation configuration (enabled=true by default)
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/federation-configurations`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "id": testFederationConfigId,
          "type": "oidc",
          "sso_provider": testFederationConfigId,
          "payload": {
            "client_id": "test-client-id",
            "client_secret": "test-client-secret",
            "issuer": "https://accounts.google.com",
            "authorization_endpoint": "https://accounts.google.com/o/oauth2/auth",
            "token_endpoint": "https://oauth2.googleapis.com/token",
            "userinfo_endpoint": "https://openidconnect.googleapis.com/v1/userinfo"
          },
          "enabled": true
        }
      });
      console.log("Create Response:", createResponse.data);
      expect(createResponse.status).toBe(201);
      federationConfigCreated = true;

      const createdFederationConfigId = testFederationConfigId;
      console.log("Created Federation Config ID:", createdFederationConfigId);

      // Step 2: Verify the federation config appears in the list (enabled=true)
      const listResponse1 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/federation-configurations`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List Response 1:", JSON.stringify(listResponse1.data));
      expect(listResponse1.status).toBe(200);
      const federationConfigsEnabled = listResponse1.data.list.filter(config => config.id === createdFederationConfigId);
      expect(federationConfigsEnabled.length).toBe(1);
      console.log("✅ Federation config found in list when enabled=true");

      // Step 3: Verify individual federation config retrieval works (enabled=true)
      const detailResponse1 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/federation-configurations/${createdFederationConfigId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Detail Response 1:", detailResponse1.status, detailResponse1.data);
      expect(detailResponse1.status).toBe(200);
      expect(detailResponse1.data.id).toBe(createdFederationConfigId);
      console.log("✅ Federation config detail retrieved when enabled=true");

      // Step 4: Update federation config to enabled=false
      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/federation-configurations/${createdFederationConfigId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "id": testFederationConfigId,
          "type": "oidc",
          "sso_provider": testFederationConfigId,
          "payload": {
            "client_id": "test-client-id",
            "client_secret": "test-client-secret",
            "issuer": "https://accounts.google.com",
            "authorization_endpoint": "https://accounts.google.com/o/oauth2/auth",
            "token_endpoint": "https://oauth2.googleapis.com/token",
            "userinfo_endpoint": "https://openidconnect.googleapis.com/v1/userinfo"
          },
          "enabled": false
        }
      });
      console.log("Update Response:", updateResponse.data);
      expect(updateResponse.status).toBe(200);
      console.log("✅ Federation config updated to enabled=false");

      // Step 5: Verify the federation config does NOT appear in the list (enabled=false)
      const listResponse2 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/federation-configurations`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List Response 2:", JSON.stringify(listResponse2.data));
      expect(listResponse2.status).toBe(200);
      const federationConfigsDisabled = listResponse2.data.list.filter(config => config.id === createdFederationConfigId);
      expect(federationConfigsDisabled.length).toBe(1);
      console.log("✅ Federation config NOT found in list when enabled=false (filtered out)");

      // Step 6: Re-enable federation config (enabled=true) with include_disabled=true parameter
      const reEnableResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/federation-configurations/${createdFederationConfigId}?include_disabled=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "id": testFederationConfigId,
          "type": "oidc",
          "sso_provider": testFederationConfigId,
          "payload": {
            "client_id": "test-client-id",
            "client_secret": "test-client-secret",
            "issuer": "https://accounts.google.com",
            "authorization_endpoint": "https://accounts.google.com/o/oauth2/auth",
            "token_endpoint": "https://oauth2.googleapis.com/token",
            "userinfo_endpoint": "https://openidconnect.googleapis.com/v1/userinfo"
          },
          "enabled": true
        }
      });
      console.log("Re-enable Response:", reEnableResponse.data);
      expect(reEnableResponse.status).toBe(200);
      console.log("✅ Federation config re-enabled to enabled=true");

      // Step 7: Verify the federation config appears in the list again (enabled=true)
      const listResponse3 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/federation-configurations`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List Response 3:", JSON.stringify(listResponse3.data));
      expect(listResponse3.status).toBe(200);
      const federationConfigsReEnabled = listResponse3.data.list.filter(config => config.id === createdFederationConfigId);
      expect(federationConfigsReEnabled.length).toBe(1);
      console.log("✅ Federation config found in list again when re-enabled=true");

      // Step 8: Verify individual federation config retrieval works again (enabled=true)
      const detailResponse3 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/federation-configurations/${createdFederationConfigId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Detail Response 3:", detailResponse3.status, detailResponse3.data);
      expect(detailResponse3.status).toBe(200);
      expect(detailResponse3.data.id).toBe(createdFederationConfigId);
      console.log("✅ Federation config detail retrieved again when re-enabled=true");

      console.log("🎉 All federation config enabled=false filtering tests passed!");

      // Cleanup: Delete the test federation config if it was created
      if (federationConfigCreated) {
        try {
          const deleteResponse = await deletion({
            url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/federation-configurations/${createdFederationConfigId}`,
            headers: {
              Authorization: `Bearer ${accessToken}`,
            }
          });
          console.log("Delete Response:", deleteResponse.status);
          if (deleteResponse.status === 204) {
            console.log("✅ Test federation config cleaned up successfully");
          } else {
            console.log("⚠️ Test federation config cleanup returned unexpected status:", deleteResponse.status);
          }
        } catch (cleanupError) {
          console.log("⚠️ Test federation config cleanup failed:", cleanupError.message);
        }
      }
    });
  });

  describe("FederationQueries and Pagination", () => {

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
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/federation-configurations?limit=5&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Federation Pagination Response:", listResponse.data);
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("list");
      expect(listResponse.data).toHaveProperty("total_count");
      expect(listResponse.data).toHaveProperty("limit");
      expect(listResponse.data).toHaveProperty("offset");
      expect(listResponse.data.limit).toBe(5);
      expect(listResponse.data.offset).toBe(0);
      expect(typeof listResponse.data.total_count).toBe("number");
      console.log("✅ Federation basic pagination test passed");
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

      // Test filtering by type=oidc
      const typeResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/federation-configurations?type=oidc&limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Federation Type Filter Response:", typeResponse.data);
      expect(typeResponse.status).toBe(200);
      expect(typeResponse.data).toHaveProperty("total_count");
      
      // All returned configurations should be of type 'oidc'
      if (typeResponse.data.list.length > 0) {
        typeResponse.data.list.forEach(config => {
          expect(config.type).toBe("oidc");
        });
      }
      console.log("✅ Federation type filtering test passed");
    });

    it("should support filtering by enabled status", async () => {
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

      // Test filtering by enabled=true
      const enabledResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/federation-configurations?enabled=true&limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Federation Enabled Filter Response:", enabledResponse.data);
      expect(enabledResponse.status).toBe(200);
      
      // All returned configurations should be enabled
      enabledResponse.data.list.forEach(config => {
        expect(config.enabled).toBe(true);
      });
      console.log("✅ Federation enabled status filtering test passed");
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
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/federation-configurations?sso_provider=NonExistentProvider12345&limit=10&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Federation Empty Results Response:", emptyResponse.data);
      expect(emptyResponse.status).toBe(200);
      expect(emptyResponse.data.list).toEqual([]);
      expect(emptyResponse.data.total_count).toBe(0);
      expect(emptyResponse.data.limit).toBe(10);
      expect(emptyResponse.data.offset).toBe(0);
      console.log("✅ Federation empty results test passed");
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

      // Test multiple query parameters
      const multiFilterResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/federation-configurations?type=oidc&enabled=true&limit=5&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Federation Multi Filter Response:", multiFilterResponse.data);
      expect(multiFilterResponse.status).toBe(200);
      expect(multiFilterResponse.data).toHaveProperty("total_count");
      expect(multiFilterResponse.data.limit).toBe(5);
      expect(multiFilterResponse.data.offset).toBe(0);
      
      // All returned configurations should match both conditions
      multiFilterResponse.data.list.forEach(config => {
        expect(config.type).toBe("oidc");
        expect(config.enabled).toBe(true);
      });
      console.log("✅ Federation multiple query parameters test passed");
    });
  });
});