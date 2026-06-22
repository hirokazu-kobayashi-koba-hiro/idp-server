import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, postWithJson } from "../../../lib/http";
import {
  requestToken,
  getAuthorizations,
  authorize,
} from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { generateCodeVerifier, calculateCodeChallengeWithS256 } from "../../../lib/oauth";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import {
  convertNextAction,
  convertToAuthorizationResponse,
} from "../../../lib/util";

/**
 * Standard Use Case: require_pkce (Issue #1523)
 *
 * Per-client PKCE enforcement. With client.require_pkce = true:
 * - /v1/authorizations without code_challenge        -> redirect error invalid_request
 * - /v1/authorizations with code_challenge_method!=S256 -> redirect error invalid_request
 * - /v1/authorizations with code_challenge(S256) + /v1/tokens with code_verifier -> success
 * - /v1/tokens without code_verifier                 -> invalid_grant
 *
 * Backward compatibility (require_pkce defaults to false => PKCE optional) is covered by
 * standard-26-pkce.test.js.
 */
describe("Standard Use Case: require_pkce (per-client PKCE enforcement, Issue #1523)", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let mgmtAccessToken;
  const redirectUri = "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

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
    systemAccessToken = tokenResponse.data.access_token;

    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    clientSecret = `cs-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const adminEmail = `admin-${timestamp}@require-pkce.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    const resp = await onboarding({
      body: {
        organization: { id: organizationId, name: `RequirePKCE Test ${timestamp}`, description: "require_pkce test" },
        tenant: {
          id: tenantId, name: `RequirePKCE Tenant ${timestamp}`, domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: { cookie_name: `REQPKCE_${tenantId.substring(0, 8)}`, use_secure_cookie: false },
          cors_config: { allow_origins: [backendUrl] },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          grant_types_supported: ["authorization_code", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management", "org-management"],
          code_challenge_methods_supported: ["S256"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: { access_token_type: "JWT" },
        },
        user: { sub: uuidv4(), provider_id: "idp-server", name: "Admin", email: adminEmail, email_verified: true, raw_password: adminPassword },
        client: {
          client_id: clientId, client_secret: clientSecret, redirect_uris: [redirectUri],
          response_types: ["code"], grant_types: ["authorization_code", "password"],
          scope: "openid profile email management org-management",
          client_name: "Require PKCE Client", token_endpoint_auth_method: "client_secret_post", application_type: "web",
          require_pkce: true,
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    expect(resp.status).toBe(201);

    const mgmtResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password", username: adminEmail, password: adminPassword,
      scope: "management org-management", clientId, clientSecret,
    });
    mgmtAccessToken = mgmtResp.data.access_token;

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtAccessToken}` },
      body: { id: uuidv4(), type: "initial-registration", attributes: {}, metadata: {}, interactions: { "initial-registration": { request: { schema: { type: "object", required: ["email", "password", "name"], properties: { name: { type: "string" }, email: { type: "string", format: "email" }, password: { type: "string", minLength: 8 } } } } } } },
    });
  });

  afterAll(async () => {
    if (mgmtAccessToken) await deletion({ url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`, headers: { Authorization: `Bearer ${mgmtAccessToken}` } }).catch(() => {});
    if (systemAccessToken) await deletion({ url: `${backendUrl}/v1/management/orgs/${organizationId}`, headers: { Authorization: `Bearer ${systemAccessToken}` } }).catch(() => {});
  });

  /** Helper: register user + get auth code with PKCE (S256). */
  async function getAuthCode({ codeChallenge, codeChallengeMethod }) {
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId, responseType: "code", state: `reqpkce-${Date.now()}`,
      scope: "openid profile email", redirectUri,
      codeChallenge, codeChallengeMethod,
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");
    expect(authId).toBeDefined();

    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: { email: `reqpkce-${Date.now()}@test.com`, password: "ReqPkce_1!", name: "Require PKCE User" },
    });
    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId, body: {},
    });
    return convertToAuthorizationResponse(authorizeResp.data.redirect_uri).code;
  }

  it("rejects authorization request without code_challenge (invalid_request)", async () => {
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId, responseType: "code", state: `reqpkce-nochallenge-${Date.now()}`,
      scope: "openid profile email", redirectUri,
    });
    expect(authResp.status).toBe(302);
    const authorizationResponse = convertToAuthorizationResponse(authResp.headers.location);
    expect(authorizationResponse.error).toBe("invalid_request");
    expect(authorizationResponse.errorDescription).toContain("PKCE is required for this client");
  });

  it("rejects authorization request with code_challenge_method other than S256 (invalid_request)", async () => {
    const codeVerifier = generateCodeVerifier();
    const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId, responseType: "code", state: `reqpkce-plain-${Date.now()}`,
      scope: "openid profile email", redirectUri,
      codeChallenge, codeChallengeMethod: "plain",
    });
    expect(authResp.status).toBe(302);
    const authorizationResponse = convertToAuthorizationResponse(authResp.headers.location);
    expect(authorizationResponse.error).toBe("invalid_request");
  });

  it("succeeds with code_challenge(S256) + matching code_verifier", async () => {
    const codeVerifier = generateCodeVerifier();
    const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
    const code = await getAuthCode({ codeChallenge, codeChallengeMethod: "S256" });

    const tokenResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code", code, redirectUri,
      clientId, clientSecret, codeVerifier,
    });
    expect(tokenResp.status).toBe(200);
    expect(tokenResp.data.access_token).toBeDefined();
  });

  it("rejects token request without code_verifier (invalid_grant)", async () => {
    const codeVerifier = generateCodeVerifier();
    const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);
    const code = await getAuthCode({ codeChallenge, codeChallengeMethod: "S256" });

    const tokenResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code", code, redirectUri,
      clientId, clientSecret, // no codeVerifier
    });
    expect(tokenResp.status).toBe(400);
    expect(tokenResp.data.error).toBe("invalid_grant");
  });
});
