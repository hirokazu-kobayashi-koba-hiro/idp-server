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
 * #1268: the generic `assert` pre_hook verifier denies an application when a declared assertion
 * (a ConditionSpec) does not hold against the verifier context. Because #1592 exposes
 * `$.previous_applications` to that context, a "verify against past applications" rule — the role
 * the dead CDD verifier was meant to fill — is now expressible declaratively, e.g. "deny when a
 * previous application already exists".
 *
 * Flow (single `apply` process, history exposes running applications):
 *   1. First apply: no past application, so the assertion holds → 200.
 *   2. Second apply: a past application exists, so the assertion fails → 400 with the configured
 *      message (pre_hook validation failure).
 */
describe("Identity Verification: generic assert verifier over previous_applications (#1268)", () => {
  const orgId = serverConfig.organizationId;
  const tenantId = serverConfig.tenantId;

  const denyMessage = "既存の申込みが存在するため実行できません";

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
              properties: { external_application_id: { type: "string" } },
              required: ["external_application_id"]
            }
          },
          // Expose past running applications of this type as $.previous_applications.
          history: {
            filters: [{ types: [configurationType], statuses: "running" }]
          },
          // Generic assert verifier: the assertion must hold, otherwise the application is denied.
          pre_hook: {
            verifications: [
              {
                type: "assert",
                details: {
                  assertions: [
                    {
                      condition: { operation: "missing", path: "$.previous_applications[0]" },
                      message: denyMessage
                    }
                  ]
                }
              }
            ]
          },
          execution: { type: "no_action" }
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

  it("allows the first apply (assertion holds) and denies the second (assertion fails over $.previous_applications)", async () => {
    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", configurationType)
      .replace("{process}", "apply");

    const first = await postWithJson({
      url: applyUrl,
      body: { external_application_id: "EXT-AAA" },
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${userAccessToken}` }
    });
    console.log("first apply:", first.status, JSON.stringify(first.data));
    expect(first.status).toBe(200);

    const second = await postWithJson({
      url: applyUrl,
      body: { external_application_id: "EXT-BBB" },
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${userAccessToken}` }
    });
    console.log("second apply:", second.status, JSON.stringify(second.data));
    // Pre-hook validation failure (assertion not held) maps to CLIENT_ERROR = 400.
    expect(second.status).toBe(400);
    expect(JSON.stringify(second.data)).toContain(denyMessage);
  });
});
