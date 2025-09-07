import { describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import { get, postWithJson, putWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { clientSecretPostClient, serverConfig, backendUrl } from "../../testConfig";

describe("security event hook configuration management api", () => {

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

      // Generate unique security event hook config ID for this test
      const testHookConfigId = uuidv4();
      console.log("Test Security Event Hook Config ID:", testHookConfigId);

      let hookConfigCreated = false;

      // Step 1: Create a test security event hook configuration (enabled=true by default)
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hook-configurations`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "id": testHookConfigId,
          "type": testHookConfigId,
          "payload": {
            "url": "https://example.com/webhook",
            "method": "POST",
            "headers": {
              "Content-Type": "application/json",
              "Authorization": "Bearer test-token"
            },
            "timeout": 5000
          },
          "execution_order": 1,
          "enabled": true
        }
      });
      console.log("Create Response:", createResponse.data);
      expect(createResponse.status).toBe(201);
      hookConfigCreated = true;

      const createdHookConfigId = testHookConfigId;
      console.log("Created Security Event Hook Config ID:", createdHookConfigId);

      // Step 2: Verify the hook config appears in the list (enabled=true)
      const listResponse1 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hook-configurations`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List Response 1:", JSON.stringify(listResponse1.data));
      expect(listResponse1.status).toBe(200);
      const hookConfigsEnabled = listResponse1.data.list.filter(config => config.id === createdHookConfigId);
      expect(hookConfigsEnabled.length).toBe(1);
      console.log("✅ Security event hook config found in list when enabled=true");

      // Step 3: Verify individual hook config retrieval works (enabled=true)
      const detailResponse1 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hook-configurations/${createdHookConfigId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Detail Response 1:", detailResponse1.status, detailResponse1.data);
      expect(detailResponse1.status).toBe(200);
      expect(detailResponse1.data.id).toBe(createdHookConfigId);
      console.log("✅ Security event hook config detail retrieved when enabled=true");

      // Step 4: Update hook config to enabled=false
      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hook-configurations/${createdHookConfigId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "id": testHookConfigId,
          "type": testHookConfigId,
          "payload": {
            "url": "https://example.com/webhook",
            "method": "POST",
            "headers": {
              "Content-Type": "application/json",
              "Authorization": "Bearer test-token"
            },
            "timeout": 5000
          },
          "execution_order": 1,
          "enabled": false
        }
      });
      console.log("Update Response:", updateResponse.data);
      expect(updateResponse.status).toBe(200);
      console.log("✅ Security event hook config updated to enabled=false");

      // Step 5: Verify the hook config does NOT appear in the list (enabled=false)
      const listResponse2 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hook-configurations`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List Response 2:", JSON.stringify(listResponse2.data));
      expect(listResponse2.status).toBe(200);
      const hookConfigsDisabled = listResponse2.data.list.filter(config => config.id === createdHookConfigId);
      expect(hookConfigsDisabled.length).toBe(0);
      console.log("✅ Security event hook config NOT found in list when enabled=false (filtered out)");

      // Step 6: Re-enable hook config (enabled=true) with include_disabled=true parameter
      const reEnableResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hook-configurations/${createdHookConfigId}?include_disabled=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "id": testHookConfigId,
          "type": testHookConfigId,
          "payload": {
            "url": "https://example.com/webhook",
            "method": "POST",
            "headers": {
              "Content-Type": "application/json",
              "Authorization": "Bearer test-token"
            },
            "timeout": 5000
          },
          "execution_order": 1,
          "enabled": true
        }
      });
      console.log("Re-enable Response:", reEnableResponse.data);
      expect(reEnableResponse.status).toBe(200);
      console.log("✅ Security event hook config re-enabled to enabled=true");

      // Step 7: Verify the hook config appears in the list again (enabled=true)
      const listResponse3 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hook-configurations`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List Response 3:", JSON.stringify(listResponse3.data));
      expect(listResponse3.status).toBe(200);
      const hookConfigsReEnabled = listResponse3.data.list.filter(config => config.id === createdHookConfigId);
      expect(hookConfigsReEnabled.length).toBe(1);
      console.log("✅ Security event hook config found in list again when re-enabled=true");

      // Step 8: Verify individual hook config retrieval works again (enabled=true)
      const detailResponse3 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hook-configurations/${createdHookConfigId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Detail Response 3:", detailResponse3.status, detailResponse3.data);
      expect(detailResponse3.status).toBe(200);
      expect(detailResponse3.data.id).toBe(createdHookConfigId);
      console.log("✅ Security event hook config detail retrieved again when re-enabled=true");

      console.log("🎉 All security event hook config enabled=false filtering tests passed!");

      // Cleanup: Delete the test hook config if it was created
      if (hookConfigCreated) {
        try {
          const deleteResponse = await deletion({
            url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hook-configurations/${createdHookConfigId}`,
            headers: {
              Authorization: `Bearer ${accessToken}`,
            }
          });
          console.log("Delete Response:", deleteResponse.status);
          if (deleteResponse.status === 204) {
            console.log("✅ Test security event hook config cleaned up successfully");
          } else {
            console.log("⚠️ Test security event hook config cleanup returned unexpected status:", deleteResponse.status);
          }
        } catch (cleanupError) {
          console.log("⚠️ Test security event hook config cleanup failed:", cleanupError.message);
        }
      }
    });
  });
});