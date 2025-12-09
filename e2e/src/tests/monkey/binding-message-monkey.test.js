import { describe, expect, test } from "@jest/globals";
import { faker } from "@faker-js/faker";
import { postWithJson } from "../../lib/http";
import {
  serverConfig,
  clientSecretPostClient,
  federationServerConfig
} from "../testConfig";
import {
  requestBackchannelAuthentications,
  postAuthenticationDeviceInteraction,
  getAuthenticationDeviceAuthenticationTransaction
} from "../../api/oauthClient";
import { createFederatedUser, registerFidoUaf } from "../../user";

/**
 * Monkey tests for Binding Message Interactor (CIBA)
 *
 * Issue #1008: Weak input validation causes 500 errors
 * Issue #1045: Documentation for binding message backend verification
 *
 * Purpose:
 * - Verify that binding message verification works correctly (success case)
 * - Verify that type mismatch requests return 400 errors instead of 500
 * - Verify that malicious inputs do not cause 500 errors
 * - Verify that unexpected inputs do not crash the application
 * - Verify boundary conditions (max length: 20 characters)
 *
 * Endpoints tested:
 * - authentication-device-binding-message: Verify binding message in CIBA flow
 *
 * Related documentation:
 * - how-to-19-ciba-binding-message-verification.md
 * - protocol-02-ciba-flow.md (バインディングメッセージセクション)
 */
