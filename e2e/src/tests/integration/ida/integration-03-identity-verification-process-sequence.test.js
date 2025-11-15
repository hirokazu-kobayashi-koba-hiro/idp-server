import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { get, postWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
  federationServerConfig,
  mockApiBaseUrl
} from "../../testConfig";
import { createFederatedUser, registerFidoUaf } from "../../../user";
import { v4 as uuidv4 } from "uuid";

describe("Identity Verification Process Sequence Validation", () => {
  const orgId = serverConfig.organizationId;
  const tenantId = serverConfig.tenantId;

  let orgAccessToken; // Organization admin token for Management API
  let userAccessToken; // Resource owner token for identity verification API
  let testUser;
  let configId;
  let configurationType; // For dynamic API routing

  beforeAll(async () => {
    console.log("Setting up Identity Verification Process Sequence Test...");

    // Authenticate as organization admin for Management API
    const orgAuthResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
      scope: "org-management account management"
    });

    expect(orgAuthResponse.status).toBe(200);
    expect(orgAuthResponse.data).toHaveProperty("access_token");
    orgAccessToken = orgAuthResponse.data.access_token;

    // Create federated user and get resource owner token for identity verification API
    const userResult = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
      scope: "openid profile phone email identity_verification_application claims:authentication_devices claims:ex_sub " + clientSecretPostClient.identityVerificationScope
    });

    testUser = userResult.user;
    userAccessToken = userResult.accessToken;
    console.log(`Created test user: ${testUser.sub}`);

    // Register FIDO UAF for identity verification (may be required)
    await registerFidoUaf({ accessToken: userAccessToken });
    console.log("Registered FIDO UAF for test user");
  });

  afterAll(async () => {
    // Clean up: delete the test configuration if created
    if (configId) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
        headers: { Authorization: `Bearer ${orgAccessToken}` }
      });
      console.log(`Cleaned up test configuration: ${configId}`);
    }
    console.log("Identity Verification Process Sequence Test completed");
  });

  describe("Process Sequence Validation Test", () => {

    it("should enforce process execution order with dependencies", async () => {
      // Step 1: Create configuration with process dependencies
      configId = uuidv4();
      configurationType = uuidv4();

      console.log(`Creating identity verification configuration: ${configId}`);

      const configurationData = {
        "id": configId,
        "type": configurationType,
        "attributes": {
          "enabled": true,
          "process_sequence_test": true
        },
        "common": {
          "callback_application_id_param": "app_id",
          "auth_type": "none"
        },
        "processes": {
          // Initial process: no dependencies
          "apply": {
            "request": {
              "schema": {
                "type": "object",
                "properties": {
                  "given_name": { "type": "string" },
                  "family_name": { "type": "string" }
                },
                "required": ["given_name", "family_name"]
              }
            },
            "pre_hook": {
              "verifications": [
                {
                  "type": "process_sequence"
                }
              ]
            },
            "execution": {
              "type": "no_action"
            },
            "dependencies": {
              "required_processes": [],
              "allow_retry": false
            },
            "transition": {
              "applied": {
                "any_of": [[{ "path": "$.request_body", "type": "object", "operation": "exists" }]]
              }
            },
            "store": {
              "application_details_mapping_rules": [
                { "from": "$.request_body", "to": "*" }
              ]
            }
          },
          // Second process: requires "apply"
          "crm-registration": {
            "request": {
              "schema": {
                "type": "object",
                "properties": {
                  "crm_id": { "type": "string" }
                },
                "required": ["crm_id"]
              }
            },
            "pre_hook": {
              "verifications": [
                {
                  "type": "process_sequence"
                }
              ]
            },
            "execution": {
              "type": "no_action"
            },
            "dependencies": {
              "required_processes": ["apply"],
              "allow_retry": false
            },
            "transition": {
              "applied": {
                "any_of": [[{ "path": "$.request_body", "type": "object", "operation": "exists" }]]
              }
            },
            "store": {
              "application_details_mapping_rules": [
                { "from": "$.request_body", "to": "crm_data" }
              ]
            }
          },
          // Third process: requires "crm-registration"
          "request-ekyc": {
            "request": {
              "schema": {
                "type": "object",
                "properties": {
                  "ekyc_provider": { "type": "string" }
                },
                "required": ["ekyc_provider"]
              }
            },
            "pre_hook": {
              "verifications": [
                {
                  "type": "process_sequence"
                }
              ]
            },
            "execution": {
              "type": "no_action"
            },
            "dependencies": {
              "required_processes": ["crm-registration"],
              "allow_retry": true
            },
            "transition": {
              "applied": {
                "any_of": [[{ "path": "$.request_body", "type": "object", "operation": "exists" }]]
              }
            },
            "store": {
              "application_details_mapping_rules": [
                { "from": "$.request_body", "to": "ekyc_data" }
              ]
            }
          }
        }
      };

      let response = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/identity-verification-configurations`,
        headers: {
          "Authorization": `Bearer ${orgAccessToken}`,
          "Content-Type": "application/json",
          "X-Test-Case": "process-sequence-setup"
        },
        body: configurationData
      });

      console.log("Configuration creation response:", response.status);
      expect(response.status).toBe(201);
      expect(response.data.result).toHaveProperty("id", configId);

      // Verify the configuration was created with dependencies
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
        headers: { Authorization: `Bearer ${orgAccessToken}` }
      });

      console.log(JSON.stringify(getResponse.data, null, 2));
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.processes.apply.dependencies).toEqual({
        allow_retry: false,
      });
      expect(getResponse.data.processes["crm-registration"].dependencies).toEqual({
        allow_retry: false,
        required_processes: ["apply"],
      });
      expect(getResponse.data.processes["request-ekyc"].dependencies).toEqual({
        allow_retry: true,
        required_processes: ["crm-registration"],
      });

      console.log("✅ Identity verification configuration created with process dependencies");

      // Step 2: Test Error - Try to execute crm-registration before apply
      console.log("\n🧪 Test Case 1: Should REJECT crm-registration without apply");

      response = await postWithJson({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/me/identity-verification/applications/${configurationType}/crm-registration`,
        body: { "crm_id": "CRM-12345" },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`,
          "X-Test-Case": "missing-dependency"
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response:", JSON.stringify(response.data, null, 2));

      // Should fail with pre_hook_validation_failed
      expect(response.status).toBe(400);
      expect(response.data).toHaveProperty("error", "pre_hook_validation_failed");
      expect(response.data.error_messages).toEqual(
        expect.arrayContaining([
          expect.stringContaining("Process 'crm-registration' requires completion of: apply")
        ])
      );

      console.log("✅ Correctly rejected crm-registration without apply");

      // Step 3: Execute processes in correct order - apply
      console.log("\n🧪 Test Case 2: Should SUCCEED apply (no dependencies)");

      response = await postWithJson({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/me/identity-verification/applications/${configurationType}/apply`,
        body: {
          "given_name": "Test",
          "family_name": "User"
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`,
          "X-Test-Case": "apply-process"
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("id");

      const applicationId = response.data.id;
      console.log(`✅ Successfully executed apply process (application ID: ${applicationId})`);

      // Step 4: Now crm-registration should succeed
      console.log("\n🧪 Test Case 3: Should SUCCEED crm-registration after apply");
      console.log(`Expected to use application ID: ${applicationId}`);

      response = await postWithJson({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/me/identity-verification/applications/${configurationType}/${applicationId}/crm-registration`,
        body: { "crm_id": "CRM-12345" },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`,
          "X-Test-Case": "after-apply"
        }
      });

      console.log(`Response status: ${response.status}`);
      console.log("Response data:", JSON.stringify(response.data, null, 2));


      expect(response.status).toBe(200);

      console.log("✅ Successfully executed crm-registration after apply");

      // Step 5: Test retry restriction - try to execute apply again
      console.log("\n🧪 Test Case 4: Should REJECT apply retry (allow_retry=false)");

      response = await postWithJson({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/me/identity-verification/applications/${configurationType}/${applicationId}/apply`,
        body: {
          "given_name": "Retry",
          "family_name": "Test"
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`,
          "X-Test-Case": "retry-attempt"
        }
      });

      console.log(`Response status: ${response.status}`);
      expect(response.status).toBe(400);
      expect(response.data).toHaveProperty("error", "pre_hook_validation_failed");
      expect(response.data.error_messages).toEqual(
        expect.arrayContaining([
          expect.stringContaining("Process 'apply' does not allow retry and has already been executed")
        ])
      );

      console.log("✅ Correctly rejected apply retry");

      // Step 6: Test retry allowance - request-ekyc allows retry
      console.log("\n🧪 Test Case 5: Should SUCCEED request-ekyc (first time)");

      const ekycUrl = serverConfig.identityVerificationApplyEndpoint
        .replace("{type}", configurationType)
        .replace("{process}", "request-ekyc");

      response = await postWithJson({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/me/identity-verification/applications/${configurationType}/${applicationId}/request-ekyc`,
        body: { "ekyc_provider": "TestProvider" },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`,
          "X-Test-Case": "ekyc-first"
        }
      });

      console.log(`Response status: ${response.status}`);
      expect(response.status).toBe(200);

      console.log("✅ Successfully executed request-ekyc (first time)");

      // Step 7: Retry request-ekyc should succeed (allow_retry=true)
      console.log("\n🧪 Test Case 6: Should SUCCEED request-ekyc retry (allow_retry=true)");

      response = await postWithJson({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/me/identity-verification/applications/${configurationType}/${applicationId}/request-ekyc`,
        body: { "ekyc_provider": "RetryProvider" },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`,
          "X-Test-Case": "ekyc-retry"
        }
      });

      console.log(`Response status: ${response.status}`);
      expect(response.status).toBe(200);

      console.log("✅ Successfully executed request-ekyc retry");

      console.log("\n✅ All process sequence validation tests passed!");
      console.log("  - Sequential dependency enforcement: ✓");
      console.log("  - Retry control (allow_retry=false): ✓");
      console.log("  - Retry allowance (allow_retry=true): ✓");
    });

  });

});
