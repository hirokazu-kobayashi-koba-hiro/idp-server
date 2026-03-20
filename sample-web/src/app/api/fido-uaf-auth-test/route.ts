import { NextRequest, NextResponse } from "next/server";

const internalIssuer = process.env.IDP_SERVER_INTERNAL_ISSUER || process.env.NEXT_PUBLIC_IDP_SERVER_ISSUER;
const clientId = process.env.NEXT_PUBLIC_IDP_CLIENT_ID || "";
const clientSecret = process.env.NEXT_IDP_CLIENT_SECRET || "";
const frontendUrl = process.env.NEXT_PUBLIC_FRONTEND_URL || "";

/**
 * FIDO-UAF Authorization Code Flow Demo Endpoint
 *
 * Demonstrates login_hint user resolution + authentication-status API + FIDO-UAF auth.
 *
 * Flow:
 * Phase 1 (Setup): Register user + FIDO-UAF device
 *   action=start → initial-registration → fido-uaf-registration-challenge → fido-uaf-registration → authorize → token-exchange
 *
 * Phase 2 (Demo): Authorization Code Flow with login_hint
 *   action=start-with-login-hint → view-data → authentication-status → authentication-device-notification → password-authentication → authentication-status → authorize → token-exchange
 */
export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const action = searchParams.get("action");

  try {
    if (action === "start" || action === "start-with-login-hint") {
      const state = generateRandomState();
      const codeVerifier = generateCodeVerifier();
      const codeChallenge = await generateCodeChallenge(codeVerifier);
      const loginHint = searchParams.get("loginHint") || "";

      const params: Record<string, string> = {
        client_id: clientId,
        response_type: "code",
        scope: "openid profile email claims:authentication_devices",
        redirect_uri: `${frontendUrl}/api/fido-uaf-auth-test/callback`,
        state,
        code_challenge: codeChallenge,
        code_challenge_method: "S256",
      };

      if (loginHint) {
        params.login_hint = loginHint;
      }

      const response = await fetch(`${internalIssuer}/v1/authorizations`, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        redirect: "manual",
        body: new URLSearchParams(params),
      });

      const setCookieHeader = response.headers.get("set-cookie");
      const locationHeader = response.headers.get("location");
      let extractedAuthorizationId: string | null = null;

      if (locationHeader) {
        const url = new URL(locationHeader, internalIssuer);
        extractedAuthorizationId = url.searchParams.get("id");
      }

      const nextResponse = NextResponse.json({
        success: !!extractedAuthorizationId,
        authorizationId: extractedAuthorizationId,
        codeVerifier,
      });

      if (setCookieHeader) {
        const modifiedSetCookie = setCookieHeader.replace(/Path=\/[^;]+;/, "Path=/;");
        nextResponse.headers.set("Set-Cookie", modifiedSetCookie);
      }

      return nextResponse;
    }

    if (action === "view-data") {
      const authorizationId = searchParams.get("authorizationId");
      if (!authorizationId) {
        return NextResponse.json({ error: "authorizationId required" }, { status: 400 });
      }

      const cookieHeader = request.headers.get("cookie") || "";
      const response = await fetch(
        `${internalIssuer}/v1/authorizations/${authorizationId}/view-data`,
        {
          headers: { Cookie: cookieHeader },
        }
      );

      const data = await response.json().catch(() => ({}));
      return NextResponse.json({ success: response.ok, status: response.status, data });
    }

    if (action === "authentication-status") {
      const authorizationId = searchParams.get("authorizationId");
      if (!authorizationId) {
        return NextResponse.json({ error: "authorizationId required" }, { status: 400 });
      }

      const cookieHeader = request.headers.get("cookie") || "";
      const response = await fetch(
        `${internalIssuer}/v1/authorizations/${authorizationId}/authentication-status`,
        {
          headers: { Cookie: cookieHeader },
        }
      );

      const data = await response.json().catch(() => ({}));
      return NextResponse.json({ success: response.ok, status: response.status, data });
    }

    return NextResponse.json({ error: "Invalid action" });
  } catch (error) {
    return NextResponse.json(
      { error: "Request failed", message: error instanceof Error ? error.message : String(error) },
      { status: 500 }
    );
  }
}

