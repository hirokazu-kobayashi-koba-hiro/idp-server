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
 * rules can reference it. This test references the external application id that lives inside each
 * past application's `application_details` and projects it across all past applications with the
 * JSONPath wildcard `$.previous_applications[*].application_details.external_application_id`.
 *
 * Flow (single `apply` process, no duplicate verifier so the second apply is allowed):
 *   1. First apply stores external_application_id "EXT-AAA" into application_details; status becomes
 *      REQUESTED (counts as "running").
 *   2. Second apply: findHistory returns the first (running) application, so the shared mapping
 *      context now carries it. The response mapping projects the past external ids as a LIST.
 *      Asserting on the response proves the same context value the execution http_request body would
 *      receive resolves correctly.
 */
describe("Identity Verification: previous_applications exposed to mapping context (#1592)", () => {
  const orgId = serverConfig.organizationId;
  const tenantId = serverConfig.tenantId;

  let orgAccessToken;
  let userAccessToken;
  const configId = uuidv4();
  const configurationType = uuidv4();

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

    const configurationData = {
      id: configId,
      type: configurationType,
      attributes: { enabled: true },
      common: { auth_type: "none" },
      processes: {
        apply: {
          request: {
            schema: {
              type: "object",
              properties: {
                external_application_id: { type: "string" }
              },
              required: ["external_application_id"]
            }
          },
          // Expose past running applications of this type as $.previous_applications.
          history: {
            filters: [{ types: [configurationType], statuses: "running" }]
          },
          execution: {
            type: "no_action"
          },
          // Persist the external application id into application_details so it becomes part of the
          // past-application read model fetched on the next request. Also persist the projected
          // list of past external ids so it can be inspected through the applications API.
          store: {
            application_details_mapping_rules: [
              {
                from: "$.request_body.external_application_id",
                to: "external_application_id"
              },
              {
                from: "$.previous_applications[*].application_details.external_application_id",
                to: "seen_past_external_application_ids"
              }
            ]
          },
          // Project the external application id of every past application as a list. This is the
          // same context the execution http_request body would draw from.
          response: {
            body_mapping_rules: [
              {
                from: "$.previous_applications[*].application_details.external_application_id",
                to: "seen_past_external_application_ids"
              }
            ]
          }
        }
      }
    };

    const createResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
        "Content-Type": "application/json"
      },
      body: configurationData
    });
    expect(createResponse.status).toBe(201);
  });

  afterAll(async () => {
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
      headers: { Authorization: `Bearer ${orgAccessToken}` }
    });
  });

  it("projects past applications' external_application_id as a list via $.previous_applications[*]", async () => {
    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", configurationType)
      .replace("{process}", "apply");

    // First apply: no history yet, so the projected list is empty.
    const firstResponse = await postWithJson({
      url: applyUrl,
      body: { external_application_id: "EXT-AAA" },
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userAccessToken}`
      }
    });
    console.log("first apply:", firstResponse.status, JSON.stringify(firstResponse.data));
    expect(firstResponse.status).toBe(200);
    expect(firstResponse.data.seen_past_external_application_ids ?? []).toEqual([]);

    // Second apply: the first (running) application is now in the history, so its
    // application_details.external_application_id is projected into the list.
    const secondResponse = await postWithJson({
      url: applyUrl,
      body: { external_application_id: "EXT-BBB" },
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userAccessToken}`
      }
    });
    console.log("second apply:", secondResponse.status, JSON.stringify(secondResponse.data));
    expect(secondResponse.status).toBe(200);
    expect(secondResponse.data.seen_past_external_application_ids).toEqual(["EXT-AAA"]);

    // The projected list was stored into application_details, so it is observable through the
    // applications API.
    const secondApplicationId = secondResponse.data.id;
    const getResponse = await get({
      url:
        serverConfig.identityVerificationApplicationsEndpoint +
        `?id=${secondApplicationId}&type=${configurationType}`,
      headers: { Authorization: `Bearer ${userAccessToken}` }
    });
    console.log("get application:", getResponse.status, JSON.stringify(getResponse.data));
    expect(getResponse.status).toBe(200);
    expect(getResponse.data.list.length).toBe(1);
    expect(
      getResponse.data.list[0].application_details.seen_past_external_application_ids
    ).toEqual(["EXT-AAA"]);
  });
});
