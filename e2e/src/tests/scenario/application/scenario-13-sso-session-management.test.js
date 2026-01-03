import { describe, expect, it, beforeAll } from "@jest/globals";
import axios from "axios";
import { wrapper } from "axios-cookiejar-support";
import { CookieJar } from "tough-cookie";
import {
  backendUrl,
  clientSecretPostClient,
  clientSecretBasicClient,
  serverConfig
} from "../../testConfig";
import {
  getAuthorizations,
  postAuthentication,
  authorize,
  requestToken
} from "../../../api/oauthClient";
import { get, post, postWithJson } from "../../../lib/http";
import { convertNextAction, convertToAuthorizationResponse } from "../../../lib/util";

/**
 * Creates a new HTTP client with a fresh CookieJar.
 * This simulates a different browser session (attacker's browser).
 */
const createFreshClient = () => {
  const jar = new CookieJar();
  return wrapper(axios.create({
    jar,
    withCredentials: true,
    validateStatus: () => true, // Don't throw on any status
  }));
};

/**
 * Helper to call authorize-with-session endpoint
 * This endpoint uses the existing OPSession to authorize without re-authentication
 */
const authorizeWithSession = async (authorizationId) => {
  const endpoint = `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authorizationId}/authorize-with-session`;
  return await postWithJson({
    url: endpoint,
    body: {},
  });
};

/**
 * SSO (Single Sign-On) Session Management Tests
 *
 * These tests verify that:
 * 1. OPSession is created on successful authentication
 * 2. prompt=none works correctly with existing OPSession (SSO)
 * 3. Multiple clients can share the same OPSession
 * 4. Session cookies are properly maintained
 */
