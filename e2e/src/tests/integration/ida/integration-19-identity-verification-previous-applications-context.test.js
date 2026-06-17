import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { postWithJson, deletion, get } from "../../../lib/http";
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
 * #1592: the past-application read model fetched via `history.filters` must be exposed in the
 * IdentityVerificationContext as `$.previous_applications`, so execution / store / response mapping
 * rules can reference it.
 *
 * Two capabilities are verified independently:
 *   1. a SCALAR list  — $.previous_applications[*].application_details.external_application_id
 *   2. an OBJECT list — $.previous_applications[*].application_details (each element is an object)
 *
 * Each test uses its own configuration type so the per-user/type history is independent. The flow
 * is: first apply stores the external id (status becomes REQUESTED = "running"); second apply finds
 * the first application in the history and projects it. Asserting on both the response and the
 * stored application_details (via the applications API) proves the same context the execution
 * http_request body would draw from resolves correctly.
 */
describe("Identity Verification: previous_applications exposed to mapping context (#1592)", () => {
  const orgId = serverConfig.organizationId;
  const tenantId = serverConfig.tenantId;

  let orgAccessToken;
  let userAccessToken;
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

    const userResult = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
      scope:
        "openid profile phone email identity_verification_application " +
        clientSecretPostClient.identityVerificationScope
    });
    userAccessToken = userResult.accessToken;
    await registerFidoUaf({ accessToken: userAccessToken });
  });

  afterAll(async () => {
    for (const id of createdConfigIds) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${id}`,
        headers: { Authorization: `Bearer ${orgAccessToken}` }
      });
    }
  });

  // Registers a config whose `apply` process projects past applications into application_details and
  // the response using the given mapping rule (reused for store + response).
  const registerConfig = async ({ configId, type, projectionRule }) => {
    createdConfigIds.push(configId);
    const res = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
      headers: { Authorization: `Bearer ${orgAccessToken}`, "Content-Type": "application/json" },
      body: {
        id: configId,
        type,
        attributes: { enabled: true },
        common: { auth_type: "none" },
        processes: {
          apply: {
            request: {
              schema: {
                type: "object",
                properties: { external_application_id: { type: "string" } },
                required: ["external_application_id"]
              }
            },
            history: { filters: [{ types: [type], statuses: "running" }] },
            execution: { type: "no_action" },
            store: {
              application_details_mapping_rules: [
                { from: "$.request_body.external_application_id", to: "external_application_id" },
                projectionRule
              ]
            },
            response: { body_mapping_rules: [projectionRule] }
          }
        }
      }
    });
    expect(res.status).toBe(201);
  };

  const apply = async ({ type, externalApplicationId }) => {
    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", type)
      .replace("{process}", "apply");
    return postWithJson({
      url: applyUrl,
      body: { external_application_id: externalApplicationId },
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${userAccessToken}` }
    });
  };

  const getApplication = async ({ type, applicationId }) => {
    return get({
      url:
        serverConfig.identityVerificationApplicationsEndpoint +
        `?id=${applicationId}&type=${type}`,
      headers: { Authorization: `Bearer ${userAccessToken}` }
    });
  };

  it("projects past applications as a SCALAR list ($.previous_applications[*]...external_application_id)", async () => {
    const type = uuidv4();
    await registerConfig({
      configId: uuidv4(),
      type,
      projectionRule: {
        from: "$.previous_applications[*].application_details.external_application_id",
        to: "seen_past_external_application_ids"
      }
    });

    const first = await apply({ type, externalApplicationId: "EXT-AAA" });
    expect(first.status).toBe(200);
    expect(first.data.seen_past_external_application_ids ?? []).toEqual([]);

    const second = await apply({ type, externalApplicationId: "EXT-BBB" });
    console.log("scalar second apply:", second.status, JSON.stringify(second.data));
    expect(second.status).toBe(200);
    expect(second.data.seen_past_external_application_ids).toEqual(["EXT-AAA"]);

    const getRes = await getApplication({ type, applicationId: second.data.id });
    expect(getRes.status).toBe(200);
    expect(getRes.data.list[0].application_details.seen_past_external_application_ids).toEqual([
      "EXT-AAA"
    ]);
  });

  it("projects past applications as an OBJECT list ($.previous_applications[*].application_details)", async () => {
    const type = uuidv4();
    await registerConfig({
      configId: uuidv4(),
      type,
      projectionRule: {
        from: "$.previous_applications[*].application_details",
        to: "seen_past_details"
      }
    });

    const first = await apply({ type, externalApplicationId: "EXT-AAA" });
    expect(first.status).toBe(200);
    expect(first.data.seen_past_details ?? []).toEqual([]);

    const second = await apply({ type, externalApplicationId: "EXT-BBB" });
    console.log("object second apply:", second.status, JSON.stringify(second.data));
    expect(second.status).toBe(200);
    // Each element is a full application_details object, not a scalar.
    expect(Array.isArray(second.data.seen_past_details)).toBe(true);
    expect(second.data.seen_past_details.length).toBe(1);
    expect(second.data.seen_past_details[0]).toMatchObject({ external_application_id: "EXT-AAA" });

    const getRes = await getApplication({ type, applicationId: second.data.id });
    expect(getRes.status).toBe(200);
    const stored = getRes.data.list[0].application_details.seen_past_details;
    expect(Array.isArray(stored)).toBe(true);
    expect(stored.length).toBe(1);
    expect(stored[0]).toMatchObject({ external_application_id: "EXT-AAA" });
  });
});
