import NextAuth from "next-auth";

export const issuer = process.env.NEXT_PUBLIC_IDP_SERVER_ISSUER;
export const frontendUrl = process.env.NEXT_PUBLIC_FRONTEND_URL;

const IdpServer = (options: any) => ({
  ...{
    id: "idp-server",
    name: "IdPServer",
    type: "oidc",
    version: "2.0",
    wellKnown: `${issuer}/.well-known/openid-configuration`,
    idToken: false,
    authorization: {
      url: `${issuer}/v1/authorizations`,
      params: {
        scope: "openid profile phone email address",
        client_id: process.env.NEXT_PUBLIC_IDP_CLIENT_ID,
        response_type: "code",
      },
    },
    checks: ["pkce", "state"],
    token: {
      async request(context: any) {
        console.log("------------- token request -----------------");
        const { code } = context.params;
        const params = new URLSearchParams({
          grant_type: "authorization_code",
          code,
          redirect_uri: `${frontendUrl}/api/auth/callback/idp-server`,
          client_id: process.env.NEXT_PUBLIC_IDP_CLIENT_ID as string,
        });
        const response = await fetch(`${issuer}/api/v1/tokens`, {
          method: "POST",
          headers: {
            "Content-Type": "application/x-www-form-urlencoded",
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
      async request(context: any) {
        console.log(context.params);
        const { access_token, refresh_token, expires_at, id_token } =
          context.params;

        const response = await fetch(`${issuer}/api/v1/userinfo`, {
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
    profile: (profile: any) => {
      return {
        id: profile.sub,
        ...profile,
      };
    },
  },
  ...options,
});

export const { handlers, auth } = NextAuth({
  providers: [
    IdpServer({
      clientId: process.env.NEXT_PUBLIC_IDP_CLIENT_ID,
      clientSecret: process.env.NEXT_IDP_CLIENT_SECRET,
      issuer: process.env.NEXT_PUBLIC_IDP_SERVER_ISSUER,
    }),
  ],
  callbacks: {
    async jwt({ token, account }) {
      console.log("--------------- jwt ----------------");
      console.log(token);
      console.log(account);
      if (account) {
        token.accessToken = account.access_token;
      }

      return token;
    },
    async session({ session, token, trigger, newSession }) {
      console.log("------------- session -----------------");
      // Note, that `rest.session` can be any arbitrary object, remember to validate it!
      console.log(session, token, trigger, newSession);

      session.accessToken = token.accessToken;
      return session;
    },
  },
  events: {
    async signOut() {
      console.log("------------- signOut -----------------");

      await fetch(
        `${issuer}/v1/logout?client_id=${process.env.NEXT_PUBLIC_IDP_CLIENT_ID}`,
        {
          method: "GET",
          credentials: "include",
        },
      );
    },
  },
  session: {
    strategy: "jwt",
    maxAge: 3600,
  },
});
