import { NextRequest, NextResponse } from "next/server";

const internalIssuer = process.env.IDP_SERVER_INTERNAL_ISSUER || process.env.NEXT_PUBLIC_IDP_SERVER_ISSUER;
const clientId = process.env.NEXT_PUBLIC_IDP_CLIENT_ID || "";
const clientSecret = process.env.NEXT_IDP_CLIENT_SECRET || "";
const frontendUrl = process.env.NEXT_PUBLIC_FRONTEND_URL || "";

/**
 * FIDO-UAF Device Authentication Demo Endpoint
 *
 * Demonstrates device credential features:
 * 1. FIDO-UAF registration with device_secret issuance
 * 2. Device endpoint authentication with device_secret_jwt
 * 3. JWT Bearer Grant with device_secret
 *
 * Flow:
 * 1. action=start: Start authorization request
 * 2. action=initial-registration: Register user with email/password
 * 3. action=fido-uaf-registration-challenge: Get FIDO-UAF registration challenge
 * 4. action=fido-uaf-registration: Complete FIDO-UAF registration (issues device_secret)
 * 5. action=authorize: Complete authorization and get tokens
 * 6. action=ciba-request: Start CIBA backchannel authentication
 * 7. action=device-authentications: Get authentication transactions (with device_secret_jwt)
 * 8. action=jwt-bearer-grant: Exchange device_secret JWT for access token
 */
