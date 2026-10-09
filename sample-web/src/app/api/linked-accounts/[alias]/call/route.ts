import { NextRequest, NextResponse } from "next/server";
import { auth, internalIssuer } from "@/app/auth";

/**
 * 保管された外部トークンで外部APIを呼ぶ (#1531 Phase 4)
 *
 * idp-server から外部IdPのアクセストークンを受け取り、その場で外部APIを呼んで結果だけを返す。
 * 受け取ったトークンはこのサーバーの中で使い切り、ブラウザには渡さない。
 *
 * 取得はクライアント認証（client_secret_basic）付きで、ユーザーは自分のアクセストークンを
 * `token` パラメータで示す。public client からは取れないので、このルートはサーバー側でしか成立しない。
 */

/** alias は URL パスにそのまま入るので、採番形式（{provider}-{seq}）以外は通さない。 */
const ALIAS_PATTERN = /^[a-z0-9-]+-[0-9]+$/;

/**
 * デモで呼ぶ外部API。外部IdP役テナントの userinfo。
 *
 * 本来は連携先ごとの業務API（例: ストレージや会計のAPI）になる。
 */
const externalApiUrl =
  process.env.LINKED_ACCOUNT_DEMO_API_URL ||
  "https://api.local.test/1e68932e-ed4a-43e7-b412-460665e42df3/v1/userinfo";

export async function GET(
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

  const clientId = process.env.NEXT_PUBLIC_IDP_CLIENT_ID ?? "";
  const clientSecret = process.env.NEXT_IDP_CLIENT_SECRET ?? "";
  const basicAuth = Buffer.from(`${clientId}:${clientSecret}`).toString("base64");

  try {
    const tokenResponse = await fetch(
      `${internalIssuer}/v1/linked-external-accounts/${encodeURIComponent(alias)}/token`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          Authorization: `Basic ${basicAuth}`,
        },
        body: new URLSearchParams({ token: session.accessToken }).toString(),
        cache: "no-store",
      }
    );
    const tokenBody = await tokenResponse.json();

    if (!tokenResponse.ok) {
      return NextResponse.json(
        { step: "token_retrieval", status: tokenResponse.status, body: tokenBody },
        { status: tokenResponse.status }
      );
    }

    const apiResponse = await fetch(externalApiUrl, {
      headers: { Authorization: `Bearer ${tokenBody.access_token}` },
      cache: "no-store",
    });
    const apiBody = await apiResponse.json().catch(() => null);

    return NextResponse.json({
      token: {
        token_type: tokenBody.token_type,
        expires_in: tokenBody.expires_in,
        scope: tokenBody.scope,
        issued_token_type: tokenBody.issued_token_type,
      },
      external_api: {
        url: externalApiUrl,
        status: apiResponse.status,
        body: apiBody,
      },
    });
  } catch (error) {
    console.error("linked account call error:", error);
    return NextResponse.json({ error: "unexpected_error" }, { status: 500 });
  }
}
