import { describe, expect, it, beforeAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
import { deletion, postWithJson, patchWithJson } from "../../../lib/http";
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
 * CIBA Use Case: #1377 — non-active user is rejected at the device interaction (mid-flow lock).
 *
 * Unlike the request-time UserStatusVerifier (covered by the security suite), this exercises the
 * gap that AuthenticationUserStatusGuard closes: a user who is ACTIVE at the backchannel request
 * but becomes LOCKED during the pending window, then completes the device authentication. The CIBA
 * token path does not re-check status, so without the guard a token would be issued.
 *
 * The device authentication here is `password-authentication` (operationType AUTHENTICATION,
 * resolves the real user status), which is exactly where the guard fires.
 */
describe("CIBA Use Case: non-active user rejected mid-flow (#1377)", () => {
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

  async function createCibaEnv() {
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
    const userEmail = `user-${timestamp}@ciba-1377.example.com`;
    const userPassword = `MidFlow_${timestamp}!`;
    const redirectUri = "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

    const resp = await onboarding({
      body: {
        organization: { id: organizationId, name: `CIBA 1377 Test ${timestamp}`, description: "CIBA mid-flow lock test" },
        tenant: {
          id: tenantId, name: `CIBA 1377 Tenant ${timestamp}`, domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: { cookie_name: `C1377_${tenantId.substring(0, 8)}`, use_secure_cookie: false },
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
            backchannel_authentication_polling_interval: 1,
            backchannel_authentication_request_expires_in: 120,
            required_backchannel_auth_user_code: false,
          },
        },
        user: {
          sub: userId, provider_id: "idp-server", email: userEmail, email_verified: true, raw_password: userPassword,
          authentication_devices: [{ id: deviceId, app_name: "MidFlow Test App" }],
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
        scope: "openid profile email", client_name: "CIBA 1377 Client",
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
        await deletion({ url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`, headers: { Authorization: `Bearer ${systemAccessToken}` } }).catch(() => {});
        await deletion({ url: `${backendUrl}/v1/management/orgs/${organizationId}`, headers: { Authorization: `Bearer ${systemAccessToken}` } }).catch(() => {});
      },
    };
  }

  async function startCibaAndGetTransaction(env) {
    const cibaResp = await requestBackchannelAuthentications({
      endpoint: `${backendUrl}/${env.tenantId}/v1/backchannel/authentications`,
      clientId: env.cibaClientId, clientSecret: env.cibaClientSecret,
      scope: "openid profile email",
      loginHint: `device:${env.deviceId},idp:idp-server`,
    });
    expect(cibaResp.status).toBe(200);

    const txResp = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: `${backendUrl}/${env.tenantId}/v1/authentication-devices/{id}/authentications`,
      deviceId: env.deviceId,
      params: { "attributes.auth_req_id": cibaResp.data.auth_req_id },
    });
    expect(txResp.status).toBe(200);
    expect(txResp.data.list.length).toBeGreaterThan(0);

    return { authReqId: cibaResp.data.auth_req_id, tx: txResp.data.list[0] };
  }

  it("control: an ACTIVE user completes CIBA via password-authentication and a token is issued", async () => {
    const env = await createCibaEnv();
    try {
      const { authReqId, tx } = await startCibaAndGetTransaction(env);

      const interaction = await postAuthenticationDeviceInteraction({
        endpoint: `${backendUrl}/${env.tenantId}/v1/authentications/{id}/`,
        flowType: tx.flow, id: tx.id,
        interactionType: "password-authentication",
        body: { username: env.userEmail, password: env.userPassword },
      });
      expect(interaction.status).toBe(200);

      const tokenResp = await requestToken({
        endpoint: `${backendUrl}/${env.tenantId}/v1/tokens`,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId, clientId: env.cibaClientId, clientSecret: env.cibaClientSecret,
      });
      expect(tokenResp.status).toBe(200);
      expect(tokenResp.data).toHaveProperty("access_token");
    } finally {
      await env.cleanup();
    }
  });

  it("#1377: a user LOCKED after the CIBA request is rejected at password-authentication (403 access_denied) and no token is issued", async () => {
    const env = await createCibaEnv();
    try {
      // 1. Start CIBA while the user is active (passes the request-time UserStatusVerifier).
      const { authReqId, tx } = await startCibaAndGetTransaction(env);

      // 2. Lock the user mid-flow (during the pending window). Uses the org-scoped mgmtToken (the
      // onboarded org admin); the JWT stays valid after the lock, which is enough for this one call.
      const lockResp = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${env.organizationId}/tenants/${env.tenantId}/users/${env.userId}`,
        headers: { Authorization: `Bearer ${env.mgmtToken}` },
        body: { status: "LOCKED" },
      });
      expect(lockResp.status).toBe(200);

      // 3. Complete the device authentication — AuthenticationUserStatusGuard must reject it.
      const interaction = await postAuthenticationDeviceInteraction({
        endpoint: `${backendUrl}/${env.tenantId}/v1/authentications/{id}/`,
        flowType: tx.flow, id: tx.id,
        interactionType: "password-authentication",
        body: { username: env.userEmail, password: env.userPassword },
      });
      console.log("mid-flow interaction:", interaction.status, JSON.stringify(interaction.data));
      expect(interaction.status).toBe(403);
      expect(interaction.data).toHaveProperty("error", "access_denied");

      // 4. Token poll must not yield a token (the grant was never authorized).
      const tokenResp = await requestToken({
        endpoint: `${backendUrl}/${env.tenantId}/v1/tokens`,
        grantType: "urn:openid:params:grant-type:ciba",
        authReqId, clientId: env.cibaClientId, clientSecret: env.cibaClientSecret,
      });
      console.log("mid-flow token poll:", tokenResp.status, JSON.stringify(tokenResp.data));
      expect(tokenResp.status).not.toBe(200);
      expect(tokenResp.data).not.toHaveProperty("access_token");
    } finally {
      await env.cleanup();
    }
  });
});
