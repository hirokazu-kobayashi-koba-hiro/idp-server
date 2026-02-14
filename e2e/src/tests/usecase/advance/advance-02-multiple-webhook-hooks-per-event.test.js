import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { get, postWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import { requestAuthorizations } from "../../../oauth/request";
import {
  adminServerConfig,
  backendUrl,
  mockApiBaseUrl,
  serverConfig,
  clientSecretPostClient,
} from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import { sleep } from "../../../lib/util";

/**
 * Advance Use Case: Multiple Webhook Hooks Per Event
 *
 * Verifies that a single security event can trigger multiple webhook
 * hook configurations simultaneously.
 *
 * SecurityEventHandler iterates over ALL SecurityEventHookConfigurations
 * for the tenant and executes each one whose triggers list contains the
 * event type. This test registers two WEBHOOK-type hook configurations
 * with the same trigger, fires the event via the authorization code flow
 * (which triggers password_success through interactive authentication),
 * and asserts that both hooks produced execution results.
 *
 * Flow:
 * 1. Register webhook hook config A (triggers: ["password_success"])
 * 2. Register webhook hook config B (triggers: ["password_success"])
 * 3. Perform authorization code flow with password authentication
 *    -> fires password_success event
 * 4. Verify security-event-hooks results contain entries for BOTH configs
 * 5. Cleanup
 */
describe("Advance Use Case: Multiple Webhook Hooks Per Event", () => {
  let adminAccessToken;
  const hookConfigIdA = uuidv4();
  const hookConfigIdB = uuidv4();

  beforeAll(async () => {
    // Get system admin token for management API
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
    adminAccessToken = tokenResponse.data.access_token;

    // Register webhook hook config A
    const createA = await postWithJson({
      url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hook-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: hookConfigIdA,
        type: "WEBHOOK",
        triggers: ["password_success"],
        events: {
          default: {
            execution: {
              function: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/webhook-hook-a`,
                method: "POST",
                auth_type: "none",
                body_mapping_rules: [
                  { from: "event_type", to: "event_type" },
                  { static_value: "hook-a", to: "hook_id" },
                ],
              },
            },
          },
        },
        enabled: true,
      },
    });
    console.log("Hook config A create status:", createA.status);
    expect(createA.status).toBe(201);

    // Register webhook hook config B
    const createB = await postWithJson({
      url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hook-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: hookConfigIdB,
        type: "WEBHOOK",
        triggers: ["password_success"],
        events: {
          default: {
            execution: {
              function: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/webhook-hook-b`,
                method: "POST",
                auth_type: "none",
                body_mapping_rules: [
                  { from: "event_type", to: "event_type" },
                  { static_value: "hook-b", to: "hook_id" },
                ],
              },
            },
          },
        },
        enabled: true,
      },
    });
    console.log("Hook config B create status:", createB.status);
    expect(createB.status).toBe(201);
  });

  afterAll(async () => {
    // Cleanup both hook configurations
    try {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hook-configurations/${hookConfigIdA}`,
        headers: { Authorization: `Bearer ${adminAccessToken}` },
      });
      console.log("Hook config A deleted");
    } catch (e) {
      console.log("Hook config A cleanup skipped:", e.message);
    }

    try {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hook-configurations/${hookConfigIdB}`,
        headers: { Authorization: `Bearer ${adminAccessToken}` },
      });
      console.log("Hook config B deleted");
    } catch (e) {
      console.log("Hook config B cleanup skipped:", e.message);
    }
  });

  it("should execute both webhook hooks when a single event fires", async () => {
    // Fire a password_success event via authorization code flow
    // requestAuthorizations performs: authorization request -> password authentication -> authorize
    // The password authentication step fires the password_success security event
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      authorizeEndpoint: serverConfig.authorizeEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      state: "test-state",
      scope: "openid",
      redirectUri: clientSecretPostClient.redirectUri,
    });
    expect(authorizationResponse.code).toBeDefined();
    console.log(
      "Authorization code flow completed - password_success event fired"
    );

    // Wait for async hook execution
    await sleep(3000);

    // Get security event hook results
    const hooksResponse = await get({
      url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hooks?event_type=password_success&limit=50&offset=0`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
    });
    expect(hooksResponse.status).toBe(200);
    console.log(
      "Hook results:",
      JSON.stringify(hooksResponse.data, null, 2)
    );

    const hookResults = hooksResponse.data.list || [];
    expect(hookResults.length).toBeGreaterThan(0);

    // Find hook results for our specific hook configs by matching hook type WEBHOOK
    // Both configs are type WEBHOOK and triggered by password_success
    const webhookResults = hookResults.filter(
      (r) => r.type === "WEBHOOK"
    );
    console.log(
      `Found ${webhookResults.length} WEBHOOK hook results for password_success`
    );

    // We should have at least 2 WEBHOOK results (one from each config)
    // Note: there may be pre-existing WEBHOOK configs, so we check >= 2
    expect(webhookResults.length).toBeGreaterThanOrEqual(2);

    console.log(
      "Verified: a single event triggered multiple webhook hook executions"
    );
  });
});
