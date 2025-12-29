export const backendUrl = process.env.IDP_SERVER_URL || "http://localhost:8080";
export const mtlBackendUrl = process.env.IDP_SERVER_MTLS_URL || "https://localhost:8445";

// Default tenant IDs for backward compatibility
const DEFAULT_ADMIN_TENANT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"; // Admin tenant for Management API tests
const DEFAULT_ORGANIZATION_ID = "9eb8eb8c-2615-4604-809f-5cae1c00a462"; // Test tenant for OAuth/OIDC tests
const DEFAULT_TENANT_ID = "67e7eae6-62b0-4500-9eff-87459f63fc66"; // Test tenant for OAuth/OIDC tests
const DEFAULT_FEDERATION_TENANT_ID = "1e68932e-ed4a-43e7-b412-460665e42df3";
const DEFAULT_UNSUPPORTED_TENANT_ID = "94d8598e-f238-4150-85c2-c4accf515784";

// Environment variables with fallbacks
const adminTenantId = process.env.ADMIN_TENANT_ID || DEFAULT_ADMIN_TENANT_ID;
const organizationId = process.env.E2E_ORGANIZATION_ID || DEFAULT_ORGANIZATION_ID;
const tenantId = process.env.E2E__TENANT_ID || DEFAULT_TENANT_ID;
const federationTenantId = process.env.IDP_SERVER_FEDERATION_TENANT_ID || DEFAULT_FEDERATION_TENANT_ID;
const unsupportedTenantId = process.env.IDP_SERVER_UNSUPPORTED_TENANT_ID || DEFAULT_UNSUPPORTED_TENANT_ID;

// CIBA test user configuration
const cibaUserSub = process.env.CIBA_USER_SUB || "3ec055a8-8000-44a2-8677-e70ebff414e2";
const cibaUserEmail = process.env.CIBA_USER_EMAIL || "ito.ichiro@gmail.com";
const cibaUserDeviceId = process.env.CIBA_USER_DEVICE_ID || "7736a252-60b4-45f5-b817-65ea9a540860";
const cibaUsername = process.env.CIBA_USERNAME || "ito.ichiro@gmail.com";
const cibaPassword = process.env.CIBA_PASSWORD || "successUserCode001";

export const mockApiBaseUrl = process.env.MOCK_API_BASE_URL || "http://host.docker.internal:4000";

/**
 * Creates a server configuration object for the specified tenant ID
 * @param {string} tenantId - The tenant ID to use for endpoints
 * @param {string} baseUrl - The backend URL (defaults to backendUrl)
 * @returns {object} Server configuration object
 */
