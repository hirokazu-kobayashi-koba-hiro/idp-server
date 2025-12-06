import { describe, expect, test } from "@jest/globals";
import { faker } from "@faker-js/faker";
import { postWithJson } from "../../lib/http";
import { serverConfig, clientSecretPostClient } from "../testConfig";
import { getAuthorizations } from "../../api/oauthClient";
import { convertNextAction } from "../../lib/util";

/**
 * Monkey tests for SMS Authentication Interactor
 *
 * Issue #1008: Weak input validation causes 500 errors
 *
 * Purpose:
 * - Verify that type mismatch requests return 400 errors instead of 500
 * - Verify that malicious inputs do not cause 500 errors
 * - Verify that unexpected inputs do not crash the application
 *
 * Endpoints tested:
 * - sms-authentication-challenge: Send verification code to phone
 * - sms-authentication: Verify the code
 */
describe("Monkey test SMS Authentication", () => {

  const authorizationIdEndpoint = serverConfig.authorizationIdEndpoint;

  // Helper to get a valid authorization request ID
  const getValidAuthorizationId = async () => {
    const response = await getAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      scope: "openid profile",
      responseType: "code",
      clientId: clientSecretPostClient.clientId,
      redirectUri: clientSecretPostClient.redirectUri,
      state: "state123",
      nonce: "nonce123"
    });

    const { location } = response.headers;
    if (!location) {
      console.log("No location header found!");
      console.log("Response status:", response.status);
      console.log("Response headers:", JSON.stringify(response.headers, null, 2));
      console.log("Response data:", JSON.stringify(response.data, null, 2));
      throw new Error("Failed to get authorization ID - no location header");
    }

    const { params } = convertNextAction(location);
    const authId = params.get("id");
    if (!authId) {
      throw new Error("Failed to get authorization ID - id param missing");
    }
    return authId;
  };

  describe("SMS Authentication Challenge - Type Mismatch", () => {

    const typeMismatchCases = [
      // phone_number type mismatch
      { field: "phone_number", value: ["array", "of", "numbers"], description: "phone_number as array" },
      { field: "phone_number", value: { number: "+1234567890" }, description: "phone_number as object" },
      { field: "phone_number", value: 1234567890, description: "phone_number as number" },
      { field: "phone_number", value: true, description: "phone_number as boolean" },
      { field: "phone_number", value: null, description: "phone_number as null" },

      // template type mismatch
      { field: "template", value: ["template1", "template2"], description: "template as array" },
      { field: "template", value: { name: "auth" }, description: "template as object" },
      { field: "template", value: 123, description: "template as number" },
      { field: "template", value: null, description: "template as null" },

      // provider_id type mismatch
      { field: "provider_id", value: ["provider1"], description: "provider_id as array" },
      { field: "provider_id", value: { id: "idp" }, description: "provider_id as object" },
      { field: "provider_id", value: 456, description: "provider_id as number" },
    ];

    test.each(typeMismatchCases)(
      "should return 400, not 500 when $description",
      async ({ field, value, description }) => {
        const authId = await getValidAuthorizationId();
        expect(authId).toBeDefined();

        let body;
        if (field === "phone_number") {
          body = { phone_number: value };
        } else if (field === "template") {
          body = { phone_number: "+1234567890", template: value };
        } else {
          body = { phone_number: "+1234567890", provider_id: value };
        }

        const response = await postWithJson({
          url: authorizationIdEndpoint.replace("{id}", authId) + "sms-authentication-challenge",
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

  describe("SMS Authentication Challenge - Malicious Input", () => {

    const maliciousInputCases = [
      // null byte inputs
      { field: "phone_number", value: "+123\x00456", description: "null byte in phone_number" },

      // SQL injection-like strings
      { field: "phone_number", value: "'; DROP TABLE users; --", description: "SQL injection in phone_number" },
      { field: "phone_number", value: "+1234567890'--", description: "SQL comment injection in phone_number" },

      // special characters
      { field: "phone_number", value: "<script>alert('xss')</script>", description: "XSS attempt in phone_number" },
      { field: "phone_number", value: "+123\n456", description: "newline in phone_number" },
      { field: "phone_number", value: "+123\r456", description: "carriage return in phone_number" },

      // invalid phone formats
      { field: "phone_number", value: "not-a-phone", description: "invalid phone format" },
      { field: "phone_number", value: "abc", description: "letters only" },
      { field: "phone_number", value: "++1234567890", description: "double plus sign" },

      // extreme lengths
      { field: "phone_number", value: "1".repeat(10000), description: "extremely long phone_number" },

      // empty or whitespace only
      { field: "phone_number", value: "", description: "empty phone_number" },
      { field: "phone_number", value: "   ", description: "whitespace only phone_number" },

      // unicode special characters
      { field: "phone_number", value: "+123\u0000456", description: "unicode null in phone_number" },
      { field: "phone_number", value: "电话号码", description: "CJK characters in phone_number" },
      { field: "phone_number", value: "+123📱456", description: "emoji in phone_number" },
    ];

    test.each(maliciousInputCases)(
      "should return 400, not 500 when $description",
      async ({ field, value, description }) => {
        const authId = await getValidAuthorizationId();
        expect(authId).toBeDefined();

        const body = { phone_number: value };

        const response = await postWithJson({
          url: authorizationIdEndpoint.replace("{id}", authId) + "sms-authentication-challenge",
          body
        });

        console.log(`\n Test: ${description}`);
        console.log("Request field:", field, "=", JSON.stringify(value).substring(0, 100));
        console.log("Status:", response.status);
        console.log("Response:", JSON.stringify(response.data));

        // Malicious input should return 400 (invalid_request)
        expect(response.status).toBe(400);
      }
    );
  });

  describe("SMS Authentication (Verification) - Type Mismatch", () => {

    const typeMismatchCases = [
      // verification_code type mismatch
      { field: "verification_code", value: 123456, description: "verification_code as number" },
      { field: "verification_code", value: ["1", "2", "3", "4", "5", "6"], description: "verification_code as array" },
      { field: "verification_code", value: { code: "123456" }, description: "verification_code as object" },
      { field: "verification_code", value: null, description: "verification_code as null" },
      { field: "verification_code", value: true, description: "verification_code as boolean" },

      // code type mismatch (alternative field name)
      { field: "code", value: 123456, description: "code as number" },
      { field: "code", value: ["123456"], description: "code as array" },
      { field: "code", value: { value: "123456" }, description: "code as object" },
      { field: "code", value: null, description: "code as null" },
    ];

    test.each(typeMismatchCases)(
      "should return 400, not 500 when $description",
      async ({ field, value, description }) => {
        const authId = await getValidAuthorizationId();
        expect(authId).toBeDefined();

        const body = { [field]: value };

        const response = await postWithJson({
          url: authorizationIdEndpoint.replace("{id}", authId) + "sms-authentication",
          body
        });

        console.log(`\n Test: ${description}`);
        console.log("Request body:", JSON.stringify(body));
        console.log("Status:", response.status);
        console.log("Response:", JSON.stringify(response.data));

        // Type mismatch should return 400 (invalid_request) or 404 (no challenge exists)
        expect([400, 404]).toContain(response.status);
      }
    );
  });

  describe("SMS Authentication (Verification) - Malicious Input", () => {

    const maliciousInputCases = [
      // null byte inputs
      { value: "123\x00456", description: "null byte in verification_code" },

      // SQL injection-like strings
      { value: "'; DROP TABLE users; --", description: "SQL injection in verification_code" },
      { value: "' OR '1'='1", description: "SQL OR injection in verification_code" },

      // special characters
      { value: "<script>alert(1)</script>", description: "XSS in verification_code" },
      { value: "123\n456", description: "newline in verification_code" },

      // extreme lengths
      { value: "1".repeat(10000), description: "extremely long verification_code" },

      // empty or whitespace only
      { value: "", description: "empty verification_code" },
      { value: "   ", description: "whitespace only verification_code" },

      // unicode special characters
      { value: "123\u0000456", description: "unicode null in verification_code" },
      { value: "１２３４５６", description: "full-width numbers in verification_code" },
    ];

    test.each(maliciousInputCases)(
      "should return 400, not 500 when $description",
      async ({ value, description }) => {
        const authId = await getValidAuthorizationId();
        expect(authId).toBeDefined();

        const body = { verification_code: value };

        const response = await postWithJson({
          url: authorizationIdEndpoint.replace("{id}", authId) + "sms-authentication",
          body
        });

        console.log(`\n Test: ${description}`);
        console.log("Request value:", JSON.stringify(value).substring(0, 100));
        console.log("Status:", response.status);
        console.log("Response:", JSON.stringify(response.data));

        // Malicious input should return 400 (invalid_request) or 404 (no challenge exists)
        expect([400, 404]).toContain(response.status);
      }
    );
  });

  describe("SMS Authentication - Missing/Extra Fields", () => {

    const fieldCases = [
      // missing required fields - challenge
      { endpoint: "sms-authentication-challenge", body: {}, description: "empty body for challenge" },

      // missing required fields - verification
      { endpoint: "sms-authentication", body: {}, description: "empty body for verification" },

      // extra unknown fields - challenge
      {
        endpoint: "sms-authentication-challenge",
        body: {
          phone_number: "+1234567890",
          unknown_field: "should be ignored",
          another_field: { nested: true }
        },
        description: "extra unknown fields for challenge"
      },

      // extra unknown fields - verification
      {
        endpoint: "sms-authentication",
        body: {
          verification_code: "123456",
          unknown_field: "should be ignored",
          malicious: ["array", "data"]
        },
        description: "extra unknown fields for verification"
      },

      // many extra fields
      {
        endpoint: "sms-authentication-challenge",
        body: Object.assign(
          { phone_number: "+1234567890" },
          ...Array.from({ length: 50 }, (_, i) => ({ [`field_${i}`]: `value_${i}` }))
        ),
        description: "50 extra fields for challenge"
      },
    ];

    test.each(fieldCases)(
      "should handle gracefully: $description",
      async ({ endpoint, body, description }) => {
        const authId = await getValidAuthorizationId();
        expect(authId).toBeDefined();

        const response = await postWithJson({
          url: authorizationIdEndpoint.replace("{id}", authId) + endpoint,
          body
        });

        console.log(`\n Test: ${description}`);
        console.log("Endpoint:", endpoint);
        console.log("Request body keys:", Object.keys(body).slice(0, 10).join(", "));
        console.log("Status:", response.status);
        console.log("Response:", JSON.stringify(response.data));

        // Missing/extra fields should return 400 (invalid_request) or 404 (no challenge for verification)
        expect([400, 404]).toContain(response.status);
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
        () => faker.phone.number(),
        () => faker.string.alphanumeric(100),
      ];
      return types[Math.floor(Math.random() * types.length)]();
    };

    test("should survive 10 random sms-authentication-challenge requests", async () => {
      const results = [];

      for (let i = 0; i < 10; i++) {
        const authId = await getValidAuthorizationId();

        const body = {
          phone_number: generateRandomValue(),
          ...(Math.random() > 0.5 ? { template: generateRandomValue() } : {}),
          ...(Math.random() > 0.5 ? { provider_id: generateRandomValue() } : {}),
          ...(Math.random() > 0.5 ? { [faker.lorem.word()]: generateRandomValue() } : {})
        };

        try {
          const response = await postWithJson({
            url: authorizationIdEndpoint.replace("{id}", authId) + "sms-authentication-challenge",
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

      console.log("\n Random Fuzzing Results (sms-authentication-challenge):");
      console.table(results);

      const has500 = results.some(r => r.is500);
      expect(has500).toBe(false);
    });

    test("should survive 10 random sms-authentication requests", async () => {
      const results = [];

      for (let i = 0; i < 10; i++) {
        const authId = await getValidAuthorizationId();

        const body = {
          verification_code: generateRandomValue(),
          ...(Math.random() > 0.5 ? { [faker.lorem.word()]: generateRandomValue() } : {})
        };

        try {
          const response = await postWithJson({
            url: authorizationIdEndpoint.replace("{id}", authId) + "sms-authentication",
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

      console.log("\n Random Fuzzing Results (sms-authentication):");
      console.table(results);

      const has500 = results.some(r => r.is500);
      expect(has500).toBe(false);
    });
  });

});
