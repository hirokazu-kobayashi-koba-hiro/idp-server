// eKYC Mock Server
//
// idp-server の身元確認（Identity Verification）をローカルで検証するためのモックサーバー。
//
// 起動:
//   node mock-server.js
//
// エンドポイント:
//
//   POST /ekyc/apply           - 身元確認申請受付
//     動作:
//       - last_name/first_name/birthdate が必須
//       - last_name が "error"       → 500 (サーバーエラー)
//       - last_name が "timeout"     → 10秒待機後 504 (タイムアウト)
//       - last_name が "retry"       → 1回目 503, 2回目以降 200 (リトライ確認用)
//       - last_name が "unauthorized"    → 401 (認証失敗、OAuth自動リトライ確認用)
//       - last_name が "business_error" → 200 だが status:"error" (response_resolve_configs確認用)
//       - それ以外                      → 200 (成功、application_id を返す)
//
//   POST /ekyc/:id/request     - eKYC サービスリクエスト（多段プロセス用）
//     動作:
//       - application_id に基づいてリクエストを受付
//       - 200 + request_id を返す
//
//   GET  /ekyc/:id/status      - ステータス確認
//     動作:
//       - application_id に基づいてステータスを返す
//       - "approved" / "pending" / "rejected"
//
//   POST /ekyc/:id/complete    - eKYC 完了通知（多段プロセス用）
//     動作:
//       - 200 + completion 確認を返す
//
//   POST /oauth/token          - OAuthトークン発行（SSO credentials / OAuth リトライ用）
//     動作:
//       - client_id が "invalid"  → 401
//       - username が "expired"   → 有効期限 1秒のトークンを返す
//       - それ以外               → 有効期限 300秒のトークンを返す
//
// テスト例:
//   # 申請（成功）
//   curl -s -X POST http://localhost:4002/ekyc/apply \
//     -H 'Content-Type: application/json' \
//     -d '{"last_name":"Tanaka","first_name":"Taro","birthdate":"1990-01-15"}'
//
//   # 申請（サーバーエラー）
//   curl -s -X POST http://localhost:4002/ekyc/apply \
//     -H 'Content-Type: application/json' \
//     -d '{"last_name":"error","first_name":"Taro","birthdate":"1990-01-15"}'
//
//   # 申請（リトライ確認: 1回目 503 → 2回目 200）
//   curl -s -X POST http://localhost:4002/ekyc/apply \
//     -H 'Content-Type: application/json' \
//     -d '{"last_name":"retry","first_name":"Taro","birthdate":"1990-01-15"}'
//
//   # OAuthトークン取得
//   curl -s -X POST http://localhost:4002/oauth/token \
//     -H 'Content-Type: application/json' \
//     -d '{"client_id":"test","username":"user","password":"pass"}'

const http = require("http");

// --- state ---
const retryCounters = {};
const authFailCounters = {};
const applications = {};

