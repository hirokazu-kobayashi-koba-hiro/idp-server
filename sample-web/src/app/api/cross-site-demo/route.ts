import { NextRequest, NextResponse } from "next/server";

const internalIssuer =
  process.env.IDP_SERVER_INTERNAL_ISSUER || process.env.NEXT_PUBLIC_IDP_SERVER_ISSUER || "";
const frontendUrl = process.env.NEXT_PUBLIC_FRONTEND_URL || "";

/**
 * Replays the authorization flow step by step, from the server, the way a browser would.
 *
 * The server stands in for the browser so every request and response can be shown. What it cannot
 * do on its own is behave like Safari, so that is modelled explicitly with a cookie jar for
 * idp-server: a top-level navigation sends the jar and keeps what the response sets, and so does
 * an XHR from a view on the same site. An XHR from a view on another site does neither, which is
 * what Safari does with third-party cookies. That is the whole difference between the two
 * topologies, and the reason the cross-site flow needs auth_proof and /complete.
 *
 * Each run signs up a new user, so nothing has to exist beforehand.
 */

type Topology = "same-site" | "cross-site";

type Scenario =
  | "same-site"
  | "cross-site"
  | "no-proof"
  | "reuse-proof"
  | "wrong-stage"
  | "complete-twice";

interface Step {
  title: string;
  kind: "top-level" | "xhr" | "backchannel";
  request: string;
  cookie: "sent" | "not sent" | "-";
  status: number;
  expected: number;
  ok: boolean;
  note: string;
  details?: Record<string, unknown>;
}

const TOPOLOGY_OF: Record<Scenario, Topology> = {
  "same-site": "same-site",
  "cross-site": "cross-site",
  "no-proof": "cross-site",
  "reuse-proof": "cross-site",
  "wrong-stage": "cross-site",
  "complete-twice": "cross-site",
};

const CLIENTS: Record<Topology, { clientId: string; provider: string; params: Record<string, string> }> =
  {
    "same-site": {
      clientId: process.env.NEXT_PUBLIC_IDP_CLIENT_ID || "",
      provider: "idp-server",
      params: {},
    },
    "cross-site": {
      clientId: process.env.NEXT_PUBLIC_IDP_CROSS_SITE_CLIENT_ID || "",
      provider: "idp-server-cross-site",
      params: { view_version: "cross-site" },
    },
  };

class BrowserStandIn {
  steps: Step[] = [];
  /** idp-server's cookies as this browser holds them, name to value. */
  jar = new Map<string, string>();

  constructor(readonly topology: Topology) {}

  /** Whether this kind of request is first-party to idp-server in a real browser. */
  private firstParty(kind: Step["kind"]): boolean {
    if (kind === "backchannel") return false;
    if (kind === "top-level") return true;
    return this.topology === "same-site";
  }

  private cookieHeader(): string {
    return Array.from(this.jar, ([name, value]) => `${name}=${value}`).join("; ");
  }

  async call(
    title: string,
    kind: Step["kind"],
    method: "GET" | "POST",
    path: string,
    expected: number,
    note: string,
    body?: Record<string, unknown> | URLSearchParams,
  ): Promise<{ response: Response; json: Record<string, unknown> }> {
    const headers: Record<string, string> = {};
    const firstParty = this.firstParty(kind);
    const withCookie = firstParty && this.jar.size > 0;
    if (withCookie) headers.Cookie = this.cookieHeader();
    if (body instanceof URLSearchParams) {
      headers["Content-Type"] = "application/x-www-form-urlencoded";
    } else if (body) {
      headers["Content-Type"] = "application/json";
    }

    const response = await fetch(`${internalIssuer}${path}`, {
      method,
      headers,
      redirect: "manual",
      body: body instanceof URLSearchParams ? body : body ? JSON.stringify(body) : undefined,
    });

    // A third-party response's cookies are dropped, not stored — the other half of what Safari does.
    const setCookies = response.headers.getSetCookie?.() ?? [];
    if (firstParty) {
      for (const cookie of setCookies) {
        const [pair, ...attributes] = cookie.split(";");
        const index = pair.indexOf("=");
        const name = pair.slice(0, index).trim();
        const value = pair.slice(index + 1).trim();
        const expired = attributes.some((a) => /^\s*max-age=0\s*$/i.test(a)) || value === "";
        if (expired) this.jar.delete(name);
        else this.jar.set(name, value);
      }
    }
    const dropped = !firstParty && kind !== "backchannel" && setCookies.length > 0;

    let json: Record<string, unknown> = {};
    const text = await response.text();
    try {
      json = text ? JSON.parse(text) : {};
    } catch {
      json = {};
    }

    this.steps.push({
      title,
      kind,
      request: `${method} ${path.replace(/auth_proof=[^&]+/, "auth_proof=…")}`,
      cookie: kind === "backchannel" ? "-" : withCookie ? "sent" : "not sent",
      status: response.status,
      expected,
      ok: response.status === expected,
      note,
      details: {
        ...summarize(response, json),
        ...(setCookies.length > 0
          ? {
              set_cookie: setCookies.map((c) => c.split("=")[0]).join(", "),
              stored: dropped ? "保存されない（サードパーティ）" : "保存される",
            }
          : {}),
      },
    });
    return { response, json };
  }
}

