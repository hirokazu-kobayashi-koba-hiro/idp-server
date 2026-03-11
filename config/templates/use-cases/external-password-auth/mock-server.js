// External Auth Mock Server
//
// idp-server の外部パスワード認証をローカルで検証するためのモックサーバー。
//
// 起動:
//   node mock-server.js
//
// エンドポイント:
//
//   POST /auth/password  - パスワード認証（基本）
//     認証ルール:
//       - password が "invalid"     → 401 (認証失敗)
//       - username/password が空    → 401 (認証失敗)
//       - それ以外                  → 200 (認証成功、user_id/email/name を返す)
//
//   POST /user/details   - ユーザー詳細取得（http_requests チェーン用）
//     - user_id を受け取り、詳細情報（birthdate, phone_number, zoneinfo, locale, role）を返す
//     - user_id が空 → 400
//
// テスト例:
//   curl -s -X POST http://localhost:4001/auth/password \
//     -H 'Content-Type: application/json' \
//     -d '{"username":"test@example.com","password":"correct"}'    → 200
//
//   curl -s -X POST http://localhost:4001/auth/password \
//     -H 'Content-Type: application/json' \
//     -d '{"username":"test@example.com","password":"invalid"}'    → 401
//
//   curl -s -X POST http://localhost:4001/user/details \
//     -H 'Content-Type: application/json' \
//     -d '{"user_id":"ext-user-test-example-com"}'                → 200

const http = require("http");

// --- POST /auth/password ---
function handleAuthPassword(body, res) {
  try {
    const { username, password } = JSON.parse(body);

    if (!username || !password) {
      res.writeHead(401, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          error: "invalid_credentials",
          error_description: "Username and password are required",
        })
      );
    } else if (password === "invalid") {
      res.writeHead(401, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          error: "invalid_credentials",
          error_description: "Invalid username or password",
        })
      );
    } else {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          user_id: `ext-user-${username.replace(/[^a-zA-Z0-9]/g, "-")}`,
          email: username,
          name: "External User",
        })
      );
    }
  } catch (e) {
    res.writeHead(400, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "invalid_request" }));
  }
}

// --- POST /user/details ---
function handleUserDetails(body, req, res) {
  try {
    const { user_id } = JSON.parse(body);

    if (!user_id) {
      res.writeHead(400, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          error: "invalid_request",
          error_description: "user_id is required",
        })
      );
      return;
    }

    const requestId = req.headers["x-request-id"] || "none";
    const issuedAt = req.headers["issued_at"] || "none";

    res.writeHead(200, {
      "Content-Type": "application/json",
      "x-trace-id": requestId,
    });
    res.end(
      JSON.stringify({
        user_id: user_id,
        birthdate: "1990-01-15",
        phone_number: "+81-90-1234-5678",
        zoneinfo: "Asia/Tokyo",
        locale: "ja",
        role: "member",
        requested_at: issuedAt,
      })
    );
  } catch (e) {
    res.writeHead(400, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "invalid_request" }));
  }
}

// --- request/response logging ---
function logRequest(req, body) {
  const timestamp = new Date().toISOString();
  console.log(`\n[${timestamp}] ${req.method} ${req.url}`);

  const skip = new Set(["host", "connection", "content-length", "content-type"]);
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
  return res;
}

const server = http.createServer((req, res) => {
  if (req.method === "POST") {
    let body = "";
    req.on("data", (chunk) => (body += chunk));
    req.on("end", () => {
      logRequest(req, body);
      const logged = withLogging(req, res);
      if (req.url === "/auth/password") {
        handleAuthPassword(body, logged);
      } else if (req.url === "/user/details") {
        handleUserDetails(body, req, logged);
      } else {
        logged.writeHead(404);
        logged.end();
      }
    });
  } else {
    res.writeHead(404);
    res.end();
  }
});

const PORT = process.env.MOCK_PORT || 4001;
server.listen(PORT, () =>
  console.log(`Mock auth server running on http://localhost:${PORT}`)
);