// --- POST /ekyc/apply ---
function handleApply(body, req, res) {
  try {
    const data = JSON.parse(body);
    // body_mapping_rules でフィールド名が変換される場合を考慮し、両方の名前を受け付ける
    const last_name = data.last_name || data.family_name;
    const first_name = data.first_name || data.given_name;
    const birthdate = data.birthdate || data.date_of_birth;

    if (!last_name || !first_name || !birthdate) {
      res.writeHead(400, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          error: "invalid_request",
          error_description:
            "last_name (or family_name), first_name (or given_name), and birthdate (or date_of_birth) are required",
        })
      );
      return;
    }

    // エラーシミュレーション
    if (last_name === "error") {
      res.writeHead(500, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          error: "internal_error",
          error_description: "eKYC service internal error",
        })
      );
      return;
    }

    // タイムアウトシミュレーション
    if (last_name === "timeout") {
      setTimeout(() => {
        res.writeHead(504, { "Content-Type": "application/json" });
        res.end(
          JSON.stringify({
            error: "gateway_timeout",
            error_description: "eKYC service timeout",
          })
        );
      }, 10000);
      return;
    }

    // リトライシミュレーション（1回目 503、2回目以降 200）
    if (last_name === "retry") {
      const idempotencyKey =
        req.headers["idempotency-key"] || `retry-${Date.now()}`;
      retryCounters[idempotencyKey] =
        (retryCounters[idempotencyKey] || 0) + 1;

      if (retryCounters[idempotencyKey] === 1) {
        res.writeHead(503, { "Content-Type": "application/json" });
        res.end(
          JSON.stringify({
            error: "service_unavailable",
            error_description: "eKYC service temporarily unavailable",
          })
        );
        return;
      }
    }

    // OAuth 401 シミュレーション（認証トークンリトライ確認用）
    if (last_name === "unauthorized") {
      const authHeader = req.headers["authorization"] || "";
      const token = authHeader.replace("Bearer ", "");
      authFailCounters[token] = (authFailCounters[token] || 0) + 1;

      if (authFailCounters[token] === 1) {
        res.writeHead(401, { "Content-Type": "application/json" });
        res.end(
          JSON.stringify({
            error: "unauthorized",
            error_description: "Invalid or expired token",
          })
        );
        return;
      }
    }

    // 業務エラーシミュレーション（HTTP 200 だが status: "error"）
    if (last_name === "business_error") {
      const applicationId = `ekyc-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          application_id: applicationId,
          status: "error",
          message: "Business validation failed",
        })
      );
      return;
    }

    // 成功
    const applicationId = `ekyc-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;

    applications[applicationId] = {
      application_id: applicationId,
      status: "applied",
      last_name,
      first_name,
      birthdate,
      email_address: data.email_address || null,
      phone_number: data.phone_number || null,
      address: data.address || null,
      applied_at: new Date().toISOString(),
    };

    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(
      JSON.stringify({
        application_id: applicationId,
        status: "applied",
        message: "Application received successfully",
      })
    );
  } catch (e) {
    res.writeHead(400, { "Content-Type": "application/json" });
    res.end(
      JSON.stringify({
        error: "invalid_request",
        error_description: "Invalid JSON body",
      })
    );
  }
}

// --- POST /ekyc/:id/request ---
function handleRequest(applicationId, body, res) {
  if (!applications[applicationId]) {
    // 存在しなくても成功扱い（外部サービスとして動作）
    applications[applicationId] = { status: "applied" };
  }

  const requestId = `req-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;
  applications[applicationId].status = "in_progress";
  applications[applicationId].request_id = requestId;

  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(
    JSON.stringify({
      application_id: applicationId,
      request_id: requestId,
      status: "in_progress",
      message: "eKYC verification request accepted",
    })
  );
}

// --- GET /ekyc/:id/status ---
function handleStatus(applicationId, res) {
  const app = applications[applicationId];

  if (!app) {
    res.writeHead(404, { "Content-Type": "application/json" });
    res.end(
      JSON.stringify({
        error: "not_found",
        error_description: `Application ${applicationId} not found`,
      })
    );
    return;
  }

  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(
    JSON.stringify({
      application_id: applicationId,
      status: app.status,
      request_id: app.request_id || null,
    })
  );
}

// --- POST /ekyc/:id/complete ---
function handleComplete(applicationId, body, res) {
  const app = applications[applicationId];

  if (!app) {
    applications[applicationId] = { status: "in_progress" };
  }

  applications[applicationId].status = "completed";

  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(
    JSON.stringify({
      application_id: applicationId,
      status: "completed",
      completed_at: new Date().toISOString(),
    })
  );
}

// --- POST /oauth/token ---
// OAuth仕様に従い application/x-www-form-urlencoded と JSON の両方を受け付ける
function parseOAuthBody(body, contentType) {
  if (contentType && contentType.includes("application/json")) {
    return JSON.parse(body);
  }
  // application/x-www-form-urlencoded（OAuth仕様標準）
  const params = new URLSearchParams(body);
  const data = {};
  for (const [key, value] of params) {
    data[key] = value;
  }
  return data;
}

function handleOAuthToken(body, req, res) {
  try {
    const contentType = req.headers["content-type"] || "";
    const data = parseOAuthBody(body, contentType);
    const { client_id, username } = data;

    if (client_id === "invalid") {
      res.writeHead(401, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          error: "invalid_client",
          error_description: "Invalid client credentials",
        })
      );
      return;
    }

    const expiresIn = username === "expired" ? 1 : 300;
    const accessToken = `mock-token-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;

    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(
      JSON.stringify({
        access_token: accessToken,
        token_type: "Bearer",
        expires_in: expiresIn,
        scope: data.scope || "application",
      })
    );
  } catch (e) {
    res.writeHead(400, { "Content-Type": "application/json" });
    res.end(
      JSON.stringify({
        error: "invalid_request",
        error_description: "Invalid request body",
      })
    );
  }
}

