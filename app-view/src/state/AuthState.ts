import { atomFamily, atomWithStorage, createJSONStorage } from "jotai/utils";

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
 *
 * <p>Keyed by authorization request id. One tab can run several flows in a row, against different
 * tenants — signing in to an application, then to the external provider it links — and a single
 * key carried the first flow's address into the next one's sign-in form.
 */
export const authIdentifierAtom = atomFamily((id: string) =>
  atomWithStorage(`authIdentifier:${id}`, "", sessionStorage, {
    getOnInit: true,
  }),
);

/**
 * User status from the last successful authentication response (`user.status`, e.g.
 * "INITIALIZED" / "REGISTERED").
 *
 * Persisted so later steps can adapt — e.g. a just-initialized user has no phone number on file
 * yet, so the SMS step must let them enter one even as a 2nd factor. Keyed by authorization request
 * id like {@link authIdentifierAtom}: another flow's user says nothing about this one's.
 */
export const authUserStatusAtom = atomFamily((id: string) =>
  atomWithStorage(`authUserStatus:${id}`, "", sessionStorage, {
    getOnInit: true,
  }),
);
