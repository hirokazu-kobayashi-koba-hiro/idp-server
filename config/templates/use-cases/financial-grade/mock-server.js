// Financial-Grade Mock Server
//
// 開発・検証専用。信頼できないネットワークに公開しないこと。
// jwks_path パラメータでローカルファイルを読み取るため、本番環境では使用不可。
//
// JWT署名エンドポイントを提供するモックサーバー。
// CIBA署名付きリクエストの生成に使用する。
//
// 起動:
//   node mock-server.js
//
// エンドポイント:
//
//   POST /jwt/sign  - JWKで署名したJWTを生成
//     body: { "payload": {...}, "jwks_path": "path/to/jwks.json" }
//     または: { "payload": {...}, "jwk": {...} }
//     response: { "jwt": "eyJ..." }

const http = require("http");
const crypto = require("crypto");
const fs = require("fs");

// --- ES256 JWT signing using Node.js crypto ---
function signEs256Jwt(payload, jwk) {
  const header = { alg: "ES256", typ: "JWT", kid: jwk.kid };

  const headerB64 = Buffer.from(JSON.stringify(header)).toString("base64url");
  const payloadB64 = Buffer.from(JSON.stringify(payload)).toString("base64url");
  const unsigned = `${headerB64}.${payloadB64}`;

  const privateKey = crypto.createPrivateKey({ key: jwk, format: "jwk" });
  const signature = crypto.sign("SHA256", Buffer.from(unsigned), {
    key: privateKey,
    dsaEncoding: "ieee-p1363",
  });

  return `${unsigned}.${signature.toString("base64url")}`;
}

// --- POST /jwt/sign ---
function handleJwtSign(body, res) {
  try {
    const data = JSON.parse(body);

    let jwk;
    if (data.jwks_path) {
      const jwks = JSON.parse(fs.readFileSync(data.jwks_path, "utf8"));
      jwk = jwks.keys[0];
    } else if (data.jwk) {
      jwk = data.jwk;
    } else {
      res.writeHead(400, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ error: "jwks_path or jwk is required" }));
      return;
    }

    if (!jwk.d) {
      res.writeHead(400, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ error: "private key (d) is required in JWK" }));
      return;
    }

    const jwt = signEs256Jwt(data.payload, jwk);

    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ jwt }));
  } catch (e) {
    res.writeHead(500, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: e.message }));
  }
}

// --- logging ---
function logRequest(req, body) {
  const ts = new Date().toISOString();
  console.log(`\n[${ts}] ${req.method} ${req.url}`);
  if (body) {
    try {
      const parsed = JSON.parse(body);
      if (parsed.jwk && parsed.jwk.d) {
        const safe = { ...parsed, jwk: { ...parsed.jwk, d: "***" } };
        console.log(`  body: ${JSON.stringify(safe)}`);
      } else {
        console.log(`  body: ${JSON.stringify(parsed)}`);
      }
    } catch {
      console.log(`  body: ${body.substring(0, 200)}`);
    }
  }
}

// --- router ---
const server = http.createServer((req, res) => {
  if (req.method === "POST") {
    let body = "";
    req.on("data", (chunk) => (body += chunk));
    req.on("end", () => {
      logRequest(req, body);

      const path = req.url.split("?")[0];
      if (path === "/jwt/sign") {
        handleJwtSign(body, res);
        return;
      }

      res.writeHead(404, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ error: "not_found" }));
    });
  } else if (req.method === "GET" && req.url === "/health") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ status: "ok" }));
  } else {
    res.writeHead(404);
    res.end();
  }
});

const PORT = process.env.MOCK_PORT || 4003;
server.listen(PORT, () => {
  console.log(`Financial-Grade Mock Server running on http://localhost:${PORT}`);
  console.log("");
  console.log("Endpoints:");
  console.log("  POST /jwt/sign   - Sign JWT with ES256");
  console.log("  GET  /health     - Health check");
});