export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const action = searchParams.get("action");

  try {
    if (action === "start") {
      // Start authorization request
      const state = generateRandomState();
      const codeVerifier = generateCodeVerifier();
      const codeChallenge = await generateCodeChallenge(codeVerifier);

      const response = await fetch(`${internalIssuer}/v1/authorizations`, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
        },
        redirect: "manual",
        body: new URLSearchParams({
          client_id: clientId,
          response_type: "code",
          scope: "openid profile email claims:authentication_devices",
          redirect_uri: `${frontendUrl}/api/fido-uaf-device-test/callback`,
          state,
          code_challenge: codeChallenge,
          code_challenge_method: "S256",
        }),
      });

      const setCookieHeader = response.headers.get("set-cookie");
      const locationHeader = response.headers.get("location");
      let extractedAuthorizationId: string | null = null;
      let tenantId: string | null = null;

      if (locationHeader) {
        const url = new URL(locationHeader, internalIssuer);
        extractedAuthorizationId = url.searchParams.get("id");
        tenantId = url.searchParams.get("tenant_id");
      }

      const nextResponse = NextResponse.json({
        success: !!extractedAuthorizationId,
        authorizationId: extractedAuthorizationId,
        tenantId,
        codeVerifier, // Store for token exchange
        redirectUri: locationHeader,
      });

      if (setCookieHeader) {
        const modifiedSetCookie = setCookieHeader.replace(/Path=\/[^;]+;/, "Path=/;");
        nextResponse.headers.set("Set-Cookie", modifiedSetCookie);
      }

      return nextResponse;
    }

    return NextResponse.json({
      error: "Invalid action",
      availableActions: [
        "start",
        "initial-registration",
        "fido-uaf-registration-challenge",
        "fido-uaf-registration",
        "authorize",
        "ciba-request",
        "device-authentications",
        "jwt-bearer-grant",
      ],
    });
  } catch (error) {
    return NextResponse.json(
      {
        error: "Request failed",
        message: error instanceof Error ? error.message : String(error),
      },
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
          headers: {
            "Content-Type": "application/json",
            Cookie: cookieHeader,
          },
          body: JSON.stringify(body),
        }
      );

      const responseData = await response.json().catch(() => ({}));

      return NextResponse.json({
        success: response.ok,
        status: response.status,
        data: responseData,
      });
    }

    if (action === "fido-uaf-registration-challenge" && authorizationId) {
      const body = await request.json();

      const response = await fetch(
        `${internalIssuer}/v1/authorizations/${authorizationId}/fido-uaf-registration-challenge`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Cookie: cookieHeader,
          },
          body: JSON.stringify(body),
        }
      );

      const responseData = await response.json().catch(() => ({}));

      return NextResponse.json({
        success: response.ok,
        status: response.status,
        data: responseData,
      });
    }

    if (action === "fido-uaf-registration" && authorizationId) {
      const body = await request.json();

      const response = await fetch(
        `${internalIssuer}/v1/authorizations/${authorizationId}/fido-uaf-registration`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Cookie: cookieHeader,
          },
          body: JSON.stringify(body),
        }
      );

      const responseData = await response.json().catch(() => ({}));

      // This response should contain device_secret when tenant policy has issue_device_secret: true
      return NextResponse.json({
        success: response.ok,
        status: response.status,
        data: responseData,
      });
    }

    if (action === "authorize" && authorizationId) {
      const response = await fetch(
        `${internalIssuer}/v1/authorizations/${authorizationId}/authorize`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Cookie: cookieHeader,
          },
          body: JSON.stringify({}),
        }
      );

      const responseData = await response.json().catch(() => ({}));

      return NextResponse.json({
        success: response.ok,
        status: response.status,
        data: responseData,
      });
    }

    if (action === "token-exchange") {
      const body = await request.json();
      const { code, codeVerifier, redirectUri } = body;

      const basicAuth = Buffer.from(`${clientId}:${clientSecret}`).toString("base64");
      const response = await fetch(`${internalIssuer}/v1/tokens`, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          "Authorization": `Basic ${basicAuth}`,
        },
        body: new URLSearchParams({
          grant_type: "authorization_code",
          code,
          redirect_uri: redirectUri || `${frontendUrl}/api/fido-uaf-device-test/callback`,
          code_verifier: codeVerifier,
        }),
      });

      const responseData = await response.json().catch(() => ({}));

      return NextResponse.json({
        success: response.ok,
        status: response.status,
        data: responseData,
      });
    }

    if (action === "ciba-request") {
      const body = await request.json();
      const { deviceId, userCode } = body;

      const basicAuth = Buffer.from(`${clientId}:${clientSecret}`).toString("base64");
      const response = await fetch(`${internalIssuer}/v1/backchannel/authentications`, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          "Authorization": `Basic ${basicAuth}`,
        },
        body: new URLSearchParams({
          scope: "openid profile email",
          login_hint: `device:${deviceId},idp:idp-server`,
          binding_message: "デバイス認証デモ",
          user_code: userCode,
        }),
      });

      const responseData = await response.json().catch(() => ({}));

      return NextResponse.json({
        success: response.ok,
        status: response.status,
        data: responseData,
      });
    }

    if (action === "device-authentications") {
      const body = await request.json();
      const { deviceId, deviceSecretJwt, authReqId } = body;

      const url = authReqId
        ? `${internalIssuer}/v1/authentication-devices/${deviceId}/authentications?attributes.auth_req_id=${authReqId}`
        : `${internalIssuer}/v1/authentication-devices/${deviceId}/authentications`;

      const headers: Record<string, string> = {
        "Content-Type": "application/json",
      };

      // Add device_secret_jwt authentication if provided
      if (deviceSecretJwt) {
        headers["Authorization"] = `Bearer ${deviceSecretJwt}`;
      }

      const response = await fetch(url, {
        method: "GET",
        headers,
      });

      const responseData = await response.json().catch(() => ({}));

      return NextResponse.json({
        success: response.ok,
        status: response.status,
        data: responseData,
        authenticated: !!deviceSecretJwt,
      });
    }

    if (action === "jwt-bearer-grant") {
      const body = await request.json();
      const { assertion, scope } = body;

      const basicAuth = Buffer.from(`${clientId}:${clientSecret}`).toString("base64");
      const response = await fetch(`${internalIssuer}/v1/tokens`, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          "Authorization": `Basic ${basicAuth}`,
        },
        body: new URLSearchParams({
          grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
          assertion,
          scope: scope || "openid profile email",
        }),
      });

      const responseData = await response.json().catch(() => ({}));

      return NextResponse.json({
        success: response.ok,
        status: response.status,
        data: responseData,
      });
    }

    return NextResponse.json({
      error: "Invalid action or missing parameters",
    });
  } catch (error) {
    return NextResponse.json(
      {
        error: "Request failed",
        message: error instanceof Error ? error.message : String(error),
      },
      { status: 500 }
    );
  }
}

// Helper functions
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
