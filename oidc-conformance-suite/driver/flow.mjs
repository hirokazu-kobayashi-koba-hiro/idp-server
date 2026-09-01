/*
 * サインイン画面を進める処理。常駐ドライバ（driver.mjs）とデモ録画（demo.mjs）で共有する。
 *
 * 署名カウンタの扱いなど間違えやすい処理があるため、実装を 1 か所に集約している。
 */
import fs from "node:fs";
import { emailVerificationCode } from "./idp-api.mjs";

export const here = (name) => new URL(name, import.meta.url).pathname;

export const CONFIG = {
  // メールアドレスを変えると新規ユーザー扱いになり、passkey は登録からやり直しになる。
  // その場合は passkeyFile も消すこと（サーバ側の鍵と食い違うとクローン検知に当たる）。
  email: process.env.DRIVER_EMAIL || "conformance-driver@example.com",
  passkeyFile: process.env.DRIVER_PASSKEY_FILE || here("passkey.json"),

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

/**
 * テストごとの振る舞い。
 *
 * 大半のテストは「ログインして同意する」で通るが、一部は違う操作を要求する。
 * 何を要求されているかはテスト名でしか判別できないため、ここに列挙する。
 *
 *   consent    "allow"(既定)    同意画面で Continue を押す
 *              "deny"           Cancel を押してエラーを返させる
 *   firstVisit "complete"(既定) 毎回ログインまで完遂する
 *              "abandon"        最初の訪問はログインせず離脱し、2 回目で完遂する
 */
export const BEHAVIORS = [
  {
    // "the tester MUST press 'cancel' on the login screen or deny consent
    //  so that an error is returned to the relying party"
    match: /user-rejects-authentication/,
    consent: "deny",
  },
  {
    // 認可が完了する前なら request_uri を再利用できることの確認。
    // "The user was authenticated on the initial visit to login page.
    //  This must not be attempted until the second visit."
    match: /par-ensure-reused-request-uri-prior-to-auth-completion/,
    firstVisit: "abandon",
  },
];

export const behaviorFor = (testName) =>
  BEHAVIORS.find((b) => b.match.test(testName ?? "")) ?? {};

/**
 * 仮想オーセンティケータを載せる。財務テナントの fido2 設定に合わせて
 * platform / resident key / user verification を有効にする。
 *
 * 仮想オーセンティケータはコンテキストごとに空なので、保存済みの passkey があれば注入する。
 * 無ければ画面側が登録フローを出すので、登録された鍵を後で書き出して次回から使う。
 */
export async function attachVirtualAuthenticator(context, page) {
  const cdp = await context.newCDPSession(page);
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

  if (fs.existsSync(CONFIG.passkeyFile)) {
    const credential = JSON.parse(fs.readFileSync(CONFIG.passkeyFile, "utf8"));
    await cdp.send("WebAuthn.addCredential", { authenticatorId, credential });
  }
  return { cdp, authenticatorId };
}

/**
 * 仮想オーセンティケータの資格情報をファイルへ書き戻す。
 *
 * 初回は登録された鍵の保存、2 回目以降は署名カウンタの更新が目的。idp-server は
 * WebAuthn §6.1.1 のクローン検知（WebAuthn4jAuthenticationExecutor:117）を実装しており、
 * 署名カウンタが前回以下だと "Failed to verify authentication data" で弾かれる。
 * 毎回同じ値を注入するとカウンタが巻き戻り、2 回目の認証が必ず失敗する。
 */
export async function savePasskey(cdp, authenticatorId, log = () => {}) {
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
export async function signIn(page, behavior = {}, log = () => {}) {
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

  // 同意。deny のテストでは Cancel を押し、access_denied を返させる。
  const deny = behavior.consent === "deny";
  const button = page.getByRole("button", { name: deny ? "Cancel" : "Continue" });
  await button.waitFor({ state: "visible", timeout: 30000 });
  log(`    passkey ok ("${label}")`);

  await button.click();
  await page.waitForURL(/localhost\.emobix\.co\.uk/, { timeout: 30000 });
  log(`    ${deny ? "cancel" : "consent"} ok -> callback`);
}
