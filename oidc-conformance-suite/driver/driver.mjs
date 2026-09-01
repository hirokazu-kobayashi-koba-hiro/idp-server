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
 * 認証は財務テナントのポリシーどおり email OTP -> Passkey(FIDO2) の 2 段:
 *   - email OTP … no_action 設定なので管理 API からコードを取る（idp-api.mjs）
 *   - FIDO2     … CDP の仮想オーセンティケータ。モック実装は不要で実 WebAuthn が走る
 *
 * 1 つのテストが複数回ブラウザを要求する（FAPI Advanced の happy path は 2 クライアント目でも
 * 認可する）ため、1 回で終わらせずポーリングし続ける。
 *
 * 使い方:
 *   npm install
 *   npx playwright install chromium
 *   node driver.mjs
 */
import fs from "node:fs";
import { chromium } from "playwright";
import { suite } from "./suite-api.mjs";
import { emailVerificationCode } from "./idp-api.mjs";

const here = (name) => new URL(name, import.meta.url).pathname;

const CONFIG = {
  // メールアドレスを変えると新規ユーザー扱いになり、passkey は登録からやり直しになる。
  // その場合は passkeyFile も消すこと（サーバ側の鍵と食い違うとクローン検知に当たる）。
  email: process.env.DRIVER_EMAIL || "conformance-driver@example.com",
  passkeyFile: process.env.DRIVER_PASSKEY_FILE || here("passkey.json"),
  logFile: process.env.DRIVER_LOG || here("driver.log"),

  // config/examples/financial-grade/setup.sh が作るリソース
  organizationId: "f1a2b3c4-d5e6-f7a8-b9c0-d1e2f3a4b5c6",
  tenantId: "c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8",
  admin: {
    tenantId: "e7f8a9b0-c1d2-e3f4-a5b6-c7d8e9f0a1b2",
    username: "fapi-test@example.com",
    password: "FapiCibaTestSecure123!",
    clientId: "c1d2e3f4-a5b6-c7d8-e9f0-a1b2c3d4e5f6",
    clientSecret: "fapi-ciba-admin-secret-change-in-production-minimum-32-characters",
  },
};

// バックグラウンド実行だと stdout がバッファされて進行が見えない。ログはファイルへ同期書き込みする。
fs.writeFileSync(CONFIG.logFile, "");
function log(line) {
  const entry = `${new Date().toISOString().slice(11, 19)} ${line}\n`;
  fs.appendFileSync(CONFIG.logFile, entry);
  process.stdout.write(entry);
}

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

/** 仮想オーセンティケータを載せた使い捨てのブラウザコンテキストを作る。 */
async function newAuthenticatedContext(browser) {
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  const page = await context.newPage();
  const cdp = await context.newCDPSession(page);

  // 財務テナントの fido2 設定（platform / resident key / user verification 必須）に合わせる。
  await cdp.send("WebAuthn.enable");
  const { authenticatorId } = await cdp.send("WebAuthn.addVirtualAuthenticator", {
    options: {
      protocol: "ctap2",
      transport: "internal",
      hasResidentKey: true,
      hasUserVerification: true,
      isUserVerified: true,
      automaticPresenceSimulation: true,
    },
  });

  // 仮想オーセンティケータはコンテキストごとに空。保存済みの passkey があれば注入する。
  // 無ければ画面側が登録フローを出すので、登録された鍵を後で書き出して次回から使う。
  if (fs.existsSync(CONFIG.passkeyFile)) {
    const credential = JSON.parse(fs.readFileSync(CONFIG.passkeyFile, "utf8"));
    await cdp.send("WebAuthn.addCredential", { authenticatorId, credential });
  }
  return { context, page, cdp, authenticatorId };
}

/**
 * 仮想オーセンティケータの資格情報をファイルへ書き戻す。
 *
 * 初回は登録された鍵の保存、2 回目以降は署名カウンタの更新が目的。idp-server は
 * WebAuthn §6.1.1 のクローン検知（WebAuthn4jAuthenticationExecutor:117）を実装しており、
 * 署名カウンタが前回以下だと "Failed to verify authentication data" で弾かれる。
 * 毎回同じ値を注入するとカウンタが巻き戻り、2 回目の認証が必ず失敗する。
 */
