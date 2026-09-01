/*
 * サインイン画面を進める処理。常駐ドライバ（driver.mjs）とデモ録画（demo.mjs）で共有する。
 *
 * 署名カウンタの扱いなど間違えやすい処理があるため、実装を 1 か所に集約している。
 */
import fs from "node:fs";
import { emailVerificationCode } from "./idp-api.mjs";

export const here = (name) => new URL(name, import.meta.url).pathname;

/**
 * テナントごとの設定。
 *
 * email の検証コードは管理 API から取るため、対象テナントとその組織管理者が要る。
 * サインイン画面の URL は ?tenant_id= を持っているので、どのスイートのテストかを
 * ドライバ側で判別できる。1 プロセスで FAPI 1.0 / FAPI 2.0 の両方をさばくため、
 * 起動時の固定値ではなくテナント ID で引く。
 *
 * 認証方式はテナントの認証ポリシーで決まるため `signIn` で指定する。
 *
 *   "otp-passkey"  email OTP → Passkey(FIDO2) の 2 段。検証コードを管理 API から取るため
 *                  organizationId / admin が要る
 *   "password"     email + password の 1 段。管理 API を使わないので admin は不要
 *
 * email を変えると新規ユーザー扱いになり、passkey は登録からやり直しになる。
 * その場合は passkeyFileFor() が指すファイルも消すこと
 * （サーバ側に残った鍵と食い違うとクローン検知に当たる）。
 *
 * ここに無いテナントに当たった場合は DEFAULT_TENANT を使う。
 */
export const TENANTS = {
  // config/examples/financial-grade/setup.sh（FAPI 1.0 Advanced / FAPI-CIBA）
  "c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8": {
    label: "financial-grade",
    signIn: "otp-passkey",
    organizationId: "f1a2b3c4-d5e6-f7a8-b9c0-d1e2f3a4b5c6",
    email: "conformance-driver@example.com",
    admin: {
      tenantId: "e7f8a9b0-c1d2-e3f4-a5b6-c7d8e9f0a1b2",
      username: "fapi-test@example.com",
      password: "FapiCibaTestSecure123!",
      clientId: "c1d2e3f4-a5b6-c7d8-e9f0-a1b2c3d4e5f6",
      clientSecret: "fapi-ciba-admin-secret-change-in-production-minimum-32-characters",
    },
  },
  // config/examples/financial-grade-2.0/setup.sh（FAPI 2.0 SP Final）
  "c3f4a5b6-d7e8-4f9a-0b1c-2d3e4f5a6b7c": {
    label: "financial-grade-2.0",
    signIn: "otp-passkey",
    organizationId: "c1f2a3b4-d5e6-4f7a-8b9c-0d1e2f3a4b5c",
    email: "fapi2-conformance-driver@example.com",
    admin: {
      tenantId: "c2f3a4b5-d6e7-4f8a-9b0c-1d2e3f4a5b6c",
      username: "fapi2-conformance-admin@example.com",
      password: "Fapi2ConformanceSecure123!",
      clientId: "c8f9a0b1-d2e3-4f4a-5b6c-7d8e9f0a1b2c",
      clientSecret: "fapi2-conformance-admin-secret-change-in-production-minimum-32-characters",
    },
  },
  // config/examples/oidcc-cross-site/setup.sh（OpenID Connect Core / Basic OP）
  "e8c169c2-019f-46c9-af39-7be12ec51e4d": {
    label: "oidcc-cross-site",
    signIn: "password",
    email: "oidcc-test@example.com",
    password: "OidccTestPassword123!",
  },
  // config/examples/oidcc-formpost-basic/setup.sh（Form Post OP）
  "d2e3f4a5-b6c7-8901-def0-234567890123": {
    label: "oidcc-formpost-basic",
    signIn: "password",
    email: "oidcc-test@example.com",
    password: "OidccTestPassword123!",
  },
  // config/examples/oidcc-cross-site-context-path/setup.sh（コンテキストパス付きデプロイ）
  "76ec54ab-8923-468d-b04b-0d8d0a5eaade": {
    label: "oidcc-cross-site-context-path",
    signIn: "password",
    email: "context-path-test@example.com",
    password: "TestPassword123!",
  },
};

const DEFAULT_TENANT = "c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8";

export function tenantConfigFor(tenantId) {
  const found = TENANTS[tenantId];
  if (found) return { tenantId, ...found };
  return { tenantId: DEFAULT_TENANT, ...TENANTS[DEFAULT_TENANT] };
}

/**
 * 認可エンドポイントの URL からテナント ID を取り出す。
 * 形は https://api.local.test/{tenantId}/v1/authorizations?... で、先頭のパスセグメント。
 *
 * サインイン画面へリダイレクトされる前に passkey を注入する必要があるため、
 * ページ遷移を待たずにここで判別する。
 */
export function tenantIdFromAuthorizationUrl(url) {
  try {
    return new URL(url).pathname.split("/").filter(Boolean)[0] ?? null;
  } catch {
    return null;
  }
}

/** そのテナントの認証が Passkey を使うか（使わないテナントでは仮想オーセンティケータが要らない）。 */
export function needsPasskey(tenantId) {
  return tenantConfigFor(tenantId).signIn !== "password";
}

/**
 * テナントごとの passkey 保存先。
 *
 * ユーザーはテナントごとに別なので、登録した資格情報も分けないと
 * 別テナントの鍵を注入してクローン検知や credential_not_found に当たる。
 */
