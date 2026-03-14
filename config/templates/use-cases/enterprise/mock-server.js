// Enterprise Use Case Mock Server
//
// セキュリティイベントフック（Webhook / SSF）の動作確認用モックサーバー。
//
// 起動:
//   node config/templates/use-cases/enterprise/mock-server.js
//
// エンドポイント:
//
//   POST /webhook/security-events    - Webhook イベント受信
//   GET  /webhook/security-events    - 受信済みイベント一覧
//   POST /ssf/events                 - SSF Security Event Token 受信
//   GET  /ssf/events                 - 受信済み SSF イベント一覧
//   POST /oauth/token                - OAuth トークン発行（モック）

const http = require("http");

// --- state ---
const webhookEvents = [];
const ssfEvents = [];

// --- POST /webhook/security-events ---
function handleWebhookEvent(body, req, res) {
  try {
    const data = JSON.parse(body);
    webhookEvents.push({ received_at: new Date().toISOString(), ...data });
    console.log(`  Webhook event: ${data.event_type || "unknown"} (total: ${webhookEvents.length})`);

    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ status: "received" }));
  } catch (e) {
    res.writeHead(400, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "invalid_request", error_description: "Invalid JSON body" }));
  }
}

// --- GET /webhook/security-events ---
function handleWebhookList(req, res) {
  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ events: webhookEvents, total: webhookEvents.length }));
}

// --- POST /ssf/events ---
function handleSsfEvent(body, req, res) {
  const contentType = req.headers["content-type"] || "";

  ssfEvents.push({
    received_at: new Date().toISOString(),
    content_type: contentType,
    body: body
  });

  console.log(`  SSF event received (total: ${ssfEvents.length})`);

  if (contentType.includes("application/secevent+jwt")) {
    // SSF SET (Security Event Token) - JWT format
    const parts = body.split(".");
    if (parts.length === 3) {
      try {
        const payload = JSON.parse(Buffer.from(parts[1], "base64url").toString());
        console.log(`    iss: ${payload.iss}`);
        console.log(`    events: ${JSON.stringify(Object.keys(payload.events || {}))}`);
      } catch (e) {
        console.log(`    (JWT decode failed)`);
      }
    }
  }

  res.writeHead(202, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ status: "accepted" }));
}

// --- GET /ssf/events ---
function handleSsfList(req, res) {
  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ events: ssfEvents, total: ssfEvents.length }));
}

// --- POST /oauth/token ---
function handleOAuthToken(body, req, res) {
  try {
    const contentType = req.headers["content-type"] || "";
    let data;
    if (contentType.includes("application/json")) {
      data = JSON.parse(body);
    } else {
      data = Object.fromEntries(new URLSearchParams(body));
    }

    if (data.client_id === "invalid") {
      res.writeHead(401, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ error: "invalid_client" }));
      return;
    }

    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({
      access_token: `mock-token-${Date.now()}`,
      token_type: "Bearer",
      expires_in: 300,
      scope: data.scope || "application"
    }));
  } catch (e) {
    res.writeHead(400, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ error: "invalid_request" }));
  }
}

// --- logging ---
function logRequest(req, body) {
  const timestamp = new Date().toISOString();
  console.log(`\n[${timestamp}] ${req.method} ${req.url}`);
  if (body && body.length < 500) {
    try {
      console.log(`  body: ${JSON.stringify(JSON.parse(body))}`);
    } catch {
      console.log(`  body: ${body.substring(0, 200)}...`);
    }
  }
}

// --- router ---
function parseUrl(url) {
  const parts = url.split("?")[0].split("/").filter(Boolean);
  return { parts, path: `/${parts.join("/")}` };
}

const server = http.createServer((req, res) => {
  const { parts } = parseUrl(req.url);

  if (req.method === "GET") {
    logRequest(req, null);

    if (parts[0] === "webhook" && parts[1] === "security-events") {
      handleWebhookList(req, res);
      return;
    }
    if (parts[0] === "ssf" && parts[1] === "events") {
      handleSsfList(req, res);
      return;
    }

    res.writeHead(404);
    res.end();
    return;
  }

  if (req.method === "DELETE") {
    logRequest(req, null);

    if (parts[0] === "webhook" && parts[1] === "security-events") {
      webhookEvents.length = 0;
      res.writeHead(204);
      res.end();
      return;
    }
    if (parts[0] === "ssf" && parts[1] === "events") {
      ssfEvents.length = 0;
      res.writeHead(204);
      res.end();
      return;
    }

    res.writeHead(404);
    res.end();
    return;
  }

  if (req.method === "POST") {
    let body = "";
    req.on("data", (chunk) => (body += chunk));
    req.on("end", () => {
      logRequest(req, body);

      if (parts[0] === "oauth" && parts[1] === "token") {
        handleOAuthToken(body, req, res);
        return;
      }
      if (parts[0] === "webhook" && parts[1] === "security-events") {
        handleWebhookEvent(body, req, res);
        return;
      }
      if (parts[0] === "ssf" && parts[1] === "events") {
        handleSsfEvent(body, req, res);
        return;
      }

      res.writeHead(404, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ error: "not_found", error_description: `No handler for ${req.method} ${req.url}` }));
    });
  } else {
    res.writeHead(404);
    res.end();
  }
});

const PORT = process.env.MOCK_PORT || 4005;
server.listen(PORT, () => {
  console.log(`Enterprise Mock Server running on http://localhost:${PORT}`);
  console.log("");
  console.log("Endpoints:");
  console.log(`  POST /webhook/security-events    - Receive webhook event`);
  console.log(`  GET  /webhook/security-events    - List received webhook events`);
  console.log(`  POST /ssf/events                 - Receive SSF Security Event Token`);
  console.log(`  GET  /ssf/events                 - List received SSF events`);
  console.log(`  POST /oauth/token                - Get OAuth token (mock)`);
});
