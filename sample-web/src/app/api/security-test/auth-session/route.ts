import { NextRequest, NextResponse } from "next/server";

const internalIssuer = process.env.IDP_SERVER_INTERNAL_ISSUER || process.env.NEXT_PUBLIC_IDP_SERVER_ISSUER;
const clientId = process.env.NEXT_PUBLIC_IDP_CLIENT_ID || "";
const frontendUrl = process.env.NEXT_PUBLIC_FRONTEND_URL || "";

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
    // 攻撃シミュレーション: 一連の攻撃フローを実行
    if (action === "simulate-attack") {
      return await simulateAttack();
    }

    if (action === "start") {
      // 認可リクエストを開始（サーバーサイドから直接）
      const response = await fetch(`${internalIssuer}/v1/authorizations`, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
        },
        redirect: "manual",
        body: new URLSearchParams({
          client_id: process.env.NEXT_PUBLIC_IDP_CLIENT_ID || "",
          response_type: "code",
          scope: "openid profile email",
          redirect_uri: `${process.env.NEXT_PUBLIC_FRONTEND_URL}/api/security-test/callback`,
          state: generateRandomState(),
          code_challenge: await generateCodeChallenge(generateCodeVerifier()),
          code_challenge_method: "S256",
        }),
      });

      // レスポンスヘッダーからSet-Cookieを取得
      const setCookieHeader = response.headers.get("set-cookie");
      const locationHeader = response.headers.get("location");
      let authorizationId: string | null = null;
      if (locationHeader) {
        const url = new URL(locationHeader, internalIssuer);
        authorizationId = url.searchParams.get("id");
      }

      return NextResponse.json({
        success: !!authorizationId,
        status: response.status,
        authorizationId,
        redirectUri: locationHeader,
        setCookieHeader: setCookieHeader,
        hasAuthSessionCookie: setCookieHeader?.includes("IDP_AUTH_SESSION"),
      });
    }

    if (action === "interact" && authorizationId) {
      // Cookie なしで interact を試みる
      const response = await fetch(
        `${internalIssuer}/v1/authorizations/${authorizationId}/password-authentication`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            // AUTH_SESSION Cookie を送信しない
          },
          body: JSON.stringify({
            username: "test@example.com",
            password: "testpassword",
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

/**
 * 攻撃シミュレーション
 *
 * シナリオ:
 * 1. 攻撃者が認可リクエストを開始 → authorization_id と AUTH_SESSION Cookie を取得
 * 2. 被害者（別セッション）がそのauthorization_idでinteractを試みる
 * 3. AUTH_SESSION Cookieがないため、401エラーでブロックされる
 */
async function simulateAttack() {
  const steps: Array<{
    step: number;
    title: string;
    description: string;
    result: "success" | "blocked" | "error";
    details: Record<string, unknown>;
  }> = [];

  try {
    // Step 1: 攻撃者が認可リクエストを開始
    const authResponse = await fetch(`${internalIssuer}/v1/authorizations`, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      redirect: "manual", // リダイレクトを手動で処理
      body: new URLSearchParams({
        client_id: clientId,
        response_type: "code",
        scope: "openid profile email",
        redirect_uri: `${frontendUrl}/api/security-test/callback`,
        state: generateRandomState(),
        code_challenge: await generateCodeChallenge(generateCodeVerifier()),
        code_challenge_method: "S256",
      }),
    });

    const authSessionCookie = authResponse.headers.get("set-cookie");
    // リダイレクトURLからauthorization_idを抽出
    const locationHeader = authResponse.headers.get("location");
    let authorizationId: string | null = null;
    if (locationHeader) {
      const url = new URL(locationHeader, internalIssuer);
      authorizationId = url.searchParams.get("id");
    }

    steps.push({
      step: 1,
      title: "攻撃者: 認可リクエスト開始",
      description: "攻撃者が認可フローを開始し、authorization_id と AUTH_SESSION Cookie を取得",
      result: authorizationId ? "success" : "error",
      details: {
        authorizationId,
        hasAuthSessionCookie: authSessionCookie?.includes("IDP_AUTH_SESSION") || false,
        status: authResponse.status,
        redirectLocation: locationHeader,
      },
    });

    if (!authorizationId) {
      return NextResponse.json({
        success: false,
        error: "認可リクエストの開始に失敗しました",
        steps,
      });
    }

    // Step 2: 攻撃者がURLを被害者に送信（シミュレーション）
    steps.push({
      step: 2,
      title: "攻撃者: URLを被害者に送信",
      description: `攻撃者が認可URL（authorization_id=${authorizationId}）を被害者に送信。Cookieは送信されない。`,
      result: "success",
      details: {
        authorizationId,
        note: "攻撃者はURLのみを共有し、AUTH_SESSION Cookieは被害者に渡せない",
      },
    });

    // Step 3: 被害者が異なるCookieでinteractを試みる
    // 被害者は自分のセッションCookieを持っているが、攻撃者のauthorization_idとは紐づいていない
    const victimFakeCookie = `IDP_AUTH_SESSION=victim-fake-session-${crypto.randomUUID()}`;
    const interactResponse = await fetch(
      `${internalIssuer}/v1/authorizations/${authorizationId}/password-authentication`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Cookie": victimFakeCookie, // 被害者の異なるCookie
        },
        body: JSON.stringify({
          username: "victim@example.com",
          password: "victimpassword",
        }),
      }
    );

    let interactData;
    try {
      interactData = await interactResponse.json();
    } catch {
      interactData = { raw: await interactResponse.text() };
    }

    const isBlocked = interactResponse.status === 401;

    steps.push({
      step: 3,
      title: "被害者: 認証を試みる（異なるCookie）",
      description: isBlocked
        ? "AUTH_SESSION Cookie が攻撃者のものと一致しないため、認証がブロックされました"
        : "警告: 認証がブロックされませんでした",
      result: isBlocked ? "blocked" : "error",
      details: {
        status: interactResponse.status,
        statusText: interactResponse.statusText,
        victimCookie: victimFakeCookie,
        response: interactData,
        expectedStatus: 401,
        isProtected: isBlocked,
      },
    });

    // Step 4: 結果サマリー
    const attackBlocked = isBlocked;

    return NextResponse.json({
      success: true,
      attackBlocked,
      summary: attackBlocked
        ? "✅ 攻撃はブロックされました。AUTH_SESSION Cookie によるセッションバインディングが正常に機能しています。"
        : "⚠️ 攻撃がブロックされませんでした。セキュリティ設定を確認してください。",
      steps,
    });
  } catch (error) {
    return NextResponse.json({
      success: false,
      error: "シミュレーション中にエラーが発生しました",
      message: error instanceof Error ? error.message : String(error),
      steps,
    }, { status: 500 });
  }
}
