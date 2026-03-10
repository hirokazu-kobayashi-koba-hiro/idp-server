// External Auth Mock Server
//
// idp-server の外部パスワード認証をローカルで検証するためのモックサーバー。
// POST /auth/password に対して、外部認証サービスと同じ API 契約で応答する。
//
// 起動:
//   node mock-server.js
//
// 認証ルール:
//   - password が "invalid"     → 401 (認証失敗)
//   - username/password が空    → 401 (認証失敗)
//   - それ以外                  → 200 (認証成功、user_id/email/name を返す)
//
// テスト例:
//   curl -s -X POST http://localhost:4000/auth/password \
//     -H 'Content-Type: application/json' \
//     -d '{"username":"test@example.com","password":"correct"}'    → 200
//
//   curl -s -X POST http://localhost:4000/auth/password \
//     -H 'Content-Type: application/json' \
//     -d '{"username":"test@example.com","password":"invalid"}'    → 401

const http = require("http");

const server = http.createServer((req, res) => {
  if (req.method === "POST" && req.url === "/auth/password") {
    let body = "";
    req.on("data", (chunk) => (body += chunk));
    req.on("end", () => {
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
    });
  } else {
    res.writeHead(404);
    res.end();
  }
});

server.listen(4000, () =>
  console.log("Mock auth server running on http://localhost:4000")
);
