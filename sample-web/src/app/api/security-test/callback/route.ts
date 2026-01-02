import { NextRequest, NextResponse } from "next/server";

/**
 * AUTH_SESSION Cookie セキュリティデモ用コールバック
 *
 * Auth.jsを使わず、認可コードの受け取りと結果表示のみを行う
 */
export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const code = searchParams.get("code");
  const state = searchParams.get("state");
  const error = searchParams.get("error");
  const errorDescription = searchParams.get("error_description");

  // HTMLでシンプルに結果を表示
  const html = `
<!DOCTYPE html>
<html lang="ja">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>セキュリティデモ - コールバック結果</title>
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      max-width: 800px;
      margin: 50px auto;
      padding: 20px;
      background: #f5f5f5;
    }
    .card {
      background: white;
      border-radius: 8px;
      padding: 24px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.1);
    }
    .success {
      border-left: 4px solid #4caf50;
    }
    .error {
      border-left: 4px solid #f44336;
    }
    h1 { margin-top: 0; }
    .label { color: #666; font-size: 14px; }
    .value {
      font-family: monospace;
      background: #f0f0f0;
      padding: 8px;
      border-radius: 4px;
      word-break: break-all;
      margin: 8px 0 16px 0;
    }
    .back-link {
      display: inline-block;
      margin-top: 16px;
      color: #1976d2;
      text-decoration: none;
    }
    .back-link:hover {
      text-decoration: underline;
    }
  </style>
</head>
<body>
  <div class="card ${error ? 'error' : 'success'}">
    <h1>${error ? 'AUTH_SESSION検証でブロックされました' : '認可成功'}</h1>

    ${error ? `
      <p class="label">エラーコード:</p>
      <div class="value">${error}</div>

      <p class="label">エラー詳細:</p>
      <div class="value">${errorDescription || '(なし)'}</div>

      <p style="color: #4caf50; font-weight: bold;">
        AUTH_SESSION Cookie による保護が正常に機能しています。
        別のブラウザセッションからの認可完了が拒否されました。
      </p>
    ` : `
      <p class="label">認可コード:</p>
      <div class="value">${code || '(なし)'}</div>

      <p class="label">State:</p>
      <div class="value">${state || '(なし)'}</div>

      <p style="color: #ff9800; font-weight: bold;">
        注意: 同じブラウザセッションからアクセスしたため、認可が成功しました。
        AUTH_SESSION Cookie が送信されたため、攻撃ではありません。
      </p>
    `}

    <a href="/security-demo" class="back-link">← セキュリティデモに戻る</a>
  </div>
</body>
</html>
  `;

  return new NextResponse(html, {
    headers: { "Content-Type": "text/html; charset=utf-8" },
  });
}
