/*
 * OIDF conformance suite が「ブラウザで訪問してほしい」と積んだ URL を、実ブラウザで消化し続ける常駐プロセス。
 *
 * なぜ必要か:
 *   suite 内蔵のブラウザは Selenium HtmlUnit (BrowserControl.java) で、JS エンジンが ES6 を
 *   解釈できない。idp-server のサインイン画面は Next.js の CSR なので全チャンクがパースエラーになり、
 *   設定 JSON の browser ブロックによる自動操作は成立しない。
 *   そこで suite が人間向けに公開している外部ブラウザ API を使い、Playwright の Chromium で
 *   人間の代わりにログインする。
 *
 * 1 つのテストが複数回ブラウザを要求する（FAPI Advanced の happy path は 2 クライアント目でも
 * 認可する）ため、1 回で終わらせずポーリングし続ける。
 *
 * 使い方:
 *   npm install
 *   node driver.mjs
 *
 * サインイン手順そのものは flow.mjs にある（demo.mjs と共有）。
 */
import fs from "node:fs";
import { chromium } from "playwright";
import { suite } from "./suite-api.mjs";
import {
  CONFIG,
  attachVirtualAuthenticator,
  behaviorFor,
  here,
  savePasskey,
  signIn,
} from "./flow.mjs";

const logFile = process.env.DRIVER_LOG || here("driver.log");

// バックグラウンド実行だと stdout がバッファされて進行が見えない。ログはファイルへ同期書き込みする。
fs.writeFileSync(logFile, "");
function log(line) {
  const entry = `${new Date().toISOString().slice(11, 19)} ${line}\n`;
  fs.appendFileSync(logFile, entry);
  process.stdout.write(entry);
}

// 同一テスト内で何回目の訪問かを数える（firstVisit: "abandon" の判定に使う）
const visitCounts = new Map();

// DRIVER_VIDEO=1 で 1 認可ごとに WebM を録画する。ヘッドレスでも撮れる。
const videoDir = process.env.DRIVER_VIDEO === "1" ? here("videos") : null;

/** 失敗時に「今どの画面にいるのか」を残す。 */
async function dumpState(page, tag) {
  const state = await page
    .evaluate(() => ({
      url: location.href,
      buttons: Array.from(document.querySelectorAll("button")).map((b) => b.innerText.trim().slice(0, 30)),
      body: document.body.innerText.replace(/\s+/g, " ").slice(0, 300),
    }))
    .catch((e) => ({ error: e.message }));
  log(`    [${tag}] ${JSON.stringify(state)}`);
  await page.screenshot({ path: `/tmp/conformance-driver-${tag}.png`, fullPage: true }).catch(() => {});
}

// ポーリングの失敗を握りつぶすとドライバが「暇そう」に見えて原因が分からなくなる。
// 毎回出すとうるさいので、同じ内容は最初の 1 回だけ出す。
let lastPollError = null;
function reportPollError(e) {
  const message = e.message.split("\n")[0].slice(0, 120);
  if (message === lastPollError) return;
  lastPollError = message;
  log(`⚠️  suite へのポーリング失敗: ${message}`);
}

async function nextPendingUrl() {
  // /api/runner/running には既に破棄されたテストの id が残ることがあり、その id で
  // /api/runner/browser を引くと 404 になる。1 件の失敗でループを落とさない。
  const running = await suite.running().catch((e) => {
    reportPollError(e);
    return [];
  });
  if (running.length > 0) lastPollError = null;

  for (const testId of running) {
    const browserState = await suite.browser(testId).catch(() => null);
    if (browserState?.urls?.length) {
      const info = await suite.info(testId).catch(() => ({}));
      return { testId, testName: info.testName, url: browserState.urls[0] };
    }
  }
  return null;
}

/**
 * 「エラーページが出ること」を目視で確認するテストの REVIEW を満たす。
 *
 * この種のテスト（request_uri の再利用・期限切れ・別クライアント）は callback を返さない。
 * suite はスクリーンショットの提出を待っており、埋めないと 240 秒でタイムアウトする。
 */
