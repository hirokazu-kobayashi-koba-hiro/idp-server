import { describe, expect, it } from "@jest/globals";
import { clientSecretPostClient, serverConfig } from "../testConfig";
import axios from "axios";

/**
 * Session Fixation Attack Prevention Test - Password Authentication Focus
 *
 * This test verifies that the system is protected against session fixation attacks.
 *
 * Session Cookie Design:
 * - IDP_AUTH_SESSION: Binds authentication transaction to browser (CSRF protection)
 * - IDP_IDENTITY: Issued after successful authentication (SSO session)
 *
 * Security Properties:
 * 1. IDP_AUTH_SESSION is scoped to tenant path (/{tenantId}/)
 * 2. IDP_IDENTITY is only issued after successful authentication
 * 3. An attacker cannot "fix" a session for a victim to use
 *
 * Related Issue: #736
 */
describe("Security: Session Fixation Prevention at Password Authentication (Issue #736)", () => {
  const getCookie = (response, cookieName) => {
    const setCookieHeader = response.headers["set-cookie"];
    if (!setCookieHeader) {
      return null;
    }

    const cookies = Array.isArray(setCookieHeader)
      ? setCookieHeader
      : [setCookieHeader];
    const cookie = cookies.find((c) => c.startsWith(`${cookieName}=`));

    if (!cookie) {
      return null;
    }

    const match = cookie.match(new RegExp(`${cookieName}=([^;]+)`));
    return match ? match[1] : null;
  };

  it("Session fixation attack must be prevented - IDP_IDENTITY only issued after authentication", async () => {
    console.log("\n" + "=".repeat(80));
    console.log("SESSION FIXATION PREVENTION TEST: Password Authentication");
    console.log("=".repeat(80) + "\n");

    // =====================================================================
    // Step 1: Start authorization flow - check cookies issued
    // =====================================================================
    console.log("📋 Step 1: Start authorization flow");
    console.log("-".repeat(80));

    const initialResponse = await axios.get(serverConfig.authorizationEndpoint, {
      params: {
        client_id: clientSecretPostClient.clientId,
        response_type: "code",
        scope: clientSecretPostClient.scope,
        redirect_uri: clientSecretPostClient.redirectUri,
        state: "test-session",
      },
      maxRedirects: 0,
      validateStatus: () => true,
    });

    const authSessionCookie = getCookie(initialResponse, "IDP_AUTH_SESSION");
    const identityCookieBefore = getCookie(initialResponse, "IDP_IDENTITY");

    console.log("   IDP_AUTH_SESSION:", authSessionCookie ? "issued" : "(none)");
    console.log("   IDP_IDENTITY:", identityCookieBefore || "(none - correct, not authenticated yet)");
    console.log("   Status:", initialResponse.status);

    // IDP_IDENTITY should NOT be issued before authentication
    expect(identityCookieBefore).toBeNull();

    // Extract the authentication ID from the redirect location
    const location = initialResponse.headers.location;
    const authIdMatch = location.match(/[?&]id=([^&]+)/);
    expect(authIdMatch).not.toBeNull();
    const authId = authIdMatch[1];
    console.log("   Authentication ID:", authId);

    // =====================================================================
    // Step 2: Perform password authentication
    // =====================================================================
    console.log("\n📋 Step 2: Perform password authentication");
    console.log("-".repeat(80));

    const passwordAuthEndpoint =
      serverConfig.authorizationIdEndpoint.replace("{id}", authId) +
      "password-authentication";

    const headers = {
      "Content-Type": "application/json",
    };
    if (authSessionCookie) {
      headers.Cookie = `IDP_AUTH_SESSION=${authSessionCookie}`;
      console.log("   Using IDP_AUTH_SESSION cookie");
    }

    const passwordAuthResponse = await axios.post(
      passwordAuthEndpoint,
      {
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
      },
      {
        headers,
        maxRedirects: 0,
        validateStatus: () => true,
      }
    );

    console.log("   Status:", passwordAuthResponse.status);
    expect(passwordAuthResponse.status).toBe(200);

    // IDP_IDENTITY is issued after successful authentication (in interact method)
    const identityCookieAfterAuth = getCookie(passwordAuthResponse, "IDP_IDENTITY");
    console.log("   IDP_IDENTITY after auth:", identityCookieAfterAuth ? "issued (SSO session)" : "(none)");

    // =====================================================================
    // Step 3: Complete authorization flow
    // =====================================================================
    console.log("\n📋 Step 3: Complete authorization flow");
    console.log("-".repeat(80));

    const authorizeHeaders = {
      "Content-Type": "application/json",
    };
    if (authSessionCookie) {
      authorizeHeaders.Cookie = `IDP_AUTH_SESSION=${authSessionCookie}`;
    }
    // Include IDP_IDENTITY cookie if issued
    if (identityCookieAfterAuth) {
      authorizeHeaders.Cookie = authorizeHeaders.Cookie
        ? `${authorizeHeaders.Cookie}; IDP_IDENTITY=${identityCookieAfterAuth}`
        : `IDP_IDENTITY=${identityCookieAfterAuth}`;
    }

    const authorizeResponse = await axios.post(
      serverConfig.authorizeEndpoint.replace("{id}", authId),
      {},
      {
        headers: authorizeHeaders,
        maxRedirects: 0,
        validateStatus: () => true,
      }
    );

    // Check if IDP_IDENTITY is also returned from authorize (might be refreshed)
    const identityCookieFromAuthorize = getCookie(authorizeResponse, "IDP_IDENTITY");
    // Use the cookie from auth response or authorize response
    const identityCookieAfter = identityCookieAfterAuth || identityCookieFromAuthorize;

    console.log("   Status:", authorizeResponse.status);
    console.log("   IDP_IDENTITY from auth:", identityCookieAfterAuth ? "issued" : "(none)");
    console.log("   IDP_IDENTITY from authorize:", identityCookieFromAuthorize ? "issued" : "(none)");
    console.log("   Redirect URI:", authorizeResponse.data?.redirect_uri);

    // =====================================================================
    // Security Analysis
    // =====================================================================
    console.log("\n" + "=".repeat(80));
    console.log("🔍 SECURITY ANALYSIS");
    console.log("=".repeat(80));
    console.log("\nCookie Lifecycle:");
    console.log("   1. Before authentication (authorization request):");
    console.log("      - IDP_AUTH_SESSION:", authSessionCookie ? "issued (transaction binding)" : "(none)");
    console.log("      - IDP_IDENTITY:", identityCookieBefore || "(none - SECURE)");
    console.log("   2. After password authentication (interact):");
    console.log("      - IDP_IDENTITY:", identityCookieAfterAuth ? "issued (SSO session)" : "(none)");
    console.log("   3. After authorization complete:");
    console.log("      - IDP_IDENTITY:", identityCookieFromAuthorize ? "refreshed" : "(using existing)");

    // =====================================================================
    // SECURITY ASSESSMENT
    // =====================================================================
    console.log("\n" + "=".repeat(80));
    console.log("✅ VULNERABILITY ASSESSMENT");
    console.log("=".repeat(80));

    /*
     * Session Fixation Prevention:
     *
     * The IDP_IDENTITY cookie (SSO session) is ONLY issued after successful
     * authentication. An attacker cannot:
     * 1. Obtain a valid IDP_IDENTITY before victim authenticates
     * 2. "Fix" a session that the victim will use
     *
     * The IDP_AUTH_SESSION cookie is:
     * - Scoped to /{tenantId}/ path
     * - Only used during the authentication transaction
     * - Not a session identifier that persists after auth
     */

    if (!identityCookieBefore && identityCookieAfter) {
      console.log("\n✅ SECURE: IDP_IDENTITY only issued after authentication");
      console.log("   - Attacker cannot obtain authenticated session before victim logs in");
      console.log("   - Session fixation attack is not possible");
      console.log("\nProtection Method: Post-authentication session creation");
    } else if (identityCookieBefore) {
      console.log("\n❌ VULNERABLE: IDP_IDENTITY issued before authentication!");
      throw new Error(
        "SECURITY VULNERABILITY: IDP_IDENTITY cookie was issued before authentication. " +
        "This enables session fixation attacks."
      );
    }

    console.log("\n" + "=".repeat(80));
    console.log("END OF SECURITY TEST");
    console.log("=".repeat(80) + "\n");

    // =====================================================================
    // Test Assertions
    // =====================================================================

    // IDP_IDENTITY must NOT exist before authentication
    expect(identityCookieBefore).toBeNull();

    // Authorization should succeed
    expect(authorizeResponse.status).toBe(200);
    expect(authorizeResponse.data?.redirect_uri).toBeDefined();

    // IDP_IDENTITY should be issued after successful authentication
    expect(identityCookieAfter).not.toBeNull();
  });
});
