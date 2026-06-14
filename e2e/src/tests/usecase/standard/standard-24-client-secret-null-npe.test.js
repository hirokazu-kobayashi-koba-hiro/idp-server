import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";

/**
 * Standard Use Case: client_secret_post with no configured client_secret (#1480)
 *
 * token_endpoint_auth_method=client_secret_post に設定されているのに client_secret が未設定（null）の
 * クライアントへ client_secret 付きトークンリクエストを送ると、ClientConfiguration.matchClientSecret で
 * NullPointerException が発生し HTTP 500 になっていた。
 *
 * 期待: 500 ではなく invalid_client (401) を返す。
 * （クライアント認証は grant 検証より先に走るため、ダミー認可コードで十分にシークレット検証へ到達する）
 */
describe("Standard Use Case: client_secret_post without configured secret (#1480)", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
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

    const ts = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    const jwks = await generateECP256JWKS();
    const adminEmail = `npe-admin-${ts}@example.com`;

    const onboardResp = await onboarding({
      body: {
        organization: { id: organizationId, name: `Secret NPE Org ${ts}`, description: "#1480" },
        tenant: {
          id: tenantId,
          name: `Secret NPE Tenant ${ts}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: { cookie_name: `NPE_${tenantId.substring(0, 8)}`, use_secure_cookie: false },
          cors_config: { allow_origins: [backendUrl] },
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
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
        // organizer 管理者ユーザー（onboarding に必要）
        user: {
          sub: uuidv4(),
          provider_id: "idp-server",
          name: "Admin",
          email: adminEmail,
          email_verified: true,
          raw_password: `Admin_${ts}!`,
        },
        // ★ 設定矛盾クライアント: client_secret_post なのに client_secret を持たせない
        client: {
          client_id: clientId,
          redirect_uris: [redirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code"],
          scope: "openid profile email",
          client_name: "Secret NPE Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    if (onboardResp.status !== 201) {
      console.error("onboarding failed:", JSON.stringify(onboardResp.data, null, 2));
    }
    expect(onboardResp.status).toBe(201);
  });

  afterAll(async () => {
    if (organizationId && systemAccessToken) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  it("returns invalid_client (not 500) when config has no client_secret", async () => {
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code: "dummy-code-1480",
      redirectUri,
      clientId,
      clientSecret: "presented-but-config-has-none",
    });
    console.log("status:", tokenResponse.status, "body:", JSON.stringify(tokenResponse.data));
    // 修正前は matchClientSecret の NPE で 500 になっていた
    expect(tokenResponse.status).not.toBe(500);
    expect(tokenResponse.status).toBe(401);
    expect(tokenResponse.data.error).toBe("invalid_client");
  });
});
