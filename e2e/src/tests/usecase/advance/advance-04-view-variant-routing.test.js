import { describe, expect, it, beforeAll } from "@jest/globals";
import { get, putWithJson } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

/**
 * Advance Use Case: request-scoped authorization view routing (#1830)
 *
 * A canary release of the authorization pages is driven by the relying party: it names a variant
 * on the authorization request, and the tenant declares what each name resolves to. What this
 * covers is the one thing unit tests on a hand-built context cannot — that the tenant's ui_config
 * survives persistence, that the variant parameter survives OAuthRequestKey filtering, and that
 * the Location header actually points at a page that exists.
 *
 * The variants below map onto the deployments this repo already runs:
 * - v2  same origin, both pages declared      -> auth.local.test/v2/...
 * - v3  another origin, sign-in only declared -> auth.idp.local/v2/signin/
 *
 * v3 is the case that decides the inheritance rule. It renames sign-in but not sign-up, so a
 * sign-up on that variant cannot borrow the default deployment's path — that path is only served
 * there. It falls back to the default deployment whole.
 */
describe("Advance Use Case: Authorization View Variant Routing", () => {
  const defaultBaseUrl = "https://auth.local.test";
  const variantBaseUrl = "https://auth.idp.local";

  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let userEmail;
  let userPassword;
  let authorizationEndpoint;

  const authorize = async (extraParams) => {
    const params = new URLSearchParams({
      response_type: "code",
      client_id: clientId,
      redirect_uri: "http://localhost:3000/callback",
      scope: "openid profile email",
      state: `state-${Date.now()}`,
      ...extraParams,
    });
    const response = await get({
      url: `${authorizationEndpoint}?${params.toString()}`,
      headers: {},
    });
    expect(response.status).toBe(302);
    return new URL(response.headers.location);
  };

  const pathOf = (location) => `${location.origin}${location.pathname}`;

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
    userEmail = `view-variant-${timestamp}@example.com`;
    userPassword = `TestPass${timestamp}!`;
    authorizationEndpoint = `${backendUrl}/${tenantId}/v1/authorizations`;

    const jwksContent = await generateECP256JWKS();

    const onboardingResponse = await onboarding({
      body: {
        organization: {
          id: organizationId,
          name: `View Variant Org ${timestamp}`,
          description: "Test organization for request-scoped view routing",
        },
        tenant: {
          id: tenantId,
          name: `View Variant Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          session_config: {
            cookie_name: `VV_TEST_${organizationId.substring(0, 8)}`,
            use_secure_cookie: false,
          },
          cors_config: {
            allow_origins: [backendUrl, defaultBaseUrl, variantBaseUrl],
          },
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
          name: `View Variant User ${timestamp}`,
          email_verified: true,
        },
        client: {
          client_id: clientId,
          client_name: `View Variant Client ${timestamp}`,
          client_secret: clientSecret,
          redirect_uris: ["http://localhost:3000/callback"],
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

    // Declare the variants through the management API rather than a fixture, so what the test
    // exercises is the same write path an operator uses (the update is a full replace).
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
    const orgAccessToken = orgTokenResponse.data.access_token;
    const managementUrl = `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`;

    const tenantResponse = await get({
      url: managementUrl,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });
    expect(tenantResponse.status).toBe(200);

    const updateBody = { ...tenantResponse.data };
    delete updateBody.created_at;
    delete updateBody.updated_at;
    updateBody.ui_config = {
      base_url: defaultBaseUrl,
      signin_page: "/signin/",
      signup_page: "/signup/",
      variant_param: "view_version",
      variants: {
        v2: { signin_page: "/v2/signin/", signup_page: "/v2/signup/" },
        v3: { base_url: variantBaseUrl, signin_page: "/v2/signin/" },
      },
    };

    const updateResponse = await putWithJson({
      url: managementUrl,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
      body: updateBody,
    });
    expect(updateResponse.status).toBe(200);
  });

  describe("a request naming no variant", () => {
    it("goes to the tenant's default pages", async () => {
      expect(pathOf(await authorize({}))).toBe(`${defaultBaseUrl}/signin/`);
      expect(pathOf(await authorize({ prompt: "create" }))).toBe(
        `${defaultBaseUrl}/signup/`,
      );
    });
  });

  describe("a variant on the same deployment", () => {
    it("serves both of the pages it declares", async () => {
      expect(pathOf(await authorize({ view_version: "v2" }))).toBe(
        `${defaultBaseUrl}/v2/signin/`,
      );
      expect(
        pathOf(await authorize({ view_version: "v2", prompt: "create" })),
      ).toBe(`${defaultBaseUrl}/v2/signup/`);
    });
  });

  describe("a variant on another deployment", () => {
    it("serves the page it declares from its own origin", async () => {
      expect(pathOf(await authorize({ view_version: "v3" }))).toBe(
        `${variantBaseUrl}/v2/signin/`,
      );
    });

    it("sends the page it did not declare to the default deployment", async () => {
      // Not variantBaseUrl + /signup/: the variant renamed sign-in, so /signup/ belongs to the
      // default deployment's scheme and only resolves there.
      expect(
        pathOf(await authorize({ view_version: "v3", prompt: "create" })),
      ).toBe(`${defaultBaseUrl}/signup/`);
    });
  });

  describe("a variant nobody declared", () => {
    it("falls back to the default pages", async () => {
      expect(pathOf(await authorize({ view_version: "v9" }))).toBe(
        `${defaultBaseUrl}/signin/`,
      );
    });

    it("never lets the name reach the path", async () => {
      // The authorization URL is public, so anyone can put a value on it.
      expect(pathOf(await authorize({ view_version: "../../admin" }))).toBe(
        `${defaultBaseUrl}/signin/`,
      );
      expect(pathOf(await authorize({ view_version: "" }))).toBe(
        `${defaultBaseUrl}/signin/`,
      );
    });
  });

  describe("the pages the redirects point at", () => {
    it("are actually served, and say which variant they are", async () => {
      // The Location header alone cannot tell a working canary from a 404 on the new deployment.
      const cases = [
        { location: await authorize({}), badge: false },
        { location: await authorize({ view_version: "v2" }), badge: true },
        {
          location: await authorize({ view_version: "v2", prompt: "create" }),
          badge: true,
        },
        { location: await authorize({ view_version: "v3" }), badge: true },
      ];

      for (const { location, badge } of cases) {
        const page = await get({ url: pathOf(location), headers: {} });
        expect(page.status).toBe(200);
        expect(String(page.data).includes("view-variant-badge")).toBe(badge);
      }
    });
  });

  describe("the variant name itself", () => {
    it("stays in the custom parameters so the page can read it", async () => {
      // It reaches the page on the URL and in view-data, which is how the page reports which
      // cohort a session belonged to.
      const location = await authorize({ view_version: "v2" });

      expect(location.searchParams.get("view_version")).toBe("v2");
      expect(location.searchParams.get("id")).toBeTruthy();
      expect(location.searchParams.get("tenant_id")).toBe(tenantId);
    });
  });
});
