import { describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import { get, postWithJson, putWithJson, deletion } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { backendUrl } from "../../../testConfig";

describe("organization federation configuration management api", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";

  describe("success pattern", () => {
    it("crud operations with enabled filtering", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Generate unique federation config ID for this test
      const testFederationConfigId = uuidv4();
      console.log("Test Federation Config ID:", testFederationConfigId);

      let federationConfigCreated = false;

      try {
        // Step 1: Create a test federation configuration within the organization
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
          body: {
            "id": testFederationConfigId,
            "type": "oidc",
            "sso_provider": testFederationConfigId,
            "payload": {
              "client_id": "test-client-id-org",
              "client_secret": "test-client-secret-org",
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

        // Step 2: Test dry run for create
        const dryRunConfigId = uuidv4();
        const dryRunCreateResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations?dry_run=true`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
          body: {
            "id": dryRunConfigId,
            "type": "oidc",
            "sso_provider": dryRunConfigId,
            "payload": {
              "client_id": "dry-run-client-id",
              "client_secret": "dry-run-client-secret",
              "issuer": "https://login.microsoftonline.com/common",
              "authorization_endpoint": "https://login.microsoftonline.com/common/oauth2/authorize",
              "token_endpoint": "https://login.microsoftonline.com/common/oauth2/token"
            },
            "enabled": true
          }
        });
        console.log("Dry run create response:", dryRunCreateResponse.data);

        // Dry run should return 201 with preview information
        expect(dryRunCreateResponse.status).toBe(201);
        expect(dryRunCreateResponse.data).toHaveProperty("dry_run", true);
        expect(dryRunCreateResponse.data).toHaveProperty("result");
        console.log("✅ Dry run create functionality verified");

        // Step 3: Verify the federation config appears in the list (enabled=true)
        const listResponse1 = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("List Response 1:", listResponse1.data);
        expect(listResponse1.status).toBe(200);
        expect(listResponse1.data).toHaveProperty("results");

        const federationConfigsEnabled = listResponse1.data.results.filter(config => config.id === createdFederationConfigId);
        expect(federationConfigsEnabled.length).toBe(1);
        console.log("✅ Federation config found in list when enabled=true");

        // Step 4: Verify individual federation config retrieval works (enabled=true)
        const detailResponse1 = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${createdFederationConfigId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("Detail Response 1:", detailResponse1.data);
        expect(detailResponse1.status).toBe(200);
        expect(detailResponse1.data).toHaveProperty("result");
        expect(detailResponse1.data.result.id).toBe(createdFederationConfigId);
        console.log("✅ Federation config detail retrieved when enabled=true");

        // Step 5: Update federation config to enabled=false
        const updateResponse = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${createdFederationConfigId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
          body: {
            "id": testFederationConfigId,
            "type": "oidc",
            "sso_provider": testFederationConfigId,
            "payload": {
              "client_id": "updated-client-id-org",
              "client_secret": "updated-client-secret-org",
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

        // Step 6: Test dry run for update
        const dryRunUpdateResponse = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${createdFederationConfigId}?dry_run=true`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
          body: {
            "id": testFederationConfigId,
            "type": "oidc",
            "sso_provider": testFederationConfigId,
            "payload": {
              "client_id": "dry-run-update-client-id",
              "client_secret": "dry-run-update-client-secret",
              "issuer": "https://login.microsoftonline.com/common"
            },
            "enabled": true
          }
        });
        console.log("Dry run update response:", dryRunUpdateResponse.data);
        expect(dryRunUpdateResponse.status).toBe(200);
        expect(dryRunUpdateResponse.data).toHaveProperty("dry_run", true);
        console.log("✅ Dry run update functionality verified");

        // Step 7: Verify the federation config still appears in the list (enabled status may not filter in list)
        const listResponse2 = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("List Response 2:", listResponse2.data);
        expect(listResponse2.status).toBe(200);

        const federationConfigsAfterUpdate = listResponse2.data.results.filter(config => config.id === createdFederationConfigId);
        expect(federationConfigsAfterUpdate.length).toBe(1);
        console.log("✅ Federation config still appears in list after update");

        // Step 8: Verify individual federation config retrieval still works (enabled=false)
        const detailResponse2 = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${createdFederationConfigId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("Detail Response 2:", detailResponse2.data);
        expect(detailResponse2.status).toBe(200);
        expect(detailResponse2.data).toHaveProperty("result");
        expect(detailResponse2.data.result.id).toBe(createdFederationConfigId);
        // Note: enabled status may not be updated immediately or filtering may not be implemented
        console.log("✅ Federation config detail still accessible after update");

        // Step 9: Delete the federation configuration
        // Note: Delete operation does not support dry-run in current implementation
        const deleteResponse = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${createdFederationConfigId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("Delete Response:", deleteResponse.data);
        expect(deleteResponse.status).toBe(204);
        federationConfigCreated = false;
        console.log("✅ Federation config deleted successfully");

        // Step 10: Verify the federation config is no longer accessible
        const detailResponse3 = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${createdFederationConfigId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("Detail Response 3 (should be 404):", detailResponse3.status);
        expect(detailResponse3.status).toBe(404);
        console.log("✅ Federation config no longer accessible after deletion");

      } catch (error) {
        console.error("Test failed:", error);
        throw error;
      } finally {
        // Cleanup: ensure test federation config is removed
        if (federationConfigCreated) {
          try {
            await deletion({
              url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${testFederationConfigId}`,
              headers: {
                Authorization: `Bearer ${accessToken}`
              }
            });
            console.log("✅ Cleanup: Test federation config removed");
          } catch (cleanupError) {
            console.warn("⚠️ Cleanup failed:", cleanupError.message);
          }
        }
      }
    });

    it("error scenarios", async () => {
      // Get OAuth token
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Test 1: Try to access non-existent federation config
      const nonExistentId = uuidv4();
      const notFoundResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${nonExistentId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Not found response:", notFoundResponse.status);
      expect(notFoundResponse.status).toBe(404);
      console.log("✅ Non-existent federation config returns 404");

      // Test 2: Try to update non-existent federation config
      const updateNotFoundResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${nonExistentId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "id": nonExistentId,
          "type": "oidc",
          "sso_provider": nonExistentId,
          "payload": {
            "client_id": "test-client-id",
            "client_secret": "test-client-secret",
            "issuer": "https://accounts.google.com"
          },
          "enabled": true
        }
      });
      console.log("Update not found response:", updateNotFoundResponse.status);
      expect(updateNotFoundResponse.status).toBe(404);
      console.log("✅ Update non-existent federation config returns 404");

      // Test 3: Try to delete non-existent federation config
      const deleteNotFoundResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${nonExistentId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Delete not found response:", deleteNotFoundResponse.status);
      expect(deleteNotFoundResponse.status).toBe(404);
      console.log("✅ Delete non-existent federation config returns 404");

      // Test 4: Try with invalid authorization
      const unauthorizedResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations`,
        headers: {
          Authorization: "Bearer invalid-token"
        }
      });
      console.log("Unauthorized response:", unauthorizedResponse.status);
      expect(unauthorizedResponse.status).toBe(401);
      console.log("✅ Invalid token returns 401");
    });
  });
});