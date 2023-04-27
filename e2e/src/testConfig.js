export const serverConfig = {
  issuer: "https://server.example.com/123",
  authorizationEndpoint:
    "http://localhost:8080/123/api/debug/v1/authorizations",
  authorizeEndpoint:
    "http://localhost:8080/123/api/debug/v1/authorizations/{id}/authorize",
  tokenEndpoint: "http://localhost:8080/123/api/v1/tokens",
  tokenIntrospectionEndpoint:
    "http://localhost:8080/123/api/v1/tokens/introspection",
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
  requestKey: {
    kty: "EC",
    d: "uj7jNVQIfSCBdiV4A_yVnY8htLZS7nskIXAGIVDb9oM",
    use: "sig",
    crv: "P-256",
    kid: "request_secret_post",
    x: "H4E6D5GqxTrZshUvkG-z0sAWNkbixERVSpm3YjcIU1U",
    y: "413NbE2n5PeQJlG1Nfq_nCbqR_ZKbVAzsyyrmYph7Fs",
    alg: "ES256",
  },
};

export const clientSecretBasicClient = {
  clientId: "s6BhdRkqt3",
  clientSecret: "cf136dc3c1fc93f31185e5885805d",
  redirectUri: "https://client.example.org/callback",
  scope: "account transfers",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
};

export const clientSecretJwtClient = {
  clientId: "clientSecretJwt",
  clientSecret:
    "clientSecretJwtSecret1234567890123456789012345678901234567890123456789012345678901234567890",
  redirectUri: "https://client.example.org/callback",
  scope: "account transfers",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
};
