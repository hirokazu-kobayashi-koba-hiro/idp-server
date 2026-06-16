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
 * Advance Use Case: SLACK Security Event Hook (unified schema)
 *
 * Regression test for Issue #1447.
 *
 * The SLACK hook executor reads its configuration from the unified hook schema
 * (events.<type>.execution.details.{incoming_webhook_url, message_template}),
 * the same shape used by EMAIL/WEBHOOK hooks and by the example configs. Before
 * the fix it deserialized the events map into a bespoke base/overlays structure
 * that never matched this schema, so the executor threw a NullPointerException
 * and no SLACK hook result was ever recorded (and in the synchronous path the
 * whole authentication request failed with 500).
 *
 * This test registers a SLACK hook against a mock receiver, fires a
 * password_success event via the authorization code flow, and asserts that a
 * SLACK hook result was recorded with status SUCCESS — which only happens once
 * the executor reads the unified schema correctly and POSTs to the configured
 * incoming_webhook_url.
 *
 * Flow:
 * 1. Register a SLACK hook config (triggers: ["password_success"]) pointing at
 *    the mock server as the incoming_webhook_url
 * 2. Perform authorization code flow with password authentication
 *    -> fires password_success
 * 3. Verify security-event-hooks results contain a SLACK entry with SUCCESS
 * 4. Cleanup
 */
describe("Advance Use Case: SLACK Security Event Hook (unified schema)", () => {
  let adminAccessToken;
  const hookConfigId = uuidv4();

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
    adminAccessToken = tokenResponse.data.access_token;

    const create = await postWithJson({
      url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hook-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: hookConfigId,
        type: "SLACK",
        triggers: ["password_success"],
        events: {
          default: {
            execution: {
              function: "slack",
              details: {
                description: "slack notification (e2e #1447)",
                incoming_webhook_url: `${mockApiBaseUrl}/slack-hook`,
                message_template:
                  "🔐 type: ${trigger} / user: ${user.id} / tenant: ${tenant.id}",
              },
            },
          },
        },
        enabled: true,
      },
    });
    console.log("SLACK hook config create status:", create.status);
    expect(create.status).toBe(201);
  });

  afterAll(async () => {
    try {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hook-configurations/${hookConfigId}`,
        headers: { Authorization: `Bearer ${adminAccessToken}` },
      });
      console.log("SLACK hook config deleted");
    } catch (e) {
      console.log("SLACK hook config cleanup skipped:", e.message);
    }
  });

  it("should record a SLACK hook result with SUCCESS when password_success fires", async () => {
    // Fire a password_success event via authorization code flow.
    // requestAuthorizations performs: authorization request -> password authentication -> authorize.
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
    console.log("Authorization code flow completed - password_success fired");

    // Wait for async hook execution.
    await sleep(3000);

    const hooksResponse = await get({
      url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hooks?event_type=password_success&limit=50&offset=0`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
    });
    expect(hooksResponse.status).toBe(200);

    const hookResults = hooksResponse.data.list || [];
    const slackResults = hookResults.filter((r) => r.type === "SLACK");
    console.log(
      `Found ${slackResults.length} SLACK hook results for password_success:`,
      JSON.stringify(slackResults, null, 2)
    );

    // At least one SLACK hook executed (the bug would have produced none),
    // and it must be SUCCESS (the bug would have thrown before delivery).
    expect(slackResults.length).toBeGreaterThanOrEqual(1);
    expect(slackResults.some((r) => r.status === "SUCCESS")).toBe(true);

    console.log(
      "Verified: SLACK hook executed end-to-end with the unified schema"
    );
  });
});
