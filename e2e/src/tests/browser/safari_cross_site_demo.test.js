import { afterAll, beforeAll, describe, expect, it } from "@jest/globals";
import axios from "axios";
import { Builder, By, until } from "selenium-webdriver";
import safari from "selenium-webdriver/safari";

/**
 * sample-web の認可画面の構成デモ（/cross-site-demo）を、実機 Safari で一周する。
 *
 * 別サイトの認可画面（auth.idp.local）から idp-server への呼び出しはすべてサードパーティになり、
 * Safari は Cookie を送りも保存もしない。それでも
 *
 *   1. サインインから RP への復帰まで通り、ID Token に sid が入る（OP セッションが残っている）
 *   2. もう一度ログインすると、資格情報を入れずに既存の OP セッションで戻ってくる（SSO）
 *
 * ことを確かめる。Safari 固有の既定値なので、ここだけ実機を使う。
 *
 * 実行には Safari 側の準備が要るため、既定ではスキップする:
 *
 *   1. Safari > 設定 > 詳細 >「Web デベロッパ用の機能を表示」
 *   2. 開発メニュー >「リモートオートメーションを許可」
 *   3. SAFARI_E2E=1 npm test -- src/tests/browser/safari_cross_site_demo
 *
 * 前提: docker compose のローカル環境が上がっていて、次を実行済みであること。
 *
 *   ./config/examples/standard-oidc-web-app/setup.sh
 *   ./config/examples/standard-oidc-web-app/setup-cross-site-client.sh
 */
const API = "https://api.local.test";
const TENANT_ID = "a1b2c3d4-5e6f-7a8b-9c0d-1e2f3a4b5c6d";
const CROSS_SITE_CLIENT_ID = "3c7e1a52-9b4d-4f0e-8a61-2d5b7c9e4f13";
const RP = "https://sample.local.test";
const DEMO = `${RP}/cross-site-demo`;
const AUTH_VIEW_HOST = "auth.idp.local";

const TIMEOUT = 30_000;

const describeSafari = process.env.SAFARI_E2E ? describe : describe.skip;

/**
 * サインインに使うユーザーを API で作る。サインアップ（prompt=create）を authorize まで進めると
 * ユーザーが保存される。ブラウザは使わない。
 */
