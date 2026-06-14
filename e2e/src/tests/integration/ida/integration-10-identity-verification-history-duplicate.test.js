import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { postWithJson, deletion } from "../../../lib/http";
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
 * Phase 1 verification for the config-driven history pipeline.
 *
 * The `apply` process declares `history.running_check` and a `duplicate_application` verifier.
 * The first apply succeeds (status becomes REQUESTED, which counts as "running"); the second
 * apply for the same user/type must be blocked by the verifier — proving that:
 *   1. the new `history` section is parsed and translated into a HistoryQueryPlan,
 *   2. `findHistory` returns the running application,
 *   3. `DenyDuplicateIdentityVerificationApplicationVerifier` reads it via `containsRunningState`.
 */
describe("Identity Verification: history-driven duplicate prevention", () => {
  const orgId = serverConfig.organizationId;
  const tenantId = serverConfig.tenantId;

  let orgAccessToken;
  let userAccessToken;
  let configId;
  let configurationType;
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
      scope: "openid profile phone email identity_verification_application " + clientSecretPostClient.identityVerificationScope
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

  it("blocks a second apply when a running application already exists for the same type", async () => {
    configId = uuidv4();
    configurationType = uuidv4();
    createdConfigIds.push(configId);

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
                trust_framework: { type: "string" }
              },
              required: ["trust_framework"]
            }
          },
          // New section under test: each entry declares a filter for the past-application
          // read model. The planner OR-combines all entries into a single SQL fetch
          // executed before the pre_hook verifiers run. Verifiers consume the resulting
          // Applications via the existing domain API (e.g. containsRunningState).
          history: {
            filters: [
              { types: [configurationType], statuses: "running" }
            ]
          },
          pre_hook: {
            verifications: [
              { type: "duplicate_application" }
            ]
          },
          execution: {
            type: "no_action"
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

    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", configurationType)
      .replace("{process}", "apply");

    const firstResponse = await postWithJson({
      url: applyUrl,
      body: { trust_framework: "eidas" },
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userAccessToken}`
      }
    });
    expect(firstResponse.status).toBe(200);

    const secondResponse = await postWithJson({
      url: applyUrl,
      body: { trust_framework: "eidas" },
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userAccessToken}`
      }
    });
    // Pre-hook validation failure (duplicate) maps to CLIENT_ERROR = 400.
    expect(secondResponse.status).toBe(400);
    const body = JSON.stringify(secondResponse.data || {});
    expect(body.toLowerCase()).toContain("duplicate");
  });

  it("falls back to running-of-type when duplicate_application is configured WITHOUT a history section (backward compatibility)", async () => {
    // Regression guard for the findAll -> findHistory change: a config that enables the
    // duplicate_application verifier but declares NO `history` section must still block
    // duplicates. historyPlan(type) falls back to observing running applications of the
    // requested type, preserving the pre-`history` behavior instead of silently no-op'ing.
    const fallbackConfigId = uuidv4();
    const fallbackType = uuidv4();
    createdConfigIds.push(fallbackConfigId);

    const configurationData = {
      id: fallbackConfigId,
      type: fallbackType,
      attributes: { enabled: true },
      common: { auth_type: "none" },
      processes: {
        apply: {
          request: {
            schema: {
              type: "object",
              properties: {
                trust_framework: { type: "string" }
              },
              required: ["trust_framework"]
            }
          },
          // NOTE: intentionally no `history` section — exercises the fallback path.
          pre_hook: {
            verifications: [
              { type: "duplicate_application" }
            ]
          },
          execution: {
            type: "no_action"
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

    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", fallbackType)
      .replace("{process}", "apply");

    const firstResponse = await postWithJson({
      url: applyUrl,
      body: { trust_framework: "eidas" },
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userAccessToken}`
      }
    });
    expect(firstResponse.status).toBe(200);

    const secondResponse = await postWithJson({
      url: applyUrl,
      body: { trust_framework: "eidas" },
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userAccessToken}`
      }
    });
    // Without the fallback this would be 200 (duplicate silently allowed).
    // Pre-hook validation failure (duplicate) maps to CLIENT_ERROR = 400.
    expect(secondResponse.status).toBe(400);
    const body = JSON.stringify(secondResponse.data || {});
    expect(body.toLowerCase()).toContain("duplicate");
  });

  it("still blocks duplicates when an explicit history section does NOT cover the request type (merge, not replace)", async () => {
    // Guards the "explicit history replaces the duplicate fallback" hole: a process declares
    // duplicate_application AND an explicit history section that observes an UNRELATED type only.
    // historyPlan(type) must MERGE the running-of-request-type filter (not be replaced by the
    // explicit one), otherwise the duplicate check silently no-ops for this type.
    const mergeConfigId = uuidv4();
    const mergeType = uuidv4();
    const unrelatedType = uuidv4();
    createdConfigIds.push(mergeConfigId);

    const configurationData = {
      id: mergeConfigId,
      type: mergeType,
      attributes: { enabled: true },
      common: { auth_type: "none" },
      processes: {
        apply: {
          request: {
            schema: {
              type: "object",
              properties: {
                trust_framework: { type: "string" }
              },
              required: ["trust_framework"]
            }
          },
          // Explicit history observes an UNRELATED type — it does NOT cover mergeType's running.
          history: {
            filters: [
              { types: [unrelatedType], statuses: "running" }
            ]
          },
          pre_hook: {
            verifications: [
              { type: "duplicate_application" }
            ]
          },
          execution: {
            type: "no_action"
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

    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", mergeType)
      .replace("{process}", "apply");

    const firstResponse = await postWithJson({
      url: applyUrl,
      body: { trust_framework: "eidas" },
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userAccessToken}`
      }
    });
    expect(firstResponse.status).toBe(200);

    const secondResponse = await postWithJson({
      url: applyUrl,
      body: { trust_framework: "eidas" },
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userAccessToken}`
      }
    });
    // With "replace" semantics this would be 200 (explicit history hides the duplicate filter).
    expect(secondResponse.status).toBe(400);
    const body = JSON.stringify(secondResponse.data || {});
    expect(body.toLowerCase()).toContain("duplicate");
  });
});
