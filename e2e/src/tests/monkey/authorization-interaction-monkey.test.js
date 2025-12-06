import { describe, expect, test } from "@jest/globals";
import { get, postWithJson } from "../../lib/http";
import { backendUrl, serverConfig, clientSecretPostClient } from "../testConfig";
import { getAuthorizations } from "../../api/oauthClient";
import { convertNextAction } from "../../lib/util";

/**
 * Monkey tests for Authorization Interactions
 *
 * Purpose:
 * - Verify that invalid authorization IDs return 400/404 errors instead of 500
 * - Verify that unsupported interaction types return appropriate errors
 */
describe("Monkey test Authorization Interactions", () => {

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

  describe("View Data API - Invalid Authorization ID", () => {

    const invalidIdCases = [
      { id: null, description: "null id" },
      { id: undefined, description: "undefined id" },
      { id: "", description: "empty string id" },
      { id: "not-a-uuid", description: "invalid uuid format" },
      { id: "12345", description: "numeric string id" },
      { id: "../../../etc/passwd", description: "path traversal attempt" },
      { id: "<script>alert(1)</script>", description: "XSS in id" },
      { id: "' OR 1=1 --", description: "SQL injection in id" },
      { id: "a".repeat(1000), description: "very long id" },
      { id: "00000000-0000-0000-0000-000000000000", description: "nil uuid" },
      { id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", description: "non-existent uuid" },
    ];

    test.each(invalidIdCases)(
      "should return 400, not 500 for $description",
      async ({ id, description }) => {
        const response = await get({
          url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/view-data`
        });

        console.log(`\n Test: ${description}`);
        console.log("Request id:", id);
        console.log("Status:", response.status);
        console.log("Response:", JSON.stringify(response.data));

        // 500 errors are not acceptable
        expect(response.status).not.toBe(500);
        // Expect 400 or 404 errors
        expect([400, 404]).toContain(response.status);
      }
    );
  });

  describe("Unsupported Interaction Type", () => {

    /**
     * Issue #1010: Unsupported interaction types should return 404 instead of 500
     */
    const unsupportedTypeCases = [
      { type: "mfa-authentication", description: "mfa-authentication endpoint" },
      { type: "consent", description: "consent endpoint" },
      { type: "unknown-type", description: "unknown type" },
      { type: "foo-bar-baz", description: "random type" },
      { type: "../password-authentication", description: "path traversal in type" },
      { type: "password-authentication/../admin", description: "path traversal attempt" },
    ];

    test.each(unsupportedTypeCases)(
      "should return 404, not 500 for $description",
      async ({ type, description }) => {
        const authId = await getValidAuthorizationId();
        expect(authId).toBeDefined();

        const response = await postWithJson({
          url: authorizationIdEndpoint.replace("{id}", authId) + type,
          body: { test: "data" }
        });

        console.log(`\n Test: ${description}`);
        console.log("Request type:", type);
        console.log("Status:", response.status);
        console.log("Response:", JSON.stringify(response.data));

        // 500 errors are not acceptable (Issue #1010)
        expect(response.status).not.toBe(500);
        // Expect 404 error
        expect(response.status).toBe(404);
      }
    );
  });

});
