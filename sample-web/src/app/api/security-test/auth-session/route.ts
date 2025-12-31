import { NextRequest, NextResponse } from "next/server";

const internalIssuer = process.env.IDP_SERVER_INTERNAL_ISSUER || process.env.NEXT_PUBLIC_IDP_SERVER_ISSUER;

/**
 * AUTH_SESSION Cookie 検証テスト用エンドポイント
 *
 * 認可リクエストを開始し、AUTH_SESSION Cookie が正しく設定されるかを確認します。
 * また、Cookie なしでのアクセスが拒否されるかをテストします。
 */
export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const action = searchParams.get("action");
  const authorizationId = searchParams.get("authorizationId");

  try {
    if (action === "start") {
      // 認可リクエストを開始（サーバーサイドから直接）
      const response = await fetch(`${internalIssuer}/v1/authorizations`, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
        },
        body: new URLSearchParams({
          client_id: process.env.NEXT_PUBLIC_IDP_CLIENT_ID || "",
          response_type: "code",
          scope: "openid profile email",
          redirect_uri: `${process.env.NEXT_PUBLIC_FRONTEND_URL}/api/auth/callback/idp-server`,
          state: generateRandomState(),
          code_challenge: await generateCodeChallenge(generateCodeVerifier()),
          code_challenge_method: "S256",
        }),
      });

      const data = await response.json();

      // レスポンスヘッダーからSet-Cookieを取得
      const setCookieHeader = response.headers.get("set-cookie");

      return NextResponse.json({
        success: response.ok,
        status: response.status,
        authorizationId: data.authorization_id || data.id,
        redirectUri: data.redirect_uri,
        setCookieHeader: setCookieHeader,
        hasAuthSessionCookie: setCookieHeader?.includes("IDP_AUTH_SESSION"),
        rawResponse: data,
      });
    }

    if (action === "interact" && authorizationId) {
      // Cookie なしで interact を試みる
      const response = await fetch(
        `${internalIssuer}/v1/authorizations/${authorizationId}/interactions`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            // AUTH_SESSION Cookie を送信しない
          },
          body: JSON.stringify({
            type: "password",
            credentials: {
              username: "test@example.com",
              password: "testpassword",
            },
          }),
        }
      );

      const data = await response.text();

      return NextResponse.json({
        success: response.ok,
        status: response.status,
        statusText: response.statusText,
        expectedBehavior: "AUTH_SESSION Cookie がないため、401エラーが返されるべき",
        actualResponse: data,
        isProtected: response.status === 401,
      });
    }

    return NextResponse.json({
      error: "Invalid action",
      availableActions: ["start", "interact"],
      usage: {
        start: "/api/security-test/auth-session?action=start",
        interact: "/api/security-test/auth-session?action=interact&authorizationId=xxx",
      },
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

// ユーティリティ関数
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
