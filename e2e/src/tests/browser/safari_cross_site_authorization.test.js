import { afterAll, beforeAll, describe, expect, it } from "@jest/globals";
import { Builder, By, until } from "selenium-webdriver";
import safari from "selenium-webdriver/safari";

/**
 * 認可画面が別サイトにある構成を、実機 Safari で一周する。
 *
 * Safari は既定でサードパーティ Cookie を落とす。読むだけでなく保存もしないので、
 * 認可画面から idp-server への fetch では
 *
 *   - ブラウザ束縛 Cookie が送られない  → 認証そのものが通らない
 *   - OP セッション Cookie が保存されない → 連携が使うセッションが残らない
 *
 * という状態になる。これは Safari 固有の既定値なので、他のブラウザや
 * Playwright の webkit ビルドでは再現しない。だからここだけ実機を使う。
 *
 * 実行には Safari 側の準備が要るため、既定ではスキップする:
 *
 *   1. Safari > 設定 > 詳細 >「Web デベロッパ用の機能を表示」
 *   2. 開発メニュー >「リモートオートメーションを許可」
 *   3. SAFARI_E2E=1 npm test -- src/tests/browser
 *
 * 前提: docker compose のローカル環境が上がっていて、public テナントが
 * cross-site 構成になっていること。
 *
 *   ./config/examples/standard-oidc-web-app/switch-auth-view.sh cross-site
 */
const RP = "https://sample.idp.local";
const AUTH_VIEW_HOST = "auth.idp.local";
const EXTERNAL_IDP_HOST = "auth.local.test";

const USER = {
  email: "sample.user@localhost.local",
  password: "LocalDevPassword123",
};

const TIMEOUT = 30_000;

const describeSafari = process.env.SAFARI_E2E ? describe : describe.skip;

describeSafari("cross-site authorization view on Safari", () => {
  let driver;

  beforeAll(async () => {
    driver = await new Builder()
      .forBrowser("safari")
      .setSafariOptions(new safari.Options())
      .build();
    await driver.manage().setTimeouts({ implicit: 0, pageLoad: TIMEOUT });
  }, 60_000);

  afterAll(async () => {
    if (driver) await driver.quit();
  });

  /**
   * 前のテストのセッションを持ち越さない。RP も OP も入口から入り直す。
   *
   * サインアウト画面はクライアント側で描かれるので、ページ遷移の完了だけでは確認ボタンが
   * まだ居ない。待たずに探すと「セッションが無い」と取り違えて素通りし、次のテストが
   * ログイン済みの画面に着いて意味を失う。
   */
  const signOut = async () => {
    await driver.get(`${RP}/api/auth/signout`);

    const button = await driver
      .wait(
        until.elementLocated(By.css('button[type="submit"], form button')),
        8000
      )
      .catch(() => null);
    if (!button) return; // そもそもサインインしていない

    await driver.executeScript("arguments[0].click()", button);
    await driver.wait(
      async () => !(await driver.getCurrentUrl()).includes("/api/auth/signout"),
      TIMEOUT
    );
  };

  /**
   * RP の入口から認可画面まで進める。
   *
   * サインアウトの戻り先は RP のルートで、そこは認証が要るので RP はその場で認可リクエストを
   * 始める。つまり多くの場合すでに認可画面に居る。そこで改めて RP を開くと認可リクエストが
   * もう一本立ち、認可画面が二度描かれて、入力が捨てられる方の画面に当たることがある。
   */
  const openAuthorizationView = async () => {
    if (!(await driver.getCurrentUrl()).includes(AUTH_VIEW_HOST)) {
      await driver.get(`${RP}/`);
    }
    await driver.wait(until.urlContains(AUTH_VIEW_HOST), TIMEOUT);
  };

  /**
   * 値が入ったことを確かめてから次へ進む。
   *
   * 入力欄が現れるのと React が受け取れるようになるのは同時ではないので、要素が見つかった
   * だけで打ち込むと、初回ロードが遅いときに黙って落ちる。打ち直して確かめる。
   */
  const typeInto = async (selector, value) => {
    const field = await driver.wait(
      until.elementLocated(By.css(selector)),
      TIMEOUT
    );
    await driver.wait(until.elementIsVisible(field), TIMEOUT);

    for (let attempt = 0; attempt < 5; attempt++) {
      await field.clear();
      await field.sendKeys(value);
      if ((await field.getAttribute("value")) === value) return;
      await driver.sleep(300);
    }
    throw new Error(`入力が反映されなかった: ${selector}`);
  };

  /**
   * ラベルで押す。MUI の Button は href を渡すと a で描画されるので、どちらも拾う。
   *
   * WebDriver のクリックではなく DOM の click() を使う。safaridriver のクリックは
   * この画面のボタンで React の onClick を起こさないことがあり、何も起きないまま
   * テストだけが進んでしまう。ここで確かめたいのは Cookie とリクエストの流れなので、
   * 押下の再現度より確実に発火することを取る。
   */
  const clickText = async (text) => {
    const label = JSON.stringify(text);
    const xpath = `//button[contains(., ${label})] | //a[contains(., ${label})]`;
    const control = await driver.wait(
      until.elementLocated(By.xpath(xpath)),
      TIMEOUT
    );
    await driver.wait(until.elementIsEnabled(control), TIMEOUT);
    await driver.executeScript("arguments[0].click()", control);
  };

  it("認可画面が別サイトでも、サインインから RP への復帰まで通る", async () => {
    await signOut();

    // RP は idp-server へ飛ばし、idp-server は別サイトの認可画面へ飛ばす。
    await openAuthorizationView();

    await typeInto('input[type="email"], input[name="email"]', USER.email);
    await typeInto('input[type="password"]', USER.password);
    await clickText("Continue");

    // 同意画面。ここの Continue が authorize を呼び、/complete へ遷移する。
    await driver.wait(
      until.elementLocated(By.xpath('//*[contains(., "You\'re all set")]')),
      TIMEOUT
    );
    await clickText("Continue");

    // RP へ戻れていること。ここまで来た時点で authorize が authProof を受け取れており、
    // つまり認証時に渡された値が Cookie 無しで往復できている。
    await driver.wait(until.urlContains("sample.idp.local"), TIMEOUT);
    const url = await driver.getCurrentUrl();
    expect(url).not.toContain("/api/auth/error");
    expect(url).toContain("sample.idp.local");

    // URL だけだと認可に失敗して RP のサインイン画面へ戻された場合も通ってしまう。
    // ログイン後にしか出ない画面が描かれているところまで見る。
    await driver.wait(
      until.elementLocated(By.xpath("//*[contains(., 'ダッシュボード')]")),
      TIMEOUT
    );
  }, 120_000);

  it("OP セッションが残っていて、アカウント連携が外部 IdP まで到達する", async () => {
    // 前のテストでサインイン済み。連携は OP セッションを要求するので、
    // /complete が first-party で Cookie を保存できていなければここで RP に押し戻される。
    await driver.get(`${RP}/linked-accounts`);
    await clickText("外部アカウントを連携する");

    await driver.wait(until.urlContains(EXTERNAL_IDP_HOST), TIMEOUT);
    const url = await driver.getCurrentUrl();

    // 以前はここで RP の認証画面へ戻され、延々と往復していた。
    expect(url).toContain(EXTERNAL_IDP_HOST);
    expect(url).not.toContain("linking=login_required");
  }, 120_000);
});
