import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, post, postWithJson } from "../../../lib/http";
import { requestToken, postAuthentication } from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { generateRS256KeyPair } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import { requestFederation } from "../../../oauth/federation";
import { requestAuthorizations } from "../../../oauth/request";

/**
 * Advance Use Case: federation authenticates to the external IdP with a client assertion (#1822)
 *
 * When idp-server is the client of an external IdP, it authenticated at the token endpoint only
 * with client_secret_basic or client_secret_post — any other client_authentication_type went out
 * as client_secret_post without a word. This drives a full SSO round trip in which the external
 * IdP (another idp-server tenant) registers the client as client_secret_jwt or private_key_jwt,
 * so the code exchange succeeds only if the assertion is built and signed as the external IdP's
 * own client authentication expects.
 */
describe("Advance Use Case: Federation client assertion (#1822)", () => {
  let systemAccessToken;
  let orgAccessToken;
  let organizationId;
  let consumerTenantId;
  let providerTenantId;
  let consumerClientId;
  let consumerClientSecret;
  let providerUserEmail;
  let providerUserPassword;
  const consumerRedirectUri = "http://localhost:3000/callback";

  // The private key the consumer signs with, and another the provider has never seen.
  let signingKey;
  let strangerKey;

  const providers = {};

  beforeAll(async () => {
    const timestamp = Date.now();
    organizationId = uuidv4();
    consumerTenantId = uuidv4();
    providerTenantId = uuidv4();
    consumerClientId = uuidv4();
    consumerClientSecret = `consumer-secret-${timestamp}`;
    providerUserEmail = `provider-user-${timestamp}@example.com`;
    providerUserPassword = `ProviderPass_${timestamp}!`;

    const orgAdminEmail = `org-admin-${timestamp}@example.com`;
    const orgAdminPassword = `OrgAdminPass_${timestamp}!`;

    const tokenResponse = await requestToken({
      endpoint: adminServerConfig.tokenEndpoint,
      grantType: "password",
      username: adminServerConfig.oauth.username,
      password: adminServerConfig.oauth.password,
      scope: adminServerConfig.adminClient.scope,
      clientId: adminServerConfig.adminClient.clientId,
      clientSecret: adminServerConfig.adminClient.clientSecret,
    });
    expect(tokenResponse.status).toBe(200);
    systemAccessToken = tokenResponse.data.access_token;

    const { jwks: consumerJwks } = await generateRS256KeyPair();
    const { jwks: providerJwks } = await generateRS256KeyPair();
    const clientKeyPair = await generateRS256KeyPair();
    signingKey = clientKeyPair.privateKey;
    strangerKey = (await generateRS256KeyPair()).privateKey;

    for (const name of ["secret-jwt", "private-key-jwt", "private-key-jwt-issuer", "stranger-key"]) {
      providers[name] = { ssoProvider: `sso-${name}-${timestamp}`, clientId: uuidv4() };
    }

    const consumerOnboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `Federation Assertion Org ${timestamp}`,
          description: "E2E for #1822",
        },
        tenant: {
          id: consumerTenantId,
          name: `Consumer Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          session_config: {
            cookie_name: `FED_ASSERT_${organizationId.substring(0, 8)}`,
            use_secure_cookie: false,
          },
          cors_config: { allow_origins: [backendUrl] },
        },
        authorization_server: {
          issuer: `${backendUrl}/${consumerTenantId}`,
          authorization_endpoint: `${backendUrl}/${consumerTenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${consumerTenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          userinfo_endpoint: `${backendUrl}/${consumerTenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${consumerTenantId}/v1/jwks`,
          jwks: consumerJwks,
          grant_types_supported: ["authorization_code", "refresh_token", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management", "org-management"],
          response_types_supported: ["code"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["RS256", "ES256"],
          response_modes_supported: ["query"],
          extension: { access_token_type: "JWT" },
        },
        user: {
          sub: uuidv4(),
          email: orgAdminEmail,
          raw_password: orgAdminPassword,
          username: orgAdminEmail,
          name: `Org Admin ${timestamp}`,
          email_verified: true,
        },
        client: {
          client_id: consumerClientId,
          client_name: `Consumer Client ${timestamp}`,
          client_secret: consumerClientSecret,
          redirect_uris: [consumerRedirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code", "refresh_token", "password"],
          scope: "openid profile email management org-management",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
          extension: {
            available_federations: Object.values(providers).map((provider) => ({
              id: uuidv4(),
              type: "oidc",
              sso_provider: provider.ssoProvider,
            })),
          },
        },
      },
    });
    expect(consumerOnboardingResponse.status).toBe(201);

    const orgAdminTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${consumerTenantId}/v1/tokens`,
      grantType: "password",
      username: orgAdminEmail,
      password: orgAdminPassword,
      scope: "management org-management",
      clientId: consumerClientId,
      clientSecret: consumerClientSecret,
    });
    expect(orgAdminTokenResponse.status).toBe(200);
    orgAccessToken = orgAdminTokenResponse.data.access_token;

    // Provider tenant: the external IdP. Its own client authentication is what checks the assertion.
    const providerTenantResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
      body: {
        tenant: {
          id: providerTenantId,
          name: `Provider Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          session_config: {
            cookie_name: `FED_ASSERT_P_${organizationId.substring(0, 8)}`,
            use_secure_cookie: false,
          },
          cors_config: { allow_origins: [backendUrl] },
        },
        authorization_server: {
          issuer: `${backendUrl}/${providerTenantId}`,
          authorization_endpoint: `${backendUrl}/${providerTenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${providerTenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_jwt", "private_key_jwt"],
          token_endpoint_auth_signing_alg_values_supported: ["RS256", "PS256", "HS256"],
          userinfo_endpoint: `${backendUrl}/${providerTenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${providerTenantId}/v1/jwks`,
          jwks: providerJwks,
          grant_types_supported: ["authorization_code", "refresh_token", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email"],
          response_types_supported: ["code"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["RS256", "ES256"],
          response_modes_supported: ["query"],
          claims_supported: ["sub", "name", "email", "email_verified"],
          extension: { access_token_type: "JWT" },
        },
      },
    });
    expect(providerTenantResponse.status).toBe(201);

    const callbackUri = `${backendUrl}/${consumerTenantId}/v1/authorizations/federations/oidc/callback`;
    const clientSecretJwtSecret = `secret-jwt-${timestamp}-0123456789abcdef0123456789abcdef`;

    const registerProviderClient = async (clientId, authentication) => {
      const response = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${providerTenantId}/clients`,
        headers: { Authorization: `Bearer ${orgAccessToken}` },
        body: {
          client_id: clientId,
          client_name: `Provider Client ${clientId}`,
          redirect_uris: [callbackUri],
          response_types: ["code"],
          grant_types: ["authorization_code", "refresh_token"],
          scope: "openid profile email",
          application_type: "web",
          ...authentication,
        },
      });
      expect(response.status).toBe(201);
    };

    // The external IdP requires the assertion's alg to be the registered key's alg, so the client
    // signing PS256 registers the same key labelled PS256.
    const publicJwks = (alg) =>
      JSON.stringify({ keys: [{ ...clientKeyPair.publicKey, alg }] });
    await registerProviderClient(providers["secret-jwt"].clientId, {
      client_secret: clientSecretJwtSecret,
      token_endpoint_auth_method: "client_secret_jwt",
    });
    await registerProviderClient(providers["private-key-jwt"].clientId, {
      token_endpoint_auth_method: "private_key_jwt",
      jwks: publicJwks("PS256"),
    });
    for (const name of ["private-key-jwt-issuer", "stranger-key"]) {
      await registerProviderClient(providers[name].clientId, {
        token_endpoint_auth_method: "private_key_jwt",
        jwks: publicJwks("RS256"),
      });
    }

    const providerUserResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${providerTenantId}/users`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
      body: {
        sub: uuidv4(),
        provider_id: "idp-server",
        email: providerUserEmail,
        raw_password: providerUserPassword,
        username: providerUserEmail,
        name: `Federation Assertion User ${timestamp}`,
        email_verified: true,
      },
    });
    expect(providerUserResponse.status).toBe(201);

    const registerFederation = async (provider, clientAuthentication) => {
      const response = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${consumerTenantId}/federation-configurations`,
        headers: { Authorization: `Bearer ${orgAccessToken}` },
        body: {
          id: uuidv4(),
          type: "oidc",
          sso_provider: provider.ssoProvider,
          enabled: true,
          payload: {
            type: "standard",
            provider: "standard",
            issuer: `${backendUrl}/${providerTenantId}`,
            issuer_name: provider.ssoProvider,
            authorization_endpoint: `${backendUrl}/${providerTenantId}/v1/authorizations`,
            token_endpoint: `${backendUrl}/${providerTenantId}/v1/tokens`,
            userinfo_endpoint: `${backendUrl}/${providerTenantId}/v1/userinfo`,
            jwks_uri: `${backendUrl}/${providerTenantId}/v1/jwks`,
            scopes_supported: ["openid", "profile", "email"],
            client_id: provider.clientId,
            redirect_uri: callbackUri,
            userinfo_mapping_rules: [
              { from: "$.http_request.response_body.sub", to: "external_user_id" },
              { from: "$.http_request.response_body.email", to: "email" },
            ],
            ...clientAuthentication,
          },
        },
      });
      expect(response.status).toBe(201);
    };

    await registerFederation(providers["secret-jwt"], {
      client_authentication_type: "client_secret_jwt",
      client_secret: clientSecretJwtSecret,
    });
    await registerFederation(providers["private-key-jwt"], {
      client_authentication_type: "private_key_jwt",
      client_authentication: { signing_algorithm: "PS256", signing_key: signingKey },
    });
    await registerFederation(providers["private-key-jwt-issuer"], {
      client_authentication_type: "private_key_jwt",
      client_authentication: { signing_key: signingKey, assertion_audience: "issuer" },
    });
    await registerFederation(providers["stranger-key"], {
      client_authentication_type: "private_key_jwt",
      client_authentication: { signing_key: strangerKey },
    });
  });

  afterAll(async () => {
    for (const tenant of [providerTenantId, consumerTenantId]) {
      if (tenant) {
        await deletion({
          url: `${backendUrl}/v1/management/tenants/${tenant}`,
          headers: { Authorization: `Bearer ${systemAccessToken}` },
        }).catch(() => {});
      }
    }
    if (organizationId) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  /** Runs a full SSO round trip against the named provider and returns the callback response. */
  async function federationCallbackFor(provider) {
    let callbackResponse;

    await requestAuthorizations({
      endpoint: `${backendUrl}/${consumerTenantId}/v1/authorizations`,
      authorizeEndpoint: `${backendUrl}/${consumerTenantId}/v1/authorizations/{id}/authorize`,
      clientId: consumerClientId,
      responseType: "code",
      state: `state-${Date.now()}`,
      scope: "openid profile email",
      redirectUri: consumerRedirectUri,
      interaction: async (authId) => {
        const { params } = await requestFederation({
          url: backendUrl,
          authSessionId: authId,
          authSessionTenantId: consumerTenantId,
          type: "oidc",
          providerName: provider.ssoProvider,
          federationTenantId: providerTenantId,
          user: { email: providerUserEmail },
          interaction: async (providerAuthId) => {
            const passwordResponse = await postAuthentication({
              endpoint: `${backendUrl}/${providerTenantId}/v1/authorizations/{id}/password-authentication`,
              id: providerAuthId,
              body: { username: providerUserEmail, password: providerUserPassword },
            });
            expect(passwordResponse.status).toBe(200);
          },
        });

        // The callback is where the code is exchanged, so it is where the client assertion is
        // presented to the external IdP.
        callbackResponse = await post({
          url: `${backendUrl}/${consumerTenantId}/v1/authorizations/federations/oidc/callback`,
          body: params.toString(),
        });
        console.log(
          `Federation callback (${provider.ssoProvider}):`,
          callbackResponse.status,
          JSON.stringify(callbackResponse.data, null, 2)
        );
      },
    }).catch(() => {
      // A refused exchange cannot reach an authorization code. The assertions are on the callback.
    });

    expect(callbackResponse).toBeDefined();
    return callbackResponse;
  }

  it("exchanges the code under client_secret_jwt, signing the assertion with the client secret", async () => {
    const callbackResponse = await federationCallbackFor(providers["secret-jwt"]);
    expect(callbackResponse.status).toBe(200);
  }, 90000);

  it("exchanges the code under private_key_jwt, signing with the configured key and algorithm", async () => {
    const callbackResponse = await federationCallbackFor(providers["private-key-jwt"]);
    expect(callbackResponse.status).toBe(200);
    expect(callbackResponse.data.id).toBeDefined();
  }, 90000);

  it("addresses the assertion to the issuer when assertion_audience is issuer", async () => {
    const callbackResponse = await federationCallbackFor(providers["private-key-jwt-issuer"]);
    expect(callbackResponse.status).toBe(200);
  }, 90000);

  it("is refused by the external IdP when the assertion is signed with a key it does not know", async () => {
    const callbackResponse = await federationCallbackFor(providers["stranger-key"]);
    // Refused by the external IdP's own client authentication, not lost on the way: the token
    // endpoint's 401 invalid_client comes back as it is.
    expect(callbackResponse.status).toBe(401);
    expect(callbackResponse.data.error).toBe("invalid_client");
  }, 90000);
});
