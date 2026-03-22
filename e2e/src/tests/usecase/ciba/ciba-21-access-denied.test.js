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
 * CIBA Use Case: Access Denied via failure_conditions
 *
 * EXPERIMENTS (ciba) Exp4 を E2E Jest に移植。
 *
 * カバー範囲 (GAP-ANALYSIS B-24):
 * - failure_conditions 未定義 → cancel しても authorization_pending のまま
 * - failure_conditions 定義あり → cancel 後に access_denied
 */
describe("CIBA Use Case: Access Denied via failure_conditions", () => {
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

  /** Helper: create CIBA environment with optional failure_conditions in CIBA policy */
  async function createCibaEnv({ withFailureConditions }) {
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
    const userEmail = `user-${timestamp}@ciba-deny.example.com`;
    const userPassword = `DenyPass_${timestamp}!`;
    const redirectUri = "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

    const onboardingResp = await postWithJson({
      url: `${backendUrl}/v1/management/onboarding`,
      body: {
        organization: { id: organizationId, name: `CIBA Deny Test ${timestamp}`, description: "CIBA access_denied test" },
        tenant: {
          id: tenantId, name: `CIBA Deny Tenant ${timestamp}`, domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: { cookie_name: `DENY_${tenantId.substring(0, 8)}`, use_secure_cookie: false },
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
          extension: { access_token_type: "JWT", backchannel_authentication_polling_interval: 5, backchannel_authentication_request_expires_in: 120, required_backchannel_auth_user_code: false },
        },
        user: {
          sub: userId, provider_id: "idp-server", email: userEmail, email_verified: true, raw_password: userPassword,
          authentication_devices: [{ id: deviceId, app_name: "CIBA Deny App" }],
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
    expect(onboardingResp.status).toBe(201);

    const mgmtResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password", username: userEmail, password: userPassword,
      scope: "management", clientId, clientSecret,
    });
    const mgmtToken = mgmtResp.data.access_token;

    // CIBA client
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        client_id: cibaClientId, client_secret: cibaClientSecret,
        redirect_uris: [redirectUri], response_types: ["code"],
        grant_types: ["urn:openid:params:grant-type:ciba"],
        scope: "openid profile email", client_name: "CIBA Deny Client",
        token_endpoint_auth_method: "client_secret_post",
        backchannel_token_delivery_mode: "poll", backchannel_user_code_parameter: false,
        extension: { default_ciba_authentication_interaction_type: "authentication-device-notification-no-action" },
      },
    });

    // Password auth config
    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-configurations`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        id: uuidv4(), type: "password", attributes: {}, metadata: { type: "password" },
        interactions: { "password-authentication": { request: { schema: { type: "object", properties: { username: { type: "string" }, password: { type: "string" } }, required: ["username", "password"] } }, pre_hook: {}, execution: { function: "password_verification" }, post_hook: {}, response: { body_mapping_rules: [{ from: "$.user_id", to: "user_id" }, { from: "$.error", to: "error" }] } } },
      },
    });

    // CIBA authentication policy
    const cibaPolicy = {
      id: uuidv4(), flow: "ciba", enabled: true,
      policies: [{
        description: "ciba_password_auth",
        priority: 10,
        conditions: { scopes: ["openid"] },
        available_methods: ["password"],
        success_conditions: {
          any_of: [[{ path: "$.password-authentication.success_count", type: "integer", operation: "gte", value: 1 }]],
        },
      }],
    };

    // Add failure_conditions if requested
    if (withFailureConditions) {
      cibaPolicy.policies[0].failure_conditions = {
        any_of: [[{ path: "$.authentication-cancel.success_count", type: "integer", operation: "gte", value: 1 }]],
      };
    }

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/authentication-policies`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: cibaPolicy,
    });

    return {
      organizationId, tenantId, deviceId, cibaClientId, cibaClientSecret, userEmail, userPassword, mgmtToken,
      cleanup: async () => {
        await deletion({ url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`, headers: { Authorization: `Bearer ${mgmtToken}` } }).catch(() => {});
        await deletion({ url: `${backendUrl}/v1/management/orgs/${organizationId}`, headers: { Authorization: `Bearer ${systemAccessToken}` } }).catch(() => {});
      },
    };
  }

  it("should remain authorization_pending after cancel when failure_conditions is NOT defined", async () => {
    console.log("\n=== Cancel WITHOUT failure_conditions ===");

    const env = await createCibaEnv({ withFailureConditions: false });
    try {
      const cibaResp = await requestBackchannelAuthentications({
        endpoint: `${backendUrl}/${env.tenantId}/v1/backchannel/authentications`,
        clientId: env.cibaClientId, clientSecret: env.cibaClientSecret,
        scope: "openid profile email",
        loginHint: `device:${env.deviceId},idp:idp-server`,
      });
      expect(cibaResp.status).toBe(200);
      const authReqId = cibaResp.data.auth_req_id;

      // Get transaction
      const txResp = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: `${backendUrl}/${env.tenantId}/v1/authentication-devices/{id}/authentications`,
        deviceId: env.deviceId,
        params: { "attributes.auth_req_id": authReqId },
      });
      expect(txResp.data.list.length).toBeGreaterThan(0);
      const tx = txResp.data.list[0];

      // Cancel
      const cancelResp = await postAuthenticationDeviceInteraction({
        endpoint: `${backendUrl}/${env.tenantId}/v1/authentications/{id}/`,
        flowType: tx.flow, id: tx.id,
        interactionType: "authentication-cancel",
        body: {},
      });
      expect(cancelResp.status).toBe(200);
      console.log("Cancel sent");

      // Poll → should still be authorization_pending (failure_conditions not defined)
      const pollResp = await requestToken({
        endpoint: `${backendUrl}/${env.tenantId}/v1/tokens`,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId, clientId: env.cibaClientId, clientSecret: env.cibaClientSecret,
      });
      expect(pollResp.status).toBe(400);
      expect(pollResp.data.error).toBe("authorization_pending");
      console.log(`After cancel (no failure_conditions): ${pollResp.data.error}`);
    } finally {
      await env.cleanup();
    }
  });

  it("should return access_denied after cancel when failure_conditions IS defined", async () => {
    console.log("\n=== Cancel WITH failure_conditions ===");

    const env = await createCibaEnv({ withFailureConditions: true });
    try {
      const cibaResp = await requestBackchannelAuthentications({
        endpoint: `${backendUrl}/${env.tenantId}/v1/backchannel/authentications`,
        clientId: env.cibaClientId, clientSecret: env.cibaClientSecret,
        scope: "openid profile email",
        loginHint: `device:${env.deviceId},idp:idp-server`,
      });
      expect(cibaResp.status).toBe(200);
      const authReqId = cibaResp.data.auth_req_id;

      // Get transaction
      const txResp = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: `${backendUrl}/${env.tenantId}/v1/authentication-devices/{id}/authentications`,
        deviceId: env.deviceId,
        params: { "attributes.auth_req_id": authReqId },
      });
      const tx = txResp.data.list[0];

      // Cancel
      const cancelResp = await postAuthenticationDeviceInteraction({
        endpoint: `${backendUrl}/${env.tenantId}/v1/authentications/{id}/`,
        flowType: tx.flow, id: tx.id,
        interactionType: "authentication-cancel",
        body: {},
      });
      expect(cancelResp.status).toBe(200);
      console.log("Cancel sent");

      // Poll → should be access_denied
      const pollResp = await requestToken({
        endpoint: `${backendUrl}/${env.tenantId}/v1/tokens`,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId, clientId: env.cibaClientId, clientSecret: env.cibaClientSecret,
      });
      expect(pollResp.status).toBe(400);
      expect(pollResp.data.error).toBe("access_denied");
      console.log(`After cancel (with failure_conditions): ${pollResp.data.error}`);
    } finally {
      await env.cleanup();
    }
  });
});
