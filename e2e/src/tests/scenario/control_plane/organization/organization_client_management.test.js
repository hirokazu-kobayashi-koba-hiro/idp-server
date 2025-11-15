import { describe, expect, it, test } from "@jest/globals";
import { deletion, get, postWithJson, putWithJson } from "../../../../lib/http";
import { backendUrl, clientSecretPostClient, serverConfig } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";
import { v4 as uuidv4 } from "uuid";

describe("organization client management api", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";

  describe("success pattern", () => {
    it("crud operations", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Create a new client within the organization
      const timestamp = Date.now();
      const clientId = uuidv4();
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "client_id": clientId,
          "client_name": `Organization Client ${timestamp}`,
          "client_type": "CONFIDENTIAL",
          "grant_types": [
            "authorization_code",
            "refresh_token"
          ],
          "redirect_uris": [
            "http://localhost:3000/callback"
          ],
          "response_types": [
            "code"
          ],
          "scope": "openid profile email",
          "token_endpoint_auth_method": "client_secret_post",
          "application_type": "web",
          "client_description": "Test client for organization management"
        }
      });
      console.log("Create client response:", createResponse.data);
      expect(createResponse.status).toBe(201);
      expect(createResponse.data).toHaveProperty("result");
      expect(createResponse.data.result).toHaveProperty("client_id", clientId);

      // List clients within the organization
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients?limit=10&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List clients response:", JSON.stringify(listResponse.data, null, 2));
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("list");
      expect(Array.isArray(listResponse.data.list)).toBe(true);
      expect(listResponse.data).toHaveProperty("total_count");
      expect(listResponse.data).toHaveProperty("limit", 10);
      expect(listResponse.data).toHaveProperty("offset", 0);

      // Get specific client details
      const detailResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients/${clientId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Client detail response:", detailResponse.data);
      expect(detailResponse.status).toBe(200);
      expect(detailResponse.data).toHaveProperty("client_id", clientId);
      expect(detailResponse.data).toHaveProperty("client_name");

      // Update client
      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients/${clientId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "client_id": clientId,
          "client_name": `Updated Organization Client ${timestamp}`,
          "client_description": "Updated description for organization client",
          "scope": "openid profile email address",
          "redirect_uris": [
            "http://localhost:3000/callback"
          ],
        }
      });
      console.log("Update client response:", updateResponse.data);
      expect(updateResponse.status).toBe(200);
      expect(updateResponse.data).toHaveProperty("result");

      // Delete client
      const deleteResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients/${clientId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Delete client response:", deleteResponse.data);
      expect(deleteResponse.status).toBe(204);
    });

    it("dry run create functionality", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const timestamp = Date.now();
      const dryRunClientId = uuidv4();

      // Test dry run for client creation
      const dryRunCreateResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients?dry_run=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "client_id": dryRunClientId,
          "client_name": `Dry Run Client ${timestamp}`,
          "client_type": "CONFIDENTIAL",
          "grant_types": [
            "authorization_code",
            "refresh_token"
          ],
          "redirect_uris": [
            "http://localhost:3000/callback"
          ],
          "response_types": [
            "code"
          ],
          "scope": "openid profile email",
          "token_endpoint_auth_method": "client_secret_post",
          "application_type": "web",
          "client_description": "Dry run test client"
        }
      });
      console.log("Dry run create response:", dryRunCreateResponse.data);
      expect(dryRunCreateResponse.status).toBe(201);
      expect(dryRunCreateResponse.data).toHaveProperty("result");

      // Verify client was not actually created by listing clients
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(listResponse.status).toBe(200);

      // Check that dry-run client ID doesn't exist in the actual list
      const clientIds = listResponse.data.list.map(client => client.client_id);
      expect(clientIds).not.toContain(dryRunClientId);
    });

    it("dry run update functionality", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const timestamp = Date.now();
      const clientId = uuidv4();

      // First create a client
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "client_id": clientId,
          "client_name": `Test Client ${timestamp}`,
          "client_type": "CONFIDENTIAL",
          "grant_types": [
            "authorization_code",
            "refresh_token"
          ],
          "redirect_uris": [
            "http://localhost:3000/callback"
          ],
          "response_types": [
            "code"
          ],
          "scope": "openid profile email",
          "token_endpoint_auth_method": "client_secret_post",
          "application_type": "web",
          "client_description": "Original description"
        }
      });
      expect(createResponse.status).toBe(201);

      // Test dry run for client update
      const dryRunUpdateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients/${clientId}?dry_run=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "client_id": clientId,
          "client_name": `Dry Run Updated Client ${timestamp}`,
          "client_description": "Dry run updated description",
          "scope": "openid profile email address",
          "redirect_uris": [
            "http://localhost:3000/callback"
          ],
        }
      });
      console.log("Dry run update response:", dryRunUpdateResponse.data);
      expect(dryRunUpdateResponse.status).toBe(200);
      expect(dryRunUpdateResponse.data).toHaveProperty("dry_run", true);

      // Verify client was not actually updated
      const verifyResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients/${clientId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(verifyResponse.status).toBe(200);
      // Verify original client_name was not changed (dry-run should not modify data)
      expect(verifyResponse.data.client_name).toBe(`Test Client ${timestamp}`);

      // Cleanup
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients/${clientId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
    });

    it("dry run delete functionality", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const timestamp = Date.now();
      const clientId = uuidv4();

      // First create a client
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "client_id": clientId,
          "client_name": `Test Client ${timestamp}`,
          "client_type": "CONFIDENTIAL",
          "grant_types": [
            "authorization_code",
            "refresh_token"
          ],
          "redirect_uris": [
            "http://localhost:3000/callback"
          ],
          "response_types": [
            "code"
          ],
          "scope": "openid profile email",
          "token_endpoint_auth_method": "client_secret_post",
          "application_type": "web",
          "client_description": "Test client for dry run delete"
        }
      });
      expect(createResponse.status).toBe(201);

      // Test dry run for client delete
      const dryRunDeleteResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients/${clientId}?dry_run=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Dry run delete response status:", dryRunDeleteResponse.status);
      console.log("Dry run delete response data:", dryRunDeleteResponse.data);
      expect(dryRunDeleteResponse.status).toBe(200);
      expect(dryRunDeleteResponse.data).toHaveProperty("message");
      expect(dryRunDeleteResponse.data).toHaveProperty("client_id", clientId);
      expect(dryRunDeleteResponse.data).toHaveProperty("dry_run", true);

      // Verify client was not actually deleted
      const verifyResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients/${clientId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(verifyResponse.status).toBe(200);
      expect(verifyResponse.data).toHaveProperty("client_id", clientId);

      // Cleanup - actual delete
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients/${clientId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
    });

    it("pagination support", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Test pagination parameters
      const paginatedResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients?limit=5&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Paginated response:", JSON.stringify(paginatedResponse.data, null, 2));
      expect(paginatedResponse.status).toBe(200);
      expect(paginatedResponse.data).toHaveProperty("list");
      expect(paginatedResponse.data).toHaveProperty("total_count");
      expect(paginatedResponse.data).toHaveProperty("limit", 5);
      expect(paginatedResponse.data).toHaveProperty("offset", 0);
      expect(Array.isArray(paginatedResponse.data.list)).toBe(true);
    });
  });

  describe("error cases", () => {
    it("unauthorized access without proper scope", async () => {
      // Get token without org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "account", // Missing org-management scope
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Try to access organization client API
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Unauthorized response:", listResponse.data);
      expect(listResponse.status).toBe(403);
    });

    it("invalid organization id", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Try to access with invalid organization ID
      const invalidOrgResponse = await get({
        url: `${backendUrl}/v1/management/organizations/invalid-org-id-123/tenants/${tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Invalid org response:", invalidOrgResponse.data);
      expect([400, 404]).toContain(invalidOrgResponse.status);
    });

    it("invalid tenant path parameter", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Try to access with invalid tenant ID
      const invalidTenantResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/invalid-tenant-id/clients`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Invalid tenant response:", invalidTenantResponse.data);
      expect([400, 403, 404]).toContain(invalidTenantResponse.status);
    });

    it("client credentials grant not supported", async () => {
      // Get client credentials token
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "client_credentials",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Try to access organization client API with client credentials
      const clientCredentialsResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Client credentials response:", clientCredentialsResponse.data);
      expect(clientCredentialsResponse.status).toBe(401);
    });

    const invalidRequestCases = [
      ["empty client_id", {
        "client_id": "",
        "client_name": "Test Client",
        "grant_types": ["authorization_code"],
        "redirect_uris": ["http://localhost:3000/callback"]
      }],
      ["invalid grant_type", {
        "client_id": uuidv4(),
        "client_name": "Test Client",
        "grant_types": ["invalid_grant"],
        "redirect_uris": ["http://localhost:3000/callback"]
      }],
    ];

    test.each(invalidRequestCases)("invalid request: %s", async (description, body) => {
      console.log("Testing invalid request:", description, body);

      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const invalidResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: body
      });
      console.log("Invalid request response:", invalidResponse.data);
      expect(invalidResponse.status).toBe(400);
    });
  });

  describe("existing client retrieval", () => {
    it("get non-existent client", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Try to get non-existent client
      const nonExistentResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients/non-existent-client`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Non-existent client response:", nonExistentResponse.data);
      expect([400, 404]).toContain(nonExistentResponse.status);
    });

    it("update non-existent client", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Try to update non-existent client
      const updateNonExistentResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients/non-existent-client`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "client_name": "Updated Non-existent Client"
        }
      });
      console.log("Update non-existent client response:", updateNonExistentResponse.data);
      expect([400, 404]).toContain(updateNonExistentResponse.status);
    });

    it("delete non-existent client", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Try to delete non-existent client
      const deleteNonExistentResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/clients/non-existent-client`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Delete non-existent client response:", deleteNonExistentResponse.data);
      expect([400, 404]).toContain(deleteNonExistentResponse.status);
    });
  });

});