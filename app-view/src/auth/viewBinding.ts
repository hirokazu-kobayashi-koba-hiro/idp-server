/**
 * Carries the value that identifies this view to idp-server, where the view is on another site.
 *
 * Same-site, idp-server knows which browser is calling from its binding cookie. Where this view is
 * on another site its calls are third-party and the cookie never reaches them, so idp-server hands
 * this view a value instead, in the fragment of the URL it sends the browser to. A fragment is not
 * sent to any server, so only the script in the browser that started the authorization sees it.
 * The view presents it on every call it makes for that authorization, in a header.
 *
 * Kept in sessionStorage per authorization request id, because the view moves between pages
 * (sign-in, second factor, consent, the external provider and back) and each page load would
 * otherwise lose it. The latest one is also kept, for the federation callback: its URL carries no
 * request id, which idp-server only learns from the external provider's state.
 */
export const VIEW_BINDING_HEADER = "x-view-binding";
const PARAMETER = "view_binding";
const KEY_PREFIX = "idp.view_binding:";
const LATEST = "idp.view_binding:latest";

const read = (key: string): string | undefined => {
  try {
    return window.sessionStorage.getItem(key) ?? undefined;
  } catch {
    return undefined;
  }
};

const held = new Map<string, string>();

const write = (key: string, value: string) => {
  held.set(key, value);
  try {
    window.sessionStorage.setItem(key, value);
  } catch {
    // Kept in memory for as long as this page lives.
  }
};

/**
 * Takes the value out of the fragment the view was opened with. Removing it from the address bar
 * is left to {@link withoutViewBinding}, once the router is ready: the router remembers the URL it
 * was loaded with and writes it back while it hydrates, which would restore the fragment.
 */
export const captureViewBinding = () => {
  if (typeof window === "undefined") return;
  const fragment = new URLSearchParams(window.location.hash.replace(/^#/, ""));
  const value = fragment.get(PARAMETER);
  if (!value) return;
  const id = new URLSearchParams(window.location.search).get("id");
  if (id) write(KEY_PREFIX + id, value);
  write(LATEST, value);
};

/**
 * The path without the value in its fragment, so it is not left in history or copied along with
 * the URL; null when there is nothing to remove.
 */
export const withoutViewBinding = (asPath: string): string | null => {
  const hashAt = asPath.indexOf("#");
  if (hashAt < 0) return null;
  const fragment = new URLSearchParams(asPath.slice(hashAt + 1));
  if (!fragment.has(PARAMETER)) return null;
  fragment.delete(PARAMETER);
  const rest = fragment.toString();
  return asPath.slice(0, hashAt) + (rest ? `#${rest}` : "");
};

/** The value for this authorization request, or the latest one where the id is not known. */
export const viewBindingFor = (id?: string): string | undefined => {
  if (typeof window === "undefined") return undefined;
  if (id) {
    const key = KEY_PREFIX + id;
    const value = held.get(key) ?? read(key);
    if (value) return value;
  }
  return held.get(LATEST) ?? read(LATEST);
};
