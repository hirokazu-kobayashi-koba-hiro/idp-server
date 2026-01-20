import { NextRequest, NextResponse } from "next/server";

const internalIssuer = process.env.IDP_SERVER_INTERNAL_ISSUER || process.env.NEXT_PUBLIC_IDP_SERVER_ISSUER;
const clientId = process.env.NEXT_PUBLIC_IDP_CLIENT_ID || "";
const frontendUrl = process.env.NEXT_PUBLIC_FRONTEND_URL || "";

/**
 * FIDO2 rpId テスト用エンドポイント
 *
 * サブドメインデプロイ時のrpId設定の挙動を確認するためのAPIです。
 * - action=start: 認可リクエストを開始し、authorization_idとAUTH_SESSION Cookieを取得
 * - action=challenge: FIDO2認証チャレンジを取得
 */
export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const action = searchParams.get("action");
  const authorizationId = searchParams.get("authorizationId");
  const username = searchParams.get("username");

  try {
    if (action === "start") {
      // 認可リクエストを開始
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

      // レスポンスヘッダーからSet-Cookieを取得
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

      // Set-Cookieヘッダーを転送（Pathを/に変更してAPIルートからもアクセス可能にする）
      if (setCookieHeader) {
        // Path=/tenant-id/; を Path=/; に置換（tenant-idにはUUIDが含まれる）
        const modifiedSetCookie = setCookieHeader.replace(/Path=\/[^;]+;/, "Path=/;");
        nextResponse.headers.set("Set-Cookie", modifiedSetCookie);
      }

      return nextResponse;
    }

    if (action === "challenge" && authorizationId) {
      // リクエストからCookieを取得
      const cookieHeader = request.headers.get("cookie") || "";

      // FIDO2認証チャレンジを取得
      const response = await fetch(
        `${internalIssuer}/v1/authorizations/${authorizationId}/fido2-authentication-challenge`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Cookie": cookieHeader,
          },
          body: JSON.stringify({
            username: username || "",
            userVerification: "required",
            timeout: 60000,
          }),
        }
      );

      if (!response.ok) {
        const errorText = await response.text();
        return NextResponse.json({
          success: false,
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
      availableActions: ["start", "challenge"],
      usage: {
        start: "/api/fido2-rpid-test?action=start",
        challenge: "/api/fido2-rpid-test?action=challenge&authorizationId=xxx&username=yyy",
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
