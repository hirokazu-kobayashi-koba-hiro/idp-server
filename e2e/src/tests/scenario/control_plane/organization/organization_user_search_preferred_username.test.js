import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { requestToken } from "../../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
} from "../../../testConfig";
import { postWithJson, get, deletion } from "../../../../lib/http";
import { v4 as uuidv4 } from "uuid";
import { generateRS256KeyPair } from "../../../../lib/jose";

/**
 * Issue #1866: preferred_username は (tenant_id, provider_id, preferred_username) の一意キーで、
 * 部分一致で探す対象ではない。認証時の引き当て（findByPreferredUsername）も、同じクラスの
 * email / phone_number も完全一致で、この検索だけが例外だった。
 *
 * 完全一致にしたことで uk_preferred_username が効く。ここでは索引の有無ではなく、
 * 検索の意味（完全一致で当たり、部分一致では当たらない）を固定する。
 */
describe("管理API ユーザー検索: preferred_username は完全一致 (#1866)", () => {
  let adminAccessToken;
  let tenantId;
  const preferredUsername = `pu-exact-${uuidv4()}@example.com`;
  let userSub;

  const search = async (query) =>
    get({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/users?${query}&limit=100`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
    });

  beforeAll(async () => {
    const adminTokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "password",
      username: serverConfig.oauth.username,
      password: serverConfig.oauth.password,
      scope: clientSecretPostClient.scope,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(adminTokenResponse.status).toBe(200);
    adminAccessToken = adminTokenResponse.data.access_token;

    tenantId = uuidv4();
    const { jwks } = await generateRS256KeyPair();
    const createTenantResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        tenant: {
          id: tenantId,
          name: "User search preferred_username Tenant",
          domain: backendUrl,
          authorization_provider: "idp-server",
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/.well-known/jwks.json`,
          jwks: jwks,
          scopes_supported: ["openid"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          id_token_signing_alg_values_supported: ["RS256"],
          claims_supported: ["sub", "preferred_username"],
        },
      },
    });
    expect(createTenantResponse.status).toBe(201);

    userSub = uuidv4();
    const createUserResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/users`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        sub: userSub,
        provider_id: "idp-server",
        name: "Preferred Username Exact Match",
        email: preferredUsername,
        preferred_username: preferredUsername,
        raw_password: "PreferredUsername123!",
      },
    });
    expect(createUserResponse.status).toBe(201);
  }, 60000);

  afterAll(async () => {
    if (!adminAccessToken || !tenantId) {
      return;
    }
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
    }).catch(() => {});
  });

  it("完全一致で当たる", async () => {
    const response = await search(
      `preferred_username=${encodeURIComponent(preferredUsername)}`
    );

    expect(response.status).toBe(200);
    expect(response.data.total_count).toBe(1);
    expect(response.data.list[0].sub).toBe(userSub);
  }, 60000);

  it("部分一致では当たらない", async () => {
    // 一意キーであり、識別子として扱う。前後どちらの断片でも成立しない。
    const middle = preferredUsername.slice(4, 14);
    const prefix = preferredUsername.slice(0, 10);

    for (const fragment of [middle, prefix]) {
      const response = await search(
        `preferred_username=${encodeURIComponent(fragment)}`
      );
      expect(response.status).toBe(200);
      expect(response.data.total_count).toBe(0);
    }
  }, 60000);
});
