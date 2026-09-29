import NextAuth from "next-auth";
import type { OAuthConfig } from "next-auth/providers";

// クライアント側（ブラウザ）用のIssuer URL
export const issuer = process.env.NEXT_PUBLIC_IDP_SERVER_ISSUER;
// サーバーサイド用の内部Issuer URL（Docker内部通信用）
export const internalIssuer = process.env.IDP_SERVER_INTERNAL_ISSUER || issuer;
export const frontendUrl = process.env.NEXT_PUBLIC_FRONTEND_URL;

interface IdpServerOptions {
  id: string;
  name: string;
  clientId?: string;
  clientSecret?: string;
  issuer?: string;
  /** Extra authorization request parameters, e.g. the view variant that selects the view's site. */
  authorizationParams?: Record<string, string>;
}

/**
 * The sign-in with idp-server, parameterised by client.
 *
 * Two are registered below so the same app can show both authorization view topologies:
 * `idp-server` goes through the view on the same site as idp-server, and
 * `idp-server-cross-site` through the view on another site, with a client configured for that.
 */
const IdpServer = ({
  authorizationParams,
  ...options
}: IdpServerOptions): OAuthConfig<Record<string, unknown>> => ({
  ...{
    id: options.id,
    name: options.name,
    type: "oidc",
    version: "2.0",
    wellKnown: `${internalIssuer}/.well-known/openid-configuration`,
    idToken: false,
    authorization: {
      url: `${issuer}/v1/authorizations`,
      params: {
        scope: "openid profile phone email address claims:authentication_devices",
        client_id: options.clientId,
        response_type: "code",
        ...authorizationParams,
      },
    },
    checks: ["pkce", "state"],
    token: {
      async request(context: { params: { code: string } }) {
        console.log("------------- token request -----------------");
        const { code } = context.params;
        const params = new URLSearchParams({
          grant_type: "authorization_code",
          code,
          redirect_uri: `${frontendUrl}/api/auth/callback/${options.id}`,
        });
        const clientId = options.clientId as string;
        const clientSecret = (options.clientSecret ?? "") as string;
        const basicAuth = Buffer.from(`${clientId}:${clientSecret}`).toString("base64");
        const response = await fetch(`${internalIssuer}/v1/tokens`, {
          method: "POST",
          headers: {
            "Content-Type": "application/x-www-form-urlencoded",
            "Authorization": `Basic ${basicAuth}`,
          },
          body: params,
        });
        if (!response.ok) {
          console.log(response);
          return {};
        }

        const body = await response.json();
        console.log("token", body);

        return {
          ...body,
        };
      },
    },
    userinfo: {
      async request(context: {
        params: {
          access_token: string;
          refresh_token: string;
          expires_at: number;
          id_token: string;
        };
      }) {
        console.log(context.params);
        const { access_token, refresh_token, expires_at, id_token } =
          context.params;

        const response = await fetch(`${internalIssuer}/v1/userinfo`, {
          method: "GET",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${access_token}`,
          },
        });
        if (!response.ok) {
          console.log(response);
          return {};
        }

        const body = await response.json();
        console.log("userinfo", body);

        return {
          access_token,
          refresh_token,
          id_token,
          expires_at,
          ...body,
        };
      },
    },
    profile: (profile: Record<string, unknown>) => {
      return {
        id: (profile.sub as string) || "",
        ...profile,
      };
    },
  },
  ...options,
});

export const { handlers, auth, signIn, signOut } = NextAuth({
  providers: [
    IdpServer({
      id: "idp-server",
      name: "IdPServer",
      clientId: process.env.NEXT_PUBLIC_IDP_CLIENT_ID,
      clientSecret: process.env.NEXT_IDP_CLIENT_SECRET,
      issuer: internalIssuer,
    }),
    IdpServer({
      id: "idp-server-cross-site",
      name: "IdPServer (cross-site view)",
      clientId: process.env.NEXT_PUBLIC_IDP_CROSS_SITE_CLIENT_ID,
      clientSecret: process.env.NEXT_IDP_CROSS_SITE_CLIENT_SECRET,
      issuer: internalIssuer,
      // The tenant's "cross-site" variant serves the view from auth.idp.local.
      authorizationParams: { view_version: "cross-site" },
    }),
  ],
  callbacks: {
    async jwt({ token, account }) {
      console.log("--------------- jwt ----------------");
      console.log(token);
      console.log(account);
      if (account) {
        token.provider = account.provider;
        token.accessToken = account.access_token;
        token.refreshToken = account.refresh_token;
        token.idToken = account.id_token;
      }

      return token;
    },
    async session({ session, token, trigger, newSession }) {
      console.log("------------- session -----------------");
      // Note, that `rest.session` can be any arbitrary object, remember to validate it!
      console.log(session, token, trigger, newSession);

      session.accessToken = token.accessToken as string | undefined;
      session.refreshToken = token.refreshToken as string | undefined;
      session.idToken = token.idToken as string | undefined;
      session.provider = token.provider as string | undefined;
      return session;
    },
  },
  events: {
    async signOut() {
      console.log("------------- signOut event -----------------");
      // Note: RP-Initiated Logout はブラウザリダイレクト方式に変更
      // LogoutButton コンポーネントから直接 IDP server の /v1/logout にリダイレクトする
      // これにより Cookie が正しく送信され、セッションが確実に削除される
    },
  },
  session: {
    strategy: "jwt",
    maxAge: 3600,
  },
});
