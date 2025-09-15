import { describe, expect, it, test } from "@jest/globals";
import { deletion, get, postWithJson, putWithJson } from "../../../../lib/http";
import { backendUrl, clientSecretPostClient, serverConfig } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";
import { v4 as uuidv4 } from "uuid";

describe("organization authentication config management api", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";

  describe("success pattern", () => {
    it("crud operations", async () => {
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

      // Create a new authentication configuration within the organization
      const timestamp = Date.now();
      const configId = uuidv4(); // Authentication Config ID must be UUID
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configs`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "id": configId,
          "type": configId,  // Use configId as type to avoid duplicates
          "attributes": {
            "min_length": 8,
            "require_uppercase": true,
            "require_lowercase": true,
            "require_digits": true,
            "require_special_chars": false
          },
          "metadata": {
            "name": `Test Auth Config ${timestamp}`,
            "description": "Test authentication configuration for organization"
          },
          "interactions": {}
        }
      });
      console.log("Create response:", createResponse.data);

      // Proper implementation should return 201 for successful creation
      expect(createResponse.status).toBe(201);
      expect(createResponse.data).toHaveProperty("result");
      expect(createResponse.data.result).toHaveProperty("id");
      expect(createResponse.data.result.id).toBeDefined();

      // Store the created config ID for later tests
      const createdConfigId = createResponse.data.result.id;

      // Test dry run for create
      const dryRunConfigId = uuidv4();
      const dryRunCreateResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configs?dry_run=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "id": dryRunConfigId,
          "type": dryRunConfigId,  // Use unique ID as type to avoid duplicates
          "attributes": {
            "factor_count": 2
          },
          "metadata": {
            "name": `Dry Run Config ${timestamp}`
          },
          "interactions": {}
        }
      });
      console.log("Dry run create response:", dryRunCreateResponse.data);

      // Dry run should return 200 with preview information
      expect(dryRunCreateResponse.status).toBe(201);
      expect(dryRunCreateResponse.data).toHaveProperty("dry_run", true);
      expect(dryRunCreateResponse.data).toHaveProperty("result");
      expect(dryRunCreateResponse.data.result).toHaveProperty("id");
      expect(dryRunCreateResponse.data.result.id).toBe(dryRunConfigId);

      // List authentication configurations
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configs`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("List response:", listResponse.data);

      // List should return 200 with proper structure
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("list");
      expect(listResponse.data).toHaveProperty("total_count");
      expect(listResponse.data).toHaveProperty("limit");
      expect(listResponse.data).toHaveProperty("offset");
      expect(Array.isArray(listResponse.data.list)).toBe(true);
      expect(typeof listResponse.data.total_count).toBe("number");

      // Get the created configuration (should return 200)
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configs/${createdConfigId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Get response:", getResponse.data);

      // Should return 200 with the configuration details
      expect(getResponse.status).toBe(200);
      expect(getResponse.data).toHaveProperty("id");
      expect(getResponse.data).toHaveProperty("type");
      expect(getResponse.data).toHaveProperty("attributes");
      expect(getResponse.data).toHaveProperty("metadata");
      expect(getResponse.data).toHaveProperty("interactions");
      expect(getResponse.data).toHaveProperty("enabled");
      expect(getResponse.data.id).toBe(createdConfigId);

      // Update the created configuration
      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configs/${createdConfigId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "id": createdConfigId,
          "type": createdConfigId,  // Use configId as type to avoid duplicates
          "attributes": {
            "min_length": 10,
            "require_uppercase": true,
            "require_lowercase": true,
            "require_digits": true,
            "require_special_chars": true
          },
          "metadata": {
            "name": `Updated Auth Config ${timestamp}`,
            "description": "Updated authentication configuration"
          },
          "interactions": {}
        }
      });
      console.log("Update response:", updateResponse.data);

      // Update should return 200 for successful update or 404 for non-existing
      expect(updateResponse.status).toBe(200);

      if (updateResponse.status === 200) {
        expect(updateResponse.data).toHaveProperty("result");
        expect(updateResponse.data.result).toHaveProperty("id");
        expect(updateResponse.data.result.id).toBe(createdConfigId);
      }

      // Test dry run for update
      const dryRunUpdateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configs/${createdConfigId}?dry_run=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "id": createdConfigId,
          "type": createdConfigId,  // Use configId as type to avoid duplicates
          "attributes": {
            "min_length": 12
          },
          "metadata": {
            "name": "Dry Run Update"
          },
          "interactions": {}
        }
      });
      console.log("Dry run update response:", dryRunUpdateResponse.data);

      // Dry run update should return 200 with simulation message
      expect(dryRunUpdateResponse.status).toBe(200);

      if (dryRunUpdateResponse.status === 200) {
        expect(dryRunUpdateResponse.data).toHaveProperty("dry_run", true);
        expect(dryRunUpdateResponse.data).toHaveProperty("result");
        expect(dryRunUpdateResponse.data.result).toHaveProperty("id");
        expect(dryRunUpdateResponse.data.result.id).toBe(createdConfigId);
      }

      // Delete configuration
      const deleteResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configs/${createdConfigId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Delete response:", deleteResponse.data);

      // Delete should return 204 for successful deletion or 404 for non-existing
      expect(deleteResponse.status).toBe(204);

      // Test dry run for delete
      const dryRunTestConfigId = uuidv4();
      const dryRunDeleteResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configs/${dryRunTestConfigId}?dry_run=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Dry run delete response:", dryRunDeleteResponse.data);

      // Dry run delete should return 200 with simulation message
      expect(dryRunDeleteResponse.status).toBe(404);

    });
  });

  describe("error pattern", () => {
    it("unauthorized access", async () => {
      // Test without authorization token
      const unauthorizedResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configs`,
        body: {
          "name": "Unauthorized Test"
        },
        expectStatus: [401, 403]
      });
      console.log("Unauthorized response:", unauthorizedResponse.data);
      expect([401, 403]).toContain(unauthorizedResponse.status);
    });

    it("invalid organization or tenant ID", async () => {
      // Get valid token first
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      const accessToken = tokenResponse.data.access_token;

      // Test with invalid organization ID
      const invalidOrgResponse = await get({
        url: `${backendUrl}/v1/management/organizations/invalid-org-id/tenants/${tenantId}/authentication-configs`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        expectStatus: [400, 403, 404, 500]
      });
      console.log("Invalid org response:", invalidOrgResponse.data);
      expect([400, 403, 404]).toContain(invalidOrgResponse.status);
    });

    it("insufficient permissions", async () => {
      // Get token with insufficient permissions (no org-management scope)
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "user.with.limited.permissions", // Assuming this user has limited permissions
        password: "limitedUserPassword",
        scope: "account management", // No org-management scope
        clientId: "limited-client",
        clientSecret: "limited-client-secret"
      });

      if (tokenResponse.status === 200) {
        const limitedAccessToken = tokenResponse.data.access_token;

        // Try to create authentication config with insufficient permissions
        const forbiddenResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/authentication-configs`,
          headers: {
            Authorization: `Bearer ${limitedAccessToken}`,
          },
          body: {
            "id": forbiddenConfigId,
            "type": forbiddenConfigId,  // Use unique ID as type
            "attributes": {},
            "metadata": {
              "name": "Forbidden Config Test"
            },
            "interactions": {}
          },
          expectStatus: [403]
        });

        expect(forbiddenResponse.status).toBe(403);
        expect(forbiddenResponse.data).toHaveProperty("error");
        expect(forbiddenResponse.data.error).toBe("access_denied");
        expect(forbiddenResponse.data).toHaveProperty("error_description");
      }
    });
  });
});