export const serverConfig = {
  issuer: "https://server.example.com/123",
  authorizationEndpoint:
    "http://localhost:8080/123/api/debug/v1/authorizations",
  authorizeEndpoint:
    "http://localhost:8080/123/api/debug/v1/authorizations/{id}/authorize",
  tokenEndpoint: "http://localhost:8080/123/api/v1/tokens",
  tokenIntrospectionEndpoint: "http://localhost:8080/123/api/v1/tokens/introspection",
  tokenRevocationEndpoint: "http://localhost:8080/123/api/v1/tokens/revocation",
  userinfoEndpoint: "http://localhost:8080/123/api/v1/userinfo",
  enabledSsr: false,
};
export const clientSecretPostClient = {
  clientId: "clientSecretPost",
  clientSecret: "clientSecretPostPassword",
  redirectUri: "https://client.example.org/callback",
  scope: "account transfers",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
};

export const clientSecretBasicClient = {
  clientId: "s6BhdRkqt3",
  clientSecret: "cf136dc3c1fc93f31185e5885805d",
  redirectUri: "https://client.example.org/callback",
  scope: "account transfers",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
};
