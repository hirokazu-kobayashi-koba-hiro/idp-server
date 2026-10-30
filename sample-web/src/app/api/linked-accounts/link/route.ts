import { NextRequest, NextResponse } from "next/server";
import { auth, frontendUrl, internalIssuer } from "@/app/auth";

/**
 * 外部IdPアカウント連携の開始 (#1531)
 *
 * ブラウザからこのルートへフォームを POST し、ここで Bearer を使って連携を開始する。
 * idp-server が返す start_url は idp-server 自身の URL で、そこで操作者を確認してから
 * 外部IdPへ送られる。連携開始をブラウザ側の fetch でなくサーバー側で行うのは、
 * アクセストークンをブラウザの URL や履歴に載せないため。
 *
 * GET では受けない。GET だと別サイトのページからリンクや画像で踏ませるだけで、
 * ログイン中の利用者に連携を始めさせられる。POST に加えて Origin も確かめ、
 * このアプリ自身のページから送られたものだけを受ける。
 */
/**
 * 連携先として指定を許すプロバイダー。
 *
 * provider はクエリ由来の値がそのまま idp-server の URL パスに入るので、
 * 素通しすると `..` を混ぜて別のエンドポイントを叩かせられる。
 */
const PROVIDERS = new Set(["account-linking"]);

export async function POST(request: NextRequest) {
  if (!frontendUrl || request.headers.get("origin") !== new URL(frontendUrl).origin) {
    return NextResponse.json({ error: "invalid_origin" }, { status: 403 });
  }

  const form = await request.formData();
  const provider = (form.get("provider") as string | null) || "account-linking";
  if (!PROVIDERS.has(provider)) {
    return redirect303(`${frontendUrl}/linked-accounts?error=invalid_provider`);
  }

  const session = await auth();
  if (!session?.accessToken) {
    return redirect303(`${frontendUrl}/linked-accounts?error=unauthenticated`);
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
      return redirect303(
        `${frontendUrl}/linked-accounts?error=${encodeURIComponent(body.error || "link_failed")}`
      );
    }

    return redirect303(body.start_url);
  } catch (error) {
    console.error("link start error:", error);
    return redirect303(`${frontendUrl}/linked-accounts?error=unexpected_error`);
  }
}

/** フォームの POST への応答は 303 で返し、遷移先を GET で開かせる。 */
function redirect303(url: string) {
  return NextResponse.redirect(url, 303);
}
