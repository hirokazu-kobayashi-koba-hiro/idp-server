import { NextRequest, NextResponse } from "next/server";
import { auth, frontendUrl, internalIssuer } from "@/app/auth";

/**
 * 連携の確定 (#1531)
 *
 * 外部IdPから戻った idp-server が、連携開始時に登録した戻り先としてここへ 302 する。
 * この時点で idp-server 側はトークンを暗号化して保持しているだけで、連携は未確定。
 * ここで本物のアクセストークンを添えて complete を呼ぶことで、はじめて確定する。
 *
 * 確定を分けているのは、外部IdPからのリダイレクトが Bearer を運べないため。
 * 誰の連携かを最後にもう一度証明できるのは、このトークンを持っている RP だけになる。
 */
export async function GET(request: NextRequest) {
  const state = request.nextUrl.searchParams.get("state");
  const error = request.nextUrl.searchParams.get("error");

  if (error) {
    return NextResponse.redirect(
      `${frontendUrl}/linked-accounts?error=${encodeURIComponent(error)}`
    );
  }

  if (!state) {
    return NextResponse.redirect(`${frontendUrl}/linked-accounts?error=missing_state`);
  }

  const session = await auth();
  if (!session?.accessToken) {
    return NextResponse.redirect(`${frontendUrl}/linked-accounts?error=unauthenticated`);
  }

  try {
    const response = await fetch(
      `${internalIssuer}/v1/me/linked-external-accounts/complete`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${session.accessToken}`,
        },
        body: JSON.stringify({ state }),
      }
    );

    const body = await response.json();
    if (!response.ok) {
      console.error("link complete failed", response.status, body);
      return NextResponse.redirect(
        `${frontendUrl}/linked-accounts?error=${encodeURIComponent(body.error || "complete_failed")}`
      );
    }

    return NextResponse.redirect(
      `${frontendUrl}/linked-accounts?linked=${encodeURIComponent(body.alias)}`
    );
  } catch (e) {
    console.error("link complete error:", e);
    return NextResponse.redirect(`${frontendUrl}/linked-accounts?error=unexpected_error`);
  }
}
