import { NextResponse } from "next/server";

// ブラウザからアクセスするためpublic issuerを使用
const publicIssuer = process.env.NEXT_PUBLIC_IDP_SERVER_ISSUER;
const clientId = process.env.NEXT_PUBLIC_IDP_CLIENT_ID || "";
const frontendUrl = process.env.NEXT_PUBLIC_FRONTEND_URL || "";

/**
 * パスキー追加登録用エンドポイント
 *
 * ブラウザを直接認可エンドポイントにリダイレクトします。
 * これによりSafariのITP (Intelligent Tracking Prevention) による
 * サードパーティCookieブロックを回避できます。
 *
 * フロー:
 * 1. state/code_verifier を生成してcookieに保存
 * 2. ブラウザを認可エンドポイントに直接リダイレクト
 * 3. 認可サーバーがAUTH_SESSION cookieを設定
 * 4. auth-viewのadd-passkeyページにリダイレクト
 */
export async function GET() {
  try {
    const state = generateRandomState();
    const codeVerifier = generateCodeVerifier();
    const codeChallenge = await generateCodeChallenge(codeVerifier);

    // セッションデータをcookieに保存（callback時に使用）
    const sessionData = JSON.stringify({ state, codeVerifier });

    // 認可エンドポイントのURLを構築（ブラウザが直接アクセス）
    const authUrl = new URL(`${publicIssuer}/v1/authorizations`);
    authUrl.searchParams.set("client_id", clientId);
    authUrl.searchParams.set("response_type", "code");
    authUrl.searchParams.set("scope", "openid profile email claims:authentication_devices");
    authUrl.searchParams.set("redirect_uri", `${frontendUrl}/api/passkey-registration/callback/`);
    authUrl.searchParams.set("state", state);
    authUrl.searchParams.set("code_challenge", codeChallenge);
    authUrl.searchParams.set("code_challenge_method", "S256");
    authUrl.searchParams.set("prompt", "login");

    // ブラウザを認可エンドポイントに直接リダイレクト
    const response = NextResponse.redirect(authUrl.toString());

    // セッションデータをcookieに保存
    response.cookies.set("passkey_registration_session", sessionData, {
      httpOnly: true,
      secure: true,
      sameSite: "lax",
      maxAge: 600, // 10分
      path: "/",
    });

    return response;
  } catch (error) {
    console.error("Passkey registration error:", error);
    return NextResponse.redirect(`${frontendUrl}/home?passkey_error=unexpected_error`);
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
