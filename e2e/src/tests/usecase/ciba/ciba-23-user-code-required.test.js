import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, postWithJson } from "../../../lib/http";
import {
  requestToken,
  requestBackchannelAuthentications,
} from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

/**
 * CIBA Use Case: User Code Required
 *
 * EXPERIMENTS (ciba) Exp3 を E2E Jest に移植。
 *
 * カバー範囲 (GAP-ANALYSIS B-23):
 * - required_backchannel_auth_user_code=true + user_code なし → エラー
 * - required_backchannel_auth_user_code=true + user_code あり → 成功
 */
describe("CIBA Use Case: User Code Required", () => {
  let systemAccessToken;

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

  it("should reject CIBA request without user_code when required, and accept with user_code", async () => {
    console.log("\n=== CIBA User Code Required Test ===");

    const timestamp = Date.now();
    const organizationId = uuidv4();
    const tenantId = uuidv4();
    const userId = uuidv4();
    const deviceId = uuidv4();
    const clientId = uuidv4();
    const clientSecret = `cs-${crypto.randomBytes(16).toString("hex")}`;
    const cibaClientId = uuidv4();
    const cibaClientSecret = `ciba-${crypto.randomBytes(16).toString("hex")}`;
    const jwksContent = await generateECP256JWKS();
    const userEmail = `user-${timestamp}@ciba-uc.example.com`;
    const userPassword = `UcPass_${timestamp}!`;
    const redirectUri = "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

    const resp = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: {
        organization: { id: organizationId, name: `CIBA UC Test ${timestamp}`, description: "CIBA user_code test" },
        tenant: {
          id: tenantId, name: `CIBA UC Tenant ${timestamp}`, domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: { cookie_name: `UC_${tenantId.substring(0, 8)}`, use_secure_cookie: false },
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
          grant_types_supported: ["authorization_code", "password", "urn:openid:params:grant-type:ciba"],
          token_signed_key_id: "signing_key_1",
          id_token_signed_key_id: "signing_key_1",
          scopes_supported: ["openid", "profile", "email", "management"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          id_token_signing_alg_values_supported: ["ES256"],
          backchannel_authentication_endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
          backchannel_token_delivery_modes_supported: ["poll"],
          backchannel_user_code_parameter_supported: true,
          extension: {
            access_token_type: "JWT",
            backchannel_authentication_polling_interval: 5,
            backchannel_authentication_request_expires_in: 120,
            required_backchannel_auth_user_code: true,
          },
        },
        user: {
          sub: userId, provider_id: "idp-server", email: userEmail, email_verified: true, raw_password: userPassword,
          authentication_devices: [{ id: deviceId, app_name: "UC Test App" }],
        },
        client: {
          client_id: clientId, client_secret: clientSecret, redirect_uris: [redirectUri],
          response_types: ["code"], grant_types: ["authorization_code", "password"],
          scope: "openid profile email management", client_name: "Mgmt Client",
          token_endpoint_auth_method: "client_secret_post", application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    expect(resp.status).toBe(201);

    const mgmtResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password", username: userEmail, password: userPassword,
      scope: "management", clientId, clientSecret,
    });
    const mgmtToken = mgmtResp.data.access_token;

    // CIBA client with user_code enabled
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        client_id: cibaClientId, client_secret: cibaClientSecret,
        redirect_uris: [redirectUri], response_types: ["code"],
        grant_types: ["urn:openid:params:grant-type:ciba"],
        scope: "openid profile email", client_name: "CIBA UC Client",
        token_endpoint_auth_method: "client_secret_post",
        backchannel_token_delivery_mode: "poll",
        backchannel_user_code_parameter: true,
        extension: { default_ciba_authentication_interaction_type: "authentication-device-notification-no-action" },
      },
    });

    try {
      // Without user_code → should fail
      console.log("Step 1: CIBA request WITHOUT user_code");
      const noCodeResp = await requestBackchannelAuthentications({
        endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
        clientId: cibaClientId, clientSecret: cibaClientSecret,
        scope: "openid profile email",
        loginHint: `device:${deviceId},idp:idp-server`,
        // No userCode
      });
      expect(noCodeResp.status).toBe(400);
      console.log(`Without user_code: status=${noCodeResp.status}, error=${noCodeResp.data.error}`);

      // With user_code → should succeed
      // idp-serverの user_code 実装: backchannel_auth_user_code_type="password" の場合、
      // UserCodeAsPasswordVerifier が user_code をユーザーのパスワードハッシュと照合する。
      // つまり user_code にはユーザーのパスワードを渡すのが正しい。
      console.log("\nStep 2: CIBA request WITH user_code (= user password)");
      const withCodeResp = await requestBackchannelAuthentications({
        endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
        clientId: cibaClientId, clientSecret: cibaClientSecret,
        scope: "openid profile email",
        loginHint: `device:${deviceId},idp:idp-server`,
        userCode: userPassword,
      });
      expect(withCodeResp.status).toBe(200);
      expect(withCodeResp.data.auth_req_id).toBeDefined();
      console.log(`With user_code: status=${withCodeResp.status}, auth_req_id=${withCodeResp.data.auth_req_id}`);

      console.log("\n=== CIBA User Code Required Test Completed ===");
    } finally {
      await deletion({ url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`, headers: { Authorization: `Bearer ${mgmtToken}` } }).catch(() => {});
      await deletion({ url: `${backendUrl}/v1/management/orgs/${organizationId}`, headers: { Authorization: `Bearer ${systemAccessToken}` } }).catch(() => {});
    }
  });
});
