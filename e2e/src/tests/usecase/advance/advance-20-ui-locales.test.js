import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get } from "../../../lib/http";
import { requestToken, getAuthorizations, getConfiguration } from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

/**
 * Advance Use Case: ui_locales reaches the sign-in view (Issue #1801)
 *
 * `ui_locales` was accepted, validated and stored on the authorization request, and then read by
 * nobody: the view-data response did not carry it, the redirect to the sign-in page did not carry
 * it, and `custom_params` drops it because it is a known parameter. An RP could ask for a language
 * and the sign-in page had no way to learn about it.
 *
 * Three things are pinned here:
 *
 *   1. The redirect to the sign-in page carries ui_locales, so the page can settle its language on
 *      first paint rather than after the view-data round trip.
 *   2. view-data carries it as an array, in the requested order of preference.
 *   3. Discovery advertises ui_locales_supported, which the configuration used to drop on read.
 *
 * The order matters and is asserted explicitly: "fr-CA fr en" means Canadian French first, and a
 * view that walks the list picks the wrong language if the order is not the requested one.
 */
describe("Advance Use Case: ui_locales (Issue #1801)", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  const redirectUri = "https://app.example.com/callback";

  // Ordered by preference, and long enough that an unordered collection would not return it in
  // request order by luck.
  const REQUESTED = ["fr-CA", "fr", "en-GB", "en", "ja-JP", "ja", "de", "es"];
  const SUPPORTED = ["ja-JP", "en-US", "fr-FR"];

  beforeAll(async () => {
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    const clientSecret = `client-secret-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();

    const systemTokenResponse = await requestToken({
      endpoint: adminServerConfig.tokenEndpoint,
      grantType: "password",
      username: adminServerConfig.oauth.username,
      password: adminServerConfig.oauth.password,
      scope: adminServerConfig.adminClient.scope,
      clientId: adminServerConfig.adminClient.clientId,
      clientSecret: adminServerConfig.adminClient.clientSecret,
    });
    expect(systemTokenResponse.status).toBe(200);
    systemAccessToken = systemTokenResponse.data.access_token;

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `UI Locales Org ${timestamp}`,
          description: "E2E for #1801",
        },
        tenant: {
          id: tenantId,
          name: `UI Locales Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          scopes_supported: ["openid", "profile", "email", "management", "org-management"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code", "password"],
          id_token_signing_alg_values_supported: ["ES256"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          claims_supported: ["sub", "name", "email", "email_verified"],
          // The field this issue adds. Before it existed, Jackson dropped it here and the
          // configuration registered without it.
          ui_locales_supported: SUPPORTED,
          extension: { access_token_type: "JWT" },
        },
        user: {
          sub: uuidv4(),
          provider_id: "idp-server",
          name: "Admin User",
          email: `admin-${timestamp}@ui-locales.example.com`,
          email_verified: true,
          raw_password: `AdminPass_${timestamp}!`,
        },
        client: {
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uris: [redirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code", "password"],
          scope: "openid profile email management org-management",
          client_name: "UI Locales Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);
  });

  afterAll(async () => {
    if (systemAccessToken) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  /** Starts an authorization and returns the sign-in page URL the server redirects to. */
  async function startAuthorization(uiLocales) {
    const authResponse = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `ui-locales-${Date.now()}`,
      scope: "openid profile email",
      redirectUri,
      uiLocales,
    });
    expect(authResponse.status).toBe(302);
    return new URL(authResponse.headers.location, backendUrl);
  }

  async function viewDataOf(signinUrl) {
    const authId = signinUrl.searchParams.get("id");
    expect(authId).toBeTruthy();

    const response = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/view-data`,
    });
    expect(response.status).toBe(200);
    return response.data;
  }

  it("carries ui_locales on the redirect to the sign-in page", async () => {
    const signinUrl = await startAuthorization(REQUESTED.join(" "));
    console.log("sign-in URL:", signinUrl.toString());

    // Space separated, exactly as requested. URLSearchParams decodes the '+' back to spaces.
    expect(signinUrl.searchParams.get("ui_locales")).toBe(REQUESTED.join(" "));
  });

  it("carries ui_locales in view-data as an ordered array", async () => {
    const signinUrl = await startAuthorization(REQUESTED.join(" "));

    const viewData = await viewDataOf(signinUrl);
    console.log("view-data ui_locales:", JSON.stringify(viewData.ui_locales));

    expect(Array.isArray(viewData.ui_locales)).toBe(true);
    // toEqual on an array compares element order, which is the point: a view walking the list has
    // to see Canadian French before French.
    expect(viewData.ui_locales).toEqual(REQUESTED);
  });

  it("passes locales through without filtering against ui_locales_supported", async () => {
    // The spec says an error SHOULD NOT result from an unsupported locale, and the view is the side
    // that knows which bundles it has. de / es are not in ui_locales_supported and still arrive.
    const signinUrl = await startAuthorization("de es");

    const viewData = await viewDataOf(signinUrl);

    expect(viewData.ui_locales).toEqual(["de", "es"]);
  });

  it("omits ui_locales entirely when the request does not ask for it", async () => {
    const signinUrl = await startAuthorization(undefined);

    expect(signinUrl.searchParams.get("ui_locales")).toBeNull();

    const viewData = await viewDataOf(signinUrl);
    expect(viewData.ui_locales).toBeUndefined();
  });

  it("advertises ui_locales_supported in discovery", async () => {
    const response = await getConfiguration({
      endpoint: `${backendUrl}/${tenantId}/.well-known/openid-configuration`,
    });
    expect(response.status).toBe(200);
    console.log("discovery ui_locales_supported:", JSON.stringify(response.data.ui_locales_supported));

    // Registered above and read back: the configuration no longer drops the field.
    expect(response.data.ui_locales_supported).toEqual(SUPPORTED);
  });
});
