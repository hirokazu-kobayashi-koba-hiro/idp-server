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
 * Advance Use Case: security event hook with legacy-format response_resolve_configs (#1500)
 *
 * HttpRequestExecutionConfig is embedded in many configuration types via the shared `http_request`
 * field, including security event hook configurations. Before #1500 the field was typed as the
 * HttpResponseResolveConfigs wrapper, so persisted hook configs hold response_resolve_configs in the
 * object-wrapper form ({"configs": [...]}). When the field was changed to a bare List, reading those
 * stored configs threw MismatchedInputException, surfacing as a 500 on any API whose synchronous
 * security event publish loaded the hook (e.g. user creation firing user_password_change).
 *
 * A central JsonConverter deserializer now accepts both the wrapper form and the canonical array
 * form. This test verifies the security event hook context (POST + GET round-trip) handles the
 * legacy wrapper form without error. The raw read path is covered by the platform unit test
 * HttpRequestExecutionConfigResponseResolveTest.
 */
describe("Advance Use Case: security event hook with legacy response_resolve_configs (#1500)", () => {
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
  });

  afterAll(async () => {
    try {
      await deletion({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hook-configurations/${hookConfigId}`,
        headers: { Authorization: `Bearer ${adminAccessToken}` },
      });
    } catch (e) {
      console.log("Hook config cleanup skipped:", e.message);
    }
  });

  it("accepts and round-trips response_resolve_configs in the legacy wrapper form", async () => {
    const createResponse = await postWithJson({
      url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hook-configurations`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
      body: {
        id: hookConfigId,
        type: "WEBHOOK",
        triggers: ["password_success"],
        events: {
          default: {
            execution: {
              function: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/webhook-hook-legacy`,
                method: "POST",
                auth_type: "none",
                // Legacy object-wrapper form persisted before #1500.
                response_resolve_configs: {
                  configs: [
                    {
                      conditions: [
                        { path: "$.status_code", operation: "eq", value: 200 },
                      ],
                      match_mode: "ALL",
                      mapped_status_code: 200,
                    },
                  ],
                },
                body_mapping_rules: [
                  { from: "event_type", to: "event_type" },
                ],
              },
            },
          },
        },
        enabled: true,
      },
    });

    console.log("Legacy-form hook config create status:", createResponse.status);
    // Before the fix this POST failed to deserialize the wrapper form.
    expect(createResponse.status).toBe(201);

    // GET must read the stored config back without error and expose the resolve config.
    const getResponse = await get({
      url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hook-configurations/${hookConfigId}`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
    });
    expect(getResponse.status).toBe(200);

    const httpRequest =
      getResponse.data.events.default.execution.http_request;
    expect(httpRequest).toHaveProperty("response_resolve_configs");
    // Normalizes to the canonical array form on output (matches identity-verification).
    expect(Array.isArray(httpRequest.response_resolve_configs)).toBe(true);
    expect(httpRequest.response_resolve_configs[0].mapped_status_code).toBe(200);
  });

  it("fires password_success without a 500 while the legacy-format hook is loaded", async () => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      authorizeEndpoint: serverConfig.authorizeEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      state: "test-state-1500",
      scope: "openid",
      redirectUri: clientSecretPostClient.redirectUri,
    });
    // The security event publish loads ALL hook configs for the tenant (including the legacy-format
    // one); before the fix that deserialization threw and broke the flow.
    expect(authorizationResponse.code).toBeDefined();

    await sleep(2000);

    const hooksResponse = await get({
      url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/security-event-hooks?event_type=password_success&limit=10&offset=0`,
      headers: { Authorization: `Bearer ${adminAccessToken}` },
    });
    expect(hooksResponse.status).toBe(200);
  });
});
