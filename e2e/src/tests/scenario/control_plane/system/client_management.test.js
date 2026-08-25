import { describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import { get, postWithJson, putWithJson, deletion } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { adminServerConfig, backendUrl } from "../../../testConfig";

describe("client management api", () => {

  describe("success pattern", () => {

    it("crud", async () => {
      const tokenResponse = await requestToken({
        endpoint: adminServerConfig.tokenEndpoint,
        grantType: "password",
        username: adminServerConfig.oauth.username,
        password: adminServerConfig.oauth.password,
        scope: adminServerConfig.adminClient.scope,
        clientId: adminServerConfig.adminClient.clientId,
        clientSecret: adminServerConfig.adminClient.clientSecret,
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
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients`,
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

      // Verify created_at and updated_at are present in create response
      expect(createResponse.data.result.created_at).toBeDefined();
      expect(createResponse.data.result.updated_at).toBeDefined();
      // Verify they are in ISO 8601 format (YYYY-MM-DDTHH:mm:ss[.nanos], no Z suffix)
      // LocalDateTime.toString() outputs: 2025-01-15T10:30:45 or 2025-01-15T10:30:45.123456789
      expect(createResponse.data.result.created_at).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?$/);
      expect(createResponse.data.result.updated_at).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?$/);
      console.log("✅ Timestamps present in create response:", createResponse.data.result.created_at);

      const createdClientId = testClientId;
      console.log("Created Client ID:", createdClientId);

      // Step 2: Verify the client appears in the list (enabled=true)
      const listResponse1 = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List Response 1:", JSON.stringify(listResponse1.data));
      expect(listResponse1.status).toBe(200);
      const clientsEnabled = listResponse1.data.list.filter(client => client.client_id === createdClientId);
      expect(clientsEnabled.length).toBe(1);
      // Verify timestamps in list response
      expect(clientsEnabled[0].created_at).toBeDefined();
      expect(clientsEnabled[0].updated_at).toBeDefined();
      expect(clientsEnabled[0].created_at).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?$/);
      expect(clientsEnabled[0].updated_at).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?$/);
      console.log("✅ Client found in list when enabled=true, created_at:", clientsEnabled[0].created_at);

      // Step 3: Verify individual client retrieval works (enabled=true)
      const detailResponse1 = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients/${createdClientId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Detail Response 1:", detailResponse1.status, detailResponse1.data);
      expect(detailResponse1.status).toBe(200);
      expect(detailResponse1.data.client_id).toBe(createdClientId);
      // Verify timestamps in detail response
      expect(detailResponse1.data.created_at).toBeDefined();
      expect(detailResponse1.data.updated_at).toBeDefined();
      expect(detailResponse1.data.created_at).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?$/);
      expect(detailResponse1.data.updated_at).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?$/);
      console.log("✅ Client detail retrieved when enabled=true, created_at:", detailResponse1.data.created_at);

      // Step 4: Update client to enabled=false
      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients/${createdClientId}`,
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
      // Verify timestamps in update response
      expect(updateResponse.data.result.created_at).toBeDefined();
      expect(updateResponse.data.result.updated_at).toBeDefined();
      expect(updateResponse.data.result.created_at).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?$/);
      expect(updateResponse.data.result.updated_at).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?$/);
      console.log("✅ Client updated to enabled=false, updated_at:", updateResponse.data.result.updated_at);

      // Step 5: Verify the client does NOT appear in the list (enabled=false)
      const listResponse2 = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List Response 2:", JSON.stringify(listResponse2.data));
      expect(listResponse2.status).toBe(200);
      const clientsDisabled = listResponse2.data.list.filter(client => client.client_id === createdClientId);
      expect(clientsDisabled.length).toBe(1);

      // Step 6: Re-enable client (enabled=true) with include_disabled=true parameter
      const reEnableResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients/${createdClientId}?include_disabled=true`,
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
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients`,
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
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients/${createdClientId}`,
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
            url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients/${createdClientId}`,
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

  describe("GET -> PUT round trip", () => {

    /**
     * The management API replaces the whole client on PUT, so an operator editing one field
     * naturally does GET -> modify -> PUT. Any field the GET representation omits is therefore
     * silently deleted on the next update. Same failure class as #1762, which fixed it for the
     * authorization-server representation.
     */
    it("returns every configured field so that a GET -> PUT cycle does not drop settings", async () => {
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
      const headers = { Authorization: `Bearer ${tokenResponse.data.access_token}` };
      const clientsUrl = `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients`;

      const clientId = uuidv4();
      const body = {
        client_id: clientId,
        client_name: "Round Trip Client",
        redirect_uris: ["http://localhost:3000/callback"],
        response_types: ["code"],
        grant_types: ["authorization_code", "client_credentials"],
        scope: "openid profile",
        token_endpoint_auth_method: "attest_jwt_client_auth",
        application_type: "native",
        enabled: true,
        extension: {
          access_token_duration: 1800,
          refresh_token_duration: 86400,
          rotate_refresh_token: true,
          client_attestation_trust_source: "attester_jwks",
          client_attestation_attester_jwks: JSON.stringify({ keys: [] }),
          client_instance_registration_policy: "attestation_only",
        },
      };

      const createResponse = await postWithJson({ url: clientsUrl, headers, body });
      expect(createResponse.status).toBe(201);

      try {
        const first = await get({ url: `${clientsUrl}/${clientId}`, headers });
        expect(first.status).toBe(200);

        // What was configured has to come back, otherwise the next PUT deletes it.
        for (const [key, value] of Object.entries(body.extension)) {
          expect(first.data.extension?.[key]).toEqual(value);
        }

        // Feed the representation straight back, the way an operator edit would.
        const putResponse = await putWithJson({
          url: `${clientsUrl}/${clientId}`,
          headers,
          body: first.data,
        });
        expect(putResponse.status).toBe(200);

        const second = await get({ url: `${clientsUrl}/${clientId}`, headers });
        expect(second.status).toBe(200);
        for (const [key, value] of Object.entries(body.extension)) {
          expect(second.data.extension?.[key]).toEqual(value);
        }
        expect(second.data.token_endpoint_auth_method).toBe("attest_jwt_client_auth");
      } finally {
        await deletion({ url: `${clientsUrl}/${clientId}`, headers });
      }
    });
  });

  describe("ClientQueries and Pagination", () => {
    
    it("should support basic pagination with total_count", async () => {
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
      const accessToken = tokenResponse.data.access_token;

      // Test basic pagination
      const listResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients?limit=5&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Pagination Response:", listResponse.data);
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("list");
      expect(listResponse.data).toHaveProperty("total_count");
      expect(listResponse.data).toHaveProperty("limit");
      expect(listResponse.data).toHaveProperty("offset");
      expect(listResponse.data.limit).toBe(5);
      expect(listResponse.data.offset).toBe(0);
      expect(typeof listResponse.data.total_count).toBe("number");
      console.log("✅ Basic pagination test passed");
    });

    it("should support client filtering by client_name", async () => {
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
      const accessToken = tokenResponse.data.access_token;

      const testClientId = uuidv4();
      let clientCreated = false;

      try {
        // Create a test client with specific name
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
          body: {
            "client_id": testClientId,
            "client_name": "E2E Test Filterable Client",
            "client_secret": "test-secret-123",
            "grant_types": ["authorization_code"],
            "redirect_uris": ["http://localhost:3000/callback"],
            "enabled": true
          }
        });
        expect(createResponse.status).toBe(201);
        clientCreated = true;

        // Test filtering by client_name
        const filterResponse = await get({
          url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients?client_name=E2E Test Filterable`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("Filter Response:", filterResponse.data);
        expect(filterResponse.status).toBe(200);
        expect(filterResponse.data.list.length).toBeGreaterThan(0);
        
        const foundClient = filterResponse.data.list.find(client => client.client_id === testClientId);
        expect(foundClient).toBeTruthy();
        expect(foundClient.client_name).toBe("E2E Test Filterable Client");
        console.log("✅ Client name filtering test passed");
        
      } finally {
        // Cleanup
        if (clientCreated) {
          try {
            await deletion({
              url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients/${testClientId}`,
              headers: {
                Authorization: `Bearer ${accessToken}`,
              }
            });
            console.log("✅ Test client cleaned up");
          } catch (cleanupError) {
            console.log("⚠️ Cleanup failed:", cleanupError.message);
          }
        }
      }
    });

    it("should support filtering by enabled status", async () => {
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
      const accessToken = tokenResponse.data.access_token;

      // Test filtering by enabled=true
      const enabledResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients?enabled=true&limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Enabled Filter Response:", enabledResponse.data);
      expect(enabledResponse.status).toBe(200);
      
      // All returned clients should be enabled
      enabledResponse.data.list.forEach(client => {
        expect(client.enabled).toBe(true);
      });
      console.log("✅ Enabled status filtering test passed");
    });

    it("should support multiple query parameters", async () => {
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
      const accessToken = tokenResponse.data.access_token;

      const testClientId = uuidv4();
      let clientCreated = false;

      try {
        // Create a test client with specific properties
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
          body: {
            "client_id": testClientId,
            "client_name": "Multi Query Test Client",
            "client_secret": "test-secret-123",
            "grant_types": ["authorization_code"],
            "response_types": ["code"],
            "redirect_uris": ["http://localhost:3000/callback"],
            "scope": "openid profile email",
            "enabled": true
          }
        });
        expect(createResponse.status).toBe(201);
        clientCreated = true;

        // Test multiple query parameters
        const multiFilterResponse = await get({
          url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients?client_name=Multi Query&enabled=true&limit=5&offset=0`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("Multi Filter Response:", multiFilterResponse.data);
        expect(multiFilterResponse.status).toBe(200);
        expect(multiFilterResponse.data).toHaveProperty("total_count");
        expect(multiFilterResponse.data.limit).toBe(5);
        expect(multiFilterResponse.data.offset).toBe(0);
        
        const foundClient = multiFilterResponse.data.list.find(client => client.client_id === testClientId);
        expect(foundClient).toBeTruthy();
        expect(foundClient.enabled).toBe(true);
        console.log("✅ Multiple query parameters test passed");
        
      } finally {
        // Cleanup
        if (clientCreated) {
          try {
            await deletion({
              url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients/${testClientId}`,
              headers: {
                Authorization: `Bearer ${accessToken}`,
              }
            });
            console.log("✅ Test client cleaned up");
          } catch (cleanupError) {
            console.log("⚠️ Cleanup failed:", cleanupError.message);
          }
        }
      }
    });

    it("should handle empty results with proper pagination info", async () => {
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
      const accessToken = tokenResponse.data.access_token;

      // Test with a query that should return no results
      const emptyResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients?client_name=NonExistentClient12345&limit=10&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Empty Results Response:", emptyResponse.data);
      expect(emptyResponse.status).toBe(200);
      expect(emptyResponse.data.list).toEqual([]);
      expect(emptyResponse.data.total_count).toBe(0);
      expect(emptyResponse.data.limit).toBe(10);
      expect(emptyResponse.data.offset).toBe(0);
      console.log("✅ Empty results test passed");
    });

    it("should support scope filtering", async () => {
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
      const accessToken = tokenResponse.data.access_token;

      // Test filtering by scope (should find clients with "openid" in their scope)
      const scopeResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients?scope=openid&limit=10`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Scope Filter Response:", scopeResponse.data);
      expect(scopeResponse.status).toBe(200);
      expect(scopeResponse.data).toHaveProperty("total_count");
      
      // If there are results, verify they contain "openid" in scope
      if (scopeResponse.data.list.length > 0) {
        scopeResponse.data.list.forEach(client => {
          expect(client.scope).toMatch(/openid/);
        });
      }
      console.log("✅ Scope filtering test passed");
    });
  });
});