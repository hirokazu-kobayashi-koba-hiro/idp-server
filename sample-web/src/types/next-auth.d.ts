import { DefaultSession } from "next-auth";

declare module "next-auth" {
  /**
   * Returned by `useSession`, `getSession` and received as a prop on the `SessionProvider` React Context
   */
  interface Session {
    user: {
      sub: string;
    } & DefaultSession["user"];
    accessToken?: string;
    refreshToken?: string;
    idToken?: string;
  }
}

declare module "next-auth/jwt" {
  /** Returned by the `jwt` callback and `auth`, when using JWT sessions */
  interface JWT {
    /** OpenID ID Token */
    accessToken?: string;
    refreshToken?: string;
    idToken?: string;
  }
}
