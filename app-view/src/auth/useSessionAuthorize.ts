import { useEffect, useRef, useState } from "react";
import { backendUrl } from "@/pages/_app";
import { ViewData } from "./types";

/**
 * Completes the authorization with the existing OP session, without showing the sign-in screen.
 *
 * OpenID Connect Core allows the OP to reuse an existing authentication instead of prompting again
 * (§3.1.2.1). Whether that is allowed for this request is decided **by the server** and surfaced as
 * `view-data.session_enabled`: it is false when there is no active session, when `prompt=login`
 * forces re-authentication, when `max_age` has elapsed, or when `acr_values` does not match the
 * session. The screen only obeys that flag — it must not derive the condition itself, or the two
 * would drift apart.
 *
 * Without this, every authorization renders the sign-in screen even when a valid session exists, so
 * `auth_time` changes on each request. The OIDF conformance test `oidcc-max-age-10000` fails on
 * exactly that (`CheckIdTokenAuthTimeClaimsSameIfPresent`).
 *
 * **The decision is taken from the first view-data only.** The screen refetches view-data as the
 * flow advances, and authenticating in this very transaction creates a session — so a later fetch
 * reports `session_enabled: true` even though the user is midway through signing in. Acting on that
 * would complete the authorization behind the user's back and skip the consent screen they were
 * about to see.
 *
 * The endpoint answers with the redirect back to the client, so a success navigates away from this
 * page and nothing else needs to render.
 */
export const useSessionAuthorize = (
  tenantId: string,
  id: string,
  viewData: ViewData | undefined,
) => {
  const [authorizing, setAuthorizing] = useState(false);
  // Latches on the first view-data. Also guards against React StrictMode running effects twice,
  // which would consume the transaction with a second authorization.
  const decidedRef = useRef(false);

  useEffect(() => {
    if (!viewData || !tenantId || !id) return;
    if (decidedRef.current) return;
    decidedRef.current = true;

    if (viewData.session_enabled !== true) return;
    setAuthorizing(true);

    const authorize = async () => {
      const response = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/authorize-with-session`,
        { method: "POST", credentials: "include" },
      );
      if (!response.ok) {
        // The session turned out to be unusable after all (expired between view-data and here, or
        // rejected by the server-side verification). Fall back to the normal sign-in screen.
        setAuthorizing(false);
        return;
      }
      const body = await response.json();
      if (typeof body?.redirect_uri === "string") {
        window.location.href = body.redirect_uri;
        return;
      }
      setAuthorizing(false);
    };

    authorize().catch(() => setAuthorizing(false));
  }, [tenantId, id, viewData]);

  return authorizing;
};
