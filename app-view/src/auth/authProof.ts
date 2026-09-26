/**
 * Carries the one-time value that proves this browser is the one that authenticated.
 *
 * Where this view is served from the same site as idp-server, the flow is held together by
 * cookies and none of this runs. Where it is on another site, every call this view makes is
 * third-party: Safari sends no cookies with them and keeps none of the ones they set, so a cookie
 * cannot say which browser is calling. The server issues a value instead, in the response to the
 * step that succeeded — a response only the browser that supplied the credentials receives — and
 * asks for it back when the authorization code is minted.
 *
 * It is kept in sessionStorage, which belongs to this view's own origin and is therefore
 * first-party — what Safari blocks is this server's cookies on a third-party request, not the
 * view's own storage. Memory alone is not enough: reloading the consent screen would drop the
 * value while the transaction stays authenticated, and the next authorize call would be rejected
 * with no way forward. sessionStorage is scoped to the tab and cleared when it closes.
 *
 * This is installed as a fetch wrapper rather than threaded through each step because the value
 * can arrive from any of them — password, SMS, email, FIDO, federation — and is needed by exactly
 * one. A view written from scratch can do it the other way round: keep whatever `auth_proof` a
 * response carries, and send it in the body of the authorize call.
 */
const AUTHORIZE = /\/v1\/authorizations\/[^/?#]+\/authorize(?:$|[?#])/;

const KEY_PREFIX = "idp.auth_proof:";

const held = new Map<string, string>();

/**
 * 認可リクエストごとに持つ。使ったら捨てる。
 *
 * 1 つのキーに入れると、authorize が返す 2 段目の値が 1 段目を上書きする。/complete への遷移に
 * 失敗して authorize からやり直すと、authorize が受け付けない値だけが残って必ず拒否される。
 * 別のフローの残りを送ってしまうこともある。
 *
 * sessionStorage は private window や site data のブロックで読み書きとも投げるので、必ず包む。
 */
const key = (id: string) => KEY_PREFIX + id;

const read = (id: string): string | undefined => {
  try {
    return window.sessionStorage.getItem(key(id)) ?? undefined;
  } catch {
    return undefined;
  }
};

const store = (id: string, value: string) => {
  held.set(id, value);
  try {
    window.sessionStorage.setItem(key(id), value);
  } catch {
    // メモリ側には載っているので、同じページに留まる限りは動く。
  }
};

/** 読み出しと同時に捨てる。同じ値を二度送らない。 */
const take = (id: string): string | undefined => {
  const value = held.get(id) ?? read(id);
  held.delete(id);
  try {
    window.sessionStorage.removeItem(key(id));
  } catch {
    // 消せなくても、次に来る値で上書きされる。
  }
  return value;
};

/** URL から認可リクエスト ID を取り出す。どのフローの値かはこれで決まる。 */
const requestIdOf = (url: string): string | undefined =>
  url.match(/\/v1\/authorizations\/([^/?#]+)\//)?.[1];

const urlOf = (input: RequestInfo | URL): string => {
  if (typeof input === "string") return input;
  if (input instanceof URL) return input.href;
  return input.url;
};

/**
 * Reads `auth_proof` out of a JSON response without consuming the body the caller will read.
 *
 * Awaited rather than left to settle on its own: a step that leads straight into the authorize
 * call would otherwise race the read and send nothing.
 */
const remember = async (response: Response, id: string) => {
  const type = response.headers.get("content-type") ?? "";
  if (!type.includes("json")) return;
  try {
    const body = await response.clone().json();
    if (body && typeof body.auth_proof === "string" && body.auth_proof) {
      store(id, body.auth_proof);
    }
  } catch {
    // Not JSON after all. Nothing to carry.
  }
};

/** Adds the held value to the authorize body, keeping whatever the caller was already sending. */
const withAuthProof = (
  init: RequestInit | undefined,
  authProof: string,
): RequestInit => {
  let body: Record<string, unknown> = {};
  if (typeof init?.body === "string" && init.body) {
    try {
      const parsed = JSON.parse(init.body);
      if (parsed && typeof parsed === "object")
        body = parsed as Record<string, unknown>;
    } catch {
      // A body we cannot merge into. Leave it alone and let the server reject the call.
      return init;
    }
  }
  // init.headers は Headers / 配列 / オブジェクトのいずれも取りうる。展開すると Headers は
  // 中身が消えるので、Headers に通してから渡す。
  const headers = new Headers(init?.headers);
  headers.set("Content-Type", "application/json");
  return {
    ...init,
    method: init?.method ?? "POST",
    headers,
    body: JSON.stringify({ ...body, auth_proof: authProof }),
  };
};

export const installAuthProofRelay = () => {
  if (typeof window === "undefined") return;
  const flag = "__idpAuthProofRelay";
  if ((window as unknown as Record<string, unknown>)[flag]) return;
  (window as unknown as Record<string, unknown>)[flag] = true;

  const original = window.fetch.bind(window);
  window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = urlOf(input);
    const id = requestIdOf(url);
    const authProof = id && AUTHORIZE.test(url) ? take(id) : undefined;
    const request = authProof ? withAuthProof(init, authProof) : init;
    const response = await original(input, request);
    if (id) await remember(response, id);
    return response;
  };
};