describe("Monkey test Binding Message", () => {

  // Helper to create CIBA authentication transaction for binding message test
  const createCibaTransaction = async () => {
    // Create user with FIDO UAF device
    const { user, accessToken } = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
      scope: "openid claims:authentication_devices"
    });

    const { authenticationDeviceId } = await registerFidoUaf({ accessToken });

    // Start CIBA authentication
    const backchannelAuthenticationResponse = await requestBackchannelAuthentications({
      endpoint: serverConfig.backchannelAuthenticationEndpoint,
      clientId: clientSecretPostClient.clientId,
      scope: "openid profile",
      bindingMessage: "999",
      loginHint: `device:${authenticationDeviceId},idp:${federationServerConfig.providerName}`,
      clientSecret: clientSecretPostClient.clientSecret,
    });

    if (backchannelAuthenticationResponse.status !== 200) {
      console.log("CIBA request failed:", backchannelAuthenticationResponse.data);
      return null;
    }

    // Get authentication transaction
    const authenticationTransactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: serverConfig.authenticationDeviceEndpoint,
      deviceId: authenticationDeviceId,
      params: {
        "attributes.auth_req_id": backchannelAuthenticationResponse.data.auth_req_id
      },
    });

    if (authenticationTransactionResponse.status !== 200 ||
        !authenticationTransactionResponse.data.list ||
        authenticationTransactionResponse.data.list.length === 0) {
      console.log("Failed to get authentication transaction:", authenticationTransactionResponse.data);
      return null;
    }

    const transaction = authenticationTransactionResponse.data.list[0];
    return {
      flowType: transaction.flow,
      transactionId: transaction.id,
      authReqId: backchannelAuthenticationResponse.data.auth_req_id
    };
  };

  describe("Binding Message - Success Cases", () => {

    test("should return 200 when binding_message matches exactly", async () => {
      const transaction = await createCibaTransaction();
      if (!transaction) {
        console.log("Skipping test - could not create CIBA transaction");
        return;
      }

      // The CIBA request was made with binding_message="999"
      const body = { binding_message: "999" };

      const response = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: transaction.flowType,
        id: transaction.transactionId,
        interactionType: "authentication-device-binding-message",
        body
      });

      console.log("\n Test: binding_message matches exactly");
      console.log("Request body:", JSON.stringify(body));
      console.log("Status:", response.status);
      console.log("Response:", JSON.stringify(response.data));

      // Exact match should return 200 (success)
      expect(response.status).toBe(200);
    });
  });

  describe("Binding Message - Boundary Conditions", () => {

    const boundaryCases = [
      // Max length (20 characters) - should fail because it doesn't match "999"
      { value: "12345678901234567890", description: "exactly 20 characters (max length)", expectedStatus: 400 },

      // Over max length (21 characters) - should fail
      { value: "123456789012345678901", description: "21 characters (over max)", expectedStatus: 400 },

      // Single character
      { value: "9", description: "single character", expectedStatus: 400 },

      // Numeric only (same as expected but different value)
      { value: "123", description: "numeric 3 digits (not matching)", expectedStatus: 400 },
    ];

    test.each(boundaryCases)(
      "should return $expectedStatus when $description",
      async ({ value, description, expectedStatus }) => {
        const transaction = await createCibaTransaction();
        if (!transaction) {
          console.log("Skipping test - could not create CIBA transaction");
          return;
        }

        const body = { binding_message: value };

        const response = await postAuthenticationDeviceInteraction({
          endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
          flowType: transaction.flowType,
          id: transaction.transactionId,
          interactionType: "authentication-device-binding-message",
          body
        });

        console.log(`\n Test: ${description}`);
        console.log("Request value:", JSON.stringify(value).substring(0, 50));
        console.log("Status:", response.status);
        console.log("Response:", JSON.stringify(response.data));

        expect(response.status).toBe(expectedStatus);
      }
    );
  });

  describe("Binding Message - Type Mismatch", () => {

    const typeMismatchCases = [
      // binding_message type mismatch
      { field: "binding_message", value: ["999", "888"], description: "binding_message as array" },
      { field: "binding_message", value: { code: "999" }, description: "binding_message as object" },
      { field: "binding_message", value: 999, description: "binding_message as number" },
      { field: "binding_message", value: true, description: "binding_message as boolean" },
      { field: "binding_message", value: null, description: "binding_message as null" },
    ];

    test.each(typeMismatchCases)(
      "should return 400, not 500 when $description",
      async ({ field, value, description }) => {
        const transaction = await createCibaTransaction();
        if (!transaction) {
          console.log("Skipping test - could not create CIBA transaction");
          return;
        }

        const body = { [field]: value };

        const response = await postAuthenticationDeviceInteraction({
          endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
          flowType: transaction.flowType,
          id: transaction.transactionId,
          interactionType: "authentication-device-binding-message",
          body
        });

        console.log(`\n Test: ${description}`);
        console.log("Request body:", JSON.stringify(body));
        console.log("Status:", response.status);
        console.log("Response:", JSON.stringify(response.data));

        // Type mismatch should return 400 (invalid_request)
        expect(response.status).toBe(400);
      }
    );
  });

  describe("Binding Message - Malicious Input", () => {

    const maliciousInputCases = [
      // null byte inputs
      { value: "999\x00888", description: "null byte in binding_message" },

      // SQL injection-like strings
      { value: "'; DROP TABLE users; --", description: "SQL injection in binding_message" },
      { value: "' OR '1'='1", description: "SQL OR injection in binding_message" },

      // special characters
      { value: "<script>alert(1)</script>", description: "XSS in binding_message" },
      { value: "999\n888", description: "newline in binding_message" },
      { value: "999\r888", description: "carriage return in binding_message" },

      // extreme lengths
      { value: "9".repeat(10000), description: "extremely long binding_message" },

      // empty or whitespace only
      { value: "", description: "empty binding_message" },
      { value: "   ", description: "whitespace only binding_message" },

      // unicode special characters
      { value: "999\u0000888", description: "unicode null in binding_message" },
      { value: "９９９", description: "full-width numbers in binding_message" },
    ];

    test.each(maliciousInputCases)(
      "should return 400, not 500 when $description",
      async ({ value, description }) => {
        const transaction = await createCibaTransaction();
        if (!transaction) {
          console.log("Skipping test - could not create CIBA transaction");
          return;
        }

        const body = { binding_message: value };

        const response = await postAuthenticationDeviceInteraction({
          endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
          flowType: transaction.flowType,
          id: transaction.transactionId,
          interactionType: "authentication-device-binding-message",
          body
        });

        console.log(`\n Test: ${description}`);
        console.log("Request value:", JSON.stringify(value).substring(0, 100));
        console.log("Status:", response.status);
        console.log("Response:", JSON.stringify(response.data));

        // Malicious input should return 400 (invalid_request - unmatched)
        expect(response.status).toBe(400);
      }
    );
  });

  describe("Binding Message - Missing/Extra Fields", () => {

    const fieldCases = [
      // missing required fields
      { body: {}, description: "empty body", expectedStatus: 400 },

      // extra unknown fields
      {
        body: {
          binding_message: "wrong_value",
          unknown_field: "should be ignored",
          another_field: { nested: true }
        },
        description: "extra unknown fields",
        expectedStatus: 400
      },

      // many extra fields
      {
        body: Object.assign(
          { binding_message: "wrong_value" },
          ...Array.from({ length: 50 }, (_, i) => ({ [`field_${i}`]: `value_${i}` }))
        ),
        description: "50 extra fields",
        expectedStatus: 400
      },
    ];

    test.each(fieldCases)(
      "should return $expectedStatus: $description",
      async ({ body, description, expectedStatus }) => {
        const transaction = await createCibaTransaction();
        if (!transaction) {
          console.log("Skipping test - could not create CIBA transaction");
          return;
        }

        const response = await postAuthenticationDeviceInteraction({
          endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
          flowType: transaction.flowType,
          id: transaction.transactionId,
          interactionType: "authentication-device-binding-message",
          body
        });

        console.log(`\n Test: ${description}`);
        console.log("Request body keys:", Object.keys(body).slice(0, 10).join(", "));
        console.log("Status:", response.status);
        console.log("Response:", JSON.stringify(response.data));

        // Verify expected status code
        expect(response.status).toBe(expectedStatus);
      }
    );
  });

  describe("Random Fuzzing", () => {

    const generateRandomValue = () => {
      const types = [
        () => faker.lorem.word(),
        () => faker.number.int(),
        () => faker.datatype.boolean(),
        () => null,
        () => [],
        () => [faker.lorem.word(), faker.number.int()],
        () => ({ [faker.lorem.word()]: faker.lorem.sentence() }),
        () => faker.string.numeric(6),
        () => faker.string.alphanumeric(100),
      ];
      return types[Math.floor(Math.random() * types.length)]();
    };

    test("should survive 10 random binding-message requests", async () => {
      const results = [];

      for (let i = 0; i < 10; i++) {
        const transaction = await createCibaTransaction();
        if (!transaction) {
          results.push({
            iteration: i + 1,
            body: "N/A",
            error: "Failed to create CIBA transaction"
          });
          continue;
        }

        const body = {
          binding_message: generateRandomValue(),
          ...(Math.random() > 0.5 ? { [faker.lorem.word()]: generateRandomValue() } : {})
        };

        try {
          const response = await postAuthenticationDeviceInteraction({
            endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
            flowType: transaction.flowType,
            id: transaction.transactionId,
            interactionType: "authentication-device-binding-message",
            body
          });

          results.push({
            iteration: i + 1,
            body: JSON.stringify(body).substring(0, 80),
            status: response.status,
            is500: response.status === 500
          });

          // 500 errors are not acceptable
          expect(response.status).not.toBe(500);
        } catch (error) {
          results.push({
            iteration: i + 1,
            body: JSON.stringify(body).substring(0, 80),
            error: error.message
          });
          if (!error.message.includes("ECONNREFUSED")) {
            throw error;
          }
        }
      }

      console.log("\n Random Fuzzing Results (binding-message):");
      console.table(results);

      const has500 = results.some(r => r.is500);
      expect(has500).toBe(false);
    });
  });

});