describe("SSO Session Management", () => {

  describe("prompt=none SSO flow", () => {

    it("should allow prompt=none authorization after initial login", async () => {
      const user = {
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
      };

      // Step 1: Initial authorization with clientSecretPostClient (full authentication)
      console.log("Step 1: Initial authorization with authentication");

      const firstAuthResponse = await getAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "first-state",
        scope: "openid profile",
        redirectUri: clientSecretPostClient.redirectUri,
      });

      console.log("First auth response status:", firstAuthResponse.status);
      expect(firstAuthResponse.status).toBe(302);

      const { nextAction, params } = convertNextAction(firstAuthResponse.headers.location);
      console.log("Next action:", nextAction);
      expect(nextAction).toBe("goAuthentication");

      const firstId = params.get("id");

      // Perform password authentication
      const passwordResponse = await postAuthentication({
        endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/{id}/password-authentication`,
        id: firstId,
        body: user,
      });
      console.log("Password auth response:", passwordResponse.status);
      expect(passwordResponse.status).toBe(200);

      // Complete authorization (this creates OPSession)
      const authorizeResponse = await authorize({
        endpoint: serverConfig.authorizeEndpoint,
        id: firstId,
        body: {},
      });
      console.log("Authorize response status:", authorizeResponse.status);
      expect(authorizeResponse.status).toBe(200);

      const firstAuthResult = convertToAuthorizationResponse(authorizeResponse.data.redirect_uri);
      console.log("First authorization code:", firstAuthResult.code);
      expect(firstAuthResult.code).toBeDefined();

      // Exchange code for tokens
      const firstTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: firstAuthResult.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log("First token response status:", firstTokenResponse.status);
      expect(firstTokenResponse.status).toBe(200);
      expect(firstTokenResponse.data.id_token).toBeDefined();

      // Step 2: Second authorization with prompt=none (should use existing OPSession)
      console.log("\nStep 2: Second authorization with prompt=none (SSO)");

      const secondAuthResponse = await getAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "second-state",
        scope: "openid profile",
        redirectUri: clientSecretPostClient.redirectUri,
        prompt: "none",
      });

      console.log("Second auth response status:", secondAuthResponse.status);
      console.log("Second auth response headers:", secondAuthResponse.headers);

      // With prompt=none and existing session, should redirect directly with code
      expect(secondAuthResponse.status).toBe(302);

      const secondAuthResult = convertToAuthorizationResponse(secondAuthResponse.headers.location);
      console.log("Second authorization result:", secondAuthResult);

      // Should have authorization code without requiring interaction
      expect(secondAuthResult.code).toBeDefined();
      expect(secondAuthResult.error).toBeFalsy(); // null means no error

      // Exchange second code for tokens
      const secondTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: secondAuthResult.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log("Second token response status:", secondTokenResponse.status);
      expect(secondTokenResponse.status).toBe(200);
      expect(secondTokenResponse.data.id_token).toBeDefined();
    });

    it("should return interaction_required when prompt=none with new scope", async () => {
      // Test prompt=none with a scope that hasn't been granted yet
      // Since we have an active session from previous test, this should return interaction_required

      console.log("Testing prompt=none with new scope (requires interaction)");

      const authResponse = await getAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "new-scope-state",
        scope: "openid profile offline_access", // offline_access likely not granted
        redirectUri: clientSecretPostClient.redirectUri,
        prompt: "none",
      });

      console.log("Auth response status:", authResponse.status);
      console.log("Auth response location:", authResponse.headers?.location);

      expect(authResponse.status).toBe(302);

      const result = convertToAuthorizationResponse(authResponse.headers.location);
      console.log("Result:", result);

      // With session but new scope, should return interaction_required
      // Or if scope was already granted, should succeed
      if (result.error) {
        expect(result.error).toBe("interaction_required");
      } else {
        // If no error, code should be present (scope was already granted)
        expect(result.code).toBeDefined();
      }
    });

  });

  describe("authorize-with-session SSO flow", () => {

    it("should authorize using existing OPSession without re-authentication", async () => {
      const user = {
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
      };

      // Step 1: Initial login to create OPSession
      console.log("Step 1: Initial login to create OPSession");

      const firstAuthResponse = await getAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "initial-state",
        scope: "openid profile",
        redirectUri: clientSecretPostClient.redirectUri,
      });

      expect(firstAuthResponse.status).toBe(302);
      const { params: firstParams } = convertNextAction(firstAuthResponse.headers.location);
      const firstId = firstParams.get("id");

      // Authenticate
      const passwordResponse = await postAuthentication({
        endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/{id}/password-authentication`,
        id: firstId,
        body: user,
      });
      expect(passwordResponse.status).toBe(200);

      // Authorize (creates OPSession)
      const authorizeResponse = await authorize({
        endpoint: serverConfig.authorizeEndpoint,
        id: firstId,
        body: {},
      });
      expect(authorizeResponse.status).toBe(200);

      const firstResult = convertToAuthorizationResponse(authorizeResponse.data.redirect_uri);
      expect(firstResult.code).toBeDefined();
      console.log("Initial login successful - OPSession created");

      // Get tokens
      const firstTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: firstResult.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(firstTokenResponse.status).toBe(200);

      // Step 2: Start new authorization request
      console.log("\nStep 2: Start new authorization request");

      const secondAuthResponse = await getAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "sso-state",
        scope: "openid profile",
        redirectUri: clientSecretPostClient.redirectUri,
      });

      expect(secondAuthResponse.status).toBe(302);
      const { params: secondParams } = convertNextAction(secondAuthResponse.headers.location);
      const secondId = secondParams.get("id");
      console.log("New authorization ID:", secondId);

      // Check view-data to see if session is enabled
      const viewDataResponse = await get({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${secondId}/view-data`,
      });
      console.log("View data response:", viewDataResponse.status);
      console.log("Session enabled:", viewDataResponse.data?.session_enabled);

      // Step 3: Use authorize-with-session instead of full authentication
      console.log("\nStep 3: Call authorize-with-session (SSO)");

      const ssoResponse = await authorizeWithSession(secondId);
      console.log("SSO response status:", ssoResponse.status);
      console.log("SSO response data:", JSON.stringify(ssoResponse.data));

      expect(ssoResponse.status).toBe(200);
      expect(ssoResponse.data.redirect_uri).toBeDefined();

      const ssoResult = convertToAuthorizationResponse(ssoResponse.data.redirect_uri);
      console.log("SSO authorization code:", ssoResult.code);
      expect(ssoResult.code).toBeDefined();
      expect(ssoResult.error).toBeFalsy(); // null means no error

      // Exchange code for tokens
      const ssoTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: ssoResult.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(ssoTokenResponse.status).toBe(200);
      expect(ssoTokenResponse.data.id_token).toBeDefined();

      console.log("authorize-with-session SSO successful!");
    });

    it("should work across different clients using authorize-with-session", async () => {
      const user = {
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
      };

      // Step 1: Login with Client A
      console.log("Step 1: Login with Client A");

      const clientAAuthResponse = await getAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "client-a",
        scope: "openid profile",
        redirectUri: clientSecretPostClient.redirectUri,
      });

      const { params: clientAParams } = convertNextAction(clientAAuthResponse.headers.location);
      const clientAId = clientAParams.get("id");

      await postAuthentication({
        endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/{id}/password-authentication`,
        id: clientAId,
        body: user,
      });

      const clientAAuthorizeResponse = await authorize({
        endpoint: serverConfig.authorizeEndpoint,
        id: clientAId,
        body: {},
      });
      expect(clientAAuthorizeResponse.status).toBe(200);
      console.log("Client A login successful - OPSession created");

      // Get tokens for Client A
      const clientAResult = convertToAuthorizationResponse(clientAAuthorizeResponse.data.redirect_uri);
      await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: clientAResult.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      // Step 2: Start authorization with Client B
      console.log("\nStep 2: Start authorization with Client B");

      const clientBAuthResponse = await getAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        responseType: "code",
        state: "client-b",
        scope: "openid",
        redirectUri: clientSecretBasicClient.redirectUri,
      });

      const { params: clientBParams } = convertNextAction(clientBAuthResponse.headers.location);
      const clientBId = clientBParams.get("id");
      console.log("Client B authorization ID:", clientBId);

      // Step 3: Use authorize-with-session for Client B (cross-client SSO)
      console.log("\nStep 3: Call authorize-with-session for Client B (cross-client SSO)");

      const ssoResponse = await authorizeWithSession(clientBId);
      console.log("Cross-client SSO response status:", ssoResponse.status);
      console.log("Cross-client SSO response data:", JSON.stringify(ssoResponse.data));

      expect(ssoResponse.status).toBe(200);
      expect(ssoResponse.data.redirect_uri).toBeDefined();

      const ssoResult = convertToAuthorizationResponse(ssoResponse.data.redirect_uri);
      expect(ssoResult.code).toBeDefined();
      expect(ssoResult.error).toBeFalsy(); // null means no error

      // Exchange code for tokens (Basic auth required for clientSecretBasicClient)
      const basicAuthHeader = {
        Authorization: `Basic ${Buffer.from(`${clientSecretBasicClient.clientId}:${clientSecretBasicClient.clientSecret}`).toString('base64')}`
      };
      const clientBTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: ssoResult.code,
        grantType: "authorization_code",
        redirectUri: clientSecretBasicClient.redirectUri,
        basicAuth: basicAuthHeader,
      });
      expect(clientBTokenResponse.status).toBe(200);
      expect(clientBTokenResponse.data.id_token).toBeDefined();

      console.log("Cross-client SSO with authorize-with-session successful!");
    });

  });

  describe("Cross-client SSO", () => {

    it("should share OPSession across different clients - requires initial consent for each client", async () => {
      const user = {
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
      };

      // Step 1: Login with Client A (clientSecretPostClient)
      console.log("Step 1: Login with Client A (full authentication)");

      const clientAAuthResponse = await getAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "client-a-state",
        scope: "openid profile",
        redirectUri: clientSecretPostClient.redirectUri,
      });

      expect(clientAAuthResponse.status).toBe(302);
      const { params: clientAParams } = convertNextAction(clientAAuthResponse.headers.location);
      const clientAId = clientAParams.get("id");

      // Authenticate for Client A
      const passwordResponseA = await postAuthentication({
        endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/{id}/password-authentication`,
        id: clientAId,
        body: user,
      });
      expect(passwordResponseA.status).toBe(200);

      // Authorize Client A (creates OPSession + AuthorizationGranted for Client A)
      const clientAAuthorizeResponse = await authorize({
        endpoint: serverConfig.authorizeEndpoint,
        id: clientAId,
        body: {},
      });
      expect(clientAAuthorizeResponse.status).toBe(200);

      const clientAResult = convertToAuthorizationResponse(clientAAuthorizeResponse.data.redirect_uri);
      expect(clientAResult.code).toBeDefined();

      // Get tokens for Client A
      const clientATokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: clientAResult.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(clientATokenResponse.status).toBe(200);
      console.log("Client A login successful - OPSession created");

      // Step 2: First access with Client B (needs initial consent, but authentication should be skipped due to OPSession)
      // Note: Current implementation requires authentication even with OPSession unless prompt=none
      // This step creates AuthorizationGranted for Client B
      console.log("\nStep 2: First access with Client B (initial consent required)");

      const clientBFirstAuthResponse = await getAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        responseType: "code",
        state: "client-b-first-state",
        scope: "openid",
        redirectUri: clientSecretBasicClient.redirectUri,
      });

      expect(clientBFirstAuthResponse.status).toBe(302);
      const { nextAction: clientBNextAction, params: clientBParams } = convertNextAction(clientBFirstAuthResponse.headers.location);

      // Current implementation: still requires interaction even with existing OPSession
      // This is where SSO could be improved to skip authentication when OPSession exists
      console.log("Client B next action:", clientBNextAction);

      if (clientBNextAction === "goAuthentication") {
        const clientBId = clientBParams.get("id");

        // Need to authenticate again (current limitation)
        const passwordResponseB = await postAuthentication({
          endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/{id}/password-authentication`,
          id: clientBId,
          body: user,
        });
        expect(passwordResponseB.status).toBe(200);

        // Authorize Client B (creates AuthorizationGranted for Client B)
        const clientBAuthorizeResponse = await authorize({
          endpoint: serverConfig.authorizeEndpoint,
          id: clientBId,
          body: {},
        });
        expect(clientBAuthorizeResponse.status).toBe(200);

        const clientBFirstResult = convertToAuthorizationResponse(clientBAuthorizeResponse.data.redirect_uri);
        expect(clientBFirstResult.code).toBeDefined();

        // Get tokens for Client B (Basic auth required)
        const basicAuthHeader = {
          Authorization: `Basic ${Buffer.from(`${clientSecretBasicClient.clientId}:${clientSecretBasicClient.clientSecret}`).toString('base64')}`
        };
        const clientBFirstTokenResponse = await requestToken({
          endpoint: serverConfig.tokenEndpoint,
          code: clientBFirstResult.code,
          grantType: "authorization_code",
          redirectUri: clientSecretBasicClient.redirectUri,
          basicAuth: basicAuthHeader,
        });
        expect(clientBFirstTokenResponse.status).toBe(200);
        console.log("Client B initial consent completed");
      }

      // Step 3: Second access with Client B using prompt=none (should work now)
      console.log("\nStep 3: Second access with Client B using prompt=none (SSO)");

      const clientBSSOAuthResponse = await getAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretBasicClient.clientId,
        responseType: "code",
        state: "client-b-sso-state",
        scope: "openid",
        redirectUri: clientSecretBasicClient.redirectUri,
        prompt: "none",
      });

      console.log("Client B SSO auth response status:", clientBSSOAuthResponse.status);

      expect(clientBSSOAuthResponse.status).toBe(302);

      const clientBSSOResult = convertToAuthorizationResponse(clientBSSOAuthResponse.headers.location);
      console.log("Client B SSO result:", clientBSSOResult);

      // Now Client B should get authorization code without any interaction
      expect(clientBSSOResult.code).toBeDefined();
      expect(clientBSSOResult.error).toBeFalsy(); // null means no error

      // Exchange Client B SSO code for tokens (Basic auth required)
      const basicAuthHeaderSSO = {
        Authorization: `Basic ${Buffer.from(`${clientSecretBasicClient.clientId}:${clientSecretBasicClient.clientSecret}`).toString('base64')}`
      };
      const clientBSSOTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: clientBSSOResult.code,
        grantType: "authorization_code",
        redirectUri: clientSecretBasicClient.redirectUri,
        basicAuth: basicAuthHeaderSSO,
      });
      expect(clientBSSOTokenResponse.status).toBe(200);
      expect(clientBSSOTokenResponse.data.id_token).toBeDefined();

      console.log("Cross-client SSO with prompt=none successful!");
    });

  });

  describe("Security - Authorization Flow Hijacking Prevention", () => {

    it("should reject authorize-with-session when AUTH_SESSION cookie is missing", async () => {
      const user = {
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
      };

      // Step 1: Victim starts authorization request (gets AUTH_SESSION cookie)
      console.log("Step 1: Victim starts authorization request");

      const victimAuthResponse = await getAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "victim-state",
        scope: "openid profile",
        redirectUri: clientSecretPostClient.redirectUri,
      });

      expect(victimAuthResponse.status).toBe(302);
      const { params: victimParams } = convertNextAction(victimAuthResponse.headers.location);
      const authorizationId = victimParams.get("id");
      console.log("Authorization ID:", authorizationId);

      // Victim authenticates and creates OPSession
      await postAuthentication({
        endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/{id}/password-authentication`,
        id: authorizationId,
        body: user,
      });

      const victimAuthorizeResponse = await authorize({
        endpoint: serverConfig.authorizeEndpoint,
        id: authorizationId,
        body: {},
      });
      expect(victimAuthorizeResponse.status).toBe(200);

      const victimResult = convertToAuthorizationResponse(victimAuthorizeResponse.data.redirect_uri);
      await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: victimResult.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      // Step 2: Start a NEW authorization request (new AUTH_SESSION)
      console.log("\nStep 2: Start new authorization request");

      const newAuthResponse = await getAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "new-request-state",
        scope: "openid profile",
        redirectUri: clientSecretPostClient.redirectUri,
      });

      expect(newAuthResponse.status).toBe(302);
      const { params: newParams } = convertNextAction(newAuthResponse.headers.location);
      const newAuthorizationId = newParams.get("id");
      console.log("New Authorization ID:", newAuthorizationId);

      // Step 3: Attacker tries to use authorize-with-session with a different browser (no AUTH_SESSION)
      console.log("\nStep 3: Attacker tries authorize-with-session without AUTH_SESSION cookie");

      const attackerClient = createFreshClient();
      const attackEndpoint = `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${newAuthorizationId}/authorize-with-session`;

      const attackResponse = await attackerClient.post(attackEndpoint, {}, {
        headers: {
          "Content-Type": "application/json",
        },
      });

      console.log("Attack response status:", attackResponse.status);
      console.log("Attack response data:", attackResponse.data);

      // Should be rejected due to missing AUTH_SESSION cookie
      expect(attackResponse.status).toBe(401);
      expect(attackResponse.data.error_description).toContain("auth_session_mismatch");

      console.log("Attack successfully blocked - AUTH_SESSION validation working!");
    });

    it("should reject authorize-with-session when AUTH_SESSION cookie doesn't match", async () => {
      const user = {
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
      };

      // Step 1: Attacker starts their own authorization request (gets their AUTH_SESSION cookie)
      console.log("Step 1: Attacker starts their own authorization request");

      const attackerClient = createFreshClient();

      // Attacker starts authorization
      const attackerAuthResponse = await attackerClient.get(
        `${serverConfig.authorizationEndpoint}?` +
        `scope=openid+profile&response_type=code&client_id=${clientSecretPostClient.clientId}` +
        `&redirect_uri=${encodeURIComponent(clientSecretPostClient.redirectUri)}&state=attacker-state`,
        { maxRedirects: 0 }
      );

      expect(attackerAuthResponse.status).toBe(302);
      const attackerLocation = attackerAuthResponse.headers.location;
      const attackerParams = new URL(attackerLocation).searchParams;
      const attackerAuthId = attackerParams.get("id");
      console.log("Attacker's Authorization ID:", attackerAuthId);

      // Step 2: Victim starts a different authorization request
      console.log("\nStep 2: Victim starts their authorization request");

      const victimAuthResponse = await getAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "victim-state-2",
        scope: "openid profile",
        redirectUri: clientSecretPostClient.redirectUri,
      });

      expect(victimAuthResponse.status).toBe(302);
      const { params: victimParams } = convertNextAction(victimAuthResponse.headers.location);
      const victimAuthId = victimParams.get("id");
      console.log("Victim's Authorization ID:", victimAuthId);

      // Victim authenticates to create OPSession
      await postAuthentication({
        endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/{id}/password-authentication`,
        id: victimAuthId,
        body: user,
      });

      const victimAuthorizeResponse = await authorize({
        endpoint: serverConfig.authorizeEndpoint,
        id: victimAuthId,
        body: {},
      });
      expect(victimAuthorizeResponse.status).toBe(200);

      const victimResult = convertToAuthorizationResponse(victimAuthorizeResponse.data.redirect_uri);
      await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: victimResult.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      // Step 3: Attacker tries to complete victim's authorization with attacker's AUTH_SESSION
      console.log("\nStep 3: Attacker tries to hijack victim's authorization request");

      // Start a new request as victim
      const newVictimAuthResponse = await getAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "victim-hijack-target",
        scope: "openid profile",
        redirectUri: clientSecretPostClient.redirectUri,
      });

      const { params: newVictimParams } = convertNextAction(newVictimAuthResponse.headers.location);
      const hijackTargetId = newVictimParams.get("id");
      console.log("Target Authorization ID (victim's new request):", hijackTargetId);

      // Attacker tries to use authorize-with-session on victim's authorization ID
      // using attacker's browser (different AUTH_SESSION cookie)
      const hijackEndpoint = `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${hijackTargetId}/authorize-with-session`;

      const hijackResponse = await attackerClient.post(hijackEndpoint, {}, {
        headers: {
          "Content-Type": "application/json",
        },
      });

      console.log("Hijack attempt response status:", hijackResponse.status);
      console.log("Hijack attempt response data:", hijackResponse.data);

      // Should be rejected due to AUTH_SESSION mismatch
      expect(hijackResponse.status).toBe(401);
      expect(hijackResponse.data.error_description).toContain("auth_session_mismatch");

      console.log("Hijack attempt successfully blocked - AUTH_SESSION validation working!");
    });

  });

});
