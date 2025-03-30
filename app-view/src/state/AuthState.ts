import { atomWithStorage, createJSONStorage } from "jotai/utils";

const sessionStorage =
  typeof window !== "undefined"
    ? createJSONStorage(() => window.sessionStorage)
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
