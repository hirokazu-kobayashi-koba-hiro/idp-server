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
 * Advance Use Case: DATADOG_LOG Security Event Hook (unified schema)
 *
 * Regression test for Issue #1621.
 *
 * The DATADOG_LOG hook executor reads its configuration from the unified hook
 * schema (events.<type>.execution.http_request), the same shape used by the
 * WEBHOOK hook. Before the fix it deserialized the whole hook configuration into
 * a bespoke base/overlays structure (WebHookConfiguration) that never matched
 * this schema, so overlays was always null and the executor threw a
 * NullPointerException that was swallowed into a failure result — the hook never
 * succeeded.
 *
 * This test registers a DATADOG_LOG hook against a mock Datadog Logs Intake
 * receiver, fires a password_success event via the authorization code flow, and
 * asserts that a DATADOG_LOG hook result was recorded with status SUCCESS —
 * which only happens once the executor reads the unified schema correctly and
 * POSTs to the configured http_request.url.
 *
 * Flow:
 * 1. Register a DATADOG_LOG hook config (triggers: ["password_success"])
 *    pointing http_request.url at the mock server
 * 2. Perform authorization code flow with password authentication
 *    -> fires password_success
 * 3. Verify security-event-hooks results contain a DATADOG_LOG entry with SUCCESS
 * 4. Cleanup
 */
describe("Advance Use Case: DATADOG_LOG Security Event Hook (unified schema)", () => {
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
        type: "DATADOG_LOG",
        triggers: ["password_success"],
        events: {
          default: {
            execution: {
              function: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/datadog-log`,
                method: "POST",
                auth_type: "none",
                header_mapping_rules: [
                  { static_value: "test-dd-api-key", to: "DD-API-KEY" },
                ],
                body_mapping_rules: [
                  { static_value: "idp-server", to: "ddsource" },
                  { static_value: "idp-server", to: "service" },
                  { from: "$.type", to: "ddtags" },
                  { from: "$.id", to: "event_id" },
                ],
              },
            },
          },
        },
        enabled: true,
      },
    });
    console.log("DATADOG_LOG hook config create status:", create.status);
    expect(create.status).toBe(201);
  });

  afterAll(async () => {
    try {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hook-configurations/${hookConfigId}`,
        headers: { Authorization: `Bearer ${adminAccessToken}` },
      });
      console.log("DATADOG_LOG hook config deleted");
    } catch (e) {
      console.log("DATADOG_LOG hook config cleanup skipped:", e.message);
    }
  });

  it("should record a DATADOG_LOG hook result with SUCCESS when password_success fires", async () => {
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
    const datadogResults = hookResults.filter((r) => r.type === "DATADOG_LOG");
    console.log(
      `Found ${datadogResults.length} DATADOG_LOG hook results for password_success:`,
      JSON.stringify(datadogResults, null, 2)
    );

    // At least one DATADOG_LOG hook executed (the bug would have produced only
    // failures), and it must be SUCCESS (the bug threw an NPE before delivery).
    expect(datadogResults.length).toBeGreaterThanOrEqual(1);
    expect(datadogResults.some((r) => r.status === "SUCCESS")).toBe(true);

    console.log(
      "Verified: DATADOG_LOG hook executed end-to-end with the unified schema"
    );
  });
});
