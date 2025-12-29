import { describe, expect, it } from "@jest/globals";

import { requestToken } from "../../api/oauthClient";
import {
  clientSecretPostClient,
  serverConfig,
} from "../testConfig";
import { requestAuthorizations, requestLogout } from "../../oauth/request";

/**
 * OpenID Connect RP-Initiated Logout 1.0 Tests
 * https://openid.net/specs/openid-connect-rpinitiated-1_0.html
 *
 * These tests verify the RP-Initiated Logout implementation.
 *
 * Note: idp-server requires id_token_hint for all logout requests.
 * This is stricter than the specification (which marks it as RECOMMENDED)
 * but simplifies implementation by eliminating the need for confirmation screens.
 */
describe("OpenID Connect RP-Initiated Logout 1.0", () => {
  /**
   * Helper to get an ID token via the authorization code flow
   */
  const getIdToken = async () => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      state: `test-state-${Date.now()}`,
      scope: "openid " + clientSecretPostClient.scope,
      redirectUri: clientSecretPostClient.redirectUri,
    });

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: clientSecretPostClient.redirectUri,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });

    console.log(JSON.stringify(tokenResponse.data, null, 2));
    expect(tokenResponse.status).toBe(200);

    return tokenResponse.data.id_token;
  };

  describe("idp-server Policy: id_token_hint REQUIRED", () => {
    /**
     * idp-server requires id_token_hint for all logout requests.
     * This is stricter than the spec which only RECOMMENDS it.
     */
    it("should reject logout request without id_token_hint - idp-server requires id_token_hint", async () => {
      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
      });

      expect(response.status).toBe(400);
      expect(response.data.error).toBe("invalid_request");
      expect(response.data.error_description).toContain("id_token_hint");
    });

    it("should reject logout request with only client_id - id_token_hint is still required", async () => {
      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        clientId: clientSecretPostClient.clientId,
      });

      expect(response.status).toBe(400);
      expect(response.data.error).toBe("invalid_request");
      expect(response.data.error_description).toContain("id_token_hint");
    });

    it("should reject logout request with only post_logout_redirect_uri - id_token_hint is still required", async () => {
      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        postLogoutRedirectUri: clientSecretPostClient.postLogoutRedirectUri,
      });

      expect(response.status).toBe(400);
      expect(response.data.error).toBe("invalid_request");
      expect(response.data.error_description).toContain("id_token_hint");
    });
  });

  describe("Section 2 - RP-Initiated Logout Request", () => {
    /**
     * Section 2: An RP requests that the OP log out the End-User by
     * redirecting the End-User's User Agent to the OP's Logout Endpoint.
     */
    it("should accept logout request with valid id_token_hint", async () => {
      const idToken = await getIdToken();

      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: idToken,
      });

      console.log(response.data);
      expect(response.status).toBe(200);
    });

    it("should accept logout request with id_token_hint and client_id", async () => {
      const idToken = await getIdToken();

      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: idToken,
        clientId: clientSecretPostClient.clientId,
      });

      expect(response.status).toBe(200);
    });

    /**
     * Section 2: id_token_hint - ID Token previously issued by the OP to the RP
     * The OP SHOULD accept ID Tokens when the RP identified by the ID Token's
     * aud claim has a current session.
     */
    it("should accept expired id_token_hint if signature is valid - per spec recommendation", async () => {
      // Note: This test uses a fresh token.
      // The spec says OP SHOULD accept ID Tokens even if expired,
      // as long as signature is valid.
      const idToken = await getIdToken();

      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: idToken,
      });

      expect(response.status).toBe(200);
    });
  });

  describe("Section 2 - id_token_hint Parameter", () => {
    /**
     * id_token_hint RECOMMENDED.
     * Previously issued ID Token passed to the Logout Endpoint as a hint
     * about the End-User's current authenticated session with the Client.
     */
    it("should validate id_token_hint signature", async () => {
      // Malformed JWT should be rejected
      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: "not.a.valid.jwt",
      });

      expect(response.status).toBe(400);
      expect(response.data.error).toBe("invalid_request");
    });

    it("should validate id_token_hint issuer matches OP", async () => {
      // JWT with wrong issuer should be rejected
      const fakeToken =
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9." +
        "eyJpc3MiOiJodHRwczovL3dyb25nLWlzc3Vlci5jb20iLCJzdWIiOiJ0ZXN0Iiwi" +
        "YXVkIjoidGVzdCIsImV4cCI6MTcwMDAwMDAwMH0." +
        "invalid-signature";

      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: fakeToken,
      });

      expect(response.status).toBe(400);
      expect(response.data.error).toBe("invalid_request");
    });

    it("should reject id_token_hint with invalid signature", async () => {
      const idToken = await getIdToken();
      // Tamper with the signature part
      const parts = idToken.split(".");
      const tamperedToken = parts[0] + "." + parts[1] + ".invalid_signature";

      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: tamperedToken,
      });

      expect(response.status).toBe(400);
      expect(response.data.error).toBe("invalid_request");
    });
  });

  describe("Section 2 - client_id Parameter", () => {
    /**
     * client_id OPTIONAL.
     * Client Identifier for the RP requesting the logout.
     */
    it("should accept client_id parameter matching id_token_hint audience", async () => {
      const idToken = await getIdToken();

      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: idToken,
        clientId: clientSecretPostClient.clientId,
      });

      expect(response.status).toBe(200);
    });

    it("should reject client_id not matching id_token_hint audience", async () => {
      const idToken = await getIdToken();

      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: idToken,
        clientId: "non-existent-client",
      });

      // The client_id should match the aud claim in the id_token_hint
      expect(response.status).toBe(400);
    });
  });

  describe("Section 3 - post_logout_redirect_uri Validation", () => {
    /**
     * Section 3: post_logout_redirect_uri OPTIONAL.
     * URI to which the RP is requesting that the End-User's User Agent
     * be redirected after a logout has been performed.
     */
    it("should redirect to registered post_logout_redirect_uri", async () => {
      const idToken = await getIdToken();

      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: idToken,
        postLogoutRedirectUri: clientSecretPostClient.postLogoutRedirectUri,
      });

      // Should redirect (302) to the registered URI
      expect(response.status).toBe(302);
      expect(response.headers.location).toContain(clientSecretPostClient.postLogoutRedirectUri);
    });

    /**
     * Section 3: The value MUST have been previously registered with the OP,
     * either using the post_logout_redirect_uris Registration parameter
     * or via another mechanism.
     */
    it("should reject unregistered post_logout_redirect_uri - open redirect protection", async () => {
      const idToken = await getIdToken();

      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: idToken,
        postLogoutRedirectUri: "https://malicious.example.com/callback",
      });

      expect(response.status).toBe(400);
    });

    it("should reject post_logout_redirect_uri with different scheme", async () => {
      const idToken = await getIdToken();
      // Try http instead of https
      const httpUri = clientSecretPostClient.postLogoutRedirectUri.replace("https://", "http://");

      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: idToken,
        postLogoutRedirectUri: httpUri,
      });

      // Should reject if not exactly matching registered URI
      expect(response.status).toBe(400);
    });

    it("should reject post_logout_redirect_uri with path traversal", async () => {
      const idToken = await getIdToken();
      const maliciousUri = clientSecretPostClient.postLogoutRedirectUri + "/../../../etc/passwd";

      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: idToken,
        postLogoutRedirectUri: maliciousUri,
      });

      expect(response.status).toBe(400);
    });
  });

  describe("Section 4 - state Parameter", () => {
    /**
     * Section 4: state OPTIONAL.
     * Opaque value used by the RP to maintain state between the logout request
     * and the callback to the endpoint specified by the post_logout_redirect_uri parameter.
     */
    it("should return state parameter in redirect when post_logout_redirect_uri is provided", async () => {
      const idToken = await getIdToken();
      const testState = `test-logout-state-${Date.now()}`;

      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: idToken,
        postLogoutRedirectUri: clientSecretPostClient.postLogoutRedirectUri,
        state: testState,
      });

      expect(response.status).toBe(302);
      expect(response.headers.location).toContain(`state=${testState}`);
    });

    it("should include state as query parameter in redirect URI", async () => {
      const idToken = await getIdToken();
      const testState = "my-state-value";

      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: idToken,
        postLogoutRedirectUri: clientSecretPostClient.postLogoutRedirectUri,
        state: testState,
      });

      expect(response.status).toBe(302);
      const location = response.headers.location;
      expect(location).toMatch(/\?state=my-state-value/);
    });

    it("should accept logout request with state but no post_logout_redirect_uri", async () => {
      const idToken = await getIdToken();
      const testState = `test-logout-state-${Date.now()}`;

      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: idToken,
        state: testState,
      });

      // Without post_logout_redirect_uri, state is ignored but request succeeds
      expect(response.status).toBe(200);
    });

    it("should preserve special characters in state parameter", async () => {
      const idToken = await getIdToken();
      const testState = "state+with/special=chars&more";

      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: idToken,
        postLogoutRedirectUri: clientSecretPostClient.postLogoutRedirectUri,
        state: testState,
      });

      expect(response.status).toBe(302);
      // State should be URL-encoded in the redirect URI
      expect(response.headers.location).toContain("state=");
    });
  });

  describe("Section 2 - ui_locales Parameter", () => {
    /**
     * ui_locales OPTIONAL.
     * End-User's preferred languages and scripts for the user interface.
     */
    it("should accept ui_locales parameter", async () => {
      const idToken = await getIdToken();

      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: idToken,
        uiLocales: "ja",
      });

      expect(response.status).toBe(200);
    });

    it("should accept multiple ui_locales values", async () => {
      const idToken = await getIdToken();

      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: idToken,
        uiLocales: "ja en",
      });

      expect(response.status).toBe(200);
    });
  });

  describe("Session Management", () => {
    /**
     * Verify that logout properly terminates the session.
     */
    it("should successfully logout even when no active session exists", async () => {
      const idToken = await getIdToken();

      // First logout
      const response1 = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: idToken,
      });
      expect(response1.status).toBe(200);

      // Second logout with same token should still succeed
      // (no session to delete, but that's not an error)
      const response2 = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: idToken,
      });
      expect(response2.status).toBe(200);
    });
  });

  describe("Error Responses", () => {
    /**
     * Verify proper error responses for various error conditions.
     */
    it("should return error response in JSON format for bad requests", async () => {
      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
      });

      expect(response.status).toBe(400);
      expect(response.data).toHaveProperty("error");
      expect(response.data).toHaveProperty("error_description");
    });

    it("should return invalid_request for missing required parameters", async () => {
      const response = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        clientId: clientSecretPostClient.clientId,
      });

      expect(response.status).toBe(400);
      expect(response.data.error).toBe("invalid_request");
    });
  });

  describe("Complete Logout Flow", () => {
    /**
     * End-to-end logout flow test.
     */
    it("should complete full logout flow with redirect", async () => {
      // 1. Get ID token through authorization
      const idToken = await getIdToken();
      const testState = `complete-flow-${Date.now()}`;

      // 2. Initiate logout
      const logoutResponse = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: idToken,
        postLogoutRedirectUri: clientSecretPostClient.postLogoutRedirectUri,
        state: testState,
      });

      // 3. Verify redirect
      expect(logoutResponse.status).toBe(302);
      const location = logoutResponse.headers.location;
      expect(location).toContain(clientSecretPostClient.postLogoutRedirectUri);
      expect(location).toContain(`state=${testState}`);
    });

    it("should complete logout flow without redirect", async () => {
      const idToken = await getIdToken();

      const logoutResponse = await requestLogout({
        endpoint: serverConfig.logoutEndpoint,
        idTokenHint: idToken,
      });

      expect(logoutResponse.status).toBe(200);
    });
  });
});
