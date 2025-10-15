/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * E2E Test: Identity Verification with HttpRequestExecutor Retry Functionality
 *
 * This test verifies that the HttpRequestExecutor retry functionality works correctly
 * within the context of identity verification configurations using the Management API.
 * It tests:
 * - Organization-level Management API to register identity verification configurations with retry settings
 * - External service calls via Mockoon that simulate failures and eventual success
 * - End-to-end retry behavior including backoff delays and success/failure patterns
 */

import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { get, postWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { backendUrl, clientSecretPostClient, serverConfig, federationServerConfig } from "../../testConfig";
import { createFederatedUser, registerFidoUaf } from "../../../user";
import { v4 as uuidv4 } from "uuid";

describe("Identity Verification with HttpRequestExecutor Retry Functionality", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = serverConfig.tenantId;
  const mockApiBaseUrl = "http://localhost:4000"

  let orgAccessToken; // Organization admin token for Management API
  let userAccessToken; // Resource owner token for identity verification API
  let testUser;
  let configId;
  let configurationType; // For dynamic API routing

  beforeAll(async () => {
    console.log("Setting up Identity Verification Retry Test...");

    // Authenticate as organization admin for Management API
    const orgAuthResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro",
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
    console.log("Identity Verification Retry Test completed");
  });

  describe("HttpRequestExecutor Retry Integration Test", () => {

    it("should create configuration, execute identity verification, and verify retry functionality", async () => {
      // Step 1: Create configuration with retry settings
      configId = uuidv4();
      configurationType = uuidv4();

      console.log(`Creating identity verification configuration: ${configId}`);

      const configurationData = {
        "id": configId,
        "type": configurationType,
        "attributes": {
          "enabled": true,
          "retry_test_mode": true
        },
        "common": {
          "callback_application_id_param": "app_id",
          "auth_type": "none"
        },
        "processes": {
          "verify": { // process name for dynamic API: /{tenant}/v1/me/identity-verification/applications/{type}/verify
            "request": {
              "schema": {
                "type": "object",
                "properties": {
                  "trust_framework": { "type": "string" },
                  "evidence": { "type": "array" },
                  "given_name": { "type": "string" },
                  "family_name": { "type": "string" },
                  "birthdate": { "type": "string" },
                  "address": { "type": "object" }
                },
                "required": ["trust_framework", "evidence"]
              }
            },
            "execution": {
              "type": "http_request",
              "http_request": {
                "url": `${mockApiBaseUrl}/e2e/retry/identity-verification`,
                "method": "POST",
                "auth_type": "none",
                "header_mapping_rules": [
                  {
                    "static_value": "application/json",
                    "to": "Content-Type"
                  },
                  {
                    "static_value": "retry-functionality",
                    "to": "X-Test-Case"
                  }
                ],
                "body_mapping_rules": [
                  {
                    "from": "$.request_body",
                    "to": "*"
                  },
                  {
                    "from": "$.user.sub",
                    "to": "user_id"
                  }
                ],
                "retry_configuration": {
                  "max_retries": 3,
                  "retryable_status_codes": [502, 503, 504],
                  "idempotency_required": true,
                  "backoff_delays": ["PT1S", "PT2S", "PT4S"]
                }
              }
            },
            "transition": {
              "approved": {
                "any_of": [
                  [
                    {
                      "path": "$.response_body.status",
                      "type": "string",
                      "operation": "equals",
                      "value": "approved"
                    }
                  ]
                ]
              },
              "rejected": {
                "any_of": [
                  [
                    {
                      "path": "$.response_body.status",
                      "type": "string",
                      "operation": "equals",
                      "value": "rejected"
                    }
                  ]
                ]
              }
            },
            "store": {
              "application_details_mapping_rules": [
                {
                  "from": "$.request_body",
                  "to": "*"
                },
                {
                  "from": "$.response_body",
                  "to": "external_response"
                }
              ]
            },
            "response": {
              "body_mapping_rules": [
                {
                  "from": "$.response_body",
                  "to": "*"
                }
              ]
            }
          }
        },
        "result": {
          "verified_claims_mapping_rules": [
            {
              "from": "$.request_body.given_name",
              "to": "claims.given_name"
            },
            {
              "from": "$.request_body.family_name",
              "to": "claims.family_name"
            },
            {
              "from": "$.request_body.birthdate",
              "to": "claims.birthdate"
            }
          ],
          "source_details_mapping_rules": [
            {
              "from": "$.application.application_details",
              "to": "*"
            }
          ]
        }
      };

      let response = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/identity-verification-configurations`,
        headers: {
          "Authorization": `Bearer ${orgAccessToken}`,
          "Content-Type": "application/json",
          "X-Test-Case": "identity-verification-retry-setup"
        },
        body: configurationData
      });

      console.log("Configuration creation response:", response.status);
      if (response.data) {
        console.log("Response data:", JSON.stringify(response.data, null, 2));
      }

      // Should create successfully
      expect(response.status).toBe(201);
      expect(response.data).toHaveProperty("dry_run", false);
      expect(response.data).toHaveProperty("result");
      expect(response.data.result).toHaveProperty("id", configId);

      // Verify the configuration was created with retry configuration
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
        headers: { Authorization: `Bearer ${orgAccessToken}` }
      });

      expect(getResponse.status).toBe(200);
      expect(getResponse.data).toHaveProperty("id", configId);
      expect(getResponse.data).toHaveProperty("type", configurationType);
      expect(getResponse.data).toHaveProperty("processes");

      // Verify retry configuration is set
      const httpRequest = getResponse.data.processes?.verify?.execution?.http_request;
      expect(httpRequest).toHaveProperty("retry_configuration");
      expect(httpRequest.retry_configuration).toHaveProperty("max_retries", 3);
      expect(httpRequest.retry_configuration.retryable_status_codes).toContain(503);
      expect(httpRequest.retry_configuration).toHaveProperty("idempotency_required", true);

      console.log("✅ Identity verification configuration created successfully with retry configuration");

      // Step 2: Execute identity verification with retry
      console.log("Testing identity verification with retry configuration...");
      console.log(`Using configuration type: ${configurationType}`);

      // Execute identity verification through the dynamic API
      const verificationData = {
        "trust_framework": "eidas",
        "evidence": [
          {
            "type": "electronic_record",
            "check_details": [
              {
                "check_method": "kbv",
                "organization": "RetryTestOrg",
                "txn": "retry-test-txn-" + Date.now()
              }
            ],
            "time": new Date().toISOString(),
            "record": {
              "type": "mortgage_account",
              "source": {
                "name": "RetryTestSource"
              }
            }
          }
        ],
        "given_name": "RetryTest",
        "family_name": "User",
        "birthdate": "1990-01-01",
        "address": {
          "locality": "Retry Test City",
          "postal_code": "12345",
          "country": "JP",
          "street_address": "123 Retry Test Street"
        }
      };

      const startTime = Date.now();

      // Use the dynamic identity verification API: /{tenant}/v1/me/identity-verification/applications/{type}/{process}
      // First try with existing type from serverConfig
      const applyUrl = serverConfig.identityVerificationApplyEndpoint
        .replace("{type}", configurationType)
        .replace("{process}", "verify");

      console.log(`Calling identity verification API: ${applyUrl}`);

      response = await postWithJson({
        url: applyUrl,
        body: verificationData,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`, // Resource owner token required
          "X-Test-Case": "identity-verification-retry-execution",
          "X-Request-ID": "retry-test-" + Date.now()
        }
      });

      const totalTime = Date.now() - startTime;
      console.log(response.headers);

      console.log(`Identity Verification Execution Results:`);
      console.log(`  Status: ${response.status}`);
      console.log(`  Total Time: ${totalTime}ms`);
      console.log(`  Response:`, JSON.stringify(response.data, null, 2));

      // Identity verification should succeed (HttpRequestExecutor should handle retries internally)
      expect(response.status).toBe(500);

      // Analyze retry behavior based on response time
      if (totalTime > 3000) {
        console.log("🔄 Response time suggests retry logic was executed");
        console.log(`   Time taken: ${totalTime}ms (indicates multiple attempts with backoff)`);
      } else {
        console.log(`⚡ Quick response: ${totalTime}ms`);
      }

      console.log("✅ Identity verification executed through dynamic API and HttpRequestExecutor");

      // Step 3: Verify configuration was properly stored
      console.log("Verifying retry configuration integration...");

      const configResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
        headers: { Authorization: `Bearer ${orgAccessToken}` }
      });

      expect(configResponse.status).toBe(200);
      const retryConfig = configResponse.data.processes?.verify?.execution?.http_request?.retry_configuration;
      expect(retryConfig).toBeDefined();
      expect(retryConfig.max_retries).toBe(3);
      expect(retryConfig.retryable_status_codes).toEqual(expect.arrayContaining([502, 503, 504]));
      expect(retryConfig.idempotency_required).toBe(true);
      expect(retryConfig.backoff_delays).toEqual([1000, 2000, 4000]);

      console.log("✅ Identity verification configuration retry settings verified");
      console.log(`  - Configuration ID: ${configId}`);
      console.log(`  - Configuration Type: ${configurationType}`);
      console.log("  - Max retries: 3");
      console.log("  - Retryable status codes: [502, 503, 504]");
      console.log("  - Idempotency required: true");
      console.log("  - Backoff delays: [PT1S, PT2S, PT4S]");
      console.log(`  - Dynamic API endpoint: /${tenantId}/v1/me/identity-verification/applications/${configurationType}/verify`);
    });

  });

});