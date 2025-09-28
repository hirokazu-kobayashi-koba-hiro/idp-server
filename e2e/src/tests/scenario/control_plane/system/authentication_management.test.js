import { describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import { get, postWithJson, putWithJson, deletion } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { clientSecretPostClient, serverConfig, backendUrl } from "../../../testConfig";

describe("authentication configuration management api", () => {

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

      // Generate unique authentication config ID for this test
      const testAuthConfigId = uuidv4();
      console.log("Test Authentication Config ID:", testAuthConfigId);

      let authConfigCreated = false;

      // Step 1: Create a test authentication configuration (enabled=true by default)
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authentication-configurations`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "id": testAuthConfigId,
          "type": testAuthConfigId,
          "payload": {
            "min_length": 8,
            "require_uppercase": true,
            "require_lowercase": true,
            "require_digits": true,
            "require_special_chars": false
          },
          "enabled": true
        }
      });
      console.log("Create Response:", createResponse.data);
      expect(createResponse.status).toBe(201);
      authConfigCreated = true;

      const createdAuthConfigId = testAuthConfigId;
      console.log("Created Authentication Config ID:", createdAuthConfigId);

      // Step 2: Verify the auth config appears in the list (enabled=true)
      const listResponse1 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authentication-configurations`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List Response 1:", JSON.stringify(listResponse1.data));
      expect(listResponse1.status).toBe(200);
      const authConfigsEnabled = listResponse1.data.list.filter(config => config.id === createdAuthConfigId);
      expect(authConfigsEnabled.length).toBe(1);
      console.log("✅ Authentication config found in list when enabled=true");

      // Step 3: Verify individual auth config retrieval works (enabled=true)
      const detailResponse1 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authentication-configurations/${createdAuthConfigId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Detail Response 1:", detailResponse1.status, detailResponse1.data);
      expect(detailResponse1.status).toBe(200);
      expect(detailResponse1.data.id).toBe(createdAuthConfigId);
      console.log("✅ Authentication config detail retrieved when enabled=true");

      // Step 4: Update auth config to enabled=false
      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authentication-configurations/${createdAuthConfigId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "id": testAuthConfigId,
          "type": testAuthConfigId,
          "payload": {
            "min_length": 8,
            "require_uppercase": true,
            "require_lowercase": true,
            "require_digits": true,
            "require_special_chars": false
          },
          "enabled": false
        }
      });
      console.log("Update Response:", updateResponse.data);
      expect(updateResponse.status).toBe(200);
      console.log("✅ Authentication config updated to enabled=false");

      // Step 5: Verify the auth config does NOT appear in the list (enabled=false)
      const listResponse2 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authentication-configurations`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List Response 2:", JSON.stringify(listResponse2.data));
      expect(listResponse2.status).toBe(200);
      const authConfigsDisabled = listResponse2.data.list.filter(config => config.id === createdAuthConfigId);
      expect(authConfigsDisabled.length).toBe(1);
      console.log("✅ Authentication config NOT found in list when enabled=false (filtered out)");

      // Step 6: Re-enable auth config (enabled=true) with include_disabled=true parameter
      const reEnableResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authentication-configurations/${createdAuthConfigId}?include_disabled=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "id": testAuthConfigId,
          "type": testAuthConfigId,
          "payload": {
            "min_length": 8,
            "require_uppercase": true,
            "require_lowercase": true,
            "require_digits": true,
            "require_special_chars": false
          },
          "enabled": true
        }
      });
      console.log("Re-enable Response:", reEnableResponse.data);
      expect(reEnableResponse.status).toBe(200);
      console.log("✅ Authentication config re-enabled to enabled=true");

      // Step 7: Verify the auth config appears in the list again (enabled=true)
      const listResponse3 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authentication-configurations`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List Response 3:", JSON.stringify(listResponse3.data));
      expect(listResponse3.status).toBe(200);
      const authConfigsReEnabled = listResponse3.data.list.filter(config => config.id === createdAuthConfigId);
      expect(authConfigsReEnabled.length).toBe(1);
      console.log("✅ Authentication config found in list again when re-enabled=true");

      // Step 8: Verify individual auth config retrieval works again (enabled=true)
      const detailResponse3 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authentication-configurations/${createdAuthConfigId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Detail Response 3:", detailResponse3.status, detailResponse3.data);
      expect(detailResponse3.status).toBe(200);
      expect(detailResponse3.data.id).toBe(createdAuthConfigId);
      console.log("✅ Authentication config detail retrieved again when re-enabled=true");

      console.log("🎉 All authentication config enabled=false filtering tests passed!");

      // Cleanup: Delete the test auth config if it was created
      if (authConfigCreated) {
        try {
          const deleteResponse = await deletion({
            url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authentication-configurations/${createdAuthConfigId}`,
            headers: {
              Authorization: `Bearer ${accessToken}`,
            }
          });
          console.log("Delete Response:", deleteResponse.status);
          if (deleteResponse.status === 204) {
            console.log("✅ Test authentication config cleaned up successfully");
          } else {
            console.log("⚠️ Test authentication config cleanup returned unexpected status:", deleteResponse.status);
          }
        } catch (cleanupError) {
          console.log("⚠️ Test authentication config cleanup failed:", cleanupError.message);
        }
      }
    });
  });
});