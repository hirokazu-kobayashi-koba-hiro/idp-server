/*
 * idp-server の管理 API クライアント。
 *
 * email 認証は no_action 設定（実メールを送らない）なので、検証コードは画面にも
 * チャレンジのレスポンスにも出てこない。管理 API から 2 ホップで取る必要がある。
 * 経路は e2e (e2e/src/user/index.js) と同じ:
 *
 *   authorization_id
 *     -> GET .../authentication-transactions?authorization_id={id}   … transaction を引く
 *     -> GET .../authentication-interactions/{txId}/email-authentication-challenge
 *          -> payload.verification_code
 *
 * ローカルの TLS はプライベート CA (docker/nginx/certs/rootCA.pem) で署名されている。
 * 検証を外すのではなく、その CA を明示的に信頼する。
 */
import https from "node:https";
import fs from "node:fs";
import { resolveLocalCaPath } from "../lib/local-ca.mjs";

const BASE = new URL(process.env.IDP_BASE_URL || "https://api.local.test");

const agent = new https.Agent({ ca: fs.readFileSync(resolveLocalCaPath()) });

function call(method, path, { headers = {}, body } = {}) {
  return new Promise((resolve, reject) => {
    const req = https.request(new URL(path, BASE), { method, agent, headers }, (res) => {
      let data = "";
      res.on("data", (chunk) => (data += chunk));
      res.on("end", () => resolve({ status: res.statusCode, body: data }));
    });
    req.on("error", reject);
    if (body) req.write(body);
    req.end();
  });
}

async function getJson(path, accessToken) {
  const res = await call("GET", path, { headers: { Authorization: `Bearer ${accessToken}` } });
  if (res.status !== 200) throw new Error(`GET ${path} -> ${res.status}: ${res.body.slice(0, 300)}`);
  return JSON.parse(res.body);
}

export async function adminAccessToken(admin) {
  const form = new URLSearchParams({
    grant_type: "password",
    username: admin.username,
    password: admin.password,
    client_id: admin.clientId,
    client_secret: admin.clientSecret,
    scope: "management",
  }).toString();

  const res = await call("POST", `/${admin.tenantId}/v1/tokens`, {
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      "Content-Length": Buffer.byteLength(form),
    },
    body: form,
  });
  if (res.status !== 200) throw new Error(`token -> ${res.status}: ${res.body.slice(0, 300)}`);
  return JSON.parse(res.body).access_token;
}

/** 認可リクエストに紐づく認証トランザクションから email の検証コードを取り出す。 */
export async function emailVerificationCode({ admin, organizationId, tenantId, authorizationId }) {
  const accessToken = await adminAccessToken(admin);
  const base = `/v1/management/organizations/${organizationId}/tenants/${tenantId}`;

  const transactions = await getJson(
    `${base}/authentication-transactions?authorization_id=${authorizationId}`,
    accessToken,
  );
  const transactionId = transactions.list?.[0]?.id;
  if (!transactionId) throw new Error(`no authentication transaction for ${authorizationId}`);

  const interaction = await getJson(
    `${base}/authentication-interactions/${transactionId}/email-authentication-challenge`,
    accessToken,
  );
  const code = interaction.payload?.verification_code;
  if (!code) throw new Error("no verification_code in interaction payload");
  return code;
}
