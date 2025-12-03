import { NextResponse } from "next/server";

/**
 * RP-Initiated Logout Callback
 *
 * IDP server の /v1/logout からリダイレクトされた後に呼ばれる。
 * NextAuth のセッション Cookie を削除してホームページにリダイレクトする。
 */
export async function GET() {
  const frontendUrl = process.env.NEXT_PUBLIC_FRONTEND_URL || "http://localhost:3000";

  // NextAuth v5 のデフォルト Cookie 名
  const sessionCookieNames = [
    "authjs.session-token",
    "__Secure-authjs.session-token",
    "authjs.callback-url",
    "authjs.csrf-token",
  ];

  const response = NextResponse.redirect(new URL("/", frontendUrl));

  // Cookie を削除（Max-Age=0 で即時削除）
  for (const name of sessionCookieNames) {
    response.cookies.delete(name);
  }

  return response;
}
