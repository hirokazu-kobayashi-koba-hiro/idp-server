export const serverConfig = {
  issuer: "https://server.example.com/123",
  authorizationEndpoint:
    "http://localhost:8080/123/api/debug/v1/authorizations",
  authorizeEndpoint:
    "http://localhost:8080/123/api/debug/v1/authorizations/{id}/authorize",
  denyEndpoint:
    "http://localhost:8080/123/api/debug/v1/authorizations/{id}/deny",
  tokenEndpoint: "http://localhost:8080/123/api/v1/tokens",
  tokenIntrospectionEndpoint:
    "http://localhost:8080/123/api/v1/tokens/introspection",
  tokenRevocationEndpoint: "http://localhost:8080/123/api/v1/tokens/revocation",
  userinfoEndpoint: "http://localhost:8080/123/api/v1/userinfo",
  jwksEndpoint: "http://localhost:8080/123/api/v1/jwks",
  backchannelAuthenticationEndpoint:
    "http://localhost:8080/123/api/v1/backchannel/authentications",
  backchannelAuthenticationAutomatedCompleteEndpoint:
    "http://localhost:8080/123/api/v1/backchannel/authentications/automated-complete",
  discoveryEndpoint:
    "http://localhost:8080/123/.well-known/openid-configuration",
  enabledSsr: false,
  ciba: {
    loginHint: "001",
    userCode: "successUserCode",
    bindingMessage: "999",
    invalidLoginHint: "invalid",
  },
};

export const unsupportedServerConfig = {
  issuer: "https://server.example.com/999",
  authorizationEndpoint:
    "http://localhost:8080/999/api/debug/v1/authorizations",
  authorizeEndpoint:
    "http://localhost:8080/999/api/debug/v1/authorizations/{id}/authorize",
  tokenEndpoint: "http://localhost:8080/999/api/v1/tokens",
  tokenIntrospectionEndpoint:
    "http://localhost:8080/999/api/v1/tokens/introspection",
  tokenRevocationEndpoint: "http://localhost:8080/999/api/v1/tokens/revocation",
  userinfoEndpoint: "http://localhost:8080/999/api/v1/userinfo",
  jwksEndpoint: "http://localhost:8080/999/api/v1/jwks",
  backchannelAuthenticationEndpoint:
    "http://localhost:8080/999/api/v1/backchannel/authentications",
  backchannelAuthenticationAutomatedCompleteEndpoint:
    "http://localhost:8080/999/api/v1/backchannel/authentications/automated-complete",
  discoveryEndpoint:
    "http://localhost:8080/999/.well-known/openid-configuration",
  enabledSsr: false,
  ciba: {
    loginHint: "001",
    userCode: "successUserCode",
    bindingMessage: "999",
    invalidLoginHint: "invalid",
  },
};
export const clientSecretPostClient = {
  clientId: "clientSecretPost",
  clientSecret:
    "clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890",
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

export const privateKeyJwtClient = {
  clientId: "privateKeyJwt",
  clientSecret: "privateKeyJwtSecret",
  redirectUri: "https://client.example.org/callback",
  scope: "account transfers",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  clientSecretKey: {
    p: "5tYV_YB0h-OATLkIJlh12EvU1eZj0Q_ttbzCS4b9fAsSYB2w6qMA_0LRT_upIZm_tPm-HmMLec-5b7enPjKj9N1iqADf_8j50tVcp60Ns9zqX4nXtFZ0w39iHJnMEhbmkBAU5oKLP0m_vYcwtExJ_9EC8aV6MrNOHHnQhaCdRZE",
    kty: "RSA",
    q: "w42ArTFuT_LbGJLUQk9xjZDOio9eTkJXehToBE5jLFZTW07BK24kv8Wgr8AgqLRZGeT3yQeNQiipWC-gdhjwSa-yC1ts47GR3iqMTktMCbEjBLK67sWXzs9RytgrmIAkHj6OqgTHzatxiL7KPTkKv42fXrDfj8anlh5PXEs_ER8",
    d: "p05-cz93iMifLJvlJ5J-zNBtCSGMQ9QUE5Xf7PKJaumj9Y_siuDu9LwwERNRS9QRKkD6e3g6lHabd9LzgatyCh7BigJoJMQtV28M7OJTwE3f0kVR5sfG3ba8nkeU82Wy37N-xcX65jBCbewr3GxxfJBrTRWnh2avgWr9u3UG2RVylhhly2o9sqgqGVWcaSffYorE-AFCt2jKry3LjmFmGO5z7eu2Wa4Yc8dfAJ1AUpsPhyzzximc6PMJdddm-Avp8vuUUIF06asB7u3FR2o3IdObgJ0ucbYRXLz9gl9lQXlXlKEP5tfgyRMy1kSRf-8wT5VMsdYa9McPvn7OT0fEIQ",
    e: "AQAB",
    use: "sig",
    kid: "client_secret_key",
    qi: "Qgz3Ht8cvTfljDwjPwH55veLTMYzVtFHUK_dInkqVDeinxrQmdmMztwW2rBNtQSU2v7mooq-mD_764a1BT-AD-iWIA42i-zFkckC4lD2Ur5be9pkz_UKIy7g_jaWIQ2OnOG_2-eICIVsOYBRb3S7dGFEqqhNRYSvI9xkHFS8lbs",
    dp: "WOP2IeGWfkGwRVs3dTS5ZKqG8ju_EmG04zgmBdmcwWiuEc89Mo3Es4dyfP9nOOYw-ar2eFMhty2ztf6d64iqtH-QHyv_Fku5UGQTQwqT7UBUDKhTJUHpwuLJ0EO1Xv3smWtn8QAySRPIP17Q9Y0vLdC59n4HmYCMwtBx6RNZnNE",
    alg: "RS256",
    dq: "FRGo3IAn53s9-d7P-bj_fgYtCqa5vWrOa_vWp6gebUd7wamxjFFYqkTLtEPaAPM1amHOBJ3IrWkeHb875Z14Pigs4aZfDAU0tyAUb9cTATRRlo7_LvyhB4o0wMsbn1mnCo8o5c6QDGu9VsNfnMsJimvi0NvzqlT392eam1IMIHc",
    n: "sFSqsWu2koU69oG67L5wsVGwzkye80Bd9lmOfiSkSTXyc8IKl4gwmj9tjzxxA1pGYi4SKEQaBYNl8JrGhttBcbtraqwaS5Q6jpG24C1z9njUumJWJneA3EJ9Lpun9d3uCA3b_71XnK5Pr-VtwzpU6z8VGNMZhl8rZ5p1L0syMpZ03y5tSWVMntceiqNaFuJCFXGMVSlp6vrVCqpcM4r035tUR-PwjSynpxe7OGlQpHVSvBCbXJJufi0QxIIjdPx2ka586TlvFjVu0QBEcEon_BMrPDWPD1aaAEcSPM9U7fWzlK6btJ8d37TXZ0_rRPQ_tVeZAlDnRclehHkKflkNjw",
  },
};

export const unsupportedClient = {
  clientId: "unsupportedClient",
  clientSecret: "unsupportedClientSecret",
  redirectUri: "https://client.example.org/callback",
  scope: "account transfers",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
};
