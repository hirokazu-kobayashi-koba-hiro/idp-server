// SMS Authentication Mock Server
//
// MFA (Password + SMS OTP) の外部SMS認証サービス連携をローカルで検証するためのモックサーバー。
//
// 起動:
//   node config/templates/use-cases/mfa-sms/mock-server.js
//
// エンドポイント:
//
//   POST /sms/challenge         - SMS認証チャレンジ（OTP送信依頼）
//     動作:
//       - phone_number が必須
//       - phone_number が "0000000000"  → 500 (サーバーエラー)
//       - phone_number が "9999999999"  → 10秒待機後 504 (タイムアウト)
//       - それ以外                      → 200 (成功、transaction_id + verification_code を返す)
//
//   POST /sms/verify            - SMS認証検証（OTP検証）
//     動作:
//       - transaction_id, verification_code が必須
//       - transaction_id が存在しない      → 400 (不正なトランザクション)
//       - verification_code が不一致       → 400 (検証失敗)
//       - verification_code が一致         → 200 (検証成功)
//
//   POST /oauth/token           - OAuthトークン発行
//     動作:
//       - client_id が "invalid"  → 401
//       - それ以外               → 200 (トークン返却)
//
// テスト例:
//   # チャレンジ（成功）
//   curl -s -X POST http://localhost:4004/sms/challenge \
//     -H 'Content-Type: application/json' \
//     -d '{"phone_number":"09012345678","template":"authentication"}'
//
//   # 検証（成功）
//   curl -s -X POST http://localhost:4004/sms/verify \
//     -H 'Content-Type: application/json' \
//     -d '{"transaction_id":"txn-xxx","verification_code":"123456"}'

const http = require("http");

// --- state ---
const transactions = {};

// --- POST /sms/challenge ---
function handleChallenge(body, req, res) {
  try {
    const data = JSON.parse(body);
    const phoneNumber = data.phone_number;

    if (!phoneNumber) {
      res.writeHead(400, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          error: "invalid_request",
          error_description: "phone_number is required",
        })
      );
      return;
    }

    // エラーシミュレーション
    if (phoneNumber === "0000000000") {
      res.writeHead(500, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          error: "internal_error",
          error_description: "SMS service internal error",
        })
      );
      return;
    }

    // タイムアウトシミュレーション
    if (phoneNumber === "9999999999") {
      setTimeout(() => {
        res.writeHead(504, { "Content-Type": "application/json" });
        res.end(
          JSON.stringify({
            error: "gateway_timeout",
            error_description: "SMS service timeout",
          })
        );
      }, 10000);
      return;
    }

    // 成功: OTP 生成
    const transactionId = `txn-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;
    const verificationCode = String(Math.floor(100000 + Math.random() * 900000));

    transactions[transactionId] = {
      phone_number: phoneNumber,
      verification_code: verificationCode,
      template: data.template || "authentication",
      created_at: new Date().toISOString(),
    };

    console.log(`  OTP generated: ${verificationCode} (transaction: ${transactionId})`);

    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(
      JSON.stringify({
        transaction_id: transactionId,
        verification_code: verificationCode,
        status: "sent",
        message: `Verification code sent to ${phoneNumber}`,
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

// --- POST /sms/verify ---
function handleVerify(body, req, res) {
  try {
    const data = JSON.parse(body);
    const { transaction_id, verification_code } = data;

    if (!transaction_id || !verification_code) {
      res.writeHead(400, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          error: "invalid_request",
          error_description: "transaction_id and verification_code are required",
        })
      );
      return;
    }

    const txn = transactions[transaction_id];

    if (!txn) {
      res.writeHead(400, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          error: "invalid_transaction",
          error_description: `Transaction ${transaction_id} not found`,
        })
      );
      return;
    }

    if (txn.verification_code !== verification_code) {
      res.writeHead(400, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({
          error: "verification_failed",
          error_description: "Invalid verification code",
        })
      );
      return;
    }

    // 成功: トランザクションを消費
    delete transactions[transaction_id];

    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(
      JSON.stringify({
        status: "verified",
        phone_number: txn.phone_number,
        message: "SMS verification successful",
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

// --- POST /oauth/token ---
function parseOAuthBody(body, contentType) {
  if (contentType && contentType.includes("application/json")) {
    return JSON.parse(body);
  }
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
    const { client_id } = data;

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

    const accessToken = `mock-token-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;

    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(
      JSON.stringify({
        access_token: accessToken,
        token_type: "Bearer",
        expires_in: 300,
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

  if (req.method === "POST") {
    let body = "";
    req.on("data", (chunk) => (body += chunk));
    req.on("end", () => {
      logRequest(req, body);
      const logged = withLogging(req, res);

      // POST /oauth/token
      if (parts[0] === "oauth" && parts[1] === "token") {
        handleOAuthToken(body, req, logged);
        return;
      }

      // POST /sms/challenge
      if (parts[0] === "sms" && parts[1] === "challenge") {
        handleChallenge(body, req, logged);
        return;
      }

      // POST /sms/verify
      if (parts[0] === "sms" && parts[1] === "verify") {
        handleVerify(body, req, logged);
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

const PORT = process.env.MOCK_PORT || 4004;
server.listen(PORT, () => {
  console.log(`SMS Auth Mock Server running on http://localhost:${PORT}`);
  console.log("");
  console.log("Endpoints:");
  console.log(`  POST /sms/challenge      - Send OTP SMS`);
  console.log(`  POST /sms/verify         - Verify OTP code`);
  console.log(`  POST /oauth/token        - Get OAuth token`);
  console.log("");
  console.log("Test scenarios (controlled by phone_number):");
  console.log(`  "09012345678"            → 200 success`);
  console.log(`  "0000000000"             → 500 server error`);
  console.log(`  "9999999999"             → 504 after 10s delay`);
});
