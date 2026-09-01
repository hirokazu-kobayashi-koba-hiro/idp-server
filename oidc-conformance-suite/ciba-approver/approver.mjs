/*
 * CIBA のデバイス認証承認を肩代わりする常駐プロセス。
 *
 * CIBA はブラウザを使わない。認可リクエストはバックチャネルで飛び、ユーザーは自分のデバイスで
 * 承認する。suite はその承認を待つだけなので、../driver/（ブラウザ操作）は出番がない。
 * 代わりに idp-server のデバイス側 API を叩いて承認/拒否する。
 *
 *   GET  /{tenant}/v1/authentication-devices/{deviceId}/authentications  保留中の認証取引
 *   POST /{tenant}/v1/authentications/{txId}/password-authentication     承認
 *   POST /{tenant}/v1/authentications/{txId}/authentication-cancel       拒否
 *
 * 手動版は config/examples/financial-grade/ciba-device-auth.sh。こちらはそれを
 * ポーリングで自動化し、テストごとに承認/拒否/放置を切り替える。
 *
 * 使い方:
 *   node approver.mjs
 */
import fs from "node:fs";
import https from "node:https";
import { suite } from "../driver/suite-api.mjs";
import { resolveLocalCaPath } from "../lib/local-ca.mjs";

const here = (name) => new URL(name, import.meta.url).pathname;

const CONFIG = {
  baseUrl: new URL(process.env.IDP_BASE_URL || "https://api.local.test"),
  // config/examples/financial-grade/setup.sh が作るリソース
  tenantId: process.env.CIBA_TENANT_ID || "c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8",
  deviceId: process.env.CIBA_DEVICE_ID || "b2c3d4e5-f6a7-8901-bcde-f23456789012",
  username: process.env.CIBA_USERNAME || "fapi-ciba-test@example.com",
  password: process.env.CIBA_PASSWORD || "FapiCibaTestSecure123!",
  logFile: process.env.CIBA_LOG || here("approver.log"),

  // WAITING になるのを待つ上限(ms)。到達しなければ諦めてそのまま承認する。
  waitForWaitingMs: Number(process.env.CIBA_WAIT_TIMEOUT_MS || 30000),
};

const agent = new https.Agent({ ca: fs.readFileSync(resolveLocalCaPath()) });

fs.writeFileSync(CONFIG.logFile, "");
function log(line) {
  const entry = `${new Date().toISOString().slice(11, 19)} ${line}\n`;
  fs.appendFileSync(CONFIG.logFile, entry);
  process.stdout.write(entry);
}

function call(method, path, body) {
  return new Promise((resolve, reject) => {
    const headers = { "Content-Type": "application/json" };
    if (body) headers["Content-Length"] = Buffer.byteLength(body);

    const req = https.request(new URL(path, CONFIG.baseUrl), { method, agent, headers }, (res) => {
      let data = "";
      res.on("data", (c) => (data += c));
      res.on("end", () => resolve({ status: res.statusCode, body: data }));
    });
    req.on("error", reject);
    if (body) req.write(body);
    req.end();
  });
}

/**
 * テストごとの振る舞い。
 *
 * CIBA も「常に承認」では通らない。何を要求されているかはテスト名でしか判別できない。
 *
 *   approve(既定) デバイスで承認する
 *   cancel        デバイスで拒否し、access_denied を返させる
 *   ignore        何もしない（認証要求の期限切れを確認するテスト）
 */
const BEHAVIORS = [
  { match: /user-rejects-authentication/, action: "cancel" },
  { match: /auth-req-id-expired/, action: "ignore" },
];

const actionFor = (testName) =>
  BEHAVIORS.find((b) => b.match.test(testName ?? ""))?.action ?? "approve";

