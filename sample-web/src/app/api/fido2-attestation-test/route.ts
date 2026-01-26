import { NextRequest, NextResponse } from "next/server";

const internalIssuer = process.env.IDP_SERVER_INTERNAL_ISSUER || process.env.NEXT_PUBLIC_IDP_SERVER_ISSUER;
const clientId = process.env.NEXT_PUBLIC_IDP_CLIENT_ID || "";
const frontendUrl = process.env.NEXT_PUBLIC_FRONTEND_URL || "";

/**
 * FIDO2 Attestation Test Endpoint
 *
 * Endpoint for demonstrating and testing attestation verification in FIDO2 registration.
 * Flow:
 * 1. action=start: Start authorization request
 * 2. action=initial-registration: Register user with email/password
 * 3. action=email-challenge: Request email verification challenge
 * 4. action=email-verify: Verify email with code
 * 5. action=registration-challenge: Get FIDO2 registration challenge
 * 6. action=register: Complete FIDO2 registration
 */
export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const action = searchParams.get("action");
  const authorizationId = searchParams.get("authorizationId");

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
          scope: "openid profile email",
          redirect_uri: `${frontendUrl}/api/security-test/callback`,
          state,
          code_challenge: codeChallenge,
          code_challenge_method: "S256",
        }),
      });

      // Get Set-Cookie header from response
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
        redirectUri: locationHeader,
      });

      // Forward Set-Cookie header (change Path to / so API routes can access)
      if (setCookieHeader) {
        const modifiedSetCookie = setCookieHeader.replace(/Path=\/[^;]+;/, "Path=/;");
        nextResponse.headers.set("Set-Cookie", modifiedSetCookie);
      }

      return nextResponse;
    }

    if (action === "registration-challenge" && authorizationId) {
      const username = searchParams.get("username") || "";
      const displayName = searchParams.get("displayName") || username;
      const cookieHeader = request.headers.get("cookie") || "";

      // Get FIDO2 registration challenge (user should already be authenticated via email)
      const response = await fetch(
        `${internalIssuer}/v1/authorizations/${authorizationId}/fido2-registration-challenge`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Cookie": cookieHeader,
          },
          body: JSON.stringify({
            username: username,
            displayName: displayName,
            authenticatorSelection: {
              requireResidentKey: true,
              userVerification: "required",
            },
            attestation: "direct",
            extensions: {
              credProps: true,
            },
          }),
        }
      );

      if (!response.ok) {
        const errorText = await response.text();
        return NextResponse.json({
          success: false,
          step: "registration-challenge",
          status: response.status,
          error: errorText,
        }, { status: response.status });
      }

      const challengeData = await response.json();

      return NextResponse.json({
        success: true,
        challenge: challengeData,
      });
    }

    return NextResponse.json({
      error: "Invalid action",
      availableActions: ["start", "initial-registration", "email-challenge", "email-verify", "registration-challenge", "register"],
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

      // Initial registration (create user)
      const response = await fetch(
        `${internalIssuer}/v1/authorizations/${authorizationId}/initial-registration`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Cookie": cookieHeader,
          },
          body: JSON.stringify(body),
        }
      );

      const responseText = await response.text();
      let responseData;
      try {
        responseData = JSON.parse(responseText);
      } catch {
        responseData = { raw: responseText };
      }

      return NextResponse.json({
        success: response.ok,
        status: response.status,
        data: responseData,
      }, { status: response.ok ? 200 : response.status });
    }

    if (action === "email-challenge" && authorizationId) {
      const body = await request.json();

      // Request email authentication challenge
      const response = await fetch(
        `${internalIssuer}/v1/authorizations/${authorizationId}/email-authentication-challenge`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Cookie": cookieHeader,
          },
          body: JSON.stringify({
            email: body.email,
            email_template: "authentication",
          }),
        }
      );

      const responseText = await response.text();
      let responseData;
      try {
        responseData = JSON.parse(responseText);
      } catch {
        responseData = { raw: responseText };
      }

      return NextResponse.json({
        success: response.ok,
        status: response.status,
        data: responseData,
      }, { status: response.ok ? 200 : response.status });
    }

    if (action === "email-verify" && authorizationId) {
      const body = await request.json();

      // Verify email with verification code
      const response = await fetch(
        `${internalIssuer}/v1/authorizations/${authorizationId}/email-authentication`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Cookie": cookieHeader,
          },
          body: JSON.stringify({
            verification_code: body.verification_code,
          }),
        }
      );

      const responseText = await response.text();
      let responseData;
      try {
        responseData = JSON.parse(responseText);
      } catch {
        responseData = { raw: responseText };
      }

      return NextResponse.json({
        success: response.ok,
        status: response.status,
        data: responseData,
      }, { status: response.ok ? 200 : response.status });
    }

    if (action === "register" && authorizationId) {
      const body = await request.json();

      // Complete FIDO2 registration
      const response = await fetch(
        `${internalIssuer}/v1/authorizations/${authorizationId}/fido2-registration`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Cookie": cookieHeader,
          },
          body: JSON.stringify(body),
        }
      );

      const responseText = await response.text();
      let responseData;
      try {
        responseData = JSON.parse(responseText);
      } catch {
        responseData = { raw: responseText };
      }

      return NextResponse.json({
        success: response.ok,
        status: response.status,
        data: responseData,
      }, { status: response.ok ? 200 : response.status });
    }

    return NextResponse.json({
      error: "Invalid action for POST",
      availableActions: ["initial-registration", "email-challenge", "email-verify", "register"],
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

// Utility functions
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
  const digest = await crypto.subtle.digest("SHA-256", data);
  return base64UrlEncode(new Uint8Array(digest));
}

function base64UrlEncode(array: Uint8Array): string {
  const base64 = btoa(String.fromCharCode(...array));
  return base64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");
}
