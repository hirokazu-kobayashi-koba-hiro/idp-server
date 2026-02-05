import { NextRequest, NextResponse } from "next/server";

const internalIssuer = process.env.IDP_SERVER_INTERNAL_ISSUER || process.env.NEXT_PUBLIC_IDP_SERVER_ISSUER;
const clientId = process.env.NEXT_PUBLIC_IDP_CLIENT_ID || "";
const clientSecret = process.env.NEXT_IDP_CLIENT_SECRET || "";
const frontendUrl = process.env.NEXT_PUBLIC_FRONTEND_URL || "";

/**
 * パスキー追加登録コールバック
 *
 * auth-viewでパスキー登録完了後、このエンドポイントにリダイレクトされます。
 */
export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const code = searchParams.get("code");
  const state = searchParams.get("state");
  const error = searchParams.get("error");
  const errorDescription = searchParams.get("error_description");

  // エラーチェック
  if (error) {
    const redirectUrl = new URL("/home", frontendUrl);
    redirectUrl.searchParams.set("passkey_error", error);
    if (errorDescription) {
      redirectUrl.searchParams.set("passkey_error_description", errorDescription);
    }
    return NextResponse.redirect(redirectUrl.toString());
  }

  // セッションからstate/code_verifierを取得
  const sessionCookie = request.cookies.get("passkey_registration_session");
  if (!sessionCookie) {
    const redirectUrl = new URL("/home", frontendUrl);
    redirectUrl.searchParams.set("passkey_error", "session_expired");
    return NextResponse.redirect(redirectUrl.toString());
  }

  let sessionData;
  try {
    sessionData = JSON.parse(sessionCookie.value);
  } catch {
    const redirectUrl = new URL("/home", frontendUrl);
    redirectUrl.searchParams.set("passkey_error", "invalid_session");
    return NextResponse.redirect(redirectUrl.toString());
  }

  // state検証
  if (state !== sessionData.state) {
    const redirectUrl = new URL("/home", frontendUrl);
    redirectUrl.searchParams.set("passkey_error", "state_mismatch");
    return NextResponse.redirect(redirectUrl.toString());
  }

  // トークン交換（パスキー登録が成功したことを確認するため）
  try {
    const tokenResponse = await fetch(`${internalIssuer}/v1/tokens`, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: new URLSearchParams({
        grant_type: "authorization_code",
        code: code || "",
        redirect_uri: `${frontendUrl}/api/passkey-registration/callback/`,
        client_id: clientId,
        client_secret: clientSecret,
        code_verifier: sessionData.codeVerifier,
      }),
    });

    if (!tokenResponse.ok) {
      const errorText = await tokenResponse.text();
      console.error("Token exchange failed:", errorText);
      const redirectUrl = new URL("/home", frontendUrl);
      redirectUrl.searchParams.set("passkey_error", "token_exchange_failed");
      return NextResponse.redirect(redirectUrl.toString());
    }

    // 成功 - ホームにリダイレクト
    const redirectUrl = new URL("/home", frontendUrl);
    redirectUrl.searchParams.set("passkey_success", "true");

    const response = NextResponse.redirect(redirectUrl.toString());

    // セッションcookieを削除
    response.cookies.delete("passkey_registration_session");

    return response;
  } catch (error) {
    console.error("Passkey registration callback error:", error);
    const redirectUrl = new URL("/home", frontendUrl);
    redirectUrl.searchParams.set("passkey_error", "unexpected_error");
    return NextResponse.redirect(redirectUrl.toString());
  }
}
