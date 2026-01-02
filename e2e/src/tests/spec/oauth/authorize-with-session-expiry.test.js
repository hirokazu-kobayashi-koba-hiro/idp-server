import { describe, expect, test } from "@jest/globals";
import { postWithJson } from "../../../lib/http";
import {
  serverConfig,
  clientSecretPostClient,
  backendUrl,
} from "../../testConfig";
import {
  getAuthorizations,
  authenticateWithPassword,
  authorize,
} from "../../../api/oauthClient";

/**
 * OAuth Authorize With Session - Session Expiry Error Handling
 *
 * Issue #1009: Session expiry causes 500 error instead of 400
 *
 * Purpose:
 * - Verify that calling authorize-with-session with expired/invalid session returns 400
 * - Verify proper error message is returned
 */
describe("OAuth Authorize With Session - Session Expiry", () => {

  /**
   * Helper to create authorization request and get the authorization ID
   */
  const createAuthorizationRequest = async () => {
    const authorizationResponse = await getAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      scope: "openid profile",
      responseType: "code",
      clientId: clientSecretPostClient.clientId,
      redirectUri: clientSecretPostClient.redirectUri,
      state: "test-state",
      nonce: "test-nonce",
    });

    // Extract authorization ID from redirect URL (format: ?id=xxx)
    const location = authorizationResponse.headers.location;
    if (!location) {
      return null;
    }
    const url = new URL(location, backendUrl);
    return url.searchParams.get("id");
  };

  /**
   * Helper to call authorize-with-session endpoint
   */
  const authorizeWithSession = async (authorizationId) => {
    const endpoint = serverConfig.authorizationIdEndpoint.replace("{id}", authorizationId) + "authorize-with-session";
    return await postWithJson({
      url: endpoint,
      body: {}
    });
  };

  test("should return 400 when session does not exist", async () => {
    // Create a new authorization request (no session exists)
    const authorizationId = await createAuthorizationRequest();
    if (!authorizationId) {
      console.log("Could not get authorization ID from response");
      return;
    }

    console.log("Authorization ID:", authorizationId);

    // Call authorize-with-session without establishing a session first
    const response = await authorizeWithSession(authorizationId);

    console.log("Status:", response.status);
    console.log("Response:", JSON.stringify(response.data));

    // Should return 400 (BAD_REQUEST) not 500 (INTERNAL_SERVER_ERROR)
    expect(response.status).toBe(400);
    expect(response.data.error).toBe("invalid_request");
    expect(response.data.error_description).toBe("session not found");
  });

  test("should return 400 when session user is null (fresh session without authentication)", async () => {
    // Create a new authorization request
    const authorizationId = await createAuthorizationRequest();
    if (!authorizationId) {
      console.log("Could not get authorization ID from response");
      return;
    }

    console.log("Authorization ID:", authorizationId);

    // Immediately try to authorize-with-session (session exists but user is null)
    const response = await authorizeWithSession(authorizationId);

    console.log("Status:", response.status);
    console.log("Response:", JSON.stringify(response.data));

    // Should return 400 (BAD_REQUEST) not 500 (INTERNAL_SERVER_ERROR)
    expect(response.status).toBe(400);
    expect(response.data.error).toBe("invalid_request");
    expect(response.data.error_description).toBe("session not found");
  });

  test("should return 200 when valid session exists with authenticated user", async () => {
    // Create authorization request
    const authorizationResponse = await getAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      scope: "openid profile",
      responseType: "code",
      clientId: clientSecretPostClient.clientId,
      redirectUri: clientSecretPostClient.redirectUri,
      state: "test-state",
      nonce: "test-nonce",
    });

    const location = authorizationResponse.headers.location;
    if (!location) {
      console.log("Could not get authorization location");
      return;
    }
    const authorizationId = new URL(location, backendUrl).searchParams.get("id");

    if (!authorizationId) {
      console.log("Could not get authorization ID");
      return;
    }

    // Authenticate with password to establish session
    const authEndpoint = serverConfig.authorizationIdEndpoint.replace("{id}", authorizationId);
    const authResponse = await authenticateWithPassword({
      endpoint: authEndpoint + "password",
      id: authorizationId,
      body: {
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password
      }
    });

    console.log("Authentication status:", authResponse.status);

    if (authResponse.status !== 200) {
      console.log("Authentication failed:", authResponse.data);
      return;
    }

    // Authorize (this establishes the session with user)
    const authorizeResponse = await authorize({
      endpoint: serverConfig.authorizeEndpoint,
      id: authorizationId,
      body: {}
    });

    console.log("First authorize status:", authorizeResponse.status);

    // Now create a new authorization request
    const newAuthResponse = await getAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      scope: "openid profile",
      responseType: "code",
      clientId: clientSecretPostClient.clientId,
      redirectUri: clientSecretPostClient.redirectUri,
      state: "test-state-2",
      nonce: "test-nonce-2",
    });

    const newLocation = newAuthResponse.headers.location;
    if (!newLocation) {
      console.log("Could not get new authorization location");
      return;
    }
    const newAuthorizationId = new URL(newLocation, backendUrl).searchParams.get("id");

    if (!newAuthorizationId) {
      console.log("Could not get new authorization ID");
      return;
    }

    // authorize-with-session should work with valid session
    const sessionResponse = await authorizeWithSession(newAuthorizationId);
    console.log("authorize-with-session status:", sessionResponse.status);
    console.log("authorize-with-session response:", JSON.stringify(sessionResponse.data));

    // With valid session, should return 200 (success)
    expect(sessionResponse.status).toBe(200);
    expect(sessionResponse.data.redirect_uri).toBeDefined();
  });

});
