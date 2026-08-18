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
 * Issue #1790: normalize mapping function.
 *
 * Verifies that a value received in one notation reaches the external API in the normalized form,
 * so two sources that spell the same name differently can be matched. The mock endpoint echoes the
 * `name` field it received, which is what makes the assertion end-to-end rather than a check of the
 * mapper in isolation: the string survives JSON, HTTP and UTF-8 on the way out.
 *
 * Values are written as escapes because a composed and a decomposed form render identically, and
 * that distinction is the subject here.
 */
describe("Identity Verification - normalize function in body_mapping_rules", () => {
  const orgId = serverConfig.organizationId;
  const tenantId = serverConfig.tenantId;

  /** "ﾔﾏﾀﾞ ﾀﾛｳ" in halfwidth katakana. The voiced ﾀﾞ is two code points. */
  const HALFWIDTH_NAME = "ﾔﾏﾀﾞ ﾀﾛｳ";

  /** "ヤマダ タロウ" in fullwidth katakana, as NFKC produces it. */
  const FULLWIDTH_NAME = "ヤマダ タロウ";

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
   * Creates a single-process IDA config whose apply step sends `name` through the given functions
   * to the echo endpoint.
   */
  async function createEchoNameConfig(configId, configurationType, functions) {
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
              "properties": { "name": { "type": "string" } },
              "required": ["name"]
            }
          },
          "execution": {
            "type": "http_request",
            "http_request": {
              "url": `${mockApiBaseUrl}/e2e/echo-user-context`,
              "method": "POST",
              "auth_type": "none",
              "header_mapping_rules": [
                { "static_value": "application/json", "to": "Content-Type" }
              ],
              "body_mapping_rules": [
                { "from": "$.request_body.name", "to": "name", "functions": functions }
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

  async function apply(configurationType, name) {
    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", configurationType)
      .replace("{process}", "apply");

    const applyResponse = await postWithJson({
      url: applyUrl,
      body: { "name": name },
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${userAccessToken}`
      }
    });
    console.log("Apply response:", JSON.stringify(applyResponse.data, null, 2));
    expect(applyResponse.status).toBe(200);
    return applyResponse;
  }

  it("should fold halfwidth katakana into the fullwidth form using the default NFKC", async () => {
    const configId = uuidv4();
    const configurationType = uuidv4();
    configIds.push(configId);

    await createEchoNameConfig(configId, configurationType, [{ "name": "normalize" }]);

    const applyResponse = await apply(configurationType, HALFWIDTH_NAME);

    expect(applyResponse.data.received_name).toBe(FULLWIDTH_NAME);
    expect(applyResponse.data.received_name).not.toBe(HALFWIDTH_NAME);
  });

  it("should make two notations of the same name match after normalization", async () => {
    // The point of the issue: the same person arrives from two sources spelled differently. Each
    // source gets its own configuration so this stays one application per type, as the other cases
    // are.
    expect(HALFWIDTH_NAME).not.toBe(FULLWIDTH_NAME);

    const halfwidthType = uuidv4();
    const fullwidthType = uuidv4();
    const halfwidthConfigId = uuidv4();
    const fullwidthConfigId = uuidv4();
    configIds.push(halfwidthConfigId, fullwidthConfigId);

    await createEchoNameConfig(halfwidthConfigId, halfwidthType, [{ "name": "normalize" }]);
    await createEchoNameConfig(fullwidthConfigId, fullwidthType, [{ "name": "normalize" }]);

    const fromHalfwidth = await apply(halfwidthType, HALFWIDTH_NAME);
    const fromFullwidth = await apply(fullwidthType, FULLWIDTH_NAME);

    expect(fromHalfwidth.data.received_name).toBe(fromFullwidth.data.received_name);
    expect(fromHalfwidth.data.received_name).toBe(FULLWIDTH_NAME);
  });

  it("should leave the notation untouched when form is NFC", async () => {
    const configId = uuidv4();
    const configurationType = uuidv4();
    configIds.push(configId);

    // NFC is canonical only, so the halfwidth form survives. Also proves the form argument reaches
    // the function through the stored configuration rather than the default being applied always.
    await createEchoNameConfig(configId, configurationType, [
      { "name": "normalize", "args": { "form": "NFC" } }
    ]);

    const applyResponse = await apply(configurationType, HALFWIDTH_NAME);

    expect(applyResponse.data.received_name).toBe(HALFWIDTH_NAME);
  });

  it("should remove the ideographic space when chained with regex_replace", async () => {
    const configId = uuidv4();
    const configurationType = uuidv4();
    configIds.push(configId);

    // normalize does Unicode normalization and nothing else; whitespace removal is a separate
    // function in the chain. NFKC turns the ideographic space into an ASCII one, which \s matches.
    await createEchoNameConfig(configId, configurationType, [
      { "name": "normalize", "args": { "form": "NFKC" } },
      { "name": "regex_replace", "args": { "pattern": "[\\s\\u3000]+", "replacement": "" } }
    ]);

    const withIdeographicSpace = "ﾔﾏﾀﾞ　ﾀﾛｳ";
    const applyResponse = await apply(configurationType, withIdeographicSpace);

    expect(applyResponse.data.received_name).toBe("ヤマダタロウ");
  });
});
