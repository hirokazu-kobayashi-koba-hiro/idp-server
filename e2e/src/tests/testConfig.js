export const backendUrl = process.env.IDP_SERVER_URL || "http://localhost:8080";

export const serverConfig = {
  issuer: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66`,
  tenantId: "67e7eae6-62b0-4500-9eff-87459f63fc66",
  authorizationEndpoint:
    `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/authorizations`,
  authorizationIdEndpoint:
    `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/authorizations/{id}/`,
  authenticationEndpoint:
    `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/authentication-transactions`,
  authenticationDeviceEndpoint:
    `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/authentication-devices/{id}/authentications`,
  authorizeEndpoint:
    `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/authorizations/{id}/authorize`,
  denyEndpoint:
    `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/authorizations/{id}/deny`,
  logoutEndpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/logout`,
  tokenEndpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/tokens`,
  tokenIntrospectionEndpoint:
    `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/tokens/introspection`,
  tokenIntrospectionExtensionsEndpoint:
    `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/tokens/introspection-extensions`,
  tokenRevocationEndpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/tokens/revocation`,
  userinfoEndpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/userinfo`,
  jwksEndpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/jwks`,
  backchannelAuthenticationEndpoint:
    `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/backchannel/authentications`,
  backchannelAuthenticationInvalidTenantIdEndpoint:
    `${backendUrl}/67e7/v1/backchannel/authentications`,
  backchannelAuthenticationAutomatedCompleteEndpoint:
    `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/backchannel/authentications/automated-complete`,
  authenticationDeviceInteractionEndpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/authentications/{id}/`,
  fidoUafFacetsEndpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/.well-known/fido/facets`,
  identityVerificationApplyEndpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/me/identity-verification/applications/{type}/{process}`,
  identityVerificationProcessEndpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/me/identity-verification/applications/{type}/{id}/{process}`,
  identityVerificationApplicationsEndpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/me/identity-verification/applications`,
  identityVerificationApplicationsDeletionEndpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/me/identity-verification/applications/{type}/{id}`,
  identityVerificationApplicationsPublicCallbackEndpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/internal/v1/identity-verification/callback/{type}/{callbackName}`,
  identityVerificationApplicationsEvaluateResultEndpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/me/identity-verification/applications/{type}/{id}/evaluate-result`,
  identityVerificationResultEndpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/internal/v1/identity-verification/results/{type}/registration`,
  identityVerificationResultResourceOwnerEndpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/me/identity-verification/results`,
  discoveryEndpoint:
    `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/.well-known/openid-configuration`,
  credentialEndpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/credentials`,
  credentialBatchEndpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/credentials/batch-requests`,
  resourceOwnerEndpoint: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/me`,
  enabledSsr: false,
  ciba: {
    sub: "3ec055a8-8000-44a2-8677-e70ebff414e2",
    loginHint: "email:ito.ichiro@gmail.com,idp:idp-server",
    loginHintSub: "sub:3ec055a8-8000-44a2-8677-e70ebff414e2,idp:idp-server",
    loginHintDevice: "device:7736a252-60b4-45f5-b817-65ea9a540860,idp:idp-server",
    username: "ito.ichiro@gmail.com",
    userCode: "successUserCode001",
    bindingMessage: "999",
    invalidLoginHint: "invalid",
    authenticationDeviceId: "7736a252-60b4-45f5-b817-65ea9a540860",
  },
  oauth: {
    username: "ito.ichiro",
    password: "successUserCode001",
  },
  identityVerification: {
    basicAuth: {
      username: "test_user",
      password: "test_user001"
    }
  },
  acr: "urn:mace:incommon:iap:bronze",
};

export const federationServerConfig = {
  issuer: `${backendUrl}/1e68932e-ed4a-43e7-b412-460665e42df3`,
  tenantId: "1e68932e-ed4a-43e7-b412-460665e42df3",
  providerName: "test-provider"
};

export const unsupportedServerConfig = {
  issuer: "https://server.example.com/94d8598e-f238-4150-85c2-c4accf515784",
  authorizationEndpoint:
    `${backendUrl}/94d8598e-f238-4150-85c2-c4accf515784/v1/authorizations`,
  authorizeEndpoint:
    `${backendUrl}/94d8598e-f238-4150-85c2-c4accf515784/v1/authorizations/{id}/authorize`,
  tokenEndpoint: `${backendUrl}/94d8598e-f238-4150-85c2-c4accf515784/v1/tokens`,
  tokenIntrospectionEndpoint:
    `${backendUrl}/94d8598e-f238-4150-85c2-c4accf515784/v1/tokens/introspection`,
  tokenRevocationEndpoint: `${backendUrl}/94d8598e-f238-4150-85c2-c4accf515784/v1/tokens/revocation`,
  userinfoEndpoint: `${backendUrl}/94d8598e-f238-4150-85c2-c4accf515784/v1/userinfo`,
  jwksEndpoint: `${backendUrl}/94d8598e-f238-4150-85c2-c4accf515784/v1/jwks`,
  backchannelAuthenticationEndpoint:
    `${backendUrl}/94d8598e-f238-4150-85c2-c4accf515784/v1/backchannel/authentications`,
  backchannelAuthenticationAutomatedCompleteEndpoint:
    `${backendUrl}/94d8598e-f238-4150-85c2-c4accf515784/v1/backchannel/authentications/automated-complete`,
  discoveryEndpoint:
    `${backendUrl}/94d8598e-f238-4150-85c2-c4accf515784/.well-known/openid-configuration`,
  enabledSsr: false,
  ciba: {
    loginHint: "001",
    userCode: "successUserCode001",
    bindingMessage: "999",
    invalidLoginHint: "invalid",
  },
};
export const clientSecretPostClient = {
  clientId: "clientSecretPost",
  clientSecret:
    "clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890",
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  scope: "account management",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  identityVerificationScope: "transfers",
  idTokenAlg: "RS256",
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
  scope: "account",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  identityVerificationScope: "transfers",
  idTokenAlg: "RS256",
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
  scope: "account",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  identityVerificationScope: "transfers",
  idTokenAlg: "RS256",
};

export const clientSecretJwtClient = {
  clientId: "clientSecretJwt",
  clientSecret:
    "clientSecretJwtSecret1234567890123456789012345678901234567890123456789012345678901234567890",
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  scope: "account",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  identityVerificationScope: "transfers",
  idTokenAlg: "RS256",
};

export const privateKeyJwtClient = {
  clientId: "privateKeyJwt",
  clientSecret: "privateKeyJwtSecret",
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  redirectUriWithPort: "https://www.certification.openid.net:443/test/a/idp_oidc_basic/callback",
  redirectUriWithHttp: "http://localhost:8081/callback",
  scope: "account",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  identityVerificationScope: "transfers",
  idTokenAlg: "RS256",
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
  clientSecretKeyWith2040: {
    p: "DnLMg3mHZY0rKM2Yl3PKbpHTZ_s32vN2N3zi0Kx8yjYNG_4YB_52kM5FrMp4GHiITQ6mVkLCnuGYM108ivKGiBscnCathi_Wm06vEEU_biWeyt97DcVdmIPoOBxXwh1uZvnqQGUPsc5iN8wTtT30tyF741HBY950_2BXkLY4Sdc",
    kty: "RSA",
    q: "DKD3GoFNmrQtcCNQ8lfRKCWWjZFaZyZXbTMmg23t_O850LkGw8Iv6AVT521nyGEMB8maWPV7C6IDEsksVoF0UpozWiJvSme-XRDleGa48CsAtAKu1BJTNUtg7IY0sT--gIoZpXQFA8Xv-_PVVREFKTcarpQ9oSEs5jkhCSvSL1c",
    d: "pNhO5BcEHGC7cCspvzAQIl1PSRPNj6mLIkGHwGu7UYhKCCWmcriPQi8YxqJFNQUgD1l8Tt8NCnljc3UO0fhcXkPEiaYq8u0Xb7d5fmR5auCk7Cxx-WF76iXGhB9hWo4wQUJRyKDXO5VnBiGouoJ2JwQqXzUcEGo9BdXYgFlOneyFkp5v3tPjxMgLd63YDYYTo8avZTj6FX3xehRFAzYDDQAtilbWBwnkcDJqSoam9e_qqzH8U1F_dFbKSKHoCYDuxezGVj3i5J0A8hkTV18w90_AJRRuzy-G8qxuy_sZb0-VQyLsYk7j1LSQTBHaj69IzQ1KqdtfGkhyGELXTHQh",
    e: "AQAB",
    use: "sig",
    kid: "client_secret_key_2040",
    qi: "BS-56f4yIToZ-fqtOAOHgkgZoRu1vmJAIiVsi7HLOhNe99k6A5phgKaoxm-74llOQleyG4bcF-oBZjdUfn7n5d42AW6g3DfwH4KraGUOXzh71Q9BypFdXSF8itYgBy4lQb-hs7Fj_YBQXBw8rVT4V9nxgglGMTT7FNCnGeTD57Q",
    dp: "CHkYKE3_sfUch1wyjTbRUxBfms3_Tn6SKC4r0VYmBGu4Ol9DdoSqwXbF83P1A9zDifT6ZQHXolcH669UzuM1M-I6X_RqtJOfVgrJrU_-x7h2K-DtCAFjRwqsByPP7z1VPjx3PHZHwu0WOkSKljIULcUMIx4RvGB72juEfo_t6Jc",
    alg: "RS256",
    dq: "BV1CWVHfYv1x55Y4xhgjUghVYSEC45nXvRzjGSTS2IPNbSmLBBAyRT4uG7nPQcBnWc967pqnf5N79rjZSo2GmafCdGD4IQTcSa_pqTEJEYtSqyQQmyiFi7fPHekL7NaE1xjSOAOGNcoYXE-AqLeoF4--l7WEUDNFXS2bKMgiadU",
    n: "tndIPiYnTw8UlbvU4GSD77tPwePNpu1VYmTWmrOA1etvNV7xeU10lHPscmbr702bbo5adEVwWsqwHkS0lvtBBepb3BKhFwl84_Ffqp-P_rqlduQ3Xnri5BfesreOy6nZQcQ95OSR0M4HYgfhrsMXCxQsA1GCDCI7oiKm43icTxaPoH232qfJFG_rInHBEokO-BCK_0Ct-to6dyRLxlDgCoKR4LnWJ_ETfxzb9LSdp6mO3ccD9r9Qit7tgbv-vBzGtMW9Yd_iwbHMo2qRRMnbQMHHK3Vip2xCEIi1v3HC--UUXuecJ_SYG2D69UKC3hvO54ljWKsIyeGAkcTyDZER"
  },
};

export const privateKeyJwtEcKeyClient = {
  clientId: "privateKeyJwtEcKey",
  clientSecret: "privateKeyJwtEcKeySecret",
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  scope: "account",
  identityVerificationScope: "transfers",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  idTokenAlg: "RS256",
  clientSecretKey: {

  },
};

export const selfSignedTlsAuthClient = {
  clientId: "selfSignedTlsClientAuth",
  clientSecret: "selfSignedTlsClientAuth1234567890123456789012345678901234567890123456789012345678901234567890",
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  scope: "account",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  identityVerificationScope: "transfers",
  idTokenAlg: "RS256",
  clientCertFile: "selfSignedTlsAuth.pem",
  requestKey: {
    "p": "8OX-b2ap00yVvPsQ46jFIGBM5NXByTuRI3QTb_Rt_41rjiHy0JNrEIqOkFW7b583rLsOsWPykVNtCwqbjUhWT81ijUneDyzcrCOGlOK9_pZjcqvIIU0fbyXPYox_tejNrv1nixjirnuGFlCDaHZ4ZrWOAN_VPsI7cJLzMyH7jos",
    "kty": "RSA",
    "q": "652Ra-SB0PSVTKwmVxxmPOqOAaN9c8N4urXMwa67auCEBk51sZzpU2QItrTpP_YxZwYyN1srDdanXOREIemb7lX8zGzcMCvhfS_jY9VqVV1tglLg8mN3EU9EMkLPfIi1rry9E2dofDr4mNhUtAd_gorOuBAau1moQfRu6vbzbjk",
    "d": "SYqNt4XMFBfjXQmfBGjeMBPi9FeZ-pHCNMb8L4RrCjnkaWErEvlIbIs1Oijt7zCyIq459GTJ9ohrVijQGm-_XsPhFwUPsYTlnKX1tZKHypsRQbdvZkc3xCm0AU-hZ2Y6XBGmEZnV11TCnH-hK5qA3uOvYAXmT681eVSXsqIuLyd_3_YdK6PzFwCUE0wrplTJxQY9m_DGYy0-kxKOMzBcx-LxTJxDtuXXWzMwc_Wxt_h3YJCTtMYQNdRq_3HUbSRnvKL7_tSE0b1MMBLDj-kJqrzm8lEqgL77Dpxribc1eUYTouBj50_IufZP6Zmu_J6GA52vIUZM-QCa-8slvM5v4Q",
    "e": "AQAB",
    "use": "sig",
    "kid": "self_request_key",
    "qi": "MtiJiYXnuv74cPEeS5dn7VIbOAQ7M3Gn1MmPjoSzm4rIw61WhFvfmt4I4xpqCuT8X2Es-JUy21y3OVnmc64FjZsaIjRjMykh2c5BENphdr3_E9mX6ePQ8aqlc-IxQcrxyFxxahjtL5i-BVeaUqW0iGK_8sdHDbbDy-sphPtntJ0",
    "dp": "MBcJ2v1Yq6jfP5GzRm9bouH2l7wgamasy7IRa0kVaG236fDXA1JajjvKx_-FYnwbZi0Ves4kD2TjINAmS2cEa_vpT6FOZiMjjm2tIS2-lgM5qRun4RX_T3Xx14-KsyrdXLCTqg32urRICU5bXVBBKufViFEmxdeKfCepyyxeho8",
    "alg": "RS256",
    "dq": "vJr0JqbTV94BiU-gnUipeofTxvIFYV6OzP7sefoSIpq4dIfn5JaTm-JOq1qkAERmBW9LUU6UZ051yIEdHPxwitegNk_J7nGh3eEwp-DVbqIMIC-Ry9XdISkPkSA0ER8qOEbzH44-cHuQNmGtZDNAWdIdae_SRjid3LX69_FYqdk",
    "n": "3bdmdRnVS_VxFwXpmetBs3n4g0THeWntkGs5wigGA38x5pTEe5B97-KgXdB4f-zL0n_auPjkIOiKGXByOWvG-9J-U5eY0fXcpCky5JxEpcEQbQMxXTsefn-E5jR2Ia1BMuYLxIzvmN4VKmYNXoPnVSbFj554VBpYnRPF9KtiBU2S1USYBdHFZ7iI3NltoKS8Qb13WgUc3nvQvfHToifWWm43R-Q8yocCXP3G7PHFlBHyNCVI0AArEbuqzJED21nRDUm8bFQ187NrmOI029L9WDTvzcnxfU5zPTnDSCt1Dr_KdcSyMClwZ2RNmd8RAGyH1kBZfMFkQU-AFSDMiDN28w"
  }
};


export const selfSignedTlsClientAuth2 = {
  clientId: "selfSignedTlsClientAuth2",
  clientSecret: "selfSignedTlsClientAuth2",
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  scope: "account",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  identityVerificationScope: "transfers",
  idTokenAlg: "RS256",
  clientCertFile: "selfSignedTlsAuth2.pem",
  requesstKey: {
    "kty": "EC",
    "d": "W4ph87WjvxxqrLZr263xD6YgW-Krd2-gI18jPjsZJ04",
    "use": "sig",
    "crv": "P-256",
    "kid": "selfSignedTlsClientAuth2_request_key",
    "x": "Yoe4KRi-_vc-F7BRkgketw0vS0XywExmRNG0nR-7hq4",
    "y": "-BMXjB9siMwrSJdmswjWosjGnJGUFpKXe0kZ_UQvJ_E",
    "alg": "ES256"
  }
};

export const unsupportedClient = {
  clientId: "unsupportedClient",
  clientSecret: "unsupportedClientSecret",
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  scope: "account",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  identityVerificationScope: "transfers",
};

export const publicClient = {
  clientId: "publicClient",
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  scope: "account",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  identityVerificationScope: "transfers",
  requestKey: {
    kty: "EC",
    d: "Aaknbn13MHEb4hxvhy4gC1nzHs_s5Uz_Mo0W227nR3CAptwrcHBtLjZt7slvLi8lZ5SfQHLjyJB83rP7XtGPAsHT",
    use: "sig",
    crv: "P-521",
    kid: "request_key_public",
    x: "AFHdaPWeOuhG36hggrYDabBc353QSconwfUi9lYXTMG1ZIjI-Z5hiX_awx1C0eQ2rJLccBVkCeI7A2DOLR8iaVx7",
    y: "AbLAhZvzCFBq4huLzupVW_dwGwfcPu4CnJ-TySKaoUWBQW9xvvSlGqLpzAx-9zhMkMVqnx78shcAGmaSV59FSLmU",
    alg: "ES512"
  }
};