function createServerConfig(tenantId, baseUrl = backendUrl) {
  return {
    organizationId,
    issuer: `${baseUrl}/${tenantId}`,
    tenantId,
    authorizationEndpoint: `${baseUrl}/${tenantId}/v1/authorizations`,
    pushedAuthorizationEndpoint: `${baseUrl}/${tenantId}/v1/authorizations/push`,
    authorizationIdEndpoint: `${baseUrl}/${tenantId}/v1/authorizations/{id}/`,
    authenticationEndpoint: `${baseUrl}/v1/management/tenants/${tenantId}/authentication-transactions`,
    authenticationDeviceEndpoint: `${baseUrl}/${tenantId}/v1/authentication-devices/{id}/authentications`,
    authorizeEndpoint: `${baseUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
    denyEndpoint: `${baseUrl}/${tenantId}/v1/authorizations/{id}/deny`,
    logoutEndpoint: `${baseUrl}/${tenantId}/v1/logout`,
    tokenEndpoint: `${baseUrl}/${tenantId}/v1/tokens`,
    tokenIntrospectionEndpoint: `${baseUrl}/${tenantId}/v1/tokens/introspection`,
    tokenIntrospectionExtensionsEndpoint: `${baseUrl}/${tenantId}/v1/tokens/introspection-extensions`,
    tokenRevocationEndpoint: `${baseUrl}/${tenantId}/v1/tokens/revocation`,
    userinfoEndpoint: `${baseUrl}/${tenantId}/v1/userinfo`,
    jwksEndpoint: `${baseUrl}/${tenantId}/v1/jwks`,
    backchannelAuthenticationEndpoint: `${baseUrl}/${tenantId}/v1/backchannel/authentications`,
    backchannelAuthenticationInvalidTenantIdEndpoint: `${baseUrl}/67e7/v1/backchannel/authentications`,
    backchannelAuthenticationAutomatedCompleteEndpoint: `${baseUrl}/${tenantId}/v1/backchannel/authentications/automated-complete`,
    authenticationDeviceInteractionEndpoint: `${baseUrl}/${tenantId}/v1/authentications/{id}/`,
    fidoUafFacetsEndpoint: `${baseUrl}/${tenantId}/.well-known/fido/facets`,
    identityVerificationApplyEndpoint: `${baseUrl}/${tenantId}/v1/me/identity-verification/applications/{type}/{process}`,
    identityVerificationProcessEndpoint: `${baseUrl}/${tenantId}/v1/me/identity-verification/applications/{type}/{id}/{process}`,
    identityVerificationApplicationsEndpoint: `${baseUrl}/${tenantId}/v1/me/identity-verification/applications`,
    identityVerificationApplicationsDeletionEndpoint: `${baseUrl}/${tenantId}/v1/me/identity-verification/applications/{type}/{id}`,
    identityVerificationApplicationsPublicCallbackEndpoint: `${baseUrl}/${tenantId}/internal/v1/identity-verification/callback/{type}/{callbackName}`,
    identityVerificationApplicationsEvaluateResultEndpoint: `${baseUrl}/${tenantId}/v1/me/identity-verification/applications/{type}/{id}/evaluate-result`,
    identityVerificationResultEndpoint: `${baseUrl}/${tenantId}/internal/v1/identity-verification/results/{type}/registration`,
    identityVerificationResultResourceOwnerEndpoint: `${baseUrl}/${tenantId}/v1/me/identity-verification/results`,
    discoveryEndpoint: `${baseUrl}/${tenantId}/.well-known/openid-configuration`,
    ssfDiscoveryEndpoint: `${baseUrl}/${tenantId}/.well-known/ssf-configuration`,
    ssfJwksEndpoint: `${baseUrl}/${tenantId}/v1/ssf/jwks`,
    credentialEndpoint: `${baseUrl}/${tenantId}/v1/credentials`,
    credentialBatchEndpoint: `${baseUrl}/${tenantId}/v1/credentials/batch-requests`,
    resourceOwnerEndpoint: `${baseUrl}/${tenantId}/v1/me`,
    enabledSsr: false,
    ciba: {
      sub: cibaUserSub,
      loginHint: `email:${cibaUserEmail},idp:idp-server`,
      loginHintSub: `sub:${cibaUserSub},idp:idp-server`,
      loginHintDevice: `device:${cibaUserDeviceId},idp:idp-server`,
      username: cibaUserEmail,
      userCode: cibaPassword,
      bindingMessage: "999",
      invalidLoginHint: "invalid",
      authenticationDeviceId: cibaUserDeviceId,
    },
    oauth: {
      username: cibaUsername,
      password: cibaPassword,
    },
    identityVerification: {
      basicAuth: {
        username: "test_user",
        password: "test_user001"
      }
    },
    acr: "urn:mace:incommon:iap:bronze",
  };
}

export const adminServerConfig =(() => {
  const config = createServerConfig(adminTenantId);
  return {
    ...config,
    oauth: {
      username: process.env.ADMIN_USER_EMAIL,
      password: process.env.ADMIN_USER_PASSWORD,
    },
    adminClient: {
      clientId: process.env.ADMIN_CLIENT_ID_ALIAS,
      clientSecret:
        process.env.ADMIN_CLIENT_SECRET,
      redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
      scope: "account management identity_verification_application claims:authentication_devices claims:ex_sub ",
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
    }
  };
})();

export const serverConfig = createServerConfig(tenantId);


export const federationServerConfig = {
  organizationId,
  issuer: `${backendUrl}/${federationTenantId}`,
  tenantId: federationTenantId,
  providerName: "test-provider"
};

export const unsupportedServerConfig = (() => {
  const config = createServerConfig(unsupportedTenantId);
  return {
    ...config,
    issuer: "https://server.example.com/" + unsupportedTenantId, // Override issuer for unsupported tests
    ciba: {
      loginHint: "001",
      userCode: cibaPassword,
      bindingMessage: "999",
      invalidLoginHint: "invalid",
    },
  };
})();
export const clientSecretPostClient = {
  clientId: "clientSecretPost",
  clientSecret:
    "clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890",
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  postLogoutRedirectUri: "https://client.example.org/logout-callback",
  scope: "account management identity_verification_application identity_verification_application_delete claims:authentication_devices claims:ex_sub ",
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

export const federationClient = {
  clientId: "federationClient",
  clientSecret:
    "142xG9AaUsoMhZTLLDGugbYsoSRXDaXeE8wtCUAxcneSH8HEdKpMPe3L6dAZqYFw",
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  scope: "account management identity_verification_application claims:authentication_devices claims:ex_sub ",
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
  requestKey: {
    "p": "-lVD9fBw_xEefz47VOpyZDR3Q2hv-duXR9_6agMeIVOdQIf08Mn8KfaAUJTLxsGBeoD24DuOeoGTDo4RsrjfEQWxs_RHEr0occvspb-yEsfSAPsH5IxmshSq_eBtP6GWAGuFFwMh-ef_a4TVAn0rBQD_ouO0em5hpLOoxAlUJL8",
    "kty": "RSA",
    "q": "vbL_ZUk6FUzhwpqQoGCuS8NyXo52E-vcHf6EjQwyXl-HV4k6zchQBtCFrlLHyCxdkrQp30Nkg1O8jShQnRpeEHHPWxsYM6OTJov6Lk5IKq8ATkZ433kRleXerljwJYv8BsuFElHp8pvdgHnBYM8FiPOdNYWQoqjgKEr2TVUgdXs",
    "d": "PijmEVHTA5S7PaBMtcisTvFJMqhV78rfJdImzh94DwprxCEDkS8BhsyzLODrZhvjM2SV6oqTYYA_MGZtI2oySWjh2Dy0uY7rcPtQebPxduL35d43R60luEitMkKjuOPO4UV7WoLrHUW-ISNtowriT9srgyV0tHVykcWcjAhy8G8Qc6HAYsrjYTQoYhg8XZJbL77hSB2l93ABV1Pga7zIbWv006qUj0qMrWqh9RexquOJg3ebhSs-8n4oMZJYQNvH8PWw846ePhNYVbfzcT-FIsEAqag--2rHohAFwJ6ecPOCzKnXrE2A6Yj-Y06nRAUunWzk1kordD0lFbYBRIMB7Q",
    "e": "AQAB",
    "use": "sig",
    "kid": "clientSecretBasicClientRequestKey",
    "qi": "uDn2B7n1NS0Tpcqy-yDEkM8yXaPUKSSwd7sTJyjyEvPm4alXh_zhsxKKyPJslRBJHEzDNeCzTVZsPf9m2jylPERUCYKHAMYkDs7ztks6KYVmIg6DqufaFMKYN6QehahRD6fsqkO0pGEyUiHPRVXLuKqcQ3lU6NmlsusHY841eCI",
    "dp": "NHXF5jZbF7jYsUWzXVo8Wq98Bs5OGQhhrZhMfAbnO4iwPaPAOu25QHn97eUgxygqICgClH8mnpmk3bn8D34akRCmatXYRx8I6MAZeaqwgKsLYVU6FDUwGEWoh8eOXXVZYAEoROhNKHfr2PZfRURBzayl5dbeLYIQSFm2Fk72KTU",
    "alg": "PS256",
    "dq": "Oal8SnxsxGa_RRkYlzdsI6mATU--gqWyHgfsoLcxZFBjYm9bSYv3D2s5B1kwUx9xPhO3mp7woZ7FdJ7piiemqhb-MbJVs_Sn2RK5dHPlKdxSGnj_auZxOwBJpPNx4-feLu6UP5JYq59HQDNCXbvv8cYjZk54PYlZwcWvgqg6mps",
    "n": "uX_8M811gfAcAR0cjI2iS9v1Rl44EmGCpfzoEkIS-8wiuJYjlF2dPUicgJn4IcAchywjGc2AN2-9m-58Wva0s-Gu21hWJgVlruA6b9eoL4TjOoIJLzpENdY3FCcw02gJSqToWQogJv24S9Xhat21iyTXK0s4mAhhuTKDfLTzpGfkMny7gcsjbdxfP7NVDcAMPCdVaaZNcGtINLPEu1_jVbRl5BEfwS73S9XnNt0KmvsFgJXUwXYia-7CDJsOYoz6olzc2yq--lgkYUb6NdsS3GQM9Vi4OHR1T55QsF01nMEtjpdp4P3DWsVWutBgah5UkDpwlY4bYcyO4mCe8xjyxQ"
  }
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
  requestKey: {
    "p": "5m85mFMnvfln_8NG0HbZ7oKlI_bL23d_x2vvqf0tdM4qyUcx82QhMLeOxshZ_hsGEU2jSVqE2-lIpaSeW9hcjsFIBkA7ZeRtTJlj7D4dUL_jRzSR2vbygE2VaewE3ui82MB4mqMx6gfzzIBxzrZomsvHpGd9r-0u7vcwibWYNP8",
    "kty": "RSA",
    "q": "mrnNwLIcWyhHPej1AuZ1iI4EnMDM18bPUhViWEagso6AQihMVxsGd0xKKMEBrIID5QIDP85JpVG5udTosvKg9Eh7aHycHgrwTloiL03a7NsbYToE7eS3patsMgXXzf4BrQHTUfFcGQF9bVOpFvjsRbmuNn3WOoH28tU1_yFV8u0",
    "d": "ehNQh9OlqNA40f6XI4BCuLt2F_bwuy1lgOaf-otz9JRd3TjmzgDgWKZ58Pb8RPFOktvTeLU3YiiErbwJVTT1utv1l5wvohpMh9rUs-CEw_T2sdAkpcZj2DgM2aUoxjjcotBVLaqR6wBQyWYYMRA2xZbtJYtByFLIbuxjABQbnXebqdPT9koAuyYVpYWYp6DQMDe6Pkn06_AupNH6iETCWE6XhzJd5WkVDcxYnZqhR8v3MGiNk-DZEUgknaFu5jNb7BT-x0SIhfOy48WvUgR6irYGv0GTKzQ0UJsq9UfLtJupu94SFkWMJQypsCoKCCqHwKGDIKZQgVovM0lfulonoQ",
    "e": "AQAB",
    "use": "sig",
    "kid": "clientSecretJwtClientRequestKey",
    "qi": "DDywq2MLT3bOl5IZM3CAdsLomCTzSevjP5TdSxHQNgOfLk5eT8Nqqwff_UZHaO9f7DjpLXyBh5fIpw6oJrJ2j3iG_3_olzoDTHhLWd6jXIz70UEB3rOmHhM9xiFH8Elwc88VJkh2eCgMXb-TCNxweYU22BCHiKPIPNCxCO6Kod4",
    "dp": "xgM1vstWD01OLN2k8GXxB5-EQM9GhiS9zKPXon9InGK9KZDgP4H_c1mIexCXJGAcaqoIcnQyTra8EnrPCaV2g8VbGuP_JqOPuI7m6o7Fqin6J6no2_vwjogcqJSD6vibrrZV7KvnrG-A9G47XaIpaTtLJt83hgGOnxISx4MgdAk",
    "alg": "PS256",
    "dq": "YJCx7x-6PtJGlEb-E09N72njSwmxTsIPxkT-WeXKiWYSeje4XWzKfk4owoKizs1eLOEysfHHWNxGs8pDaNuwxIUwMCgFL2LYU65BQ5Ctn8GntuiFBwwF6emw3w9yfmeXGCjZgHzIY_rgEwlxsJquq12VcwDaYS2XuDqFNrPRdok",
    "n": "i0YoOsX3CRmEMKmaJrcsMfnl9B1YRCiTPFLuRK5nSMkEez5tTfOxUpU3NG45WJo_KJCiGD8ayi7FOD6Qt6wNZ4YAH3P9o6ttVjjHSiBNvdZewgN8rxnfcYQPaMXPB3DVdzB4EUEnAqqBWn92oO95eYut6cE_0L7igWcq_58mULNzA0VWP8embRDOdErKmXraV853tev2UD811dGRuJ3EhXDFQyaJxziaH6BrhoU2ymBKCrK81sy-hqaPM_YrglLRqpgcNEX7hfYOthkLMu156EqDqZzNg55zyXrOe8_ztU9MX_MIyFsKRJxOS-mr4oM-A-f-hZu5aTlQnJyGd60eEw"
  }
};

export const clientSecretJwt2Client = {
  clientId: "clientSecretJwt2",
  clientSecret:
    "clientSecretJwt2Secret1234567890123456789012345678901234567890123456789012345678901234567890",
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  scope: "account",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  identityVerificationScope: "transfers",
  idTokenAlg: "RS256",
  requestKey: {
    "p": "zydNTOC11ae772O5d9rrjqBCTog4cs357sYR_ggq3kv5dvKJSI4soDccHrGE4PfaHhJ0jZoTfdWGuoivkDaqi2z2aTsaEdGplewizil9ktFA3dL93_T88wBLyeBzxSWrJmyPTfquXyffY2WLG_1lNUYZnFCeOrrwN6sQA8wMXKU",
    "kty": "RSA",
    "q": "orVf2uFD8R2zLiifjIfbvDjXToobBtq4wvQxr17nYSQ-QJ7kX0dB8pQgnDRZglJT1kO2_VaIa5r_gndqf-xvRCcQIq6SRXQWSGQxMj34lorkaX4iwA5xa1ZxWUBCU7pRXACnlSrIZsU4-W5wRStGYChs8hE5PjKDNG4FN1wY7cM",
    "d": "HErgxdw_rtVi1eRBvEqpKICFZ40K0DpgurNaRAHT4FQTyV-H-21qijB5HcuWX4pB5l7m_-fk8saCxI3Ghf3gFWQukhx6hlW7YmO4yhusJ6D0WP-TUYQkM7pqpFGXdCNHfaqMjiHnfL5szzjmCaEZUpjfzxmi5ht_iofljcNqt62GJ9PLeSI66UtE3H7DY8Jmgn30eTmbmPt66a9rr6Q4-Qp3kbKk8r6icqQGoXU1EVGWtxnw3CoAc5VY1ZL32NinpFizd00ZJ2yUvRdv_f3ETP0D5cb3O7o9vp7x2Faan6F_tMPQ_xUqxl5hf4K1uSSm8sdvcZJp2Yn6Fj5MdnSQ2Q",
    "e": "AQAB",
    "use": "sig",
    "kid": "clientSecretJwt2ClientRequestKey",
    "qi": "Dhd3mOWcVDS0kR8A9WT9aKWcS3ZRO2Py3bti6pMJTsj1ASRyJNI7BeiqqQPy0LQArCuPDFiLKzkITmVNYKnxYpWdtd7FM5yUeYK6dSDnHc2ISXvTePz7n_lUI9hOmplWp3CQsZvyhw1M74klNnw-FwVf9IhAcV2stH4GF55XGPs",
    "dp": "Lcgt2VPAXBvZ-So9DzQe40Kf5ONONnE3T4IO7-m2L7Lyy0sG9DYXvsDvQglRqLaeSg8iGzl2HVVXlVooEuNCBdZrDw600ENaC3xIpO-ehRgiEeRTn29xh_MEzPUCIQwWK0PK2B0S9IzHXhxPBjyWZZO8jUNz9N7jwCnaPDyYbtE",
    "alg": "PS256",
    "dq": "CqLIjUpEYrKKT-4f39PRrVAZ4uxsL9qEUVodFVWi0lrIJvk7_G5wBSdhqlRRtY8kI9FxWMYbTM1o_mJH9-zAqTfUei_nJ9saycii3qHmahDSNYMlSmCnUVjpN-hXbZm0T8tLKRkO2Ijz3Ho3pI3bn9WEqZ4um745jWfTt3axunk",
    "n": "g6mjRQD90fTefCOMncqxOEFB0tqmpGL6ZxIIKx6qAMM7tF4KfOhsMB4EcLdPOMf7y0xtQ566J4JPM9XPde11uaf7UL9Q9g-zXS6ci51xp2u9gRSHViKtq3KLw_G-jfJcG03S5Qzq6D1loGDynMmJupBXG7YWpSK7Tlra2zUR9ihXsTGfmoYvCAFE2DvbjKzB6WC09e8ApBUNnr-_ouAD379RQ_6CW50qv5NaWHD7_nr08vLYU0bCZ2nevw66R4CPvMAahNBlY1ae_nneJYHyOMbu11CGLU22GXSPp77JGKGNnGCjP8CSsThH2UaICfArt3ZmaLXZ0KwKLWmy2qdSrw"
  }
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

export const privateKeyJwtMtlsClient = {
  clientId: "privateKeyJwtMtls",
  clientSecret: "privateKeyJwtMtlsSecret123456789012345678901234567890123456789012345678901234567890123456",
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  redirectUriWithPort: "https://www.certification.openid.net:443/test/a/idp_oidc_basic/callback",
  redirectUriWithHttp: "http://localhost:8081/callback",
  scope: "account",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  identityVerificationScope: "transfers",
  idTokenAlg: "RS256",
  clientCertFile: "privateKeyJwtMtls.pem",
  clientSecretKey: {
    "kty": "EC",
    "d": "W0lsWcJZeCFudlw4s6wUH1P7UWDxpQneroGUr-NbjbY",
    "use": "sig",
    "crv": "P-256",
    "kid": "privateKeyMtlsSecretKey",
    "x": "n-SgrlqX-2EZ-oC1c8TW2PRa1cAMtfHsipHza7zW_88",
    "y": "qxMEw6qvhLuglglE9uIiSqZdtBiu9S0xtWW3CbZVTQw",
    "alg": "ES256"
  },
  requestKey: {
    "kty": "EC",
    "d": "ddIacq49C3mUEB6jCKhh4IpNgsCsIYls60mPNTLAVQA",
    "use": "sig",
    "crv": "P-256",
    "kid": "privateKeyMtlsRequesttKey",
    "x": "7NEL__3iYg6nF-qXH9rMDKceeoPX7K8tcFPKAf5QF2w",
    "y": "c4pN0A_K3t3bqeF_BRzYbsbKe_leUYh5eUZW2JOFSdg",
    "alg": "ES256"
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
    "kty": "RSA",
    "n": "8BU7QUaOy7Hd8f70jcdeIfMYNUXUaV5V85-yu3VxuEIeBlz2trV_7RGF5z__2KcqDe9P2m-aclpwy7vNX3eHY7lHlern-8B6fCcX7rqNmT35QCBq4pnF5QgbcpEmL53hkG-E4mYrIkalkLehlF4WxdNCt_TQCkHgqgeaA1bOmRFVFYXoGU6P_EQBf8HGgambs1JtR0TsTZBUjKGAW5uGeEHZg9HDpmnXG0K9d2nPcfrl7_F_dN9kcKdDlIzQPMbEADA-3lA0oSo53Eolfy9N_5O2i_WXjjDQc91V9ZmDszcMGZAJ1mj5UExJuEMJG4Pwxxxrz60I3xSuyjND5vjwgQ",
    "e": "AQAB",
    "d": "AU65iz_bC8PbXMHE4Vm6mPdkYmAD176Ak_1yrlLFz6NCK3Jbsm3SZ2UzhSmBBUge6Hc9LGw0JXVBuvmSASgVEmJrM-9E7-MD3MG0D5374ufAi_5oNB1yn4JO51W4ITFKQe6KTQ8JuyGytasrGtAszjtJ7D9lx5hFeOIWbxgwsyKbYE0JwRe5R3ZWFKmdI-YxjQM5jNHOOyDOi0R6SNedAHBEEt31Lp_FNIYe_vBdNvTDI4PFbkcVA6iZeNuE0hZVm3CMHtKJoED8fd7K8cZjq-bib7GPstZwCWlNnudz-2g9oR49Sc6jUBke3UD-6In8ssGhsYjUs1NX_-9jHmZOAQ",
    "p": "_A2rHFZGlqOYvev0-0AH_A_Rs6FgHaJKoeq6utLLJ8_dDC9sIPiEFkRC6bXu677FkJ1Hmdh3XzXGTmz22vPqDnfPfmkZ3ZGg_yZrcw5kZb-wBesBX24CH3X9dz3l2kIA2GFmPkyJDUhzuKO8W80qx4klnmViQIDfLysVDnrWqYE",
    "q": "89eUpVFi3VGjT1K0fjukjQK1s0MKQh1GlTppiqi543TXJhr2GjiSkxhTbYzircRIJiIw5q91yTwY3W1BLFXfzvdoASMH0s_BW1u1NRZZPXW-Tr9Tk_MIZc_T8fqzMcNEGEYjHZiQWFguIACqvJFADjRTKTzdOSeLHxvxiGffxwE",
    "dp": "DcTBYB7mZDwzj8xf6Ymp5f8HOYTLN1MyOH4WDTaOmnIkZRICGhr5PC4maZXio0YcjLXKv4CvpKfPWWFiaruAQaIR15nuK8hUvxteKz7SBSzfdLNNs-TSgN11Jxpef_mzCh7n8udDZVJb3-4xz-H5QEUhpPFs2JExn-X-lHfpWgE",
    "dq": "Vz8Gb5RMNHYWYbZs4FHE0bNrgeV10SwaaiCJTNSMFmdi575z2fPt9fWZPDRRku0NB5_qMVAMa-E14FxMGmnrd2ksbGO1U-eD_oU0s_b4HTZ4zkJEzEYD9jHDWuxhm2eLgMfvnpJM6185ubejX271JV_xdWrSmLWeYJYl-LF57wE",
    "qi": "4VvayTv8X63bu2PHIl4NCyu5C_93JvrCedDj0dnxGl-VyIvhrOAtHb0rR1TWouFMIXXxBh8WdI-zRMKM1OvC4mZZK6_maLlYtkvU_e1zXhV4iCmcZAgUIiRwcmbFKQcGFaYRc4X0wCsDjOwpyMVuZ6fKe9NTgl1utqdCKA2Hy5g",
    "kid": "self_request_key_ps256",
    "alg": "PS256",
    "use": "sig"
  },
  es256RequestKey: {
    "kty": "EC",
    "d": "hsitETTZG0bvqTq6JcmysuVB0r94fNyt2vTTNHJODnQ",
    "use": "sig",
    "crv": "P-256",
    "kid": "es256Key",
    "x": "LKepGZKS65l7QzOsEjm_KOX17SW4DHDj41Rb_8KRWaY",
    "y": "uKhGTzXvred6ODs75P-rsYgQ1oyJyriHucPiKEqLn_Y",
    "alg": "ES256"
  },
  shortSizeRequestKey: {
    "p": "-K5-a31eYd1_FwqQp3XvOPXCxoMDh__B7BvzByjcBZPsqkZ35rF4CHQIKjfrU2J3XnnNx7rN07OxCSY59B_MrGUwQSqTO7HSXi98HGttEIXbEA70LRTTd7YD7nxu1E3PsIAhrSXNXu4l7JnXWxWZm8Rtb-VLpv9mdJdPf-M",
    "kty": "RSA",
    "q": "w4Muk0zDmIe4VFy8Kgx-i1Y13TDikK4erOz9LrJx_ntv3ufQ1s2Dz8hBo_Hmr_Z2ELKfnT1GCOjTP8FLPUEa7oXMg9aH624b9lWiCxdZoJROUexzHw6ouqF1-HvCk1h6dKMrolhAH0ZVn_3AmsqtUsyQbOcla138w0C8ByE",
    "d": "V7iYdxp_qjkazXAhiyQFHgsmf4pXSDxAwxUmT2yz_LXAY-SYizJspJm-7rimT4Lk1nguJDjsfGnUcuDcsX5qF337yV2Hy0x1A5a8r8OBwP65tYbeHpAUAjsGkh8A6Zh8pEqXtgi01piDaJTlQGF3tGUWD-wi7p9R13gbze3Md6jS9rhdk30JmWdu_77HebXSmXSatvM20ot8vYZJpiXSOGDa7niS8zXjzRdMUaulNxfaTErZsh6aJor90iiHOFFQabhERZO9Szc7Lew4gBD6L1-P7BLh5HMknPuulpZ0arZmBlh76-7uKtFLEwIrBQ0Vw1HNhwVt6vS7gQ",
    "e": "AQAB",
    "use": "sig",
    "kid": "2000PSKey",
    "qi": "FU6ffMZ9sYdpeYPmFq8WAX0hi08p6qZkLJW955YQiOvs_7VpGEX9pkdeSd8TCXc7_uZux6t-eatoC--NTiDxQDdivPWxmi9r8jHVG-AK7zY2nAl2bM1YXliActhBD4WCJBged2bXodYClnkfCM63dBnXs8KGk-Nd9oMewUU",
    "dp": "BlY79nU9YHUKdgXY5cuFiwgILJLOFjsYL_IOYVJTOPkqALTG_WPsURrT0m-WWYuayxeDfOFvuUNM8ZS9yVC-IG57qk1xbwX6_FdtbDP4lYEzsdcPtc5gDR7gPsWA9Xv-HNtHAGiEMTUUVlEdQb7tKSD6QO2w3dKIQ9RPPE0",
    "alg": "PS256",
    "dq": "wR3sfXbPpPQ6MKpA-k_JFxyaq9dDk6FJj4mvM9bzHmkSwVy9Pc9WKYEmxSeFs72ANVIasxf4-4fuUgU9qk2KqpvivT7Eltn2KMXA_6-ayjBzhBxIQh8aru4ZNs4YDa7RljAuQ3dkLwAsR5JAEyWrOiPxbICMx5bYuoxQrcE",
    "n": "vexY1QL7wrVmKvQiZdaP0vE0Vs_eVAbo1qkN_juIqec_HyXXXqlP6Zd2_zw9tIm4ZVN6WHRgl735LCy32JTx7ShvsosDPNfmBRJ9ZBWYfOZ3FGusn7mFumflLvNspYCv4gVpos4TchlsJKxOUxtRSiSFqpnGwES-g5O1J2n_wzxEDONqAzPXxy4Jm5QjmMpqOKuf6AdQIbQkI44UrHVRkVxwkD_NTpR1EP_P-DGKDoxtYHMKCOyDihyL3ROYJq7VgTAcGF7NNmta7G-AX8btCeFP7KNwFzXHpSDxidCFmAkRJu5FbUEx-rl75EeGLkemcGPAFUFuWHKxQw"
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
  clientCertFile: "selfSignedTlsAuth.pem",
  requestKey: {
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

export const tlsClientAuth = {
  clientId: "tlsClientAuth",
  clientSecret: "tlsClientAuth12345678901234567890123456789012345678901234567890123456789012345678901234567890",
  redirectUri: "https://www.certification.openid.net/test/a/idp_oidc_basic/callback",
  scope: "account",
  fapiBaselineScope: "read",
  fapiAdvanceScope: "write",
  identityVerificationScope: "transfers",
  idTokenAlg: "RS256",
  clientCertFile: "tlsClientAuth.pem",
  requestKey: {
    "kty": "EC",
    "d": "i9vzoxGroAEQxwGmn4mF6kAWiP5NZ0fMmNkkesY3V4Y",
    "use": "sig",
    "crv": "P-256",
    "kid": "tlsClientRequestKey",
    "x": "BMbdGWZ3-ui_tZrwXmUXjPJOkkPyXBN1jXRlYAYp0j4",
    "y": "5PN28UzVlNy8-_Y-oaYTOw2CDWOE4-p0DAixRD0_tm0",
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
  scope: "account identity_verification_application claims:authentication_devices claims:ex_sub ",
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

/**
 * Native App Client (application_type: native)
 * RFC 8252: OAuth 2.0 for Native Apps
 */
export const nativeAppClient = {
  clientId: "nativeAppClient",
  clientSecret: "nativeAppClientSecret1234567890123456789012345678901234567890123456789012345678901234567890",
  redirectUri: "http://127.0.0.1:8080/callback",
  redirectUriLocalhost: "http://localhost:8080/callback",
  scope: "openid profile email account",
  applicationType: "native",
};

/**
 * Helper function to create a custom server configuration for any tenant ID
 * Useful for tests that need to work with different tenants dynamically
 * 
 * @param {string} tenantId - The tenant ID to create configuration for
 * @param {string} [baseUrl] - Optional base URL (defaults to backendUrl)
 * @returns {object} Server configuration object
 * 
 * @example
 * // Create config for a specific tenant
 * const myTenantConfig = createServerConfig('my-test-tenant-id');
 * 
 * // Create config with custom base URL
 * const stagingConfig = createServerConfig('staging-tenant', 'https://staging.example.com');
 */
export { createServerConfig };

/**
 * Environment variables used for configuration:
 *
 * - IDP_SERVER_URL: Backend server URL (default: http://localhost:8080)
 * - IDP_SERVER_ADMIN_TENANT_ID: Admin tenant ID for Management API tests (default: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa)
 * - IDP_SERVER_TENANT_ID: Main tenant ID for OAuth/OIDC tests (default: 67e7eae6-62b0-4500-9eff-87459f63fc66)
 * - IDP_SERVER_FEDERATION_TENANT_ID: Federation tenant ID for SSO tests
 * - IDP_SERVER_UNSUPPORTED_TENANT_ID: Tenant ID for unsupported feature tests
 * - CIBA_USER_SUB: CIBA test user subject identifier
 * - CIBA_USER_EMAIL: CIBA test user email address
 * - CIBA_USER_DEVICE_ID: CIBA test user device ID
 * - CIBA_USERNAME: CIBA test username (for OAuth resource owner password credentials)
 * - CIBA_PASSWORD: CIBA test password
 *
 * All variables have sensible defaults for backward compatibility.
 *
 * Tenant Usage:
 * - adminServerConfig: Use for control_plane/system tests (Management API with 'management' scope)
 * - serverConfig: Use for spec/scenario tests (OAuth/OIDC/CIBA/FAPI tests)
 */