async function savePasskey(cdp, authenticatorId) {
  const { credentials } = await cdp.send("WebAuthn.getCredentials", { authenticatorId });
  if (!credentials.length) return;

  const c = credentials[0];
  const next = {
    credentialId: c.credentialId,
    isResidentCredential: c.isResidentCredential,
    rpId: c.rpId,
    privateKey: c.privateKey,
    userHandle: c.userHandle,
    signCount: c.signCount,
  };

  const previous = fs.existsSync(CONFIG.passkeyFile)
    ? JSON.parse(fs.readFileSync(CONFIG.passkeyFile, "utf8"))
    : null;
  const unchanged =
    previous && previous.credentialId === next.credentialId && previous.signCount === next.signCount;
  if (unchanged) return; // 認証まで進まなかった回。書き戻す必要がない

  fs.writeFileSync(CONFIG.passkeyFile, JSON.stringify(next, null, 2));
  log(`    passkey 保存 (signCount ${previous?.signCount ?? "-"} -> ${next.signCount})`);
}

/** サインイン画面を最後まで進めて、suite の callback に戻す。 */
async function signIn(page) {
  const authorizationId = new URL(page.url()).searchParams.get("id");
  if (!authorizationId) throw new Error(`no authorization id in ${page.url()}`);

  // 1 段目: email OTP
  await page.getByLabel("Email").fill(CONFIG.email);
  await page.getByRole("button", { name: "Send code" }).click();
  await page.waitForTimeout(2000);

  const code = await emailVerificationCode({
    admin: CONFIG.admin,
    organizationId: CONFIG.organizationId,
    tenantId: CONFIG.tenantId,
    authorizationId,
  });

  // 6 桁は 1 文字ずつのボックスに分かれている。先頭にフォーカスして打つと次へ送られる。
  const codeBoxes = page.locator('input[type="text"]');
  await codeBoxes.first().click();
  await page.keyboard.type(code, { delay: 50 });
  await page.getByRole("button", { name: "Verify" }).click();
  log(`    email OTP ok (${code})`);

  // 2 段目: Passkey。新規ユーザーなら "Set up passkey"（登録）、既存なら "Use passkey"（認証）。
  // 固定 sleep ではなく次の画面の要素が出るのを待つ。
  const passkeyButton = page.getByRole("button", { name: /passkey/i }).first();
  await passkeyButton.waitFor({ state: "visible", timeout: 20000 });
  const label = (await passkeyButton.innerText()).trim();
  await passkeyButton.click();

  // 同意
  const consent = page.getByRole("button", { name: "Continue" });
  await consent.waitFor({ state: "visible", timeout: 30000 });
  log(`    passkey ok ("${label}")`);

  await consent.click();
  await page.waitForURL(/localhost\.emobix\.co\.uk/, { timeout: 30000 });
  log("    consent ok -> callback");
}

async function handle(browser, pending) {
  log(`▶ ${pending.testName} (${pending.testId})`);
  // 拾った時点で visited にしておく。urls から外れるので次のポーリングで再度拾わない
  // （テスト自体は callback を待ち続けるので進行には影響しない）。
  await suite.markVisited(pending.testId, pending.url);

  const { context, page, cdp, authenticatorId } = await newAuthenticatedContext(browser);
  try {
    await page.goto(pending.url, { waitUntil: "networkidle", timeout: 60000 });
    if (page.url().includes("/error/")) {
      log(`    ⚠️  エラーページに着地: ${new URL(page.url()).searchParams.get("error")}`);
      return;
    }
    await signIn(page);
  } catch (e) {
    log(`    ❌ ${e.message.split("\n")[0].slice(0, 160)}`);
    await dumpState(page, `fail-${pending.testId}`);
  } finally {
    // 成功・失敗にかかわらず署名カウンタを書き戻す。認証まで進んだ後に落ちた場合、
    // 保存を飛ばすとカウンタが巻き戻り、次回の認証がクローン検知に引っかかる。
    await savePasskey(cdp, authenticatorId).catch((e) =>
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
