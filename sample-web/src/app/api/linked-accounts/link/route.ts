import { NextRequest, NextResponse } from "next/server";
import { auth, frontendUrl, internalIssuer } from "@/app/auth";

/**
 * 外部IdPアカウント連携の開始 (#1531)
 *
 * ブラウザからこのルートへトップレベル遷移し、ここで Bearer を使って連携を開始する。
 * idp-server が返す start_url は idp-server 自身の URL で、そこで操作者を確認してから
 * 外部IdPへ送られる。連携開始をブラウザ側の fetch でなくサーバー側で行うのは、
 * アクセストークンをブラウザの URL や履歴に載せないため。
 */
/**
 * 連携先として指定を許すプロバイダー。
 *
 * provider はクエリ由来の値がそのまま idp-server の URL パスに入るので、
 * 素通しすると `..` を混ぜて別のエンドポイントを叩かせられる。
 */
const PROVIDERS = new Set(["account-linking"]);

export async function GET(request: NextRequest) {
  const provider = request.nextUrl.searchParams.get("provider") || "account-linking";
  if (!PROVIDERS.has(provider)) {
    return NextResponse.redirect(`${frontendUrl}/linked-accounts?error=invalid_provider`);
  }

  const session = await auth();
  if (!session?.accessToken) {
    return NextResponse.redirect(`${frontendUrl}/linked-accounts?error=unauthenticated`);
  }

  try {
    const response = await fetch(
      `${internalIssuer}/v1/me/linked-external-accounts/link/${encodeURIComponent(provider)}`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${session.accessToken}`,
        },
        body: JSON.stringify({
          redirect_uri: `${frontendUrl}/api/linked-accounts/complete`,
          scope: "openid profile email offline_access",
        }),
      }
    );

    const body = await response.json();
    if (!response.ok) {
      console.error("link start failed", response.status, body);
      return NextResponse.redirect(
        `${frontendUrl}/linked-accounts?error=${encodeURIComponent(body.error || "link_failed")}`
      );
    }

    return NextResponse.redirect(body.start_url);
  } catch (error) {
    console.error("link start error:", error);
    return NextResponse.redirect(`${frontendUrl}/linked-accounts?error=unexpected_error`);
  }
}
