import { describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import { get, postWithJson, putWithJson, deletion } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { backendUrl } from "../../../testConfig";

describe("organization security event hook configuration management api", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";

  describe("success pattern", () => {
    it("crud operations with dry-run functionality", async () => {
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

      // Generate unique security event hook config ID for this test
      const testConfigId = uuidv4();
      console.log("Test Security Event Hook Config ID:", testConfigId);

      let configCreated = false;

      try {
        // Step 1: Create a test security event hook configuration within the organization
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configs`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
          body: {
            "id": testConfigId,
            "type": testConfigId, // Use UUID to avoid type conflicts
            "payload": {
              "url": "https://webhook.example.com/security-events",
              "headers": {
                "Authorization": "Bearer webhook-token-org",
                "Content-Type": "application/json"
              },
              "events": ["user.login", "user.logout", "authentication.failed"],
              "retry_policy": {
                "max_retries": 3,
                "backoff_seconds": 30
              }
            },
            "enabled": true
          }
        });
        console.log("Create Response:", createResponse.data);
        expect(createResponse.status).toBe(201);
        configCreated = true;

        const createdConfigId = testConfigId;
        console.log("Created Security Event Hook Config ID:", createdConfigId);

        // Step 2: Test dry run for create
        const dryRunConfigId = uuidv4();
        const dryRunCreateResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configs?dry_run=true`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
          body: {
            "id": dryRunConfigId,
            "type": dryRunConfigId,
            "payload": {
              "url": "https://dry-run.example.com/webhooks",
              "headers": {
                "Authorization": "Bearer dry-run-token"
              },
              "events": ["user.registration", "password.reset"]
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

        // Step 3: Verify the config appears in the list
        const listResponse1 = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configs`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("List Response 1:", listResponse1.data);
        expect(listResponse1.status).toBe(200);
        expect(listResponse1.data).toHaveProperty("results");

        const configs = listResponse1.data.results.filter(config => config.id === createdConfigId);
        expect(configs.length).toBe(1);
        console.log("✅ Security event hook config found in list");

        // Step 4: Verify individual config retrieval works
        const detailResponse1 = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configs/${createdConfigId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("Detail Response 1:", detailResponse1.data);
        expect(detailResponse1.status).toBe(200);
        expect(detailResponse1.data).toHaveProperty("result");
        expect(detailResponse1.data.result.id).toBe(createdConfigId);
        console.log("✅ Security event hook config detail retrieved");

        // Step 5: Update security event hook config
        const updateResponse = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configs/${createdConfigId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
          body: {
            "id": testConfigId,
            "type": testConfigId,
            "payload": {
              "url": "https://updated-webhook.example.com/security-events",
              "headers": {
                "Authorization": "Bearer updated-webhook-token-org",
                "Content-Type": "application/json",
                "X-Custom-Header": "updated-value"
              },
              "events": ["user.login", "user.logout", "authentication.failed", "account.locked"],
              "retry_policy": {
                "max_retries": 5,
                "backoff_seconds": 60
              }
            },
            "enabled": true
          }
        });
        console.log("Update Response:", updateResponse.data);
        expect(updateResponse.status).toBe(200);
        console.log("✅ Security event hook config updated");

        // Step 6: Test dry run for update
        const dryRunUpdateResponse = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configs/${createdConfigId}?dry_run=true`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
          body: {
            "id": testConfigId,
            "type": testConfigId,
            "payload": {
              "url": "https://dry-run-update.example.com/webhooks",
              "headers": {
                "Authorization": "Bearer dry-run-update-token"
              },
              "events": ["user.registration", "user.deletion"]
            },
            "enabled": false
          }
        });
        console.log("Dry run update response:", dryRunUpdateResponse.data);
        expect(dryRunUpdateResponse.status).toBe(200);
        expect(dryRunUpdateResponse.data).toHaveProperty("dry_run", true);
        console.log("✅ Dry run update functionality verified");

        // Step 7: Verify the config still appears in the list after update
        const listResponse2 = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configs`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("List Response 2:", listResponse2.data);
        expect(listResponse2.status).toBe(200);

        const configsAfterUpdate = listResponse2.data.results.filter(config => config.id === createdConfigId);
        expect(configsAfterUpdate.length).toBe(1);
        console.log("✅ Security event hook config still appears in list after update");

        // Step 8: Verify individual config retrieval still works after update
        const detailResponse2 = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configs/${createdConfigId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("Detail Response 2:", detailResponse2.data);
        expect(detailResponse2.status).toBe(200);
        expect(detailResponse2.data).toHaveProperty("result");
        expect(detailResponse2.data.result.id).toBe(createdConfigId);
        console.log("✅ Security event hook config detail still accessible after update");

        // Step 9: Delete the security event hook configuration
        const deleteResponse = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configs/${createdConfigId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("Delete Response:", deleteResponse.data);
        expect(deleteResponse.status).toBe(204);
        configCreated = false;
        console.log("✅ Security event hook config deleted successfully");

        // Step 10: Verify the config is no longer accessible
        const detailResponse3 = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configs/${createdConfigId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log("Detail Response 3 (should be 404):", detailResponse3.status);
        expect(detailResponse3.status).toBe(404);
        console.log("✅ Security event hook config no longer accessible after deletion");

      } catch (error) {
        console.error("Test failed:", error);
        throw error;
      } finally {
        // Cleanup: ensure test security event hook config is removed
        if (configCreated) {
          try {
            await deletion({
              url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configs/${testConfigId}`,
              headers: {
                Authorization: `Bearer ${accessToken}`
              }
            });
            console.log("✅ Cleanup: Test security event hook config removed");
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

      // Test 1: Try to access non-existent security event hook config
      const nonExistentId = uuidv4();
      const notFoundResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configs/${nonExistentId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Not found response:", notFoundResponse.status);
      expect(notFoundResponse.status).toBe(404);
      console.log("✅ Non-existent security event hook config returns 404");

      // Test 2: Try to update non-existent security event hook config
      const updateNotFoundResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configs/${nonExistentId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "id": nonExistentId,
          "type": nonExistentId,
          "payload": {
            "url": "https://test.example.com/webhook",
            "events": ["user.login"]
          },
          "enabled": true
        }
      });
      console.log("Update not found response:", updateNotFoundResponse.status);
      expect(updateNotFoundResponse.status).toBe(404);
      console.log("✅ Update non-existent security event hook config returns 404");

      // Test 3: Try to delete non-existent security event hook config
      const deleteNotFoundResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configs/${nonExistentId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Delete not found response:", deleteNotFoundResponse.status);
      expect(deleteNotFoundResponse.status).toBe(404);
      console.log("✅ Delete non-existent security event hook config returns 404");

      // Test 4: Try with invalid authorization
      const unauthorizedResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configs`,
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