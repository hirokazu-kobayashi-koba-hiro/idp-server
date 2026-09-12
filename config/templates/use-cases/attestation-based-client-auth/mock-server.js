// MFA (Password + FIDO-UAF) Mock Server
//
// メール認証 + FIDO-UAF 認証の外部サービス連携をローカルで検証するためのモックサーバー。
//
// 起動:
//   node config/templates/use-cases/mfa-fido-uaf/mock-server.js
//
// エンドポイント:
//
//   POST /email-authentication-challenge  - メール認証チャレンジ（OTP送信）
//   POST /email-authentication            - メール認証検証（OTP検証）
//   POST /fido-uaf/registration-challenge - FIDO-UAF 登録チャレンジ
//   POST /fido-uaf/registration           - FIDO-UAF 登録完了
//   POST /fido-uaf/authentication-challenge - FIDO-UAF 認証チャレンジ
//   POST /fido-uaf/authentication         - FIDO-UAF 認証
//   POST /token                           - OAuthトークン発行（モック）

const http = require("http");

const transactions = {};
let deviceCounter = 0;

// --- Email Authentication ---
function handleEmailChallenge(body, req, res) {
  try {
    const data = JSON.parse(body);
    const email = data.email;

    if (!email) {
      res.writeHead(400, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ error: "invalid_request", error_description: "email is required" }));
      return;
    }

    const transactionId = `txn-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;
    const verificationCode = String(Math.floor(100000 + Math.random() * 900000));

    transactions[transactionId] = { email, verification_code: verificationCode, created_at: new Date().toISOString() };

    console.log(`  Email OTP: ${verificationCode} (transaction: ${transactionId}, email: ${email})`);

    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ transaction_id: transactionId, verification_code: verificationCode, status: "sent" }));
  } catch (e) {
    res.writeHead(400, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "invalid_request", error_description: "Invalid JSON body" }));
  }
}

function handleEmailVerify(body, req, res) {
  try {
    const data = JSON.parse(body);
    const { transaction_id, verification_code } = data;

    if (!transaction_id || !verification_code) {
      res.writeHead(400, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ error: "invalid_request", error_description: "transaction_id and verification_code are required" }));
      return;
    }

    const txn = transactions[transaction_id];
    if (!txn) {
      res.writeHead(400, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ error: "invalid_transaction", error_description: "Transaction not found" }));
      return;
    }

    if (txn.verification_code !== verification_code) {
      res.writeHead(400, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ error: "verification_failed", error_description: "Invalid verification code" }));
      return;
    }

    delete transactions[transaction_id];
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ status: "verified", email: txn.email }));
  } catch (e) {
    res.writeHead(400, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "invalid_request", error_description: "Invalid JSON body" }));
  }
}

// --- FIDO-UAF ---
function handleFidoUafRegistrationChallenge(body, req, res) {
  const challenge = `challenge-${Date.now()}`;
  const deviceId = `device-${++deviceCounter}-${Date.now()}`;

  console.log(`  FIDO-UAF registration challenge: ${challenge}, device_id: ${deviceId}`);

  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(JSON.stringify({
    uafRequest: [{ header: { op: "Reg", upv: { major: 1, minor: 1 } }, challenge, transaction: "registration" }],
    challenge,
    device_id: deviceId,
  }));
}

function handleFidoUafRegistration(body, req, res) {
  console.log("  FIDO-UAF registration completed");

  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ status: "registered", message: "Device registered successfully" }));
}

function handleFidoUafAuthenticationChallenge(body, req, res) {
  const challenge = `auth-challenge-${Date.now()}`;

  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(JSON.stringify({
    uafRequest: [{ header: { op: "Auth", upv: { major: 1, minor: 1 } }, challenge, transaction: "authentication" }],
  }));
}

function handleFidoUafAuthentication(body, req, res) {
  console.log("  FIDO-UAF authentication completed");

  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ status: "authenticated", message: "Device authentication successful" }));
}

// --- OAuth Token ---
function handleOAuthToken(body, req, res) {
  const accessToken = `mock-token-${Date.now()}`;
  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ access_token: accessToken, token_type: "Bearer", expires_in: 300 }));
}

// --- Logging ---
function logRequest(req, body) {
  const timestamp = new Date().toISOString();
  console.log(`\n[${timestamp}] ${req.method} ${req.url}`);
  if (body) {
    try { console.log(`  body: ${JSON.stringify(JSON.parse(body))}`); }
    catch { console.log(`  body: ${body}`); }
  }
}

// --- Router ---
const server = http.createServer((req, res) => {
  const path = req.url.split("?")[0];

  if (req.method === "POST") {
    let body = "";
    req.on("data", (chunk) => (body += chunk));
    req.on("end", () => {
      logRequest(req, body);

      switch (path) {
        case "/email-authentication-challenge": return handleEmailChallenge(body, req, res);
        case "/email-authentication": return handleEmailVerify(body, req, res);
        case "/fido-uaf/registration-challenge": return handleFidoUafRegistrationChallenge(body, req, res);
        case "/fido-uaf/registration": return handleFidoUafRegistration(body, req, res);
        case "/fido-uaf/authentication-challenge": return handleFidoUafAuthenticationChallenge(body, req, res);
        case "/fido-uaf/authentication": return handleFidoUafAuthentication(body, req, res);
        case "/token": return handleOAuthToken(body, req, res);
        default:
          res.writeHead(404, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ error: "not_found", error_description: `No handler for ${req.method} ${path}` }));
      }
    });
  } else {
    res.writeHead(404);
    res.end();
  }
});

const PORT = process.env.MOCK_PORT || 4005;
server.listen(PORT, () => {
  console.log(`MFA FIDO-UAF Mock Server running on http://localhost:${PORT}`);
  console.log("");
  console.log("Endpoints:");
  console.log("  POST /email-authentication-challenge  - Send email OTP");
  console.log("  POST /email-authentication            - Verify email OTP");
  console.log("  POST /fido-uaf/registration-challenge - FIDO-UAF registration challenge");
  console.log("  POST /fido-uaf/registration           - FIDO-UAF registration");
  console.log("  POST /fido-uaf/authentication-challenge - FIDO-UAF auth challenge");
  console.log("  POST /fido-uaf/authentication         - FIDO-UAF authentication");
  console.log("  POST /token                           - OAuth token (mock)");
});
