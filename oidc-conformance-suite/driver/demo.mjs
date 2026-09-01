/*
 * 適合性テストの動きを 1 本の MP4 にする。
 *
 * 左に OIDF conformance suite の実行ログ画面、右に idp-server のサインイン画面を並べ、
 * 「suite が条件を緑にしていく裏で、ドライバが実ブラウザでログインしている」ことを 1 画面で見せる。
 *
 * 同一コンテキストに 2 ページを同時に開くことで、2 本の録画が同じ時間軸になる。
 * 合成は ffmpeg の hstack。
 *
 * 前提: idp-server / suite / テナントが起動済みで、常駐ドライバは **止めておく**こと
 *       （URL を取り合ってしまうため）。
 *
 * 使い方:
 *   CONFORMANCE_SUITE_DIR=/path/to/conformance-suite node demo.mjs
 *   → demo/fapi1-advanced-happy-path.mp4
 */
import fs from "node:fs";
import { spawn } from "node:child_process";
import { chromium } from "playwright";
import { suite } from "./suite-api.mjs";
import { attachVirtualAuthenticator, behaviorFor, here, savePasskey, signIn } from "./flow.mjs";

const OUT_DIR = here("demo");
const VIDEO_DIR = `${OUT_DIR}/raw`;
const OUTPUT = `${OUT_DIR}/fapi1-advanced-happy-path.mp4`;
const SIZE = { width: 1280, height: 900 };

const log = (line) => process.stdout.write(`${new Date().toISOString().slice(11, 19)} ${line}\n`);

fs.rmSync(VIDEO_DIR, { recursive: true, force: true });
fs.mkdirSync(VIDEO_DIR, { recursive: true });

const run = (cmd, args, opts = {}) =>
  new Promise((resolve, reject) => {
    const child = spawn(cmd, args, { stdio: "inherit", ...opts });
    child.on("error", reject);
    child.on("close", (code) => (code === 0 ? resolve() : reject(new Error(`${cmd} -> ${code}`))));
  });

async function waitForPending(timeoutSeconds = 120) {
  const deadline = Date.now() + timeoutSeconds * 1000;
  while (Date.now() < deadline) {
    for (const testId of await suite.running().catch(() => [])) {
      const b = await suite.browser(testId).catch(() => null);
      if (b?.urls?.length) {
        const info = await suite.info(testId).catch(() => ({}));
        return { testId, testName: info.testName, url: b.urls[0] };
      }
    }
    await new Promise((r) => setTimeout(r, 300));
  }
  throw new Error("no pending browser url");
}

// 1 回のサインインは数秒で終わり、肝心の操作がほとんど映らない。
// デモではひと操作ごとに待たせて見えるようにする。
const browser = await chromium.launch({
  slowMo: Number(process.env.DEMO_SLOWMO || 350),
  args: ["--host-resolver-rules=MAP localhost.emobix.co.uk 127.0.0.1"],
});

// 2 ページを同じコンテキストに置き、同じ瞬間から録画を始める。
const context = await browser.newContext({
  ignoreHTTPSErrors: true,
  viewport: SIZE,
  recordVideo: { dir: VIDEO_DIR, size: SIZE },
});

const suitePage = await context.newPage();
const loginPage = await context.newPage();
const { cdp, authenticatorId } = await attachVirtualAuthenticator(context, loginPage);

await suitePage.goto(`${suite.baseUrl}plans.html`, { waitUntil: "domcontentloaded" });
// 右が真っ白のまま始まると何を待っているのか分からないので、待機中であることを出す。
// （こちらはブラウザが描画するので日本語で問題ない）
await loginPage.setContent(`
  <body style="margin:0;height:100vh;display:flex;align-items:center;justify-content:center;
               font-family:system-ui,-apple-system,'Hiragino Sans',sans-serif;background:#f8fafc;color:#475569">
    <div style="text-align:center">
      <div style="font-size:22px;font-weight:600;color:#1e293b">ブラウザ操作の待機中</div>
      <div style="margin-top:10px;font-size:15px">suite が認可エンドポイントの URL を積むのを待っています</div>
    </div>
  </body>`);

// テスト実行を開始する。run.sh はランナーをコンテナで起動する。
log("テスト実行を開始");
const runner = run(here("../fapi1-advanced/run.sh"), ["--rerun", "1:2"], {
  stdio: "ignore",
  env: process.env,
});

// テストが始まったら suite 側の表示をそのテストのログ画面へ寄せる。
const first = await waitForPending();
log(`拾った: ${first.testName} (${first.testId})`);
await suitePage.goto(`${suite.baseUrl}log-detail.html?log=${first.testId}`, {
  waitUntil: "domcontentloaded",
});

// happy path は 2 回ブラウザを使う（2 クライアント目の JARM 確認）。
let pending = first;
for (let i = 0; i < 2; i++) {
  if (i > 0) pending = await waitForPending();
  await suite.markVisited(pending.testId, pending.url);
  log(`  ${i + 1} 回目の認可`);

  await loginPage.goto(pending.url, { waitUntil: "networkidle", timeout: 60000 });
  await signIn(loginPage, behaviorFor(pending.testName), log);
  await savePasskey(cdp, authenticatorId, log);

  // callback の着地と、suite 側の表示が追いつくのを少し見せる
  await loginPage.waitForTimeout(1500);
  await suitePage.reload({ waitUntil: "domcontentloaded" }).catch(() => {});
  await suitePage.waitForTimeout(1500);
}

await runner.catch(() => {});
const info = await suite.info(first.testId).catch(() => ({}));
log(`結果: ${info.status} / ${info.result}`);

// 最後に結果が見えている状態を数秒残す
await suitePage.reload({ waitUntil: "domcontentloaded" }).catch(() => {});
await suitePage.waitForTimeout(3000);

const suiteVideo = suitePage.video();
const loginVideo = loginPage.video();
await context.close(); // ここで WebM が確定する
await browser.close();

const left = await suiteVideo.path();
const right = await loginVideo.path();
log(`録画: ${left} / ${right}`);

// 左右に並べ、上にラベルを載せる。尺は短い方に合わせず、足りない側は最終フレームで埋める。
log("ffmpeg で合成");
await run("ffmpeg", [
  "-y",
  "-i", left,
  "-i", right,
  "-filter_complex",
  // ラベルは下端に置く。上端は suite 側の自前ヘッダと重なるため。
  // ffmpeg の drawtext は既定フォントで日本語を描けず豆腐になるので、英字のみにする。
  [
    "[0:v]scale=960:-2,tpad=stop_mode=clone:stop_duration=10," +
      "drawtext=text='OIDF conformance suite - runs the tests'" +
      ":x=16:y=h-46:fontsize=22:fontcolor=white:box=1:boxcolor=0x1f2937@0.9:boxborderw=10[l]",
    "[1:v]scale=960:-2,tpad=stop_mode=clone:stop_duration=10," +
      "drawtext=text='Playwright driver - signs in for a human'" +
      ":x=16:y=h-46:fontsize=22:fontcolor=white:box=1:boxcolor=0x1d4ed8@0.9:boxborderw=10[r]",
    "[l][r]hstack=inputs=2,scale=1600:-2,format=yuv420p[v]",
  ].join(";"),
  "-map", "[v]",
  "-shortest",
  "-r", "12",
  "-movflags", "+faststart",
  OUTPUT,
]);

const mb = (fs.statSync(OUTPUT).size / 1024 / 1024).toFixed(1);
log(`完成: ${OUTPUT} (${mb} MB)`);
