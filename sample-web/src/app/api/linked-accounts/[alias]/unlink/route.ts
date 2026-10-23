import { NextRequest, NextResponse } from "next/server";
import { auth, internalIssuer } from "@/app/auth";

/**
 * 連携の解除 (#1531)
 *
 * idp-server が外部IdPで保管トークンを失効させてから、連携を消す。
 * 失効はベストエフォートなので、外部IdPに届かなくても解除は成立する。
 */

/** alias は URL パスにそのまま入るので、採番形式（{provider}-{8 文字の英小文字と数字}）以外は通さない。 */
const ALIAS_PATTERN = /^[a-z0-9-]+-[a-z0-9]{8}$/;

export async function POST(
  _request: NextRequest,
  { params }: { params: Promise<{ alias: string }> }
) {
  const { alias } = await params;
  if (!ALIAS_PATTERN.test(alias)) {
    return NextResponse.json({ error: "invalid_alias" }, { status: 400 });
  }

  const session = await auth();
  if (!session?.accessToken) {
    return NextResponse.json({ error: "unauthenticated" }, { status: 401 });
  }

  try {
    const response = await fetch(
      `${internalIssuer}/v1/me/linked-external-accounts/${encodeURIComponent(alias)}`,
      {
        method: "DELETE",
        headers: { Authorization: `Bearer ${session.accessToken}` },
      }
    );

    if (response.status === 204) {
      return new NextResponse(null, { status: 204 });
    }

    const body = await response.json().catch(() => ({}));
    console.error("unlink failed", response.status, body);
    return NextResponse.json(
      { error: body.error || "unlink_failed", error_description: body.error_description },
      { status: response.status }
    );
  } catch (error) {
    console.error("unlink error:", error);
    return NextResponse.json({ error: "unexpected_error" }, { status: 500 });
  }
}
