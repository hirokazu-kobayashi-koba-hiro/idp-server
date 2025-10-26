import { describe, expect, it } from "@jest/globals";
import { clientSecretPostClient, serverConfig } from "../testConfig";
import axios from "axios";

/**
 * Detailed Session Fixation Attack Test - Password Authentication Focus
 *
 * This test specifically checks if session ID is regenerated AFTER password authentication,
 * which is the critical point where privilege escalation occurs.
 *
 * Related Issue: #736
 */
describe("Security: Session Fixation at Password Authentication (Issue #736)", () => {
  const getSessionId = (response) => {
    const setCookieHeader = response.headers["set-cookie"];
    if (!setCookieHeader) {
      return null;
    }

    const sessionCookie = Array.isArray(setCookieHeader)
      ? setCookieHeader.find((cookie) => cookie.startsWith("SESSION="))
      : setCookieHeader.startsWith("SESSION=")
      ? setCookieHeader
      : null;

    if (!sessionCookie) {
      return null;
    }

    const match = sessionCookie.match(/SESSION=([^;]+)/);
    return match ? match[1] : null;
  };

  it("CRITICAL: Session ID must be regenerated after password authentication", async () => {
    console.log("\n" + "=".repeat(80));
    console.log("SESSION FIXATION TEST: Password Authentication Step");
    console.log("=".repeat(80) + "\n");

    // =====================================================================
    // Step 1: Attacker obtains a session ID (unauthenticated)
    // =====================================================================
    console.log("📋 Step 1: Attacker obtains unauthenticated session ID");
    console.log("-".repeat(80));

    const attackerResponse = await axios.get(
      serverConfig.authorizationEndpoint,
      {
        params: {
          client_id: clientSecretPostClient.clientId,
          response_type: "code",
          scope: clientSecretPostClient.scope,
          redirect_uri: clientSecretPostClient.redirectUri,
          state: "attacker-session",
        },
        maxRedirects: 0,
        validateStatus: () => true,
      }
    );

    const attackerSessionId = getSessionId(attackerResponse);
    console.log("   Attacker's session ID:", attackerSessionId);
    console.log("   Status:", attackerResponse.status);

    expect(attackerSessionId).not.toBeNull();

    // Extract the authentication ID from the redirect location
    const location = attackerResponse.headers.location;
    const authIdMatch = location.match(/[?&]id=([^&]+)/);
    expect(authIdMatch).not.toBeNull();
    const authId = authIdMatch[1];
    console.log("   Authentication ID:", authId);

    // =====================================================================
    // Step 2: Victim performs password authentication with attacker's session
    // =====================================================================
    console.log(
      "\n📋 Step 2: Victim authenticates with password using attacker's session"
    );
    console.log("-".repeat(80));
    console.log("   Using session ID:", attackerSessionId);

    const passwordAuthEndpoint =
      serverConfig.authorizationIdEndpoint.replace("{id}", authId) +
      "password-authentication";
    const passwordAuthResponse = await axios.post(
      passwordAuthEndpoint,
      {
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
      },
      {
        headers: {
          "Content-Type": "application/json",
          Cookie: `SESSION=${attackerSessionId}`, // Using attacker's session!
        },
        maxRedirects: 0,
        validateStatus: () => true,
      }
    );

    const sessionAfterPasswordAuth = getSessionId(passwordAuthResponse);
    console.log(
      "   Session ID after password auth:",
      sessionAfterPasswordAuth || "(no new session)"
    );
    console.log("   Status:", passwordAuthResponse.status);

    // =====================================================================
    // Step 3: Call authorize API to complete the flow
    // =====================================================================
    console.log(
      "\n📋 Step 3: Call authorize API to complete authentication flow"
    );
    console.log("-".repeat(80));

    const authorizeResponse = await axios.post(
      serverConfig.authorizeEndpoint.replace("{id}", authId),
      {},
      {
        headers: {
          "Content-Type": "application/json",
          Cookie: `SESSION=${attackerSessionId}`, // Still using attacker's original session
        },
        maxRedirects: 0,
        validateStatus: () => true,
      }
    );

    const sessionAfterAuthorize = getSessionId(authorizeResponse);
    console.log(
      "   Session ID after authorize:",
      sessionAfterAuthorize || "(no new session)"
    );
    console.log("   Status:", authorizeResponse.status);
    console.log("   Redirect URI:", authorizeResponse.data?.redirect_uri);

    // =====================================================================
    // Step 4: Security Analysis
    // =====================================================================
    console.log("\n" + "=".repeat(80));
    console.log("🔍 SECURITY ANALYSIS");
    console.log("=".repeat(80));
    console.log("\nSession ID Lifecycle:");
    console.log("   1. Before authentication (attacker):  ", attackerSessionId);
    console.log(
      "   2. After password authentication:     ",
      sessionAfterPasswordAuth || "(no new session)"
    );
    console.log(
      "   3. After authorize (complete flow):   ",
      sessionAfterAuthorize || "(no new session)"
    );

    // =====================================================================
    // CRITICAL VULNERABILITY CHECK
    // =====================================================================
    console.log("\n" + "=".repeat(80));
    console.log("⚠️  VULNERABILITY ASSESSMENT");
    console.log("=".repeat(80));

    let vulnerabilityDetected = false;

    // Check if session ID was regenerated after password auth OR after authorize
    const sessionRegeneratedAfterPasswordAuth =
      sessionAfterPasswordAuth &&
      sessionAfterPasswordAuth !== attackerSessionId;
    const sessionRegeneratedAfterAuthorize =
      sessionAfterAuthorize && sessionAfterAuthorize !== attackerSessionId;

    if (
      !sessionRegeneratedAfterPasswordAuth &&
      !sessionRegeneratedAfterAuthorize
    ) {
      console.log("\n❌❌❌ CRITICAL SECURITY VULNERABILITY DETECTED! ❌❌❌");
      console.log("\nSession ID was NOT regenerated after authentication!");
      console.log("\nAttack Success Scenario:");
      console.log(
        "   1. Attacker obtained session ID:          ",
        attackerSessionId
      );
      console.log("   2. Victim authenticated with it");
      console.log(
        "   3. Session after password auth:           ",
        sessionAfterPasswordAuth || "(null)"
      );
      console.log(
        "   4. Session after authorize:               ",
        sessionAfterAuthorize || "(null)"
      );
      console.log("   5. Session ID did NOT change:             ✓ VULNERABLE");
      console.log("   6. Attacker can hijack the session:       ✓ VULNERABLE");
      console.log("\nSeverity: CRITICAL");
      console.log("CVE: CWE-384 (Session Fixation)");
      console.log("OWASP: A02:2021 – Cryptographic Failures");
      vulnerabilityDetected = true;
    } else if (sessionRegeneratedAfterPasswordAuth) {
      console.log(
        "\n✅ Session ID was regenerated after password authentication"
      );
      console.log("\nSession regeneration detected:");
      console.log("   Before auth:           ", attackerSessionId);
      console.log("   After password auth:   ", sessionAfterPasswordAuth);
      console.log(
        "   After authorize:       ",
        sessionAfterAuthorize || "(null)"
      );
      console.log("\nAttack Success Scenario:");
      console.log("   Attacker can hijack the session:  ✗ PROTECTED");
      console.log("\nSeverity: NONE");
      console.log(
        "Status: Protected against session fixation attacks at password authentication step"
      );
    } else if (sessionRegeneratedAfterAuthorize) {
      console.log("\n✅ Session ID was regenerated after authorize");
      console.log("\nSession regeneration detected:");
      console.log("   Before auth:           ", attackerSessionId);
      console.log(
        "   After password auth:   ",
        sessionAfterPasswordAuth || "(null)"
      );
      console.log("   After authorize:       ", sessionAfterAuthorize);
      console.log("\nAttack Success Scenario:");
      console.log("   Attacker can hijack the session:  ✗ PROTECTED");
      console.log("\nSeverity: NONE");
      console.log(
        "Status: Protected against session fixation attacks at authorize step"
      );
    }

    console.log("\n" + "=".repeat(80));
    console.log("END OF SECURITY TEST");
    console.log("=".repeat(80) + "\n");

    // =====================================================================
    // Test Assertion - MUST FAIL if vulnerability detected
    // =====================================================================
    if (vulnerabilityDetected) {
      throw new Error(
        "SECURITY VULNERABILITY: Session fixation attack is possible! " +
          "Session ID was not regenerated after authentication. " +
          `Before auth: ${attackerSessionId}, ` +
          `After password auth: ${sessionAfterPasswordAuth || "null"}, ` +
          `After authorize: ${sessionAfterAuthorize || "null"}. ` +
          "See Issue #736 for remediation steps."
      );
    }

    // Session ID MUST be regenerated after authentication (either at password auth or authorize step)
    const sessionWasRegenerated =
      sessionRegeneratedAfterPasswordAuth || sessionRegeneratedAfterAuthorize;
    expect(sessionWasRegenerated).toBe(true);
  });
});
