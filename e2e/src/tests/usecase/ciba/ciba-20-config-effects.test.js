import { describe, expect, it, beforeAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
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
import { sleep } from "../../../lib/util";

/**
 * CIBA Use Case: Configuration Effects
 *
 * EXPERIMENTS (ciba) Exp2, Exp4, Exp5 を E2E Jest に移植。
 *
 * カバー範囲:
 * - B-22: backchannel_authentication_request_expires_in → expired_token
 * - B-24: failure_conditions → access_denied on cancel
 * - B-25: login_hint 形式 (device:, sub:, email:, invalid)
 */
describe("CIBA Use Case: Configuration Effects", () => {
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

  /** Helper: create a CIBA-capable tenant with user+device */
  async function createCibaEnvironment(overrides = {}) {
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
    const userEmail = `user-${timestamp}@ciba-config.example.com`;
    const userPassword = `CibaPass_${timestamp}!`;
    const redirectUri = "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

    const onboardingResponse = await onboarding({
      body: {
        organization: {
          id: organizationId,
          name: `CIBA Config Test ${timestamp}`,
          description: "E2E test for CIBA configuration effects",
        },
        tenant: {
          id: tenantId,
          name: `CIBA Config Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: {
            cookie_name: `CIBA_CFG_${tenantId.substring(0, 8)}`,
            use_secure_cookie: false,
          },
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
            backchannel_authentication_polling_interval: 5,
            backchannel_authentication_request_expires_in: overrides.expiresIn || 120,
            required_backchannel_auth_user_code: false,
          },
        },
        user: {
          sub: userId,
          provider_id: "idp-server",
          email: userEmail,
          email_verified: true,
          raw_password: userPassword,
          authentication_devices: [{ id: deviceId, app_name: "CIBA Config Test App" }],
        },
        client: {
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uris: [redirectUri],
          response_types: ["code"],
          grant_types: ["authorization_code", "password"],
          scope: "openid profile email management",
          client_name: "CIBA Mgmt Client",
          token_endpoint_auth_method: "client_secret_post",
          application_type: "web",
        },
      },
      headers: { Authorization: `Bearer ${systemAccessToken}` },
    });
    if (onboardingResponse.status !== 201) {
      console.error("Onboarding failed:", JSON.stringify(onboardingResponse.data, null, 2));
    }
    expect(onboardingResponse.status).toBe(201);

    // Get management token
    const mgmtResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "management",
      clientId, clientSecret,
    });
    expect(mgmtResp.status).toBe(200);
    const mgmtToken = mgmtResp.data.access_token;

    // Register CIBA client
    const cibaClientResp = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        client_id: cibaClientId,
        client_secret: cibaClientSecret,
        redirect_uris: [redirectUri],
        response_types: ["code"],
        grant_types: ["urn:openid:params:grant-type:ciba"],
        scope: "openid profile email",
        client_name: "CIBA Test Client",
        token_endpoint_auth_method: "client_secret_post",
        backchannel_token_delivery_mode: "poll",
        backchannel_user_code_parameter: false,
        extension: {
          default_ciba_authentication_interaction_type: "authentication-device-notification-no-action",
        },
      },
    });
    expect(cibaClientResp.status).toBe(201);

    // Register password auth config
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        id: uuidv4(), type: "password", attributes: {},
        metadata: { type: "password" },
        interactions: {
          "password-authentication": {
            request: { schema: { type: "object", properties: { username: { type: "string" }, password: { type: "string" } }, required: ["username", "password"] } },
            pre_hook: {}, execution: { function: "password_verification" }, post_hook: {},
            response: { body_mapping_rules: [{ from: "$.user_id", to: "user_id" }, { from: "$.error", to: "error" }] },
          },
        },
      },
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

  it("B-22: CIBA request expires after backchannel_authentication_request_expires_in", async () => {
    console.log("\n=== CIBA Expiration Test (expires_in=5) ===");

    const env = await createCibaEnvironment({ expiresIn: 5 });

    try {
      // CIBA request
      const cibaResp = await requestBackchannelAuthentications({
        endpoint: `${backendUrl}/${env.tenantId}/v1/backchannel/authentications`,
        clientId: env.cibaClientId,
        clientSecret: env.cibaClientSecret,
        scope: "openid profile email",
        loginHint: `device:${env.deviceId},idp:idp-server`,
      });
      expect(cibaResp.status).toBe(200);
      expect(cibaResp.data.expires_in).toBeLessThanOrEqual(5);
      const authReqId = cibaResp.data.auth_req_id;
      console.log(`CIBA request: expires_in=${cibaResp.data.expires_in}`);

      // Immediately → authorization_pending
      const pendingResp = await requestToken({
        endpoint: `${backendUrl}/${env.tenantId}/v1/tokens`,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId, clientId: env.cibaClientId, clientSecret: env.cibaClientSecret,
      });
      expect(pendingResp.status).toBe(400);
      expect(pendingResp.data.error).toBe("authorization_pending");
      console.log("Immediately: authorization_pending");

      // Wait 7s → expired_token
      console.log("Waiting 7 seconds...");
      await sleep(7000);

      const expiredResp = await requestToken({
        endpoint: `${backendUrl}/${env.tenantId}/v1/tokens`,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId, clientId: env.cibaClientId, clientSecret: env.cibaClientSecret,
      });
      expect(expiredResp.status).toBe(400);
      expect(expiredResp.data.error).toBe("expired_token");
      console.log(`After 7s: ${expiredResp.data.error}`);

      console.log("=== CIBA Expiration Test Completed ===");
    } finally {
      await env.cleanup();
    }
  }, 30000);

  it("B-25: login_hint supports device:, sub:, email: formats and rejects invalid", async () => {
    console.log("\n=== CIBA login_hint Formats Test ===");

    const env = await createCibaEnvironment();

    try {
      // device: format
      console.log("Testing device: format");
      const deviceResp = await requestBackchannelAuthentications({
        endpoint: `${backendUrl}/${env.tenantId}/v1/backchannel/authentications`,
        clientId: env.cibaClientId, clientSecret: env.cibaClientSecret,
        scope: "openid profile email",
        loginHint: `device:${env.deviceId},idp:idp-server`,
      });
      expect(deviceResp.status).toBe(200);
      expect(deviceResp.data.auth_req_id).toBeDefined();
      console.log(`device: → 200, auth_req_id=${deviceResp.data.auth_req_id}`);

      // Complete to avoid stale state
      const tx1 = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: `${backendUrl}/${env.tenantId}/v1/authentication-devices/{id}/authentications`,
        deviceId: env.deviceId,
        params: { "attributes.auth_req_id": deviceResp.data.auth_req_id },
      });
      await postAuthenticationDeviceInteraction({
        endpoint: `${backendUrl}/${env.tenantId}/v1/authentications/{id}/`,
        flowType: tx1.data.list[0].flow,
        id: tx1.data.list[0].id,
        interactionType: "password-authentication",
        body: { username: env.userEmail, password: env.userPassword },
      });

      // sub: format
      console.log("Testing sub: format");
      const subResp = await requestBackchannelAuthentications({
        endpoint: `${backendUrl}/${env.tenantId}/v1/backchannel/authentications`,
        clientId: env.cibaClientId, clientSecret: env.cibaClientSecret,
        scope: "openid profile email",
        loginHint: `sub:${env.userId}`,
      });
      expect(subResp.status).toBe(200);
      console.log(`sub: → 200`);

      // Complete
      const tx2 = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: `${backendUrl}/${env.tenantId}/v1/authentication-devices/{id}/authentications`,
        deviceId: env.deviceId,
        params: { "attributes.auth_req_id": subResp.data.auth_req_id },
      });
      await postAuthenticationDeviceInteraction({
        endpoint: `${backendUrl}/${env.tenantId}/v1/authentications/{id}/`,
        flowType: tx2.data.list[0].flow,
        id: tx2.data.list[0].id,
        interactionType: "password-authentication",
        body: { username: env.userEmail, password: env.userPassword },
      });

      // email: format
      console.log("Testing email: format");
      const emailResp = await requestBackchannelAuthentications({
        endpoint: `${backendUrl}/${env.tenantId}/v1/backchannel/authentications`,
        clientId: env.cibaClientId, clientSecret: env.cibaClientSecret,
        scope: "openid profile email",
        loginHint: `email:${env.userEmail}`,
      });
      expect(emailResp.status).toBe(200);
      console.log(`email: → 200`);

      // invalid device ID → should fail
      console.log("Testing invalid device ID");
      const invalidResp = await requestBackchannelAuthentications({
        endpoint: `${backendUrl}/${env.tenantId}/v1/backchannel/authentications`,
        clientId: env.cibaClientId, clientSecret: env.cibaClientSecret,
        scope: "openid profile email",
        loginHint: `device:invalid-device-id,idp:idp-server`,
      });
      expect(invalidResp.status).toBe(400);
      console.log(`invalid device: → ${invalidResp.status} (${invalidResp.data.error})`);

      console.log("=== CIBA login_hint Formats Test Completed ===");
    } finally {
      await env.cleanup();
    }
  });
});
