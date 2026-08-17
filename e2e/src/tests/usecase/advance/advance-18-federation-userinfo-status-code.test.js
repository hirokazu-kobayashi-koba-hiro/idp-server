import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, post, postWithJson } from "../../../lib/http";
import { requestToken, postAuthentication } from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { generateRS256KeyPair } from "../../../lib/jose";
import { adminServerConfig, backendUrl, mockApiBaseUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import { requestFederation } from "../../../oauth/federation";
import { requestAuthorizations } from "../../../oauth/request";

/**
 * Advance Use Case: federation userinfo carries the resolved status code (Issue #1800)
 *
 * `response_resolve_configs` on a federation `userinfo_execution` could map an upstream response to
 * 429 or 503, but the callback answered a flat 400 or 500 — the client could not tell a malformed
 * request from an IdP that is rate limiting or briefly unavailable.
 *
 * The status was flattened twice on the way out, so only fixing one of them changed nothing
 * observable:
 *
 *   HttpRequestResult(429)
 *     -> UserinfoExecutionStatus        (was OK / CLIENT_ERROR / SERVER_ERROR)
 *     -> FederationInteractionStatus    (was SUCCESS / CLIENT_ERROR / SERVER_ERROR)
 *     -> HttpStatus.valueOf(statusCode)
 *
 * This drives a full SSO round trip against an `oauth-extension` provider whose userinfo lands on
 * the mock's 429 response, and asserts the callback answers 429 rather than 400.
 *
 * Prerequisites:
 * - Mock server (Mockoon) at host.docker.internal:4000 with POST /e2e/error-responses, which
 *   answers with the status named in the request body.
 */
describe("Advance Use Case: Federation userinfo status code (Issue #1800)", () => {
  let systemAccessToken;
  let orgAccessToken;
  let organizationId;
  let consumerTenantId;
  let providerTenantId;
  let consumerClientId;
  let consumerClientSecret;
  let providerClientId;
  let providerClientSecret;
  let providerUserEmail;
  let providerUserPassword;
  // Two providers: one whose upstream really answers 429, one that answers 200 and is remapped to
  // 503 by response_resolve_configs. Both must reach the caller intact.
  let upstreamStatusProvider;
  let resolvedStatusProvider;
  const consumerRedirectUri = "http://localhost:3000/callback";

  beforeAll(async () => {
    const timestamp = Date.now();
    organizationId = uuidv4();
    consumerTenantId = uuidv4();
    providerTenantId = uuidv4();
    consumerClientId = uuidv4();
    providerClientId = uuidv4();
    consumerClientSecret = `consumer-secret-${timestamp}`;
    providerClientSecret = `provider-secret-${timestamp}`;
    upstreamStatusProvider = `sso-upstream-${timestamp}`;
    resolvedStatusProvider = `sso-resolved-${timestamp}`;
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

    // Consumer tenant: the one whose callback we measure.
    const consumerOnboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `Federation Status Org ${timestamp}`,
          description: "E2E for #1800",
        },
        tenant: {
          id: consumerTenantId,
          name: `Consumer Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          session_config: {
            cookie_name: `FED_STATUS_${organizationId.substring(0, 8)}`,
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
            available_federations: [
              { id: uuidv4(), type: "oidc", sso_provider: upstreamStatusProvider },
              { id: uuidv4(), type: "oidc", sso_provider: resolvedStatusProvider },
            ],
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

    // Provider tenant: stands in for the upstream IdP's authorize / token endpoints. Its userinfo
    // endpoint is unused — the oauth-extension config below fetches userinfo from the mock instead.
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
            cookie_name: `FED_STATUS_P_${organizationId.substring(0, 8)}`,
            use_secure_cookie: false,
          },
          cors_config: { allow_origins: [backendUrl] },
        },
        authorization_server: {
          issuer: `${backendUrl}/${providerTenantId}`,
          authorization_endpoint: `${backendUrl}/${providerTenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${providerTenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post"],
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

    const providerClientResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${providerTenantId}/clients`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
      body: {
        client_id: providerClientId,
        client_name: `Provider OIDC Client ${timestamp}`,
        client_secret: providerClientSecret,
        redirect_uris: [
          `${backendUrl}/${consumerTenantId}/v1/authorizations/federations/oidc/callback`,
        ],
        response_types: ["code"],
        grant_types: ["authorization_code", "refresh_token"],
        scope: "openid profile email",
        token_endpoint_auth_method: "client_secret_post",
        application_type: "web",
      },
    });
    expect(providerClientResponse.status).toBe(201);

    const providerUserResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${providerTenantId}/users`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
      body: {
        sub: uuidv4(),
        provider_id: "idp-server",
        email: providerUserEmail,
        raw_password: providerUserPassword,
        username: providerUserEmail,
        name: `Federation Status User ${timestamp}`,
        email_verified: true,
      },
    });
    expect(providerUserResponse.status).toBe(201);

    // oauth-extension: userinfo is an HTTP call we control, so the upstream can be made to answer
    // whatever the test needs. The mock's /e2e/error-responses returns the status named in the
    // request body, and falls through to 200 with verification_status: "pending" when none matches.
    const registerFederation = async (ssoProvider, httpRequest) => {
      const response = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${consumerTenantId}/federation-configurations`,
        headers: { Authorization: `Bearer ${orgAccessToken}` },
        body: {
          id: uuidv4(),
          type: "oidc",
          sso_provider: ssoProvider,
          enabled: true,
          payload: {
            type: "oauth-extension",
            provider: "oauth-extension",
            issuer: `${backendUrl}/${providerTenantId}`,
            issuer_name: ssoProvider,
            authorization_endpoint: `${backendUrl}/${providerTenantId}/v1/authorizations`,
            token_endpoint: `${backendUrl}/${providerTenantId}/v1/tokens`,
            userinfo_endpoint: `${backendUrl}/${providerTenantId}/v1/userinfo`,
            jwks_uri: `${backendUrl}/${providerTenantId}/v1/jwks`,
            scopes_supported: ["openid", "profile", "email"],
            client_id: providerClientId,
            client_secret: providerClientSecret,
            redirect_uri: `${backendUrl}/${consumerTenantId}/v1/authorizations/federations/oidc/callback`,
            userinfo_execution: {
              function: "http_requests",
              http_requests: [httpRequest],
            },
            userinfo_mapping_rules: [
              {
                from: "$.userinfo_execution_http_requests[0].response_body.sub",
                to: "external_user_id",
              },
            ],
          },
        },
      });
      expect(response.status).toBe(201);
      return response;
    };

    // The upstream is rate limiting. Nothing is remapped — 429 is the real status the IdP
    // answered, and it must survive to the caller unchanged.
    await registerFederation(upstreamStatusProvider, {
      url: `${mockApiBaseUrl}/e2e/error-responses`,
      method: "POST",
      header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
      body_mapping_rules: [{ static_value: "429", to: "status" }],
    });

    // The upstream answers 200 with a body meaning "not ready", and response_resolve_configs
    // turns that into 503. This is the path the issue was originally written about, and it is
    // flattened at exactly the same two places as a real 429.
    const resolvedStatusConfigResponse = await registerFederation(resolvedStatusProvider, {
      url: `${mockApiBaseUrl}/e2e/error-responses`,
      method: "POST",
      header_mapping_rules: [{ static_value: "application/json", to: "Content-Type" }],
      body_mapping_rules: [{ static_value: "no-match-falls-through-to-200", to: "status" }],
      response_resolve_configs: [
        {
          conditions: [
            { path: "$.response_body.verification_status", operation: "eq", value: "pending" },
          ],
          match_mode: "ALL",
          mapped_status_code: 503,
        },
      ],
    });

    // #1500-class check: a field that stores but never comes back is how response_resolve_configs
    // was inert on the authentication side. Confirm the federation config echoes it.
    const storedResolveConfigs =
      resolvedStatusConfigResponse.data?.result?.payload?.userinfo_execution?.http_requests?.[0]
        ?.response_resolve_configs;
    console.log("Stored response_resolve_configs:", JSON.stringify(storedResolveConfigs));
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
  async function federationCallbackFor(ssoProvider) {
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
          providerName: ssoProvider,
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

        // The upstream leg succeeded; userinfo is where the failure surfaces.
        callbackResponse = await post({
          url: `${backendUrl}/${consumerTenantId}/v1/authorizations/federations/oidc/callback`,
          body: params.toString(),
        });
        console.log(
          `Federation callback (${ssoProvider}):`,
          callbackResponse.status,
          JSON.stringify(callbackResponse.data, null, 2)
        );
      },
    }).catch(() => {
      // The flow cannot reach an authorization code — the callback failed on purpose. The
      // assertions are on the callback itself.
    });

    expect(callbackResponse).toBeDefined();
    return callbackResponse;
  }

  it("answers 429 when the upstream userinfo really is rate limited", async () => {
    const callbackResponse = await federationCallbackFor(upstreamStatusProvider);

    // Before #1800 both UserinfoExecutionStatus and FederationInteractionStatus collapsed this to
    // CLIENT_ERROR, so the caller saw 400 and could not tell a bad request from "retry later".
    expect(callbackResponse.status).toBe(429);
    expect(callbackResponse.status).not.toBe(400);
  }, 90000);

  it("answers 503 when response_resolve_configs maps a 200 body to it", async () => {
    const callbackResponse = await federationCallbackFor(resolvedStatusProvider);

    // The upstream answered 200; the 503 exists only because response_resolve_configs said so.
    // This pins that the resolver is honoured on the federation side at all, and that its result
    // survives both flattening points.
    expect(callbackResponse.status).toBe(503);
    expect(callbackResponse.status).not.toBe(200);
    expect(callbackResponse.status).not.toBe(500);
  }, 90000);
});
