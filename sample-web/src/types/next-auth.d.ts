// eslint-disable-next-line no-unused-vars
import NextAuth, { DefaultSession } from "next-auth";
// eslint-disable-next-line no-unused-vars
import { JWT } from "next-auth/jwt";

declare module "next-auth" {
  /**
   * Returned by `useSession`, `getSession` and received as a prop on the `SessionProvider` React Context
   */
  // eslint-disable-next-line no-unused-vars
  interface Session {
    user: {
      sub: string;
    } & DefaultSession["user"];
    accessToken?: string;
  }
}

declare module "next-auth/jwt" {
  /** Returned by the `jwt` callback and `auth`, when using JWT sessions */
  // eslint-disable-next-line no-unused-vars
  interface JWT {
    /** OpenID ID Token */
    accessToken?: string;
    idToken?: string;
  }
}
