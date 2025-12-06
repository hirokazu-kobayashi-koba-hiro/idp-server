import { describe, expect, it, test } from "@jest/globals";
import { faker } from "@faker-js/faker";
import { postWithJson } from "../../lib/http";
import { serverConfig, clientSecretPostClient } from "../testConfig";
import { getAuthorizations } from "../../api/oauthClient";
import { convertNextAction } from "../../lib/util";

/**
 * Monkey tests for Password Authentication Interactor
 *
 * Issue #1008: Weak input validation causes 500 errors
 *
 * Purpose:
 * - Verify that type mismatch requests (arrays, objects, numbers, etc.) return 400 errors instead of 500
 * - Verify that null bytes and SQL injection-like strings do not cause 500 errors
 * - Verify that unexpected inputs do not crash the application
 */
describe("Monkey test Password Authentication", () => {

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
      console.log("No location header found! Response:", JSON.stringify(response.data, null, 2));
      return null;
    }

    const { params } = convertNextAction(location);
    return params.get("id");
  };

  describe("Type Mismatch", () => {

    /**
     * Issue #1008 reproduction test
     * Sending an array as username causes ClassCastException
     * java.lang.ClassCastException: class java.util.ArrayList cannot be cast to class java.lang.String
     */
    const typeMismatchCases = [
      // username type mismatch
      { field: "username", value: ["array", "of", "strings"], description: "username as array" },
      { field: "username", value: { email: "test@example.com" }, description: "username as object" },
      { field: "username", value: 12345, description: "username as number" },
      { field: "username", value: true, description: "username as boolean" },
      { field: "username", value: null, description: "username as null" },

      // password type mismatch
      { field: "password", value: ["password1", "password2"], description: "password as array" },
      { field: "password", value: { secret: "mypassword" }, description: "password as object" },
      { field: "password", value: 123456, description: "password as number" },
      { field: "password", value: false, description: "password as boolean" },
      { field: "password", value: null, description: "password as null" },

      // combined type mismatch
      { field: "both", value: { username: ["user"], password: { pwd: "pass" } }, description: "both fields as wrong types" },
    ];

    test.each(typeMismatchCases)(
      "should return 400, not 500 when $description",
      async ({ field, value, description }) => {
        const authId = await getValidAuthorizationId();
        expect(authId).toBeDefined();

        let body;
        if (field === "both") {
          body = value;
        } else if (field === "username") {
          body = { username: value, password: "validPassword123" };
        } else {
          body = { username: "valid@example.com", password: value };
        }

        const response = await postWithJson({
          url: authorizationIdEndpoint.replace("{id}", authId) + "password-authentication",
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

  describe("Malicious Input", () => {

    /**
     * Issue #1008 reproduction test
     * Strings containing null bytes (\x00) cause SQL errors
     * ERROR: invalid byte sequence for encoding "UTF8": 0x00
     */
    const maliciousInputCases = [
      // null byte inputs
      { field: "username", value: "user\x00name", description: "null byte in username" },
      { field: "password", value: "pass\x00word", description: "null byte in password" },

      // SQL injection-like strings
      { field: "username", value: "'; DROP TABLE users; --", description: "SQL injection attempt in username" },
      { field: "username", value: "admin'--", description: "SQL comment injection in username" },
      { field: "password", value: "' OR '1'='1", description: "SQL injection attempt in password" },

      // special characters
      { field: "username", value: "<script>alert('xss')</script>", description: "XSS attempt in username" },
      { field: "username", value: "user\nname", description: "newline in username" },
      { field: "username", value: "user\rname", description: "carriage return in username" },
      { field: "username", value: "user\tname", description: "tab in username" },

      // extreme lengths
      { field: "username", value: "a".repeat(10000), description: "extremely long username" },
      { field: "password", value: "p".repeat(10000), description: "extremely long password" },

      // empty or whitespace only
      { field: "username", value: "", description: "empty username" },
      { field: "password", value: "", description: "empty password" },
      { field: "username", value: "   ", description: "whitespace only username" },
      { field: "password", value: "   ", description: "whitespace only password" },

      // unicode special characters
      { field: "username", value: "user\u0000name", description: "unicode null in username" },
      { field: "username", value: "user\uFFFDname", description: "unicode replacement char in username" },
      { field: "username", value: "用户名", description: "CJK characters in username" },
      { field: "username", value: "user", description: "emoji in username" },
    ];

    test.each(maliciousInputCases)(
      "should return 400, not 500 when $description",
      async ({ field, value, description }) => {
        const authId = await getValidAuthorizationId();
        expect(authId).toBeDefined();

        const body = field === "username"
          ? { username: value, password: "validPassword123" }
          : { username: "valid@example.com", password: value };

        const response = await postWithJson({
          url: authorizationIdEndpoint.replace("{id}", authId) + "password-authentication",
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

  describe("Missing/Extra Fields", () => {

    const fieldCases = [
      // missing required fields
      { body: {}, description: "empty body" },
      { body: { username: "user@example.com" }, description: "missing password" },
      { body: { password: "password123" }, description: "missing username" },

      // extra unknown fields
      {
        body: {
          username: "user@example.com",
          password: "password123",
          unknown_field: "should be ignored",
          another_field: { nested: true }
        },
        description: "extra unknown fields"
      },

      // many extra fields
      {
        body: Object.assign(
          { username: "user@example.com", password: "password123" },
          ...Array.from({ length: 100 }, (_, i) => ({ [`field_${i}`]: `value_${i}` }))
        ),
        description: "100 extra fields"
      },
    ];

    test.each(fieldCases)(
      "should handle gracefully: $description",
      async ({ body, description }) => {
        const authId = await getValidAuthorizationId();
        expect(authId).toBeDefined();

        const response = await postWithJson({
          url: authorizationIdEndpoint.replace("{id}", authId) + "password-authentication",
          body
        });

        console.log(`\n Test: ${description}`);
        console.log("Request body keys:", Object.keys(body).slice(0, 10).join(", "));
        console.log("Status:", response.status);
        console.log("Response:", JSON.stringify(response.data));

        // Missing/extra fields should return 400 (invalid_request)
        expect(response.status).toBe(400);
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
        () => faker.lorem.paragraphs(10),
        () => faker.string.alphanumeric(1000),
      ];
      return types[Math.floor(Math.random() * types.length)]();
    };

    it("should survive 20 random password authentication requests", async () => {
      const results = [];

      for (let i = 0; i < 20; i++) {
        const authId = await getValidAuthorizationId();

        const body = {
          username: generateRandomValue(),
          password: generateRandomValue(),
          ...(Math.random() > 0.5 ? { [faker.lorem.word()]: generateRandomValue() } : {})
        };

        try {
          const response = await postWithJson({
            url: authorizationIdEndpoint.replace("{id}", authId) + "password-authentication",
            body
          });

          results.push({
            iteration: i + 1,
            body: JSON.stringify(body).substring(0, 100),
            status: response.status,
            is500: response.status === 500
          });

          // 500 errors are not acceptable
          expect(response.status).not.toBe(500);
        } catch (error) {
          results.push({
            iteration: i + 1,
            body: JSON.stringify(body).substring(0, 100),
            error: error.message
          });
          // Only network errors are acceptable
          if (!error.message.includes("ECONNREFUSED")) {
            throw error;
          }
        }
      }

      console.log("\n Random Fuzzing Results:");
      console.table(results);

      const has500 = results.some(r => r.is500);
      expect(has500).toBe(false);
    });
  });

});
