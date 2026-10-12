import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { get, postWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
  federationServerConfig
} from "../../testConfig";
import { createFederatedUser, registerFidoUaf } from "../../../user";
import { v4 as uuidv4 } from "uuid";

/**
 * Issue #1971
 *
 * - A request that process_sequence rejects (required process not completed / status not allowed)
 *   is not recorded as an attempt, so the process runs once its prerequisite is met
 *   (allow_retry: false).
 * - A process with allow_during_statuses called without an application is rejected with 400, not
 *   500.
 * - allow_during_statuses accepts only application status values (lower case).
 * - Keys expanded by `to: "*"` are written as-is, not read as paths.
 */
describe("Identity Verification: process_sequence and mapping hardening (Issue #1971)", () => {
  const orgId = serverConfig.organizationId;
  const tenantId = serverConfig.tenantId;
  let orgAccessToken;
  const createdConfigIds = [];

  const configurationsUrl = `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`;

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
        url: `${configurationsUrl}/${configId}`,
        headers: { Authorization: `Bearer ${orgAccessToken}` }
      }).catch(() => {});
    }
  });

  const postConfiguration = (configurationData) =>
    postWithJson({
      url: configurationsUrl,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
        "Content-Type": "application/json"
      },
      body: configurationData
    });

  const registerConfiguration = async (configurationData) => {
    const response = await postConfiguration(configurationData);
    expect(response.status).toBe(201);
    createdConfigIds.push(configurationData.id);
  };

  const createTestUser = async () => {
    const { accessToken } = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
      scope:
        "openid profile phone email identity_verification_application " +
        clientSecretPostClient.identityVerificationScope
    });
    await registerFidoUaf({ accessToken: accessToken });
    return accessToken;
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

  const callApply = (accessToken, type, process, body) =>
    postWithJson({
      url: serverConfig.identityVerificationApplyEndpoint
        .replace("{type}", type)
        .replace("{process}", process),
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` },
      body
    });

  const callProcess = (accessToken, type, applicationId, process) =>
    postWithJson({
      url: serverConfig.identityVerificationProcessEndpoint
        .replace("{type}", type)
        .replace("{id}", applicationId)
        .replace("{process}", process),
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` },
      body: {}
    });

  const processSequence = { verifications: [{ type: "process_sequence" }] };

  const configuration = (type) => ({
    id: uuidv4(),
    type,
    attributes: { enabled: true },
    processes: {
      apply: {
        caller: "end_user",
        request: {
          schema: {
            type: "object",
            required: ["name"],
            properties: { name: { type: "string" } }
          }
        },
        store: {
          application_details_mapping_rules: [{ from: "$.request_body", to: "*" }]
        }
      },
      "first-step": { caller: "end_user" },
      "second-step": {
        caller: "end_user",
        pre_hook: processSequence,
        dependencies: { required_processes: ["first-step"] }
      },
      "while-applying": {
        caller: "end_user",
        pre_hook: processSequence,
        dependencies: { allow_during_statuses: ["applying"] }
      }
    }
  });

  it("runs an allow_retry: false process once its required process is completed", async () => {
    const accessToken = await createTestUser();
    const type = uuidv4();
    await registerConfiguration(configuration(type));

    const applyResponse = await callApply(accessToken, type, "apply", { name: "Test User" });
    expect(applyResponse.status).toBe(200);
    const applicationId = applyResponse.data.id;

    const tooEarly = await callProcess(accessToken, type, applicationId, "second-step");
    expect(tooEarly.status).toBe(400);
    expect(tooEarly.data.error).toBe("pre_hook_validation_failed");
    expect(tooEarly.data.error_messages[0]).toContain("requires completion of: first-step");

    const afterRejection = await getApplication(accessToken, applicationId);
    expect(afterRejection.processes["second-step"]).toBeUndefined();

    expect((await callProcess(accessToken, type, applicationId, "first-step")).status).toBe(200);
    expect((await callProcess(accessToken, type, applicationId, "second-step")).status).toBe(200);

    const retry = await callProcess(accessToken, type, applicationId, "second-step");
    expect(retry.status).toBe(400);
    expect(retry.data.error_messages[0]).toContain("does not allow retry");

    const completed = await getApplication(accessToken, applicationId);
    expect(completed.processes["second-step"]).toEqual({
      call_count: 1,
      success_count: 1,
      failure_count: 0
    });
  });

  it("runs an allow_during_statuses process once the application reaches the status", async () => {
    const accessToken = await createTestUser();
    const type = uuidv4();
    await registerConfiguration(configuration(type));

    const applyResponse = await callApply(accessToken, type, "apply", { name: "Test User" });
    expect(applyResponse.status).toBe(200);
    const applicationId = applyResponse.data.id;
    expect((await getApplication(accessToken, applicationId)).status).toBe("requested");

    const tooEarly = await callProcess(accessToken, type, applicationId, "while-applying");
    expect(tooEarly.status).toBe(400);
    expect(tooEarly.data.error_messages[0]).toContain(
      "cannot be executed during status: requested"
    );
    expect((await getApplication(accessToken, applicationId)).processes["while-applying"])
      .toBeUndefined();

    expect((await callProcess(accessToken, type, applicationId, "first-step")).status).toBe(200);
    expect((await getApplication(accessToken, applicationId)).status).toBe("applying");

    expect((await callProcess(accessToken, type, applicationId, "while-applying")).status).toBe(
      200
    );
  });

  it("rejects an allow_during_statuses process called without an application with 400", async () => {
    const accessToken = await createTestUser();
    const type = uuidv4();
    await registerConfiguration(configuration(type));

    const response = await callApply(accessToken, type, "while-applying", {});
    expect(response.status).toBe(400);
    expect(response.data.error).toBe("pre_hook_validation_failed");
    expect(response.data.error_messages[0]).toContain(
      "cannot be executed before the application exists"
    );
  });

  it("rejects allow_during_statuses that is not an application status", async () => {
    const type = uuidv4();
    const invalid = configuration(type);
    invalid.processes["while-applying"].dependencies.allow_during_statuses = ["APPLYING"];

    const response = await postConfiguration(invalid);
    expect(response.status).toBe(400);
    expect(JSON.stringify(response.data)).toContain("allow_during_statuses");
  });

  it("returns a configuration error when a to path index is over 999", async () => {
    const accessToken = await createTestUser();
    const type = uuidv4();
    const overLimit = configuration(type);
    overLimit.processes.apply.store.application_details_mapping_rules = [
      { from: "$.request_body.name", to: "items.1000" }
    ];
    await registerConfiguration(overLimit);

    const response = await callApply(accessToken, type, "apply", { name: "Test User" });
    expect(response.status).toBe(500);
    expect(response.data).toEqual({
      error: "server_error",
      error_description: "The server has a configuration error. Please contact the administrator."
    });
  });

  it("writes keys expanded by \"*\" as-is", async () => {
    const accessToken = await createTestUser();
    const type = uuidv4();
    await registerConfiguration(configuration(type));

    const applyResponse = await callApply(accessToken, type, "apply", {
      name: "Test User",
      "contact.email": "dotted@example.com",
      "0": "digit"
    });
    expect(applyResponse.status).toBe(200);

    const application = await getApplication(accessToken, applyResponse.data.id);
    expect(application.application_details["contact.email"]).toBe("dotted@example.com");
    expect(application.application_details["0"]).toBe("digit");
    expect(application.application_details.contact).toBeUndefined();
  });
});
