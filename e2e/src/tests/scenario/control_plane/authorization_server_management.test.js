import { describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import { get, postWithJson, putWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { clientSecretPostClient, serverConfig, backendUrl } from "../../testConfig";

describe("authorization server configuration management api", () => {

  describe("success pattern", () => {

    xit("crud with enabled filtering", async () => {
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

      // Generate unique authorization server config ID for this test
      const testAuthServerConfigId = uuidv4();
      console.log("Test Authorization Server Config ID:", testAuthServerConfigId);

      let authServerConfigCreated = false;

      // Step 1: Create a test authorization server configuration (enabled=true by default)
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authorization-server-configurations`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "id": testAuthServerConfigId,
          "type": "oauth2",
          "payload": {
            "issuer": "https://idp.example.com",
            "authorization_code_expires_in": 600,
            "access_token_expires_in": 3600,
            "refresh_token_expires_in": 86400,
            "id_token_expires_in": 3600,
            "supported_grant_types": ["authorization_code", "client_credentials"],
            "supported_response_types": ["code"]
          },
          "enabled": true
        }
      });
      console.log("Create Response:", createResponse.data);
      expect(createResponse.status).toBe(201);
      authServerConfigCreated = true;

      const createdAuthServerConfigId = testAuthServerConfigId;
      console.log("Created Authorization Server Config ID:", createdAuthServerConfigId);

      // Step 2: Verify the auth server config appears in the list (enabled=true)
      const listResponse1 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authorization-server-configurations`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List Response 1:", JSON.stringify(listResponse1.data));
      expect(listResponse1.status).toBe(200);
      const authServerConfigsEnabled = listResponse1.data.list.filter(config => config.id === createdAuthServerConfigId);
      expect(authServerConfigsEnabled.length).toBe(1);
      console.log("✅ Authorization server config found in list when enabled=true");

      // Step 3: Verify individual auth server config retrieval works (enabled=true)
      const detailResponse1 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authorization-server-configurations/${createdAuthServerConfigId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Detail Response 1:", detailResponse1.status, detailResponse1.data);
      expect(detailResponse1.status).toBe(200);
      expect(detailResponse1.data.id).toBe(createdAuthServerConfigId);
      console.log("✅ Authorization server config detail retrieved when enabled=true");

      // Step 4: Update auth server config to enabled=false
      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authorization-server-configurations/${createdAuthServerConfigId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "id": testAuthServerConfigId,
          "type": "oauth2",
          "payload": {
            "issuer": "https://idp.example.com",
            "authorization_code_expires_in": 600,
            "access_token_expires_in": 3600,
            "refresh_token_expires_in": 86400,
            "id_token_expires_in": 3600,
            "supported_grant_types": ["authorization_code", "client_credentials"],
            "supported_response_types": ["code"]
          },
          "enabled": false
        }
      });
      console.log("Update Response:", updateResponse.data);
      expect(updateResponse.status).toBe(200);
      console.log("✅ Authorization server config updated to enabled=false");

      // Step 5: Verify the auth server config does NOT appear in the list (enabled=false)
      const listResponse2 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authorization-server-configurations`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List Response 2:", JSON.stringify(listResponse2.data));
      expect(listResponse2.status).toBe(200);
      const authServerConfigsDisabled = listResponse2.data.list.filter(config => config.id === createdAuthServerConfigId);
      expect(authServerConfigsDisabled.length).toBe(0);
      console.log("✅ Authorization server config NOT found in list when enabled=false (filtered out)");

      // Step 6: Re-enable auth server config (enabled=true) with include_disabled=true parameter
      const reEnableResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authorization-server-configurations/${createdAuthServerConfigId}?include_disabled=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "id": testAuthServerConfigId,
          "type": "oauth2",
          "payload": {
            "issuer": "https://idp.example.com",
            "authorization_code_expires_in": 600,
            "access_token_expires_in": 3600,
            "refresh_token_expires_in": 86400,
            "id_token_expires_in": 3600,
            "supported_grant_types": ["authorization_code", "client_credentials"],
            "supported_response_types": ["code"]
          },
          "enabled": true
        }
      });
      console.log("Re-enable Response:", reEnableResponse.data);
      expect(reEnableResponse.status).toBe(200);
      console.log("✅ Authorization server config re-enabled to enabled=true");

      // Step 7: Verify the auth server config appears in the list again (enabled=true)
      const listResponse3 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authorization-server-configurations`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List Response 3:", JSON.stringify(listResponse3.data));
      expect(listResponse3.status).toBe(200);
      const authServerConfigsReEnabled = listResponse3.data.list.filter(config => config.id === createdAuthServerConfigId);
      expect(authServerConfigsReEnabled.length).toBe(1);
      console.log("✅ Authorization server config found in list again when re-enabled=true");

      // Step 8: Verify individual auth server config retrieval works again (enabled=true)
      const detailResponse3 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authorization-server-configurations/${createdAuthServerConfigId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Detail Response 3:", detailResponse3.status, detailResponse3.data);
      expect(detailResponse3.status).toBe(200);
      expect(detailResponse3.data.id).toBe(createdAuthServerConfigId);
      console.log("✅ Authorization server config detail retrieved again when re-enabled=true");

      console.log("🎉 All authorization server config enabled=false filtering tests passed!");

      // Cleanup: Delete the test auth server config if it was created
      if (authServerConfigCreated) {
        try {
          const deleteResponse = await deletion({
            url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authorization-server-configurations/${createdAuthServerConfigId}`,
            headers: {
              Authorization: `Bearer ${accessToken}`,
            }
          });
          console.log("Delete Response:", deleteResponse.status);
          if (deleteResponse.status === 204) {
            console.log("✅ Test authorization server config cleaned up successfully");
          } else {
            console.log("⚠️ Test authorization server config cleanup returned unexpected status:", deleteResponse.status);
          }
        } catch (cleanupError) {
          console.log("⚠️ Test authorization server config cleanup failed:", cleanupError.message);
        }
      }
    });
  });
});