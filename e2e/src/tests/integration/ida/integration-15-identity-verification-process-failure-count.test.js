import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { get, postWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
  federationServerConfig,
  mockApiBaseUrl
} from "../../testConfig";
import { createFederatedUser, registerFidoUaf } from "../../../user";
import { v4 as uuidv4 } from "uuid";

/**
 * Regression test for Issue #1608
 *
 * Bug: IdentityVerificationApplicationEntryService.process() early-returned on
 * applyingResult.isError() WITHOUT calling updateProcessWith / applicationCommandRepository.update,
 * so a hard error (HTTP 4xx/5xx, timeout, connection failure) during a process execution was never
 * recorded against the application. failure_count stayed 0, breaking failure_count-based
 * conditions (lock_conditions / retry limits) and leaving no audit trail of the failed attempt.
 *
 * This test:
 *   1. apply() succeeds (no_action) → an application exists.
 *   2. A second process ("verify") whose execution calls the mock eKYC endpoint with status=500
 *      hard-errors. The API returns 500 either way (the bug is about persistence, not the response).
 *   3. The application is fetched back and the failed attempt MUST be recorded:
 *      processes.verify = { call_count: 1, success_count: 0, failure_count: 1 }.
 *   4. The original "apply" process counters MUST be preserved (the failed verify attempt only
 *      touches its own process result; the empty error context makes the detail merge a no-op).
 *   5. A second hard error increments the counters again (call_count: 2, failure_count: 2).
 *
 * Before the fix, step 3 fails because processes.verify is never persisted (undefined).
 *
 * Note: Requires the mock server at http://host.docker.internal:4000 (/e2e/error-responses).
 */
describe("Identity Verification: process() records failure_count on hard error (Issue #1608)", () => {
  const orgId = serverConfig.organizationId;
  const tenantId = serverConfig.tenantId;

  let orgAccessToken;
  const createdConfigIds = [];

  beforeAll(async () => {
    const orgAuthResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
      scope: "org-management account management"
    });
    expect(orgAuthResponse.status).toBe(200);
    orgAccessToken = orgAuthResponse.data.access_token;
  });

  afterAll(async () => {
    for (const configId of createdConfigIds) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
        headers: { Authorization: `Bearer ${orgAccessToken}` }
      }).catch(() => {});
    }
  });

  const registerConfiguration = async (configurationData) => {
    const response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
      headers: {
        "Authorization": `Bearer ${orgAccessToken}`,
        "Content-Type": "application/json"
      },
      body: configurationData
    });
    expect(response.status).toBe(201);
    createdConfigIds.push(configurationData.id);
  };

  const createTestUser = async () => {
    const { user, accessToken } = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
      scope: "openid profile phone email identity_verification_application " + clientSecretPostClient.identityVerificationScope
    });
    await registerFidoUaf({ accessToken: accessToken });
    return { user, accessToken };
  };

  const getApplication = async (accessToken, applicationId) => {
    const response = await get({
      url: `${serverConfig.identityVerificationApplicationsEndpoint}?id=${applicationId}`,
      headers: { Authorization: `Bearer ${accessToken}` }
    });
    expect(response.status).toBe(200);
    const application = response.data.list.find((a) => a.id === applicationId);
    expect(application).toBeDefined();
    return application;
  };

  it("records failure_count on the application when a process execution hard-errors", async () => {
    const { accessToken } = await createTestUser();

    const configId = uuidv4();
    const type = uuidv4();

    // apply: succeeds (no_action). verify: execution calls the mock eKYC endpoint which returns the
    // HTTP status carried in the request body (status=500 / 503) → hard error.
    await registerConfiguration({
      "id": configId,
      "type": type,
      "attributes": { "enabled": true },
      "common": { "auth_type": "none" },
      "processes": {
        "apply": {
          "request": {
            "schema": {
              "type": "object",
              "required": ["name"],
              "properties": { "name": { "type": "string" } }
            }
          },
          "execution": { "type": "no_action" },
          "store": {
            "application_details_mapping_rules": [{ "from": "$.request_body", "to": "*" }]
          },
          "transition": { "applying": { "any_of": [[]] } }
        },
        "verify": {
          "request": {
            "schema": {
              "type": "object",
              "properties": { "status": { "type": "string" } }
            }
          },
          "execution": {
            "type": "http_request",
            "http_request": {
              "url": `${mockApiBaseUrl}/e2e/error-responses`,
              "method": "POST",
              "auth_type": "none",
              "body_mapping_rules": [{ "from": "$.request_body", "to": "*" }]
            }
          }
        }
      }
    });

    // Step 1: apply → an application is created
    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", type)
      .replace("{process}", "apply");
    const applyResponse = await postWithJson({
      url: applyUrl,
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` },
      body: { name: "Test User" }
    });
    expect(applyResponse.status).toBe(200);
    const applicationId = applyResponse.data.id;
    expect(applicationId).toBeDefined();

    // Baseline: apply recorded as one successful attempt.
    const appBefore = await getApplication(accessToken, applicationId);
    expect(appBefore.processes.apply).toEqual({
      call_count: 1,
      success_count: 1,
      failure_count: 0
    });

    const verifyUrl = serverConfig.identityVerificationProcessEndpoint
      .replace("{type}", type)
      .replace("{id}", applicationId)
      .replace("{process}", "verify");

    // Step 2: first hard error (500)
    const firstError = await postWithJson({
      url: verifyUrl,
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` },
      body: { status: "500" }
    });
    expect(firstError.status).toBe(500);

    // Step 3: the failed attempt MUST be recorded against the application (the bug)
    const appAfterFirst = await getApplication(accessToken, applicationId);
    expect(appAfterFirst.processes.verify).toBeDefined();
    expect(appAfterFirst.processes.verify).toEqual({
      call_count: 1,
      success_count: 0,
      failure_count: 1
    });

    // Step 4: the apply counters MUST be untouched by the failed verify attempt
    expect(appAfterFirst.processes.apply).toEqual({
      call_count: 1,
      success_count: 1,
      failure_count: 0
    });

    // Step 5: a second hard error (503) increments the counters again
    const secondError = await postWithJson({
      url: verifyUrl,
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` },
      body: { status: "503" }
    });
    expect(secondError.status).toBe(503);

    const appAfterSecond = await getApplication(accessToken, applicationId);
    expect(appAfterSecond.processes.verify).toEqual({
      call_count: 2,
      success_count: 0,
      failure_count: 2
    });
  });
});
