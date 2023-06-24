export const serverConfig = {
  issuer: "http://localhost:8080/123",
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
  oauth: {
    username: "001",
    password: "successUserCode",
  },
  acr: "urn:mace:incommon:iap:silver",
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
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  scope: "account transfers",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  idTokenAlg: "ES256",
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
  requestEncKey: {
    kty: "EC",
    use: "enc",
    crv: "P-256",
    kid: "request_enc_key",
    x: "PM6be42POiKdNzRKGeZ1Gia8908XfmSSbS4cwPasWTo",
    y: "wksaan9a4h3L8R1UMmvc9w6rPB_F07IA-VHx7n7Add4",
    alg: "ECDH-ES"
  },
  requestEnc: "A256GCM",
  requestUri: "",
  invalidRequestUri: "https://invalid.request.uri/request",
  httpRedirectUri: "http://localhost:8081/callback",
};

export const clientSecretPostWithIdTokenEncClient = {
  clientId: "clientSecretPostWithIdTokenEnc",
  clientSecret:
    "clientSecretPostWithIdTokenEncPassword1234567890123456789012345678901234567890123456789012345678901234567890",
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  scope: "account transfers",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  idTokenAlg: "ES256",
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
  requestEncKey: {
    kty: "EC",
    use: "enc",
    crv: "P-256",
    kid: "request_enc_key",
    x: "PM6be42POiKdNzRKGeZ1Gia8908XfmSSbS4cwPasWTo",
    y: "wksaan9a4h3L8R1UMmvc9w6rPB_F07IA-VHx7n7Add4",
    alg: "ECDH-ES"
  },
  idTokenEncKey: {
    p: "6EbwM4mQBqW-ytGVm-73h0VhXEijFRh317Ye4aCPWboMjg5fJRcqRTNxY-xln-k5f2kcLhEDoEsiVlAdNIbZeQhFFVHcmYE7qkOBch4deicPuevvtgqF-bNbH70T1FAGh7EZCK-l-CLzTt0jgRRi-pvRQla3nH5lgGpJ1piyhzU",
    kty: "RSA",
    q: "wcjXiEPd7c8qjrnnaTl_ALtJeeC3UzJS_ZX_D1Fu6T8WtD4CidF34cdsOW_QEAcgNL2y6Yg_HM32XCQbZPENGMxHBfE_RVQ_PYmHJnhgYDYETc6yy9rV_rCdS-GAAmSzbOYzm8GG6Z2rwPNqM9NrGXOnBKvEqtFTLE9bf7P1R6c",
    d: "nQBW241LoMW27oOy4LWrBTplGUhh-4V29MqG-QX6m_aHmrTotCzf-AZ-NuL0EW3dE5tQNPvOu9SkjtGmtwmRXpacJY--JvUZzriWdezQBMR5D_RBGBUyV3Dv_Feg9FbZKsRvsk879FE--dnkgDvdd1qo9gisrueGk9-PMMz4eq1T6qHQnoJr4J1vvI3Ib0mjgAVxc3oumhJL1pr4LEVqlrwwXZGOx8ZbS9CajcYd1PvRxd2fy4dCh9M6twBHp0YQlrFshoxjxqTejSP5l3tbIScxySHaf7BFfRbvcE88uH8gjjgqDJAZ4-eRnRisiKTbiSMx5HuN_0NJybVUIBT1QQ",
    e: "AQAB",
    use: "enc",
    kid: "rsa_enc",
    qi: "faOGV_1Hxj3Nts7o-x5utKYYt8VrpPEGKekX36gUMTK-gBPTOgNmuNOJPz2fbLKRwcJqkOJfPUnzJhZj_C1f4MNfktORVWHX-mqhJIfzLqoCfk2LrEMSEIBlnc8jlogNlmDNlW0aDUbx2TXN2yMw13lGr09YCw_Pkd5JMSwSuOY",
    dp: "buF2RtPzSgkTNBSqm56O0SdAm-Ic37QneXT59vFDnSygU6vupXESf6hYB8BQnu6hwP23MxJyLbHQOW3TE0EQTaOx_sRuT2UOy2-gOo6_uZEuA63qZ3dMj2-cH2GONrrg8yOKdMgMrZBZn5sXGMZXnZSGZ2moCu-Xmp6ikuufxcU",
    alg: "RSA1_5",
    dq: "WPFV-7Uqp3vujJPHIwTAxhUwJEB_5C-0569w4hb-URAj25aak6cQ3xApHDO1y6V5ortu4sEmNpJSAPiRmkMJP9iCwLd50thYLmZxIbcehQpF73BvoCFRFxT5HVri5jZSJCmEhnjM82zq6CTRGfhvr77lab9tBPoOsse5t2NhsQE",
    n: "r9O2Ebn331GuRDhH1Q_DyJbPec_k3BdRjUmmNtx-jl7_D3z82uSdpWMaRV5LMRvQ1L71QeGRuWREVBbFoPNVQJqFmabqOff0QgOXojk13dzshrmgX5F01sDdGLS15QRLLgKQxn71wvQmi3cWDqFS_bDWz-MI4yVvcakm4f27Fxg7H08La81mqe_lS4D2Kq1HMjoDl1QMD01TiLDZ-_TpnC50Ng_qTcLRSGZxtl0k6TfkPoi0La1Ua1Qh5JK-qvNgt0OSmbHFXqJweKr5gi4kZq1u3YrUonU5t7OaF3MshDP1R4KkrDlVd_mNVTNaRSWfg1aE2OX6pW8C8ZEHlK7mkw"
  },
  requestEnc: "A256GCM",
  requestUri: "",
  invalidRequestUri: "https://invalid.request.uri/request",
  httpRedirectUri: "http://localhost:8081/callback",
};

export const clientSecretBasicClient = {
  clientId: "s6BhdRkqt3",
  clientSecret: "cf136dc3c1fc93f31185e5885805d",
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  scope: "account transfers",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  idTokenAlg: "ES256",
};

export const clientSecretJwtClient = {
  clientId: "clientSecretJwt",
  clientSecret:
    "clientSecretJwtSecret1234567890123456789012345678901234567890123456789012345678901234567890",
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  scope: "account transfers",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  idTokenAlg: "ES256",
};

export const privateKeyJwtClient = {
  clientId: "privateKeyJwt",
  clientSecret: "privateKeyJwtSecret",
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  scope: "account transfers",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  idTokenAlg: "ES256",
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

export const selfSignedTlsAuthClient = {
  clientId: "selfSignedTlsClientAuth",
  clientSecret: "selfSignedTlsClientAuth1234567890123456789012345678901234567890123456789012345678901234567890",
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  scope: "account transfers",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  idTokenAlg: "ES256",
  clientCertFile: "selfSignedTlsAuth.pem"
};

export const unsupportedClient = {
  clientId: "unsupportedClient",
  clientSecret: "unsupportedClientSecret",
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  scope: "account transfers",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
};