const signUp = async () => {
  const email = `safari-demo-${Date.now()}@example.com`;
  const password = `SafariDemo${Date.now()}!`;
  const http = axios.create({ maxRedirects: 0, validateStatus: () => true });
  const authorizations = `${API}/${TENANT_ID}/v1/authorizations`;

  const started = await http.get(authorizations, {
    params: {
      client_id: CROSS_SITE_CLIENT_ID,
      response_type: "code",
      scope: "openid profile email",
      redirect_uri: `${RP}/api/auth/callback/idp-server-cross-site`,
      state: "safari-demo-signup",
      prompt: "create",
      view_version: "cross-site",
    },
  });
  const view = new URL(started.headers.location);
  const id = view.searchParams.get("id");
  // 別サイトの認可画面は、URL の fragment で渡された値をヘッダーで付けて呼ぶ
  const headers = {
    "x-view-binding": new URLSearchParams(view.hash.replace(/^#/, "")).get("view_binding"),
  };
  const registered = await http.post(
    `${authorizations}/${id}/initial-registration`,
    { email, password, name: "Safari Demo" },
    { headers }
  );
  expect(registered.status).toBe(200);
  const authorized = await http.post(
    `${authorizations}/${id}/authorize`,
    { auth_proof: registered.data.auth_proof },
    { headers }
  );
  expect(authorized.status).toBe(200);
  return { email, password };
};

describeSafari("cross-site demo on Safari", () => {
  let driver;
  let user;

  beforeAll(async () => {
    user = await signUp();
    driver = await new Builder()
      .forBrowser("safari")
      .setSafariOptions(new safari.Options())
      .build();
    await driver.manage().setTimeouts({ implicit: 0, pageLoad: TIMEOUT });

    // 前の実行の OP セッションと RP のセッションを持ち越さない
    await driver.get(`${API}/${TENANT_ID}/v1/logout?client_id=${CROSS_SITE_CLIENT_ID}`);
    await driver.get(`${RP}/api/auth/signout`);
    const signOut = await driver
      .wait(until.elementLocated(By.css('button[type="submit"], form button')), 8000)
      .catch(() => null);
    if (signOut) {
      await driver.executeScript("arguments[0].click()", signOut);
      await driver.sleep(1000);
    }
  }, 120_000);

  afterAll(async () => {
    if (driver) await driver.quit();
  });

  /**
   * 値が入ったことを確かめてから次へ進む。
   *
   * キー入力は、画面の描画直後だと React に届かず黙って捨てられることがある。ここで確かめたいのは
   * Cookie とリクエストの流れなので、入力の再現度より確実さを取り、React が受け取る形
   * （ネイティブの setter と input イベント）で入れる。
   */
  const typeInto = async (selector, value) => {
    const field = await driver.wait(until.elementLocated(By.css(selector)), TIMEOUT);
    await driver.wait(until.elementIsVisible(field), TIMEOUT);
    for (let attempt = 0; attempt < 10; attempt++) {
      await driver.executeScript(
        `const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value").set;
         setter.call(arguments[0], arguments[1]);
         arguments[0].dispatchEvent(new Event("input", { bubbles: true }));`,
        field,
        value
      );
      await driver.sleep(300);
      if ((await field.getAttribute("value")) === value) return;
    }
    throw new Error(`入力が反映されなかった: ${selector}`);
  };

  /** safaridriver のクリックは React の onClick を起こさないことがあるので、DOM の click() を使う。 */
  const clickText = async (text) => {
    const label = JSON.stringify(text);
    const control = await driver.wait(
      until.elementLocated(By.xpath(`//button[contains(., ${label})] | //a[contains(., ${label})]`)),
      TIMEOUT
    );
    await driver.wait(until.elementIsEnabled(control), TIMEOUT);
    await driver.executeScript("arguments[0].click()", control);
  };

  /** デモページの「いまのログイン」の表から値を読む。 */
  const claim = async (name) => {
    const cell = await driver.wait(
      until.elementLocated(By.xpath(`//tr[td[1][normalize-space()="${name}"]]/td[2]`)),
      TIMEOUT
    );
    return (await cell.getText()).trim();
  };

  const backOnDemo = async () => {
    await driver.wait(async () => (await driver.getCurrentUrl()).startsWith(DEMO), TIMEOUT);
    await driver.wait(
      until.elementLocated(By.xpath("//*[contains(., '別サイトの認可画面（auth_proof）')]")),
      TIMEOUT
    );
  };

  let firstAuthTime;

  it("別サイトの認可画面でサインインし、デモページに戻って sid が入っている", async () => {
    await driver.get(DEMO);
    await clickText("別サイトの認可画面でログイン");

    await driver.wait(until.urlContains(AUTH_VIEW_HOST), TIMEOUT);
    await typeInto('input[type="email"], input[name="email"]', user.email);
    await typeInto('input[type="password"]', user.password);
    await clickText("Continue");

    // 同意画面の Continue が authorize を呼び、/complete へトップレベル遷移する
    await driver.wait(
      until.elementLocated(By.xpath('//*[contains(., "You\'re all set")]')),
      TIMEOUT
    );
    await clickText("Continue");

    await backOnDemo();
    expect(await claim("sid")).not.toContain("なし");
    firstAuthTime = await claim("auth_time");
    expect(firstAuthTime).not.toBe("-");
  }, 180_000);

  it("もう一度ログインすると、資格情報を入れずに既存の OP セッションで戻る", async () => {
    // クリック前のページを掴んでおき、それが捨てられる（ページ遷移が起きる）のを待つ。
    // URL で「一度離れた」を待つと、SSO の往復が一瞬で終わって戻ってきたときに取り逃がす。
    // 待たなければ、戻りを待つ条件がクリック直後の古いページで満たされ、1 回目の値を読んでしまう。
    const before = await driver.findElement(By.css("body"));
    await clickText("別サイトの認可画面でログイン");
    await driver.wait(until.stalenessOf(before), TIMEOUT);

    // 認可画面は開くが、既存のセッションで続けるので入力欄は出ない。
    // 出た場合は SSO が効いていない（OP セッションが Safari に残っていない）。
    await backOnDemo();
    expect(await claim("sid")).not.toContain("なし");
    expect(await claim("auth_time")).toBe(firstAuthTime);
  }, 180_000);
});
