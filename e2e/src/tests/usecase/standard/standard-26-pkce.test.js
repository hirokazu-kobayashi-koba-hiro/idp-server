import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
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
 * Standard Use Case: PKCE (Proof Key for Code Exchange)
 *
 * EXPERIMENTS-authorization-server.md Exp8 を E2E Jest に移植。
 *
 * カバー範囲 (GAP-ANALYSIS B-13):
 * - PKCE あり + 正しい code_verifier → トークン交換成功
 * - PKCE あり + 間違った code_verifier → トークン交換失敗
 * - PKCE なし → トークン交換成功（PKCE は任意）
 */
describe("Standard Use Case: PKCE (Proof Key for Code Exchange)", () => {
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
    const adminEmail = `admin-${timestamp}@pkce.example.com`;
    const adminPassword = `AdminPass_${timestamp}!`;

    const resp = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: {
        organization: { id: organizationId, name: `PKCE Test ${timestamp}`, description: "PKCE test" },
        tenant: {
          id: tenantId, name: `PKCE Tenant ${timestamp}`, domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: { cookie_name: `PKCE_${tenantId.substring(0, 8)}`, use_secure_cookie: false },
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
          client_name: "PKCE Client", token_endpoint_auth_method: "client_secret_post", application_type: "web",
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

  /** Helper: register user + get auth code with optional PKCE */
  async function getAuthCode({ codeChallenge, codeChallengeMethod } = {}) {
    const authResp = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId, responseType: "code", state: `pkce-${Date.now()}`,
      scope: "openid profile email", redirectUri,
      codeChallenge, codeChallengeMethod,
    });
    expect(authResp.status).toBe(302);
    const { params } = convertNextAction(authResp.headers.location);
    const authId = params.get("id");

    await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/initial-registration`,
      body: { email: `pkce-${Date.now()}@test.com`, password: "PkceTest_1!", name: "PKCE User" },
    });
    const authorizeResp = await authorize({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
      id: authId, body: {},
    });
    return convertToAuthorizationResponse(authorizeResp.data.redirect_uri).code;
  }

  it("should succeed with PKCE + correct code_verifier", async () => {
    console.log("\n=== PKCE: correct verifier ===");

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
    console.log("PKCE + correct verifier: token obtained");
  });

  it("should fail with PKCE + wrong code_verifier", async () => {
    console.log("\n=== PKCE: wrong verifier ===");

    const codeVerifier = generateCodeVerifier();
    const codeChallenge = calculateCodeChallengeWithS256(codeVerifier);

    const code = await getAuthCode({ codeChallenge, codeChallengeMethod: "S256" });

    const tokenResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code", code, redirectUri,
      clientId, clientSecret, codeVerifier: "wrong-verifier-that-does-not-match",
    });
    expect(tokenResp.status).toBe(400);
    console.log(`PKCE + wrong verifier: ${tokenResp.data.error}`);
  });

  it("should succeed without PKCE (optional)", async () => {
    console.log("\n=== No PKCE (optional) ===");

    const code = await getAuthCode(); // No code_challenge

    const tokenResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code", code, redirectUri,
      clientId, clientSecret,
    });
    expect(tokenResp.status).toBe(200);
    expect(tokenResp.data.access_token).toBeDefined();
    console.log("No PKCE: token obtained (PKCE is optional)");
  });
});
