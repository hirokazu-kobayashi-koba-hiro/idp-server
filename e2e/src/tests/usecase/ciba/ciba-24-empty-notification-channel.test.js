import { describe, expect, it, beforeAll } from "@jest/globals";
import { onboarding } from "../../../api/managementClient";
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
 * CIBA Use Case: Empty notification_channel fallback
 *
 * When authentication_device.notification_channel is empty string,
 * optNotificationChannel() should fall back to the default channel ("fcm")
 * instead of throwing UnSupportedException.
 *
 * Regression test for: https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/1411
 */
describe("CIBA Use Case: Empty notification_channel fallback", () => {
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

  it("should not fail with UnSupportedException when notification_channel is empty string", async () => {
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
    const userEmail = `user-${timestamp}@ciba-empty-nc.example.com`;
    const userPassword = `EmptyNC_${timestamp}!`;
    const redirectUri = "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

    console.log("\n=== Step 1: Create environment with empty notification_channel ===");

    const onboardingResponse = await onboarding({
      body: {
        organization: { id: organizationId, name: `Empty NC Test ${timestamp}`, description: "Empty notification_channel test" },
        tenant: {
          id: tenantId, name: `Empty NC Tenant ${timestamp}`, domain: backendUrl,
          authorization_provider: "idp-server",
          identity_policy_config: { identity_unique_key_type: "EMAIL" },
          session_config: { cookie_name: `ENC_${tenantId.substring(0, 8)}`, use_secure_cookie: false },
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
            backchannel_authentication_request_expires_in: 120,
            required_backchannel_auth_user_code: false,
          },
        },
        user: {
          sub: userId, provider_id: "idp-server", email: userEmail, email_verified: true, raw_password: userPassword,
          authentication_devices: [{
            id: deviceId,
            app_name: "Empty NC Test App",
            notification_channel: "",
            notification_token: "dummy-token",
          }],
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
    expect(onboardingResponse.status).toBe(201);

    console.log("\n=== Step 2: Get management token and create CIBA client ===");

    const mgmtResp = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password", username: userEmail, password: userPassword,
      scope: "management", clientId, clientSecret,
    });
    expect(mgmtResp.status).toBe(200);
    const mgmtToken = mgmtResp.data.access_token;

    const clientCreationResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/clients`,
      headers: { Authorization: `Bearer ${mgmtToken}` },
      body: {
        client_id: cibaClientId, client_secret: cibaClientSecret,
        redirect_uris: [redirectUri], response_types: ["code"],
        grant_types: ["urn:openid:params:grant-type:ciba"],
        scope: "openid profile email", client_name: "CIBA Empty NC Client",
        token_endpoint_auth_method: "client_secret_post",
        backchannel_token_delivery_mode: "poll", backchannel_user_code_parameter: false,
        extension: { default_ciba_authentication_interaction_type: "authentication-device-notification-no-action" },
      },
    });
    expect(clientCreationResponse.status).toBe(201);

    console.log("\n=== Step 3: CIBA backchannel authentication request ===");

    const cibaResponse = await requestBackchannelAuthentications({
      endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
      clientId: cibaClientId,
      clientSecret: cibaClientSecret,
      scope: "openid profile email",
      loginHint: `device:${deviceId},idp:idp-server`,
    });

    console.log("CIBA response status:", cibaResponse.status);
    console.log("CIBA response:", JSON.stringify(cibaResponse.data, null, 2));

    // Before fix: 500 (UnSupportedException: "Authentication device notifier  not supported")
    // After fix: 200 (notification_channel falls back to "fcm")
    expect(cibaResponse.status).toBe(200);
    expect(cibaResponse.data).toHaveProperty("auth_req_id");
    expect(cibaResponse.data).toHaveProperty("expires_in");

    console.log("\n=== Step 4: Clean up ===");

    await deletion({ url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}`, headers: { Authorization: `Bearer ${mgmtToken}` } }).catch(() => {});
    await deletion({ url: `${backendUrl}/v1/management/orgs/${organizationId}`, headers: { Authorization: `Bearer ${systemAccessToken}` } }).catch(() => {});

    console.log("=== Test Completed ===");
  });
});
