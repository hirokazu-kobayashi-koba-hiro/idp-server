export const serverConfig = {
  issuer: "https://server.example.com/123",
  authorizationEndpoint: "http://localhost:8080/123/api/debug/v1/authorizations",
  authorizeEndpoint: "http://localhost:8080/123/api/debug/v1/authorizations/{id}/authorize",
  tokenEndpoint: "http://localhost:8080/123/api/v1/tokens",
  enabledSsr: false,
};
export const clientSecretPostClient = {
  clientId: "s6BhdRkqt3",
  clientSecret: "cf136dc3c1fc93f31185e5885805d",
  redirectUri: "https://client.example.org/callback",
};