// --- request/response logging ---
function logRequest(req, body) {
  const timestamp = new Date().toISOString();
  console.log(`\n[${timestamp}] ${req.method} ${req.url}`);

  const skip = new Set([
    "host",
    "connection",
    "content-length",
    "content-type",
  ]);
  for (const [key, value] of Object.entries(req.headers)) {
    if (!skip.has(key)) {
      console.log(`  ${key}: ${value}`);
    }
  }

  if (body) {
    try {
      console.log(`  body: ${JSON.stringify(JSON.parse(body))}`);
    } catch {
      console.log(`  body: ${body}`);
    }
  }
}

function withLogging(req, res) {
  const originalWriteHead = res.writeHead.bind(res);
  res.writeHead = (statusCode, ...args) => {
    console.log(`  → ${statusCode}`);
    return originalWriteHead(statusCode, ...args);
  };
  const originalEnd = res.end.bind(res);
  res.end = (body, ...args) => {
    if (body) {
      try {
        console.log(`  body: ${JSON.stringify(JSON.parse(body))}`);
      } catch {
        console.log(`  body: ${body}`);
      }
    }
    return originalEnd(body, ...args);
  };
  return res;
}

// --- URL parser ---
function parseUrl(url) {
  const parts = url.split("?")[0].split("/").filter(Boolean);
  return { parts, path: `/${parts.join("/")}` };
}

// --- router ---
const server = http.createServer((req, res) => {
  const { parts } = parseUrl(req.url);

  if (req.method === "POST" || req.method === "GET") {
    let body = "";
    req.on("data", (chunk) => (body += chunk));
    req.on("end", () => {
      logRequest(req, body);
      const logged = withLogging(req, res);

      // POST /oauth/token
      if (req.method === "POST" && parts[0] === "oauth" && parts[1] === "token") {
        handleOAuthToken(body, req, logged);
        return;
      }

      // POST /ekyc/apply
      if (req.method === "POST" && parts[0] === "ekyc" && parts[1] === "apply") {
        handleApply(body, req, logged);
        return;
      }

      // POST /ekyc/:id/request
      if (req.method === "POST" && parts[0] === "ekyc" && parts[2] === "request") {
        handleRequest(parts[1], body, logged);
        return;
      }

      // GET /ekyc/:id/status
      if (req.method === "GET" && parts[0] === "ekyc" && parts[2] === "status") {
        handleStatus(parts[1], logged);
        return;
      }

      // POST /ekyc/:id/complete
      if (req.method === "POST" && parts[0] === "ekyc" && parts[2] === "complete") {
        handleComplete(parts[1], body, logged);
        return;
      }

      logged.writeHead(404, { "Content-Type": "application/json" });
      logged.end(
        JSON.stringify({
          error: "not_found",
          error_description: `No handler for ${req.method} ${req.url}`,
        })
      );
    });
  } else {
    res.writeHead(404);
    res.end();
  }
});

const PORT = process.env.MOCK_PORT || 4002;
server.listen(PORT, () => {
  console.log(`eKYC Mock Server running on http://localhost:${PORT}`);
  console.log("");
  console.log("Endpoints:");
  console.log(`  POST /ekyc/apply           - Apply for identity verification`);
  console.log(`  POST /ekyc/:id/request     - Request eKYC verification`);
  console.log(`  GET  /ekyc/:id/status      - Check verification status`);
  console.log(`  POST /ekyc/:id/complete    - Complete verification`);
  console.log(`  POST /oauth/token          - Get OAuth token`);
  console.log("");
  console.log("Test scenarios (controlled by last_name):");
  console.log(`  "Tanaka"       → 200 success`);
  console.log(`  "error"        → 500 server error`);
  console.log(`  "timeout"      → 504 after 10s delay`);
  console.log(`  "retry"        → 503 on first call, 200 on retry`);
  console.log(`  "unauthorized"    → 401 on first call, 200 on retry (OAuth token refresh test)`);
  console.log(`  "business_error"  → 200 with status:"error" (response_resolve_configs test)`);
});
