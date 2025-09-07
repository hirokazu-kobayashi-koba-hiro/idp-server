import { describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import { get, postWithJson, putWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { clientSecretPostClient, serverConfig, backendUrl } from "../../testConfig";

describe("client management api", () => {

  describe("success pattern", () => {

    it("crud", async () => {
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

      // Generate unique client ID for this test
      const testClientId = uuidv4();
      console.log("Test Client ID:", testClientId);

      let clientCreated = false;

      // Step 1: Create a test client (enabled=true by default)
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "client_id": testClientId,
          "client_name": "Test Enabled Filtering Client",
          "client_secret": "test-secret-123",
          "grant_types": ["authorization_code"],
          "redirect_uris": ["http://localhost:3000/callback"],
          "enabled": true
        }
      });
      console.log("Create Response:", createResponse.data);
      expect(createResponse.status).toBe(201);
      clientCreated = true;

      const createdClientId = testClientId;
      console.log("Created Client ID:", createdClientId);

      // Step 2: Verify the client appears in the list (enabled=true)
      const listResponse1 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List Response 1:", JSON.stringify(listResponse1.data));
      expect(listResponse1.status).toBe(200);
      const clientsEnabled = listResponse1.data.list.filter(client => client.client_id === createdClientId);
      expect(clientsEnabled.length).toBe(1);
      console.log("✅ Client found in list when enabled=true");

      // Step 3: Verify individual client retrieval works (enabled=true)
      const detailResponse1 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients/${createdClientId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Detail Response 1:", detailResponse1.status, detailResponse1.data);
      expect(detailResponse1.status).toBe(200);
      expect(detailResponse1.data.client_id).toBe(createdClientId);
      console.log("✅ Client detail retrieved when enabled=true");

      // Step 4: Update client to enabled=false
      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients/${createdClientId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "client_id": testClientId,
          "client_name": "Test Enabled Filtering Client",
          "client_secret": "test-secret-123",
          "grant_types": ["authorization_code"],
          "redirect_uris": ["http://localhost:3000/callback"],
          "enabled": false
        }
      });
      console.log("Update Response:", updateResponse.data);
      expect(updateResponse.status).toBe(200);
      console.log("✅ Client updated to enabled=false");

      // Step 5: Verify the client does NOT appear in the list (enabled=false)
      const listResponse2 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List Response 2:", JSON.stringify(listResponse2.data));
      expect(listResponse2.status).toBe(200);
      const clientsDisabled = listResponse2.data.list.filter(client => client.client_id === createdClientId);
      expect(clientsDisabled.length).toBe(0);
      console.log("✅ Client NOT found in list when enabled=false (filtered out)");

      // Step 6: Re-enable client (enabled=true) with include_disabled=true parameter
      const reEnableResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients/${createdClientId}?include_disabled=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "client_id": testClientId,
          "client_name": "Test Enabled Filtering Client",
          "client_secret": "test-secret-123", 
          "grant_types": ["authorization_code"],
          "redirect_uris": ["http://localhost:3000/callback"],
          "enabled": true
        }
      });
      console.log("Re-enable Response:", reEnableResponse.data);
      expect(reEnableResponse.status).toBe(200);
      console.log("✅ Client re-enabled to enabled=true");

      // Step 7: Verify the client appears in the list again (enabled=true)
      const listResponse3 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List Response 3:", JSON.stringify(listResponse3.data));
      expect(listResponse3.status).toBe(200);
      const clientsReEnabled = listResponse3.data.list.filter(client => client.client_id === createdClientId);
      expect(clientsReEnabled.length).toBe(1);
      console.log("✅ Client found in list again when re-enabled=true");

      // Step 8: Verify individual client retrieval works again (enabled=true)
      const detailResponse3 = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients/${createdClientId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Detail Response 3:", detailResponse3.status, detailResponse3.data);
      expect(detailResponse3.status).toBe(200);
      expect(detailResponse3.data.client_id).toBe(createdClientId);
      console.log("✅ Client detail retrieved again when re-enabled=true");

      console.log("🎉 All enabled=false filtering tests passed!");

      // Cleanup: Delete the test client if it was created
      if (clientCreated) {
        try {
          const deleteResponse = await deletion({
            url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients/${createdClientId}`,
            headers: {
              Authorization: `Bearer ${accessToken}`,
            }
          });
          console.log("Delete Response:", deleteResponse.status);
          if (deleteResponse.status === 204) {
            console.log("✅ Test client cleaned up successfully");
          } else {
            console.log("⚠️ Test client cleanup returned unexpected status:", deleteResponse.status);
          }
        } catch (cleanupError) {
          console.log("⚠️ Test client cleanup failed:", cleanupError.message);
        }
      }
    });
  });
});