export async function POST(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const action = searchParams.get("action");
  const authorizationId = searchParams.get("authorizationId");

  try {
    const cookieHeader = request.headers.get("cookie") || "";

    if (action === "initial-registration" && authorizationId) {
      const body = await request.json();
      const response = await fetch(
        `${internalIssuer}/v1/authorizations/${authorizationId}/initial-registration`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json", Cookie: cookieHeader },
          body: JSON.stringify(body),
        }
      );
      const data = await response.json().catch(() => ({}));
      return NextResponse.json({ success: response.ok, status: response.status, data });
    }

    if (action === "fido-uaf-registration-challenge" && authorizationId) {
      const body = await request.json();
      const response = await fetch(
        `${internalIssuer}/v1/authorizations/${authorizationId}/fido-uaf-registration-challenge`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json", Cookie: cookieHeader },
          body: JSON.stringify(body),
        }
      );
      const data = await response.json().catch(() => ({}));
      return NextResponse.json({ success: response.ok, status: response.status, data });
    }

    if (action === "fido-uaf-registration" && authorizationId) {
      const body = await request.json();
      const response = await fetch(
        `${internalIssuer}/v1/authorizations/${authorizationId}/fido-uaf-registration`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json", Cookie: cookieHeader },
          body: JSON.stringify(body),
        }
      );
      const data = await response.json().catch(() => ({}));
      return NextResponse.json({ success: response.ok, status: response.status, data });
    }

    if (action === "password-authentication" && authorizationId) {
      const body = await request.json();
      const response = await fetch(
        `${internalIssuer}/v1/authorizations/${authorizationId}/password-authentication`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json", Cookie: cookieHeader },
          body: JSON.stringify(body),
        }
      );
      const data = await response.json().catch(() => ({}));
      return NextResponse.json({ success: response.ok, status: response.status, data });
    }

    if (action === "authentication-device-notification" && authorizationId) {
      const response = await fetch(
        `${internalIssuer}/v1/authorizations/${authorizationId}/authentication-device-notification`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json", Cookie: cookieHeader },
          body: JSON.stringify({}),
        }
      );
      const data = await response.json().catch(() => ({}));
      return NextResponse.json({ success: response.ok, status: response.status, data });
    }

    if (action === "device-authentications") {
      const body = await request.json();
      const { deviceId, authorizationId: authzId, deviceSecretJwt } = body;

      const params = new URLSearchParams();
      if (authzId) params.set("authorization_id", authzId);

      const headers: Record<string, string> = { "Content-Type": "application/json" };
      if (deviceSecretJwt) {
        headers["Authorization"] = `Bearer ${deviceSecretJwt}`;
      }

      const response = await fetch(
        `${internalIssuer}/v1/authentication-devices/${deviceId}/authentications?${params.toString()}`,
        { headers }
      );
      const data = await response.json().catch(() => ({}));
      return NextResponse.json({ success: response.ok, status: response.status, data });
    }

    if (action === "fido-uaf-auth-challenge") {
      const body = await request.json();
      const { transactionId } = body;

      const response = await fetch(
        `${internalIssuer}/v1/authentications/${transactionId}/fido-uaf-authentication-challenge`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({}),
        }
      );
      const data = await response.json().catch(() => ({}));
      return NextResponse.json({ success: response.ok, status: response.status, data });
    }

    if (action === "fido-uaf-auth") {
      const body = await request.json();
      const { transactionId } = body;

      const response = await fetch(
        `${internalIssuer}/v1/authentications/${transactionId}/fido-uaf-authentication`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({}),
        }
      );
      const data = await response.json().catch(() => ({}));
      return NextResponse.json({ success: response.ok, status: response.status, data });
    }

    if (action === "update-device") {
      const body = await request.json();
      const { accessToken, deviceIdToUpdate, notificationChannel, notificationToken } = body;

      const response = await fetch(
        `${internalIssuer}/v1/me/authentication-devices/${deviceIdToUpdate}`,
        {
          method: "PATCH",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${accessToken}`,
          },
          body: JSON.stringify({
            notification_channel: notificationChannel,
            notification_token: notificationToken,
          }),
        }
      );
      const data = await response.json().catch(() => ({}));
      return NextResponse.json({ success: response.ok, status: response.status, data });
    }

    if (action === "authorize" && authorizationId) {
      const response = await fetch(
        `${internalIssuer}/v1/authorizations/${authorizationId}/authorize`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json", Cookie: cookieHeader },
          body: JSON.stringify({}),
        }
      );
      const data = await response.json().catch(() => ({}));
      return NextResponse.json({ success: response.ok, status: response.status, data });
    }

    if (action === "token-exchange") {
      const body = await request.json();
      const { code, codeVerifier } = body;
      const basicAuth = Buffer.from(`${clientId}:${clientSecret}`).toString("base64");
      const response = await fetch(`${internalIssuer}/v1/tokens`, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          Authorization: `Basic ${basicAuth}`,
        },
        body: new URLSearchParams({
          grant_type: "authorization_code",
          code,
          redirect_uri: `${frontendUrl}/api/fido-uaf-auth-test/callback`,
          code_verifier: codeVerifier,
        }),
      });
      const data = await response.json().catch(() => ({}));
      return NextResponse.json({ success: response.ok, status: response.status, data });
    }

    return NextResponse.json({ error: "Invalid action or missing parameters" });
  } catch (error) {
    return NextResponse.json(
      { error: "Request failed", message: error instanceof Error ? error.message : String(error) },
      { status: 500 }
    );
  }
}

function generateRandomState(): string {
  const array = new Uint8Array(32);
  crypto.getRandomValues(array);
  return Array.from(array, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function generateCodeVerifier(): string {
  const array = new Uint8Array(32);
  crypto.getRandomValues(array);
  return base64UrlEncode(array);
}

async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const hash = await crypto.subtle.digest("SHA-256", data);
  return base64UrlEncode(new Uint8Array(hash));
}

function base64UrlEncode(buffer: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < buffer.length; i++) {
    binary += String.fromCharCode(buffer[i]);
  }
  const base64 = btoa(binary);
  return base64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");
}
