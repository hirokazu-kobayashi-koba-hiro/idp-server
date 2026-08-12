import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { postWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
  federationServerConfig,
  mockApiBaseUrl
} from "../../testConfig";
import { createFederatedUser } from "../../../user";
import { v4 as uuidv4 } from "uuid";

/**
 * Issue #1774: base64 mapping function.
 *
 * Verifies that an outgoing header can be assembled from raw credentials at request time, so the
 * configuration holds only `client_id:client_secret` and not a separately-managed base64 derivative.
 * The mock endpoint echoes the Authorization header it received, which is what makes the assertion
 * end-to-end rather than a check of the mapper in isolation.
 */
describe("Identity Verification - base64 function in header_mapping_rules", () => {
  const orgId = serverConfig.organizationId;
  const tenantId = serverConfig.tenantId;

  // Fixed sample credentials: base64("test-client-id:test-client-secret").
  const CLIENT_ID = "test-client-id";
  const CLIENT_SECRET = "test-client-secret";
  const EXPECTED_BASIC =
    "Basic " + Buffer.from(`${CLIENT_ID}:${CLIENT_SECRET}`, "utf8").toString("base64");

  let orgAccessToken;
  let userAccessToken;

  const configIds = [];

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

    const { accessToken } = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
      scope:
        "openid profile email identity_verification_application " +
        clientSecretPostClient.identityVerificationScope
    });

    userAccessToken = accessToken;
  });

  afterAll(async () => {
    for (const configId of configIds) {
      try {
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
          headers: { Authorization: `Bearer ${orgAccessToken}` }
        });
      } catch (e) {
        console.log(`Failed to clean up configuration: ${configId}`, e.message);
      }
    }
  });

  /**
   * Creates a single-process IDA config whose apply step calls the header-echo endpoint.
   */
  async function createEchoHeaderConfig(configId, configurationType, headerMappingRules) {
    const configurationData = {
      "id": configId,
      "type": configurationType,
      "attributes": { "enabled": true },
      "common": { "auth_type": "none" },
      "processes": {
        "apply": {
          "request": {
            "schema": {
              "type": "object",
              "properties": { "trust_framework": { "type": "string" } },
              "required": ["trust_framework"]
            }
          },
          "execution": {
            "type": "http_request",
            "http_request": {
              "url": `${mockApiBaseUrl}/e2e/echo-request-headers`,
              "method": "POST",
              "auth_type": "none",
              "header_mapping_rules": headerMappingRules,
              "body_mapping_rules": [
                { "from": "$.request_body", "to": "*" }
              ]
            }
          },
          "response": {
            "body_mapping_rules": [
              { "from": "$.response_body", "to": "*" }
            ]
          }
        }
      }
    };

    const response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
      headers: {
        "Authorization": `Bearer ${orgAccessToken}`,
        "Content-Type": "application/json"
      },
      body: configurationData
    });
    expect(response.status).toBe(201);
    return response;
  }

  async function apply(configurationType) {
    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", configurationType)
      .replace("{process}", "apply");

    const applyResponse = await postWithJson({
      url: applyUrl,
      body: { "trust_framework": "uk_tfida" },
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${userAccessToken}`
      }
    });
    console.log("Apply response:", JSON.stringify(applyResponse.data, null, 2));
    expect(applyResponse.status).toBe(200);
    return applyResponse;
  }

  it("should build a Basic Authorization header from raw credentials using base64 and format", async () => {
    const configId = uuidv4();
    const configurationType = uuidv4();
    configIds.push(configId);

    await createEchoHeaderConfig(configId, configurationType, [
      { "static_value": "application/json", "to": "Content-Type" },
      {
        "static_value": `${CLIENT_ID}:${CLIENT_SECRET}`,
        "to": "Authorization",
        "functions": [
          { "name": "base64" },
          { "name": "format", "args": { "template": "Basic {{value}}" } }
        ]
      }
    ]);

    const applyResponse = await apply(configurationType);

    expect(applyResponse.data.received_authorization).toBe(EXPECTED_BASIC);
  });

  it("should produce base64url without padding when url_safe and padding args are set", async () => {
    const configId = uuidv4();
    const configurationType = uuidv4();
    configIds.push(configId);

    // Multi-byte input so the standard alphabet would emit '/', making the url_safe swap visible.
    const rawValue = "ÿþ";
    const expected = Buffer.from(rawValue, "utf8").toString("base64url");

    await createEchoHeaderConfig(configId, configurationType, [
      { "static_value": "application/json", "to": "Content-Type" },
      {
        "static_value": rawValue,
        "to": "Authorization",
        "functions": [
          { "name": "base64", "args": { "url_safe": true, "padding": false } }
        ]
      }
    ]);

    const applyResponse = await apply(configurationType);

    expect(applyResponse.data.received_authorization).toBe(expected);
    expect(applyResponse.data.received_authorization).not.toContain("/");
    expect(applyResponse.data.received_authorization).not.toContain("=");
  });
});
