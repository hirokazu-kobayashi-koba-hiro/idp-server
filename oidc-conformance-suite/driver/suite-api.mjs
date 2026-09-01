/*
 * OIDF conformance suite の REST API クライアント。
 *
 * suite は「ブラウザで訪問してほしい URL」を人間向けに公開している。設定 JSON に browser
 * ブロックが無い（= suite 内蔵の HtmlUnit で自動操作できない）場合、対象の URL はここに積まれ、
 * 誰かがブラウザで踏むのを待つ。driver.mjs はこの API を使って実ブラウザで肩代わりする。
 *
 *   GET  /api/runner/browser/{id}        訪問してほしい URL の一覧
 *   POST /api/runner/browser/{id}/visit  訪問済みとしてマーク
 */
import https from "node:https";

// suite の TLS 証明書はイメージビルド時に CN=localhost で生成される自己署名で、
// アクセス先ホスト名と一致しない。検証を外すのは suite 宛のこの接続に限る
// （プロセス全体の NODE_TLS_REJECT_UNAUTHORIZED は使わない）。
const SUITE = new URL(process.env.SUITE || "https://localhost:8443");
const suiteAgent = new https.Agent({ rejectUnauthorized: false });

function request(method, path, { searchParams, body, contentType } = {}) {
  const url = new URL(path, SUITE);
  if (searchParams) url.search = new URLSearchParams(searchParams).toString();

  const headers = { Accept: "application/json" };
  if (body !== undefined) {
    headers["Content-Type"] = contentType || "text/plain";
    headers["Content-Length"] = Buffer.byteLength(body);
  }

  return new Promise((resolve, reject) => {
    const req = https.request(url, { method, agent: suiteAgent, headers }, (res) => {
      let data = "";
      res.on("data", (chunk) => (data += chunk));
      res.on("end", () => resolve({ status: res.statusCode, body: data }));
    });
    req.on("error", reject);
    if (body !== undefined) req.write(body);
    req.end();
  });
}

async function getJson(path) {
  const { status, body } = await request("GET", path);
  if (status !== 200) throw new Error(`GET ${path} -> ${status}: ${body.slice(0, 200)}`);
  return JSON.parse(body);
}

export const suite = {
  baseUrl: SUITE.toString(),
  running: () => getJson("/api/runner/running"),
  browser: (testId) => getJson(`/api/runner/browser/${testId}`),
  info: (testId) => getJson(`/api/info/${testId}`),
  log: (testId) => getJson(`/api/log/${testId}`),

  markVisited: async (testId, url) => {
    const { status, body } = await request("POST", `/api/runner/browser/${testId}/visit`, {
      searchParams: { url },
    });
    if (status !== 204) throw new Error(`visit -> ${status}: ${body.slice(0, 200)}`);
  },

  /**
   * 「エラーページが表示されたことを目視で確認する」タイプの条件が待っている placeholder を返す。
   *
   * suite はブラウザに見せたい URL を積むだけでなく、結果を人が確認するテストでは
   * REVIEW のログエントリを作り、その `upload` にスクリーンショットの提出先 ID を入れる。
   * これを埋めないとテストは完了せずタイムアウトする。
   */
  pendingReviewPlaceholders: async (testId) => {
    const entries = await getJson(`/api/log/${testId}`).catch(() => []);
    return entries.filter((e) => e.result === "REVIEW" && e.upload).map((e) => e.upload);
  },

  /** スクリーンショット(PNG)を placeholder に提出して REVIEW を満たす。 */
  uploadScreenshot: async (testId, placeholder, pngBuffer) => {
    const encoded = `data:image/png;base64,${pngBuffer.toString("base64")}`;
    const { status, body } = await request("POST", `/api/log/${testId}/images/${placeholder}`, {
      body: encoded,
    });
    if (status !== 200) throw new Error(`upload image -> ${status}: ${body.slice(0, 200)}`);
  },
};