async function fulfillReview(page, testId) {
  const placeholders = await suite.pendingReviewPlaceholders(testId);
  if (!placeholders.length) return false;

  const screenshot = await page.screenshot({ fullPage: true });
  for (const placeholder of placeholders) {
    await suite.uploadScreenshot(testId, placeholder, screenshot);
  }
  log(`    エラーページを提出 (${placeholders.length} 件の REVIEW)`);
  return true;
}

async function handle(browser, pending) {
  const behavior = behaviorFor(pending.testName);
  const visit = (visitCounts.get(pending.testId) ?? 0) + 1;
  visitCounts.set(pending.testId, visit);

  const note = [
    behavior.consent === "deny" ? "deny" : null,
    behavior.firstVisit === "abandon" ? `visit=${visit}` : null,
  ]
    .filter(Boolean)
    .join(" ");
  log(`▶ ${pending.testName} (${pending.testId})${note ? ` [${note}]` : ""}`);

  // 拾った時点で visited にしておく。urls から外れるので次のポーリングで再度拾わない
  // （テスト自体は callback を待ち続けるので進行には影響しない）。
  await suite.markVisited(pending.testId, pending.url);

  const context = await browser.newContext({
    ignoreHTTPSErrors: true,
    ...(videoDir ? { recordVideo: { dir: videoDir, size: { width: 1280, height: 800 } } } : {}),
  });
  const page = await context.newPage();
  const { cdp, authenticatorId } = await attachVirtualAuthenticator(context, page);

  try {
    await page.goto(pending.url, { waitUntil: "networkidle", timeout: 60000 });

    if (page.url().includes("/error/")) {
      const error = new URL(page.url()).searchParams.get("error");
      const submitted = await fulfillReview(page, pending.testId);
      log(`    エラーページに着地: ${error}${submitted ? "" : "（REVIEW なし）"}`);
      return;
    }

    // ログイン画面が出ることだけを確認して離脱する回。ここでログインしてしまうと
    // suite が「初回訪問で認証された」と判定してテストが落ちる。
    if (behavior.firstVisit === "abandon" && visit === 1) {
      await fulfillReview(page, pending.testId);
      log("    ログイン画面を確認して離脱（2 回目で完遂する）");
      return;
    }

    await signIn(page, behavior, log);
  } catch (e) {
    log(`    ❌ ${e.message.split("\n")[0].slice(0, 160)}`);
    await dumpState(page, `fail-${pending.testId}`);
  } finally {
    // 成功・失敗にかかわらず署名カウンタを書き戻す。認証まで進んだ後に落ちた場合、
    // 保存を飛ばすとカウンタが巻き戻り、次回の認証がクローン検知に引っかかる。
    await savePasskey(cdp, authenticatorId, log).catch((e) =>
      log(`    ⚠️  passkey 保存に失敗: ${e.message.slice(0, 80)}`),
    );
    await context.close();
  }
}

// suite の callback は https://localhost.emobix.co.uk:8443/... に来る。このホスト名は公開 DNS で
// 127.0.0.1 に解決される前提だが、そこに依存すると名前が引けない環境でブラウザが
// ERR_NAME_NOT_RESOLVED になる。Chromium 側で固定して外部 DNS を不要にする。
//
// DRIVER_HEADED=1 で実ウィンドウを表示する。1 回のサインインは数秒で終わるため、
// 目で追いたいときは DRIVER_SLOWMO でひと操作ごとに待たせる。
const headed = process.env.DRIVER_HEADED === "1";
const browser = await chromium.launch({
  headless: !headed,
  slowMo: Number(process.env.DRIVER_SLOWMO || 0),
  args: ["--host-resolver-rules=MAP localhost.emobix.co.uk 127.0.0.1"],
});

log(
  `conformance driver 起動 (suite=${suite.baseUrl}, user=${CONFIG.email}` +
    `${headed ? ", 画面あり" : ""})`,
);
process.on("SIGINT", async () => {
  log("停止");
  await browser.close();
  process.exit(0);
});

for (;;) {
  const pending = await nextPendingUrl();
  if (pending) {
    await handle(browser, pending);
  } else {
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
}