function summarize(response: Response, json: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  const location = response.headers.get("location");
  if (location) out.location = redact(location);
  if (typeof json.auth_proof === "string") out.auth_proof = "（発行された）";
  if (typeof json.redirect_uri === "string") out.redirect_uri = redact(json.redirect_uri);
  if (typeof json.error === "string") out.error = json.error;
  if (typeof json.error_description === "string") out.error_description = json.error_description;
  return out;
}

/** Codes and ids are shortened so the table stays readable. */
function redact(url: string): string {
  return url.replace(/(code|id|state)=([^&]{8})[^&]*/g, "$1=$2…");
}

function random(bytes = 32): string {
  const array = new Uint8Array(bytes);
  crypto.getRandomValues(array);
  return Buffer.from(array).toString("base64url");
}

async function challengeOf(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  return Buffer.from(new Uint8Array(digest)).toString("base64url");
}

function decodeJwt(jwt: string): Record<string, unknown> {
  const payload = jwt.split(".")[1] ?? "";
  return JSON.parse(Buffer.from(payload, "base64url").toString("utf8"));
}

async function run(scenario: Scenario): Promise<{ steps: Step[]; idToken?: Record<string, unknown> }> {
  const topology = TOPOLOGY_OF[scenario];
  const client = CLIENTS[topology];
  const browser = new BrowserStandIn(topology);
  const redirectUri = `${frontendUrl}/api/auth/callback/${client.provider}`;
  const verifier = random();

  // 1. RP sends the browser to the authorization endpoint. Always a top-level navigation.
  const start = await browser.call(
    "認可リクエスト（RP → idp-server）",
    "top-level",
    "GET",
    `/v1/authorizations?${new URLSearchParams({
      client_id: client.clientId,
      response_type: "code",
      scope: "openid profile email",
      redirect_uri: redirectUri,
      state: random(16),
      nonce: random(16),
      code_challenge: await challengeOf(verifier),
      code_challenge_method: "S256",
      prompt: "create",
      ...client.params,
    })}`,
    302,
    "トップレベル遷移なので Cookie は first-party。IDP_AUTH_SESSION がブラウザに保存され、認可画面へ 302 する。",
  );
  const viewUrl = start.response.headers.get("location") ?? "";
  const id = new URL(viewUrl, internalIssuer).searchParams.get("id");
  if (!id) return { steps: browser.steps };

  // 2. The view signs the user up. An XHR from the view.
  const email = `demo-${random(6).toLowerCase()}@example.com`;
  const registered = await browser.call(
    "サインアップ（認可画面 → idp-server）",
    "xhr",
    "POST",
    `/v1/authorizations/${id}/initial-registration`,
    200,
    topology === "same-site"
      ? "同一サイトなので XHR にも Cookie が付き、IDP_AUTH_SESSION で束縛を確かめる。"
      : "別サイトの XHR なので Cookie は付かない。代わりに、資格情報を出したこのブラウザにだけ auth_proof ① を返す。",
    { email, password: `Demo${random(8)}!1`, name: "Cross-site Demo" },
  );
  const proof1 = registered.json.auth_proof as string | undefined;

  if (scenario === "wrong-stage") {
    await browser.call(
      "authorize 用の proof ① を /complete に出す",
      "top-level",
      "GET",
      `/v1/authorizations/${id}/complete?auth_proof=${encodeURIComponent(proof1 ?? "")}`,
      302,
      "2 段の proof は取り違えられない。① は authorize 専用なので /complete では拒否され、RP ではなく idp-server のエラー画面へ遷移する。",
    );
    return { steps: browser.steps };
  }

  // 3. The view asks for the code. Cross-site, the proof has to come along.
  const authorizeBody = scenario === "no-proof" || !proof1 ? {} : { auth_proof: proof1 };
  const authorized = await browser.call(
    scenario === "no-proof" ? "authorize（auth_proof なし）" : "authorize（認可画面 → idp-server）",
    "xhr",
    "POST",
    `/v1/authorizations/${id}/authorize`,
    scenario === "no-proof" ? 400 : 200,
    scenario === "no-proof"
      ? "認証したブラウザだと示すものが無い。認可リクエスト ID を知っているだけの第三者と区別できないので拒否する。"
      : topology === "same-site"
        ? "code 付きの redirect_uri が返り、画面はそのまま RP へ遷移する。"
        : "proof ① を消費する。code はボディに出さず、/complete 用の proof ② の中に入れて返す。",
    authorizeBody,
  );
  if (scenario === "no-proof") return { steps: browser.steps };

  if (scenario === "reuse-proof") {
    await browser.call(
      "同じ proof ① でもう一度 authorize",
      "xhr",
      "POST",
      `/v1/authorizations/${id}/authorize`,
      400,
      "proof は使い捨て。一度消費されたものは二度と使えない。",
      { auth_proof: proof1 },
    );
    return { steps: browser.steps };
  }

  let code: string | null = null;
  if (topology === "same-site") {
    const redirect = authorized.json.redirect_uri as string | undefined;
    code = redirect ? new URL(redirect).searchParams.get("code") : null;
  } else {
    // 4. Back through idp-server as a top-level navigation, where the session cookie can be kept.
    const proof2 = authorized.json.auth_proof as string | undefined;
    const completePath = `/v1/authorizations/${id}/complete?auth_proof=${encodeURIComponent(proof2 ?? "")}`;
    const completed = await browser.call(
      "/complete（トップレベル遷移）",
      "top-level",
      "GET",
      completePath,
      302,
      "first-party なので OP セッションの Cookie を保存できる。遷移先は proof ② の中にあり、URL で指定できるものは無い。",
    );
    const location = completed.response.headers.get("location");
    code = location ? new URL(location).searchParams.get("code") : null;

    if (scenario === "complete-twice") {
      await browser.call(
        "同じ proof ② でもう一度 /complete",
        "top-level",
        "GET",
        completePath,
        302,
        "proof ② も使い捨て。code を二度届けることはできず、RP ではなく idp-server のエラー画面へ遷移する。",
      );
      return { steps: browser.steps };
    }
  }
  if (!code) return { steps: browser.steps };

  // 5. The RP redeems the code. Server to server; shows what the flow left behind.
  const token = await browser.call(
    "トークン要求（RP のサーバー → idp-server）",
    "backchannel",
    "POST",
    "/v1/tokens",
    200,
    "ID Token に sid があれば、この認可は OP セッションに結びついている（バックチャネルログアウトの対象になる）。",
    new URLSearchParams({
      grant_type: "authorization_code",
      code,
      redirect_uri: redirectUri,
      client_id: client.clientId,
      code_verifier: verifier,
    }),
  );
  const idToken = typeof token.json.id_token === "string" ? decodeJwt(token.json.id_token) : undefined;
  return { steps: browser.steps, idToken };
}

export async function POST(request: NextRequest) {
  const { scenario } = (await request.json()) as { scenario: Scenario };
  if (!(scenario in TOPOLOGY_OF)) {
    return NextResponse.json({ error: "unknown scenario" }, { status: 400 });
  }
  try {
    const result = await run(scenario);
    return NextResponse.json({
      scenario,
      topology: TOPOLOGY_OF[scenario],
      ok: result.steps.length > 0 && result.steps.every((s) => s.ok),
      steps: result.steps,
      idToken: result.idToken
        ? {
            sub: result.idToken.sub,
            sid: result.idToken.sid,
            auth_time: result.idToken.auth_time,
            amr: result.idToken.amr,
          }
        : undefined,
    });
  } catch (error) {
    return NextResponse.json(
      { error: error instanceof Error ? error.message : String(error) },
      { status: 500 },
    );
  }
}