export function passkeyFileFor(tenantId) {
  if (process.env.DRIVER_PASSKEY_FILE) return process.env.DRIVER_PASSKEY_FILE;
  const label = TENANTS[tenantId]?.label ?? "default";
  return here(`passkey-${label}.json`);
}

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
 *   session    "fresh"(既定)    認可ごとに新しいブラウザコンテキストを使う（クッキーを持ち越さない）
 *              "reuse"          同じテスト内ではコンテキストを共有し、セッションを持ち越す
 *   screenshot なし(既定)        スクリーンショットはエラーページのときだけ提出する
 *              "second-login"   2 回目以降のログイン画面を提出する
 *
 * 先に一致したものが採用されるため、具体的なパターンを先に置くこと。
 *
 * session を既定で "fresh" にしているのは、あるテストのセッションが次のテストへ漏れると
 * 適合性テストの独立性が壊れるため。セッションの有無で挙動が変わることを確認するテストだけ
 * "reuse" にする。
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
  {
    // 「2 回目は再認証を求められること」を目視確認するテスト。
    // ExpectSecondLoginPage がスクリーンショットの提出先を作って待つため
    // （OIDCCMaxAge1 / OIDCCPromptLogin の createPlaceholder）、埋めないと
    // 検証がすべて成功していても 240 秒でタイムアウトして UNKNOWN になる。
    // max-age-10000 に一致させないため末尾を固定する。
    match: /oidcc-(prompt-login|max-age-1)$/,
    session: "reuse",
    screenshot: "second-login",
  },
  {
    // 1 回目でログインし、2 回目は既存セッションでの挙動を見るテスト群。
    // クッキーを持ち越さないと 2 回目が login_required になる。
    match: /oidcc-(prompt-none-logged-in|max-age-10000|id-token-hint)/,
    session: "reuse",
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
export async function attachVirtualAuthenticator(context, page, passkeyFile) {
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

  if (fs.existsSync(passkeyFile)) {
    const credential = JSON.parse(fs.readFileSync(passkeyFile, "utf8"));
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
export async function savePasskey(cdp, authenticatorId, passkeyFile, log = () => {}) {
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

  const previous = fs.existsSync(passkeyFile)
    ? JSON.parse(fs.readFileSync(passkeyFile, "utf8"))
    : null;
  const unchanged =
    previous && previous.credentialId === next.credentialId && previous.signCount === next.signCount;
  if (unchanged) return; // 認証まで進まなかった回。書き戻す必要がない

  fs.writeFileSync(passkeyFile, JSON.stringify(next, null, 2));
  log(`    passkey 保存 (signCount ${previous?.signCount ?? "-"} -> ${next.signCount})`);
}

/** email + password の 1 段（oidcc 系テナント）。 */
async function signInWithPassword(page, tenant, log) {
  await page.getByLabel("Email").fill(tenant.email);
  // "Password" のラベルは入力欄と「表示」ボタンの両方に付くので、textbox に限定する。
  await page.getByRole("textbox", { name: "Password" }).fill(tenant.password);
  await page.getByRole("button", { name: "Continue" }).click();
  log("    password ok");
}

/** email OTP → Passkey の 2 段（financial-grade 系テナント）。 */
async function signInWithOtpAndPasskey(page, tenant, authorizationId, log) {
  await page.getByLabel("Email").fill(tenant.email);
  await page.getByRole("button", { name: "Send code" }).click();
  await page.waitForTimeout(2000);

  const code = await emailVerificationCode({
    admin: tenant.admin,
    organizationId: tenant.organizationId,
    tenantId: tenant.tenantId,
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
  log(`    passkey ok ("${label}")`);
}

const CALLBACK = /localhost\.emobix\.co\.uk/;

/** サインイン画面を最後まで進めて、suite の callback に戻す。 */
export async function signIn(page, behavior = {}, log = () => {}) {
  // 画面を出さずに callback へ直行する回。セッションを引き継いで認証が省略された場合と、
  // prompt=none でセッションが無く login_required が返った場合の両方がある。
  if (CALLBACK.test(page.url())) {
    const error = new URL(page.url()).searchParams.get("error");
    log(error ? `    画面なしで callback (${error})` : "    セッションで認証済み -> callback");
    return;
  }

  const url = new URL(page.url());
  const authorizationId = url.searchParams.get("id");
  if (!authorizationId) throw new Error(`no authorization id in ${page.url()}`);

  // どのテナントのサインインかは URL が持っている。1 プロセスで複数スイートを
  // さばけるよう、起動時の固定値ではなくここで引く。
  const tenant = tenantConfigFor(url.searchParams.get("tenant_id"));

  // セッションが残っていると同意画面から始まる。その場合は認証をやり直さない。
  const emailField = page.getByLabel("Email").first();
  if (await emailField.isVisible().catch(() => false)) {
    if (tenant.signIn === "password") {
      await signInWithPassword(page, tenant, log);
    } else {
      await signInWithOtpAndPasskey(page, tenant, authorizationId, log);
    }
  } else {
    log("    セッションで認証済み（同意から再開）");
  }

  // 同意。deny のテストでは Cancel を押し、access_denied を返させる。
  const deny = behavior.consent === "deny";
  const button = page.getByRole("button", { name: deny ? "Cancel" : "Continue" });
  await button.waitFor({ state: "visible", timeout: 30000 });

  await button.click();
  await page.waitForURL(CALLBACK, { timeout: 30000 });
  log(`    ${deny ? "cancel" : "consent"} ok -> callback`);
}
