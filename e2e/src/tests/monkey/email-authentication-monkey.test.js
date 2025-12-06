import { describe, expect, test } from "@jest/globals";
import { faker } from "@faker-js/faker";
import { postWithJson } from "../../lib/http";
import { serverConfig, clientSecretPostClient } from "../testConfig";
import { getAuthorizations } from "../../api/oauthClient";
import { convertNextAction } from "../../lib/util";

/**
 * Monkey tests for Email Authentication Interactor
 *
 * Issue #1008: Weak input validation causes 500 errors
 *
 * Purpose:
 * - Verify that type mismatch requests return 400 errors instead of 500
 * - Verify that malicious inputs do not cause 500 errors
 * - Verify that unexpected inputs do not crash the application
 *
 * Endpoints tested:
 * - email-authentication-challenge: Send verification code to email
 * - email-authentication: Verify the code
 */
describe("Monkey test Email Authentication", () => {

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

  describe("Email Authentication Challenge - Type Mismatch", () => {

    const typeMismatchCases = [
      // email type mismatch - should return 400 (required field)
      { field: "email", value: ["array", "of", "emails"], description: "email as array", expectedStatus: 400 },
      { field: "email", value: { address: "test@example.com" }, description: "email as object", expectedStatus: 400 },
      { field: "email", value: 12345, description: "email as number", expectedStatus: 400 },
      { field: "email", value: true, description: "email as boolean", expectedStatus: 400 },
      { field: "email", value: null, description: "email as null", expectedStatus: 400 },

      // template type mismatch - should return 200 (optional field, uses default)
      { field: "template", value: ["template1", "template2"], description: "template as array", expectedStatus: 200 },
      { field: "template", value: { name: "auth" }, description: "template as object", expectedStatus: 200 },
      { field: "template", value: 123, description: "template as number", expectedStatus: 200 },
      { field: "template", value: null, description: "template as null", expectedStatus: 200 },

      // provider_id type mismatch - should return 200 (optional field, uses default)
      { field: "provider_id", value: ["provider1"], description: "provider_id as array", expectedStatus: 200 },
      { field: "provider_id", value: { id: "idp" }, description: "provider_id as object", expectedStatus: 200 },
      { field: "provider_id", value: 456, description: "provider_id as number", expectedStatus: 200 },
    ];

    test.each(typeMismatchCases)(
      "should return $expectedStatus when $description",
      async ({ field, value, description, expectedStatus }) => {
        const authId = await getValidAuthorizationId();
        expect(authId).toBeDefined();

        let body;
        if (field === "email") {
          body = { email: value };
        } else if (field === "template") {
          body = { email: "test@example.com", template: value };
        } else {
          body = { email: "test@example.com", provider_id: value };
        }

        const response = await postWithJson({
          url: authorizationIdEndpoint.replace("{id}", authId) + "email-authentication-challenge",
          body
        });

        console.log(`\n Test: ${description}`);
        console.log("Request body:", JSON.stringify(body));
        console.log("Status:", response.status);
        console.log("Response:", JSON.stringify(response.data));

        // Verify expected status code
        expect(response.status).toBe(expectedStatus);
      }
    );
  });

  describe("Email Authentication Challenge - Malicious Input", () => {

    const maliciousInputCases = [
      // null byte inputs - server rejects null bytes (400)
      { field: "email", value: "test\x00@example.com", description: "null byte in email", expectedStatus: 400 },

      // SQL injection-like strings - server accepts as valid string (200)
      { field: "email", value: "'; DROP TABLE users; --@example.com", description: "SQL injection in email", expectedStatus: 200 },
      { field: "email", value: "admin'--@example.com", description: "SQL comment injection in email", expectedStatus: 200 },

      // special characters - server accepts as valid string (200)
      { field: "email", value: "<script>alert('xss')</script>@example.com", description: "XSS attempt in email", expectedStatus: 200 },
      { field: "email", value: "test\n@example.com", description: "newline in email", expectedStatus: 200 },
      { field: "email", value: "test\r@example.com", description: "carriage return in email", expectedStatus: 200 },

      // invalid email formats - server is permissive, accepts as string (200)
      { field: "email", value: "not-an-email", description: "invalid email format", expectedStatus: 200 },
      { field: "email", value: "@example.com", description: "missing local part", expectedStatus: 200 },
      { field: "email", value: "test@", description: "missing domain", expectedStatus: 200 },
      { field: "email", value: "test@@example.com", description: "double @ sign", expectedStatus: 200 },

      // extreme lengths - server accepts (200)
      { field: "email", value: "a".repeat(10000) + "@example.com", description: "extremely long email", expectedStatus: 200 },

      // empty or whitespace only - empty is rejected (400), whitespace is accepted (200)
      { field: "email", value: "", description: "empty email", expectedStatus: 400 },
      { field: "email", value: "   ", description: "whitespace only email", expectedStatus: 200 },

      // unicode special characters - null char rejected (400), others accepted (200)
      { field: "email", value: "test\u0000@example.com", description: "unicode null in email", expectedStatus: 400 },
      { field: "email", value: "用户@example.com", description: "CJK characters in email", expectedStatus: 200 },
      { field: "email", value: "test@example.com", description: "emoji in email", expectedStatus: 200 },
    ];

    test.each(maliciousInputCases)(
      "should not return 500 when $description (expect $expectedStatus)",
      async ({ field, value, description, expectedStatus }) => {
        const authId = await getValidAuthorizationId();
        expect(authId).toBeDefined();

        const body = { email: value };

        const response = await postWithJson({
          url: authorizationIdEndpoint.replace("{id}", authId) + "email-authentication-challenge",
          body
        });

        console.log(`\n Test: ${description}`);
        console.log("Request field:", field, "=", JSON.stringify(value).substring(0, 100));
        console.log("Status:", response.status);
        console.log("Response:", JSON.stringify(response.data));

        // Verify expected status code (primary goal: no 500 errors)
        expect(response.status).toBe(expectedStatus);
      }
    );
  });

  describe("Email Authentication (Verification) - Type Mismatch", () => {

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
      "should return 400 or 404 when $description",
      async ({ field, value, description }) => {
        const authId = await getValidAuthorizationId();
        expect(authId).toBeDefined();

        const body = { [field]: value };

        const response = await postWithJson({
          url: authorizationIdEndpoint.replace("{id}", authId) + "email-authentication",
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

  describe("Email Authentication (Verification) - Malicious Input", () => {

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
          url: authorizationIdEndpoint.replace("{id}", authId) + "email-authentication",
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

  describe("Email Authentication - Missing/Extra Fields", () => {

    const fieldCases = [
      // missing required fields - challenge
      { endpoint: "email-authentication-challenge", body: {}, description: "empty body for challenge", expectedStatus: 400 },

      // missing required fields - verification
      { endpoint: "email-authentication", body: {}, description: "empty body for verification", expectedStatus: 404 },

      // extra unknown fields - challenge (valid email, extra fields ignored -> 200)
      {
        endpoint: "email-authentication-challenge",
        body: {
          email: "test@example.com",
          unknown_field: "should be ignored",
          another_field: { nested: true }
        },
        description: "extra unknown fields for challenge",
        expectedStatus: 200
      },

      // extra unknown fields - verification (no challenge exists -> 404)
      {
        endpoint: "email-authentication",
        body: {
          verification_code: "123456",
          unknown_field: "should be ignored",
          malicious: ["array", "data"]
        },
        description: "extra unknown fields for verification",
        expectedStatus: 404
      },

      // many extra fields (valid email, extra fields ignored -> 200)
      {
        endpoint: "email-authentication-challenge",
        body: Object.assign(
          { email: "test@example.com" },
          ...Array.from({ length: 50 }, (_, i) => ({ [`field_${i}`]: `value_${i}` }))
        ),
        description: "50 extra fields for challenge",
        expectedStatus: 200
      },
    ];

    test.each(fieldCases)(
      "should return $expectedStatus: $description",
      async ({ endpoint, body, description, expectedStatus }) => {
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
        () => faker.internet.email(),
        () => faker.string.alphanumeric(100),
      ];
      return types[Math.floor(Math.random() * types.length)]();
    };

    test("should survive 10 random email-authentication-challenge requests", async () => {
      const results = [];

      for (let i = 0; i < 10; i++) {
        const authId = await getValidAuthorizationId();

        const body = {
          email: generateRandomValue(),
          ...(Math.random() > 0.5 ? { template: generateRandomValue() } : {}),
          ...(Math.random() > 0.5 ? { provider_id: generateRandomValue() } : {}),
          ...(Math.random() > 0.5 ? { [faker.lorem.word()]: generateRandomValue() } : {})
        };

        try {
          const response = await postWithJson({
            url: authorizationIdEndpoint.replace("{id}", authId) + "email-authentication-challenge",
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

      console.log("\n Random Fuzzing Results (email-authentication-challenge):");
      console.table(results);

      const has500 = results.some(r => r.is500);
      expect(has500).toBe(false);
    });

    test("should survive 10 random email-authentication requests", async () => {
      const results = [];

      for (let i = 0; i < 10; i++) {
        const authId = await getValidAuthorizationId();

        const body = {
          verification_code: generateRandomValue(),
          ...(Math.random() > 0.5 ? { [faker.lorem.word()]: generateRandomValue() } : {})
        };

        try {
          const response = await postWithJson({
            url: authorizationIdEndpoint.replace("{id}", authId) + "email-authentication",
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

      console.log("\n Random Fuzzing Results (email-authentication):");
      console.table(results);

      const has500 = results.some(r => r.is500);
      expect(has500).toBe(false);
    });
  });

});
