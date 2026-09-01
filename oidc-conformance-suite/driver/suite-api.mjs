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

function request(method, path, { searchParams } = {}) {
  const url = new URL(path, SUITE);
  if (searchParams) url.search = new URLSearchParams(searchParams).toString();

  return new Promise((resolve, reject) => {
    const req = https.request(
      url,
      { method, agent: suiteAgent, headers: { Accept: "application/json" } },
      (res) => {
        let body = "";
        res.on("data", (chunk) => (body += chunk));
        res.on("end", () => resolve({ status: res.statusCode, body }));
      },
    );
    req.on("error", reject);
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
  markVisited: async (testId, url) => {
    const { status, body } = await request("POST", `/api/runner/browser/${testId}/visit`, {
      searchParams: { url },
    });
    if (status !== 204) throw new Error(`visit -> ${status}: ${body.slice(0, 200)}`);
  },
};
