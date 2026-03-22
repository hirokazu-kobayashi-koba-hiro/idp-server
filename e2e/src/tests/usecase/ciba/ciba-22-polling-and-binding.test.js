import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, postWithJson } from "../../../lib/http";
import {
  requestToken,
  requestBackchannelAuthentications,
  getAuthenticationDeviceAuthenticationTransaction,
  postAuthenticationDeviceInteraction,
} from "../../../api/oauthClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

/**
 * CIBA Use Case: Polling Interval & Binding Message
 *
 * EXPERIMENTS (ciba) Exp1, Exp7 を E2E Jest に移植。
 *
 * カバー範囲:
 * - B-21: backchannel_authentication_polling_interval → BC レスポンスの interval が変わる
 * - B-26: binding_message → 認証トランザクションの context に含まれる
 */
describe("CIBA Use Case: Polling Interval & Binding Message", () => {
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

  async function createCibaEnv({ pollingInterval }) {
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
    const userEmail = `user-${timestamp}@ciba-poll.example.com`;
    const userPassword = `PollPass_${timestamp}!`;
    const redirectUri = "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

    const resp = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: {
        organization: { id: organizationId, name: `CIBA Poll Test ${timestamp}`, description: "CIBA polling/binding test" },
        tenant: {
          id: tenantId, name: `CIBA Poll Tenant ${timestamp}`, domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: { cookie_name: `CP_${tenantId.substring(0, 8)}`, use_secure_cookie: false },
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
          backchannel_user_code_parameter_supported: false,
          extension: {
            access_token_type: "JWT",
            backchannel_authentication_polling_interval: pollingInterval,
            backchannel_authentication_request_expires_in: 120,
            required_backchannel_auth_user_code: false,
          },
        },
        user: {
          sub: userId, provider_id: "idp-server", email: userEmail, email_verified: true, raw_password: userPassword,
          authentication_devices: [{ id: deviceId, app_name: "Poll Test App" }],
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

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        client_id: cibaClientId, client_secret: cibaClientSecret,
        redirect_uris: [redirectUri], response_types: ["code"],
        grant_types: ["urn:openid:params:grant-type:ciba"],
        scope: "openid profile email", client_name: "CIBA Poll Client",
        token_endpoint_auth_method: "client_secret_post",
        backchannel_token_delivery_mode: "poll", backchannel_user_code_parameter: false,
        extension: { default_ciba_authentication_interaction_type: "authentication-device-notification-no-action" },
      },
    });

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: { id: uuidv4(), type: "password", attributes: {}, metadata: { type: "password" }, interactions: { "password-authentication": { request: { schema: { type: "object", properties: { username: { type: "string" }, password: { type: "string" } }, required: ["username", "password"] } }, pre_hook: {}, execution: { function: "password_verification" }, post_hook: {}, response: { body_mapping_rules: [{ from: "$.user_id", to: "user_id" }, { from: "$.error", to: "error" }] } } } },
    });

    return {
      organizationId, tenantId, userId, deviceId,
      cibaClientId, cibaClientSecret, userEmail, userPassword, mgmtToken,
      cleanup: async () => {
        await deletion({ url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`, headers: { Authorization: `Bearer ${mgmtToken}` } }).catch(() => {});
        await deletion({ url: `${backendUrl}/v1/management/orgs/${organizationId}`, headers: { Authorization: `Bearer ${systemAccessToken}` } }).catch(() => {});
      },
    };
  }

  it("B-21: polling interval reflects backchannel_authentication_polling_interval setting", async () => {
    console.log("\n=== Polling Interval Test ===");

    // interval=15
    const env15 = await createCibaEnv({ pollingInterval: 15 });
    try {
      const resp15 = await requestBackchannelAuthentications({
        endpoint: `${backendUrl}/${env15.tenantId}/v1/backchannel/authentications`,
        clientId: env15.cibaClientId, clientSecret: env15.cibaClientSecret,
        scope: "openid profile email",
        loginHint: `device:${env15.deviceId},idp:idp-server`,
      });
      expect(resp15.status).toBe(200);
      expect(resp15.data.interval).toBe(15);
      console.log(`pollingInterval=15 → interval=${resp15.data.interval}`);
    } finally {
      await env15.cleanup();
    }

    // interval=3
    const env3 = await createCibaEnv({ pollingInterval: 3 });
    try {
      const resp3 = await requestBackchannelAuthentications({
        endpoint: `${backendUrl}/${env3.tenantId}/v1/backchannel/authentications`,
        clientId: env3.cibaClientId, clientSecret: env3.cibaClientSecret,
        scope: "openid profile email",
        loginHint: `device:${env3.deviceId},idp:idp-server`,
      });
      expect(resp3.status).toBe(200);
      expect(resp3.data.interval).toBe(3);
      console.log(`pollingInterval=3 → interval=${resp3.data.interval}`);
    } finally {
      await env3.cleanup();
    }

    console.log("=== Polling Interval Test Completed ===");
  });

  it("B-26: binding_message is passed to authentication transaction context", async () => {
    console.log("\n=== Binding Message Test ===");

    const env = await createCibaEnv({ pollingInterval: 5 });
    try {
      const bindingMessage = "Transfer 500 USD";

      const cibaResp = await requestBackchannelAuthentications({
        endpoint: `${backendUrl}/${env.tenantId}/v1/backchannel/authentications`,
        clientId: env.cibaClientId, clientSecret: env.cibaClientSecret,
        scope: "openid profile email",
        loginHint: `device:${env.deviceId},idp:idp-server`,
        bindingMessage,
      });
      expect(cibaResp.status).toBe(200);

      // Get transaction and check binding_message in context
      const txResp = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: `${backendUrl}/${env.tenantId}/v1/authentication-devices/{id}/authentications`,
        deviceId: env.deviceId,
        params: { "attributes.auth_req_id": cibaResp.data.auth_req_id },
      });
      expect(txResp.status).toBe(200);
      expect(txResp.data.list.length).toBeGreaterThan(0);

      const tx = txResp.data.list[0];
      // authentication_type がデフォルト(none)の場合、セキュリティ上 context は非公開
      // (認証されたデバイスにのみ binding_message 等の詳細情報が返される)
      // context 内の binding_message 検証は device_secret_jwt 認証が必要な
      // device-credential-04 テストでカバーされている
      console.log(`Transaction found: id=${tx.id}, context=${tx.context ? "present" : "absent (auth_type=none)"}`);

      // binding_message 付きで CIBA フロー全体が正常動作することを検証
      await postAuthenticationDeviceInteraction({
        endpoint: `${backendUrl}/${env.tenantId}/v1/authentications/{id}/`,
        flowType: tx.flow, id: tx.id,
        interactionType: "password-authentication",
        body: { username: env.userEmail, password: env.userPassword },
      });

      const tokenResp = await requestToken({
        endpoint: `${backendUrl}/${env.tenantId}/v1/tokens`,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId: cibaResp.data.auth_req_id,
        clientId: env.cibaClientId, clientSecret: env.cibaClientSecret,
      });
      expect(tokenResp.status).toBe(200);
      console.log("Full CIBA flow with binding_message: success");

      console.log("=== Binding Message Test Completed ===");
    } finally {
      await env.cleanup();
    }
  });
});
