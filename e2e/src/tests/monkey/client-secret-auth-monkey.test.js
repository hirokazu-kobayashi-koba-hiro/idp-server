import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { onboarding } from "../../api/managementClient";
import { deletion } from "../../lib/http";
import { requestToken } from "../../api/oauthClient";
import { generateECP256JWKS, createJwt } from "../../lib/jose";
import { adminServerConfig, backendUrl } from "../testConfig";
import { createBasicAuthHeader } from "../../lib/util";
import { v4 as uuidv4 } from "uuid";

/**
 * Monkey tests for client authentication with a misconfigured (secret-less) client.
 *
 * Purpose:
 * - Verify that a client whose token_endpoint_auth_method requires a client_secret but has no
 *   configured client_secret returns invalid_client (401), not a 500.
 *
 * Background:
 * - #1480 (client_secret_post / client_secret_basic): ClientConfiguration.matchClientSecret threw a
 *   NullPointerException on a null secret -> HTTP 500.
 * - #1598 (client_secret_jwt): a null secret reached the HMAC verifier -> NullPointerException -> 500.
 *
 * クライアント認証は grant 検証より先に走るため、ダミー認可コードで十分にシークレット検証へ到達する。
 */
describe("Monkey test: client authentication without configured client_secret (#1480, #1598)", () => {
  let systemAccessToken;
  const createdOrgIds = [];
  const redirectUri = "http://localhost:3000/callback";

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
  });

  afterAll(async () => {
    for (const orgId of createdOrgIds) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${orgId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  /**
   * token_endpoint_auth_method=authMethod に設定しつつ client_secret を持たせない（設定矛盾）
   * クライアントを onboarding し、{ tenantId, clientId } を返す。
   */
  async function onboardSecretlessClient(authMethod) {
    const ts = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const clientId = uuidv4();
    const jwks = await generateECP256JWKS();
    const adminEmail = `secretless-admin-${ts}@example.com`;

    const onboardResp = await onboarding({
      body: {
        organization: {
          id: organizationId,
          name: `Secretless Client Org ${ts}`,
          description: "#1480 / #1598",
        },
        tenant: {
          id: tenantId,
          name: `Secretless Client Tenant ${ts}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: {
            cookie_name: `SL_${tenantId.substring(0, 8)}`,
            use_secure_cookie: false,
          },
          cors_config: { allow_origins: [backendUrl] },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: [
            "client_secret_post",
            "client_secret_basic",
            "client_secret_jwt",
          ],
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks,
          grant_types_supported: ["authorization_code", "password"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management"],
          claims_supported: ["sub", "iss"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          extension: { access_token_type: "JWT" },
        },
        user: {
          sub: uuidv4(),
          provider_id: "idp-server",
          name: "Admin",
          email: adminEmail,
          email_verified: true,
          raw_password: `Admin_${ts}!`,
        },
        // ★ 設定矛盾クライアント: secret を要求する認証方式なのに client_secret を持たせない
        client: {
          client_id: clientId,
          redirect_uris: [redirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code"],
          scope: "openid profile email",
          client_name: `Secretless ${authMethod} Client`,
          token_endpoint_auth_method: authMethod,
          application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    if (onboardResp.status !== 201) {
      console.error("onboarding failed:", JSON.stringify(onboardResp.data, null, 2));
    }
    expect(onboardResp.status).toBe(201);
    createdOrgIds.push(organizationId);
    return { tenantId, clientId };
  }

  it("client_secret_post: returns invalid_client (not 500) when config has no client_secret (#1480)", async () => {
    const { tenantId, clientId } = await onboardSecretlessClient("client_secret_post");

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: "dummy-code-1480",
      redirectUri,
      clientId,
      clientSecret: "presented-but-config-has-none",
    });
    console.log("[post] status:", tokenResponse.status, "body:", JSON.stringify(tokenResponse.data));
    // 修正前は matchClientSecret の NPE で 500 になっていた
    expect(tokenResponse.status).not.toBe(500);
    expect(tokenResponse.status).toBe(401);
    expect(tokenResponse.data.error).toBe("invalid_client");
  });

  it("client_secret_basic: returns invalid_client (not 500) when config has no client_secret (#1480)", async () => {
    const { tenantId, clientId } = await onboardSecretlessClient("client_secret_basic");

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: "dummy-code-1480",
      redirectUri,
      basicAuth: createBasicAuthHeader({
        username: clientId,
        password: "presented-but-config-has-none",
      }),
    });
    console.log("[basic] status:", tokenResponse.status, "body:", JSON.stringify(tokenResponse.data));
    // client_secret_post と同じ matchClientSecret 経路。null secret で 500 にならないこと
    expect(tokenResponse.status).not.toBe(500);
    expect(tokenResponse.status).toBe(401);
    expect(tokenResponse.data.error).toBe("invalid_client");
  });

  it("client_secret_jwt: returns invalid_client (not 500) when config has no client_secret (#1598)", async () => {
    const { tenantId, clientId } = await onboardSecretlessClient("client_secret_jwt");

    // クライアントが（誤った）任意のシークレットで署名した、形式上は正当な HS256 client_assertion。
    // 修正前はサーバー設定の client_secret が null のまま HMAC 検証鍵に渡り NPE -> 500 になっていた。
    const now = Math.floor(Date.now() / 1000);
    const clientAssertion = createJwt({
      payload: {
        iss: clientId,
        sub: clientId,
        aud: `${backendUrl}/${tenantId}`,
        jti: uuidv4(),
        exp: now + 3600,
        iat: now,
      },
      secret: "client-guessed-secret-but-server-config-has-none",
    });

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: "dummy-code-1598",
      redirectUri,
      clientId,
      clientAssertion,
      clientAssertionType: "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
    });
    console.log("[jwt] status:", tokenResponse.status, "body:", JSON.stringify(tokenResponse.data));
    // 修正前は HMAC 検証で null secret により NPE -> 500 になっていた
    expect(tokenResponse.status).not.toBe(500);
    expect(tokenResponse.status).toBe(401);
    expect(tokenResponse.data.error).toBe("invalid_client");
  });
});
