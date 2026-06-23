import { atomWithStorage, createJSONStorage } from "jotai/utils";

const sessionStorage =
  typeof window !== "undefined"
    ? createJSONStorage<string>(() => window.sessionStorage)
    : undefined;

export const authSessionIdAtom = atomWithStorage(
  "authSessionId",
  "",
  sessionStorage,
  {
    getOnInit: true,
  },
);

export const authSessionTenantIdAtom = atomWithStorage(
  "authSessionTenantId",
  "",
  sessionStorage,
  {
    getOnInit: true,
  },
);

/**
 * Identifier (email / username) used to identify the user during the flow.
 *
 * <p>Persisted in sessionStorage so it survives reload / direct page access. This is the source for
 * the FIDO2 registration username, which previously lived only in the in-memory AppContext and was
 * lost on reload (Issue #1373).
 */
export const authIdentifierAtom = atomWithStorage(
  "authIdentifier",
  "",
  sessionStorage,
  {
    getOnInit: true,
  },
);
