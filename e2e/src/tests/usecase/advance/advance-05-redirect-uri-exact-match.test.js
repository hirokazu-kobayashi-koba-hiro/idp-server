import { describe, expect, it, beforeAll } from "@jest/globals";
import { get, putWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

/**
 * Advance Use Case: redirect_uri exact string matching (#1835)
 *
 * RFC 6749 Section 3.1.2.3 admits any RFC 3986 Section 6 comparison; RFC 9700 Section 2.1
 * (BCP 240, updating 6749) narrows it to exact string matching, keeping only the loopback port
 * exception for native apps. `redirect_uri_exact_match_required` selects which rule applies.
 *
 * Two things only an end-to-end run can show. First, which profile a request lands on is decided
 * by the requested scope, so the same client and the same registered URI compare differently with
 * and without `openid` — that is the reason the flag is profile-independent. Second, where the
 * failure goes: RFC 6749 Section 3.1.2.4 forbids redirecting to an unverified redirect_uri, so the
 * request lands on the tenant's error page instead of returning `invalid_request` to the client.
 * Both responses are 302s, which is why these tests compare destinations rather than status codes,
 * and why flipping the default is a change end users see.
 */
describe("Advance Use Case: redirect_uri Exact String Matching", () => {
  const registeredRedirectUri = "https://app.example.com/cb";

  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let userEmail;
  let userPassword;
  let orgAccessToken;
  let managementUrl;
  let authorizationEndpoint;

  /**
   * A rejected redirect_uri is still a 302 — to the tenant's error page, not to the client. RFC
   * 6749 Section 3.1.2.4 forbids redirecting to an unverified redirect_uri, so the status alone
   * cannot tell acceptance from rejection; the destination can.
   */
  const authorize = async ({ redirectUri, scope }) => {
    const params = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: redirectUri,
      scope,
      state: `state-${Date.now()}`,
    });
    const response = await get({
      url: `${authorizationEndpoint}?${params.toString()}`,
      headers: {},
    });
    expect(response.status).toBe(302);

    const location = new URL(response.headers.location);
    return {
      location,
      accepted: !location.pathname.startsWith("/error"),
      error: location.searchParams.get("error"),
      errorDescription: location.searchParams.get("error_description"),
    };
  };

  const setExactMatchRequired = async (enabled) => {
    const current = await get({
      url: `${managementUrl}/authorization-server`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });
    expect(current.status).toBe(200);

    const body = { ...current.data };
    delete body.created_at;
    delete body.updated_at;
    body.extension = {
      ...body.extension,
      redirect_uri_exact_match_required: enabled,
    };

    const response = await putWithJson({
      url: `${managementUrl}/authorization-server`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
      body,
    });
    expect(response.status).toBe(200);
  };

  beforeAll(async () => {
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
    const systemAccessToken = tokenResponse.data.access_token;

    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    userEmail = `redirect-exact-${timestamp}@example.com`;
    userPassword = `TestPass${timestamp}!`;
    authorizationEndpoint = `${backendUrl}/${tenantId}/v1/authorizations`;
    managementUrl = `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`;

    const jwksContent = await generateECP256JWKS();

    const onboardingResponse = await onboarding({
      body: {
        organization: {
          id: organizationId,
          name: `Redirect Exact Match Org ${timestamp}`,
          description: "Test organization for RFC 9700 exact string matching",
        },
        tenant: {
          id: tenantId,
          name: `Redirect Exact Match Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          session_config: {
            cookie_name: `RE_TEST_${organizationId.substring(0, 8)}`,
            use_secure_cookie: false,
          },
          cors_config: { allow_origins: [backendUrl] },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: authorizationEndpoint,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          grant_types_supported: ["authorization_code", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management"],
          response_types_supported: ["code"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          response_modes_supported: ["query"],
          extension: { access_token_type: "JWT" },
        },
        user: {
          sub: uuidv4(),
          email: userEmail,
          raw_password: userPassword,
          username: userEmail,
          name: `Redirect Exact Match User ${timestamp}`,
          email_verified: true,
        },
        client: {
          client_id: clientId,
          client_name: `Redirect Exact Match Client ${timestamp}`,
          client_secret: clientSecret,
          redirect_uris: [registeredRedirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code", "password"],
          scope: "openid profile email management",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    expect(onboardingResponse.status).toBe(201);

    const orgTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "openid profile email management",
      clientId: clientId,
      clientSecret: clientSecret,
    });
    expect(orgTokenResponse.status).toBe(200);
    orgAccessToken = orgTokenResponse.data.access_token;
  });

  describe("the default (RFC 6749 comparison)", () => {
    beforeAll(async () => {
      await setExactMatchRequired(false);
    });

    it("accepts an identical redirect_uri", async () => {
      const result = await authorize({
        redirectUri: registeredRedirectUri,
        scope: "profile",
      });

      expect(result.accepted).toBe(true);
    });

    it("accepts differences syntax-based normalization removes", async () => {
      // RFC 3986 Section 6.2.2 / 6.2.3 — the destination host is unchanged either way.
      for (const redirectUri of [
        "HTTPS://APP.EXAMPLE.COM/cb",
        "https://app.example.com:443/cb",
      ]) {
        const result = await authorize({ redirectUri, scope: "profile" });
        expect(result.accepted).toBe(true);
      }
    });

    it("accepts a query the registered redirect_uri does not have", async () => {
      // The normalized comparison never looks at the query. This is the difference most likely to
      // be relied on in practice, so it is the one that decides whether flipping the default is
      // safe for a given tenant.
      const result = await authorize({
        redirectUri: `${registeredRedirectUri}?foo=bar`,
        scope: "profile",
      });

      expect(result.accepted).toBe(true);
    });

    it("still compares exactly once openid makes it an OIDC request", async () => {
      // The looser comparison is reachable by dropping openid, which is why the flag is
      // profile-independent rather than a fix to the OAuth 2.0 verifier alone.
      const result = await authorize({
        redirectUri: `${registeredRedirectUri}?foo=bar`,
        scope: "openid profile",
      });

      expect(result.accepted).toBe(false);
    });
  });

  describe("with redirect_uri_exact_match_required", () => {
    beforeAll(async () => {
      await setExactMatchRequired(true);
    });

    it("accepts an identical redirect_uri", async () => {
      const result = await authorize({
        redirectUri: registeredRedirectUri,
        scope: "profile",
      });

      expect(result.accepted).toBe(true);
    });

    it("rejects what syntax-based normalization used to accept", async () => {
      for (const redirectUri of [
        "HTTPS://APP.EXAMPLE.COM/cb",
        "https://app.example.com:443/cb",
        `${registeredRedirectUri}?foo=bar`,
      ]) {
        const result = await authorize({ redirectUri, scope: "profile" });
        expect(result.accepted).toBe(false);
      }
    });

    it("sends the failure to the error page, not back to the client", async () => {
      // RFC 6749 Section 3.1.2.4: an unverified redirect_uri must not be redirected to. The
      // failure is still a 302, but to the tenant's error page — so tightening this strands the
      // end user rather than returning invalid_request to the client. That is why the default
      // stays off.
      const result = await authorize({
        redirectUri: `${registeredRedirectUri}?foo=bar`,
        scope: "profile",
      });

      expect(result.location.origin).not.toBe("https://app.example.com");
      expect(result.error).toBe("invalid_request");
      expect(result.errorDescription).toContain("exact string matching failed");
    });

    it("keeps behaving the same for OIDC requests", async () => {
      const identical = await authorize({
        redirectUri: registeredRedirectUri,
        scope: "openid profile",
      });
      const withQuery = await authorize({
        redirectUri: `${registeredRedirectUri}?foo=bar`,
        scope: "openid profile",
      });

      expect(identical.accepted).toBe(true);
      expect(withQuery.accepted).toBe(false);
    });
  });
});