/** そのデバイス宛に保留されている認証取引を返す。 */
async function pendingTransactionId() {
  const path = `/${CONFIG.tenantId}/v1/authentication-devices/${CONFIG.deviceId}/authentications`;
  const res = await call("GET", path);
  if (res.status !== 200) return null;
  return JSON.parse(res.body).list?.[0]?.id ?? null;
}

async function approve(transactionId) {
  const res = await call(
    "POST",
    `/${CONFIG.tenantId}/v1/authentications/${transactionId}/password-authentication`,
    JSON.stringify({ username: CONFIG.username, password: CONFIG.password }),
  );
  if (res.status !== 200) throw new Error(`approve -> ${res.status}: ${res.body.slice(0, 200)}`);
}

async function cancel(transactionId) {
  const res = await call(
    "POST",
    `/${CONFIG.tenantId}/v1/authentications/${transactionId}/authentication-cancel`,
  );
  if (res.status !== 200) throw new Error(`cancel -> ${res.status}: ${res.body.slice(0, 200)}`);
}

/**
 * いま走っている CIBA テストを返す。
 *
 * デバイス側 API は「どのテストのための認証要求か」を持たないため、suite 側に問い合わせて
 * 振る舞いを決める。CIBA プランは alias を持ち直列実行されるので、走っているテストは 1 つ。
 */
async function activeTest() {
  for (const testId of await suite.running().catch(() => [])) {
    const info = await suite.info(testId).catch(() => null);
    if (info?.status === "RUNNING" || info?.status === "WAITING") {
      return { testId, testName: info.testName, status: info.status };
    }
  }
  return null;
}

/**
 * テストが WAITING に入るまで待つ。
 *
 * suite はテストの説明で明示している。
 *   "Do not respond to the request until the test enters the 'WAITING' state."
 *
 * 先に承認してしまうと、suite がトークンエンドポイントを叩いた時点で既にトークンが発行され、
 * 「ポーリングは authorization_pending (400) を返すこと」を確認するブロックが落ちる
 * （CheckTokenEndpointHttpStatus400: actual 200, expected 400）。
 *
 * 認証要求を受けた直後は RUNNING で、実測では約 5 秒後に WAITING へ遷移する。固定の待ち時間で
 * はなくこの状態を待つことで、suite のポーリング間隔に依存せずに済む。
 */
async function waitUntilWaiting(testId) {
  const deadline = Date.now() + CONFIG.waitForWaitingMs;
  while (Date.now() < deadline) {
    const info = await suite.info(testId).catch(() => null);
    if (!info || info.status === "WAITING") return true;
    if (info.status === "FINISHED" || info.status === "INTERRUPTED") return false;
    await new Promise((r) => setTimeout(r, 250));
  }
  log(`    ⚠️  WAITING にならないまま ${CONFIG.waitForWaitingMs}ms 経過。そのまま続行する`);
  return true;
}

// 同じ取引を二重に処理しない
const handled = new Set();

log(`ciba approver 起動 (tenant=${CONFIG.tenantId}, device=${CONFIG.deviceId})`);
process.on("SIGINT", () => {
  log("停止");
  process.exit(0);
});

for (;;) {
  const transactionId = await pendingTransactionId().catch(() => null);

  if (transactionId && !handled.has(transactionId)) {
    handled.add(transactionId);
    const test = await activeTest();
    const action = actionFor(test?.testName);
    log(`▶ ${test?.testName ?? "(テスト不明)"} → ${action}`);

    try {
      if (action === "ignore") {
        log("    放置する（期限切れを確認するテスト）");
      } else {
        const proceed = test ? await waitUntilWaiting(test.testId) : true;
        if (!proceed) {
          log("    テストが既に終了していた。何もしない");
        } else {
          if (action === "approve") await approve(transactionId);
          else await cancel(transactionId);
          log(`    ${action} ok`);
        }
      }
    } catch (e) {
      log(`    ❌ ${e.message.split("\n")[0].slice(0, 160)}`);
    }
  }

  await new Promise((r) => setTimeout(r, 500));
}
