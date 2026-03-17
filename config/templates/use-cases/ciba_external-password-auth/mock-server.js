// CIBA + External Password Auth Mock Server
//
// 外部パスワード認証 + CIBA デバイス通知をローカルで検証するためのモックサーバー。
//
// 起動:
//   node mock-server.js
//
// エンドポイント:
//
//   POST /auth/password  - パスワード認証（デバイス情報付き）
//     認証ルール:
//       - password が "invalid"     → 401 (認証失敗)
//       - username/password が空    → 401 (認証失敗)
//       - それ以外                  → 200 (認証成功、user + device 情報を返す)
//
//   POST /ciba/notification - CIBA デバイス通知受信（no-action モード）
//     - 通知内容をログ出力し、即座に 200 を返す
//
//   GET  /ciba/notifications - 受信した CIBA 通知一覧を返す
//   DELETE /ciba/notifications - 受信した CIBA 通知をクリア
//
// テスト例:
//   curl -s -X POST http://localhost:4002/auth/password \
//     -H 'Content-Type: application/json' \
//     -d '{"username":"test@example.com","password":"correct"}'    → 200

const http = require("http");
const crypto = require("crypto");

// --- State ---
const cibaNotifications = [];

// Per-user stable device ID (deterministic UUID from username)
function userDeviceId(username) {
  const hash = crypto.createHash("md5").update(`device-${username}`).digest("hex");
  return [
    hash.slice(0, 8),
    hash.slice(8, 12),
    hash.slice(12, 16),
    hash.slice(16, 20),
    hash.slice(20, 32),
  ].join("-");
}

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
      const userId = `ext-user-${username.replace(/[^a-zA-Z0-9]/g, "-")}`;
      const deviceId = userDeviceId(username);

      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          user_id: userId,
          email: username,
          name: "External User",
          device: {
            id: deviceId,
            app_name: "CIBA Mock App",
            platform: "mock",
            notification_channel: "mock",
            notification_token: `mock-token-${deviceId}`,
            priority: 1,
          },
        })
      );
    }
  } catch (e) {
    res.writeHead(400, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "invalid_request" }));
  }
}

// --- POST /ciba/notification ---
function handleCibaNotification(body, req, res) {
  try {
    const notification = JSON.parse(body);
    const entry = {
      received_at: new Date().toISOString(),
      headers: {
        "content-type": req.headers["content-type"],
        authorization: req.headers["authorization"] || "none",
      },
      body: notification,
    };
    cibaNotifications.push(entry);
    console.log(`  CIBA notification received: ${JSON.stringify(entry, null, 2)}`);

    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ status: "ok" }));
  } catch (e) {
    res.writeHead(400, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "invalid_request" }));
  }
}

// --- GET /ciba/notifications ---
function handleGetCibaNotifications(res) {
  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(
    JSON.stringify({
      events: cibaNotifications,
      total: cibaNotifications.length,
    })
  );
}

// --- DELETE /ciba/notifications ---
function handleDeleteCibaNotifications(res) {
  cibaNotifications.length = 0;
  res.writeHead(204);
  res.end();
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
      } else if (req.url === "/ciba/notification") {
        handleCibaNotification(body, req, logged);
      } else {
        logged.writeHead(404);
        logged.end();
      }
    });
  } else if (req.method === "GET") {
    logRequest(req);
    const logged = withLogging(req, res);
    if (req.url === "/ciba/notifications") {
      handleGetCibaNotifications(logged);
    } else {
      logged.writeHead(404);
      logged.end();
    }
  } else if (req.method === "DELETE") {
    logRequest(req);
    const logged = withLogging(req, res);
    if (req.url === "/ciba/notifications") {
      handleDeleteCibaNotifications(logged);
    } else {
      logged.writeHead(404);
      logged.end();
    }
  } else {
    res.writeHead(404);
    res.end();
  }
});

const PORT = process.env.MOCK_PORT || 4007;
server.listen(PORT, () =>
  console.log(
    `CIBA + External Auth mock server running on http://localhost:${PORT}`
  )
);
