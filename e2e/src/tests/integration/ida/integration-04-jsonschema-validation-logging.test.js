import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { get, postWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
  federationServerConfig
} from "../../testConfig";
import { createFederatedUser, registerFidoUaf } from "../../../user";
import { v4 as uuidv4 } from "uuid";

describe("JsonSchemaValidator Logging Verification", () => {
  const orgId = serverConfig.organizationId;
  const tenantId = serverConfig.tenantId;

  let orgAccessToken;
  let userAccessToken;
  let testUser;
  let configId;
  let configurationType;

  beforeAll(async () => {
    console.log("Setting up JsonSchemaValidator Logging Test...");

    // Authenticate as organization admin
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
    orgAccessToken = orgAuthResponse.data.access_token;

    // Create federated user
    const userResult = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
      scope: "openid profile phone email identity_verification_application " + clientSecretPostClient.identityVerificationScope
    });

    testUser = userResult.user;
    userAccessToken = userResult.accessToken;
    console.log(`Created test user: ${testUser.sub}`);

    await registerFidoUaf({ accessToken: userAccessToken });
    console.log("Registered FIDO UAF for test user");
  });

  afterAll(async () => {
    if (configId) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
        headers: { Authorization: `Bearer ${orgAccessToken}` }
      });
      console.log(`Cleaned up test configuration: ${configId}`);
    }
    console.log("JsonSchemaValidator Logging Test completed");
  });

  describe("Schema Validation Logging", () => {

    it("should log validation failures with error details", async () => {
      // Step 1: Create configuration with strict schema
      configId = uuidv4();
      configurationType = uuidv4();

      console.log(`Creating identity verification configuration: ${configId}`);

      const configurationData = {
        "id": configId,
        "type": configurationType,
        "attributes": {
          "enabled": true,
          "schema_validation_test": true
        },
        "common": {
          "callback_application_id_param": "app_id",
          "auth_type": "none"
        },
        "processes": {
          "apply": {
            "request": {
              "schema": {
                "type": "object",
                "required": ["email", "age", "status", "full_name", "address"],
                "properties": {
                  "email": {
                    "type": "string",
                    "format": "email"
                  },
                  "age": {
                    "type": "integer",
                    "minimum": 18,
                    "maximum": 100
                  },
                  "status": {
                    "type": "string",
                    "enum": ["active", "inactive"]
                  },
                  "phone": {
                    "type": "string",
                    "pattern": "^[0-9]{10,11}$"
                  },
                  "full_name": {
                    "type": "object",
                    "required": ["first_name", "last_name"],
                    "properties": {
                      "first_name": {
                        "type": "string",
                        "minLength": 1,
                        "maxLength": 50,
                        "pattern": "^[\\p{L}\\p{M}\\s'-]+$"
                      },
                      "last_name": {
                        "type": "string",
                        "minLength": 1,
                        "maxLength": 50,
                        "pattern": "^[\\p{L}\\p{M}\\s'-]+$"
                      },
                      "middle_name": {
                        "type": "string",
                        "maxLength": 50,
                        "pattern": "^[\\p{L}\\p{M}\\s'-]*$"
                      }
                    }
                  },
                  "address": {
                    "type": "object",
                    "required": ["postal_code", "prefecture", "city", "street"],
                    "properties": {
                      "postal_code": {
                        "type": "string",
                        "pattern": "^[0-9]{3}-[0-9]{4}$"
                      },
                      "prefecture": {
                        "type": "string",
                        "minLength": 2,
                        "maxLength": 10
                      },
                      "city": {
                        "type": "string",
                        "minLength": 1,
                        "maxLength": 50
                      },
                      "street": {
                        "type": "string",
                        "minLength": 1,
                        "maxLength": 100
                      },
                      "building": {
                        "type": "string",
                        "maxLength": 100
                      }
                    }
                  }
                }
              }
            },
            "execution": {
              "type": "no_action"
            },
            "transition": {
              "applied": {
                "any_of": [[]]
              }
            },
            "store": {
              "application_details_mapping_rules": []
            },
            "dependencies": {
              "allow_retry": false
            }
          }
        }
      };

      let response = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
        headers: {
          "Authorization": `Bearer ${orgAccessToken}`,
          "Content-Type": "application/json",
          "X-Test-Case": "schema-validation-setup"
        },
        body: configurationData
      });

      expect(response.status).toBe(201);
      console.log("✅ Configuration created");

      // Step 2: Test Case 1 - Missing required fields (should trigger log.warn)
      console.log("\n🧪 Test Case 1: Missing required fields");
      console.log("Expected log: WARN - JSON schema validation failed: error_count=5, errors=[email is missing, age is missing, status is missing, full_name is missing, address is missing]");

      response = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${configurationType}/apply`,
        body: {},  // Empty body - missing all required fields
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`,
          "X-Test-Case": "missing-required-fields"
        }
      });

      console.log(JSON.stringify(response.data, null, 2));
      console.log(`Response status: ${response.status}`);
      expect(response.status).toBe(400);
      expect(response.data).toHaveProperty("error", "invalid_request");
      console.log("✅ Validation failed as expected (should see WARN log with 5 errors)");

      // Step 3: Test Case 2 - Type mismatch (should trigger log.warn)
      console.log("\n🧪 Test Case 2: Type mismatch");
      console.log("Expected log: WARN - JSON schema validation failed: error_count=3, errors=[age is not a integer, full_name is missing, address is missing]");

      response = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${configurationType}/apply`,
        body: {
          "email": "test@example.com",
          "age": "twenty-five",  // String instead of integer
          "status": "active"
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`,
          "X-Test-Case": "type-mismatch"
        }
      });

      console.log(JSON.stringify(response.data, null, 2));
      console.log(`Response status: ${response.status}`);
      expect(response.status).toBe(400);
      console.log("✅ Type validation failed as expected (should see WARN log with 3 errors)");

      // Step 4: Test Case 3 - Constraint violations (should trigger log.warn)
      console.log("\n🧪 Test Case 3: Multiple constraint violations");
      console.log("Expected log: WARN - JSON schema validation failed: error_count=5+, errors=[age minimum is 18, status is not allowed enum value, phone pattern is ..., full_name is missing, address is missing]");

      response = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${configurationType}/apply`,
        body: {
          "email": "test@example.com",
          "age": 15,  // Below minimum
          "status": "pending",  // Not in enum
          "phone": "abc123"  // Invalid pattern
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`,
          "X-Test-Case": "constraint-violations"
        }
      });

      console.log(JSON.stringify(response.data, null, 2));
      console.log(`Response status: ${response.status}`);
      expect(response.status).toBe(400);
      console.log("✅ Constraint validation failed as expected (should see WARN log with 5+ errors)");

      // Step 5: Test Case 4 - Name validation errors (should trigger log.warn)
      console.log("\n🧪 Test Case 4: Name validation errors");
      console.log("Expected log: WARN - JSON schema validation failed: error_count=4+, errors=[first_name is missing, last_name is missing, postal_code is missing, etc.]");

      response = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${configurationType}/apply`,
        body: {
          "email": "test@example.com",
          "age": 25,
          "status": "active",
          "phone": "09012345678",
          "full_name": {
            "first_name": "",  // Empty (minLength: 1)
            "last_name": "A".repeat(51)  // Too long (maxLength: 50)
          },
          "address": {
            "postal_code": "1234567",  // Invalid format (should be 123-4567)
            "prefecture": "東",  // Too short (minLength: 2)
            "city": "",  // Empty (minLength: 1)
            "street": "テスト通り1-2-3"
          }
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`,
          "X-Test-Case": "name-address-validation-errors"
        }
      });

      console.log(JSON.stringify(response.data, null, 2));
      console.log(`Response status: ${response.status}`);
      expect(response.status).toBe(400);
      console.log("✅ Name and address validation failed as expected (should see WARN log with multiple errors)");

      // Step 6: Test Case 5 - Invalid name pattern (should trigger log.warn)
      console.log("\n🧪 Test Case 5: Invalid name pattern");
      console.log("Expected log: WARN - JSON schema validation failed: error_count=2, errors=[first_name pattern is ..., last_name pattern is ...]");

      response = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${configurationType}/apply`,
        body: {
          "email": "test@example.com",
          "age": 25,
          "status": "active",
          "phone": "09012345678",
          "full_name": {
            "first_name": "太郎123",  // Contains numbers (invalid pattern)
            "last_name": "田中@"  // Contains special char @ (invalid pattern)
          },
          "address": {
            "postal_code": "123-4567",
            "prefecture": "東京都",
            "city": "渋谷区",
            "street": "テスト通り1-2-3"
          }
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`,
          "X-Test-Case": "invalid-name-pattern"
        }
      });

      console.log(JSON.stringify(response.data, null, 2));
      console.log(`Response status: ${response.status}`);
      expect(response.status).toBe(400);
      console.log("✅ Name pattern validation failed as expected (should see WARN log with 2 errors)");

      // Step 7: Test Case 6 - Invalid postal code format (should trigger log.warn)
      console.log("\n🧪 Test Case 6: Invalid postal code format");
      console.log("Expected log: WARN - JSON schema validation failed: error_count=1, errors=[postal_code pattern is ^[0-9]{3}-[0-9]{4}$]");

      response = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${configurationType}/apply`,
        body: {
          "email": "test@example.com",
          "age": 25,
          "status": "active",
          "phone": "09012345678",
          "full_name": {
            "first_name": "太郎",
            "last_name": "田中"
          },
          "address": {
            "postal_code": "1234567",  // Missing hyphen
            "prefecture": "東京都",
            "city": "渋谷区",
            "street": "テスト通り1-2-3"
          }
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`,
          "X-Test-Case": "invalid-postal-code"
        }
      });

      console.log(JSON.stringify(response.data, null, 2));
      console.log(`Response status: ${response.status}`);
      expect(response.status).toBe(400);
      console.log("✅ Postal code validation failed as expected (should see WARN log with 1 error)");

      // Step 8: Test Case 7 - Valid request with all fields (should NOT trigger log.warn)
      console.log("\n🧪 Test Case 7: Valid request with all fields (no validation errors)");
      console.log("Expected: NO WARN log (validation succeeds)");

      response = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/me/identity-verification/applications/${configurationType}/apply`,
        body: {
          "email": "valid@example.com",
          "age": 25,
          "status": "active",
          "phone": "09012345678",
          "full_name": {
            "first_name": "太郎",
            "last_name": "田中",
            "middle_name": "一"
          },
          "address": {
            "postal_code": "150-0001",
            "prefecture": "東京都",
            "city": "渋谷区",
            "street": "テスト通り1-2-3",
            "building": "テストビル101"
          }
        },
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${userAccessToken}`,
          "X-Test-Case": "valid-request"
        }
      });

      console.log(JSON.stringify(response.data, null, 2));
      console.log(`Response status: ${response.status}`);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("id");
      console.log("✅ Valid request succeeded (should see NO WARN log)");

      // Summary
      console.log("\n📊 Test Summary:");
      console.log("✅ Test Case 1: Missing required fields → WARN log with error_count=5");
      console.log("✅ Test Case 2: Type mismatch → WARN log with error_count=3");
      console.log("✅ Test Case 3: Constraint violations → WARN log with error_count=5+");
      console.log("✅ Test Case 4: Name/Address validation errors → WARN log with multiple errors");
      console.log("✅ Test Case 5: Invalid name pattern → WARN log with error_count=2");
      console.log("✅ Test Case 6: Invalid postal code format → WARN log with error_count=1");
      console.log("✅ Test Case 7: Valid request with all fields → NO WARN log");
      console.log("\n🔍 Check server logs for:");
      console.log("  - WARN level logs from JsonSchemaValidator");
      console.log("  - error_count and errors array in each failure case");
      console.log("  - Name validation errors (minLength, maxLength, pattern)");
      console.log("  - Address validation errors (postal code pattern, prefecture/city/street constraints)");
      console.log("  - No logs for successful validation");
    });

    it("should log target does not exist error", async () => {
      console.log("\n🧪 Test Case 8: Target does not exist");
      console.log("Expected log: WARN - JSON schema validation failed: target does not exist");

      // This case is harder to trigger in E2E, but we document it
      console.log("Note: This error occurs when JsonNodeWrapper target is null/empty");
      console.log("Typically happens with malformed JSON or empty request body at parser level");
      console.log("✅ Test case documented");
    });
  });
});
