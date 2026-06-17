import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { postWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
  federationServerConfig,
  mockApiBaseUrl,
} from "../../testConfig";
import { createFederatedUser } from "../../../user";
import { v4 as uuidv4 } from "uuid";

/**
 * #1384: when the external eKYC execution fails to obtain an OAuth token (misconfigured
 * oauth_authorization, e.g. invalid client_id → token endpoint returns 401), the API must respond
 * with a meaningful 502 external_service_error — NOT a generic 500 server_error.
 *
 * The token endpoint is pointed at the mock `e2e/oauth-retry/401-always` route (POST → 401), so the
 * OAuth resolver fails while building the request, before the main eKYC call. Before the fix this
 * surfaced as 500 server_error ("unexpected error is occurred"); after the fix it is 502
 * external_service_error.
 */
describe("Identity Verification - external OAuth failure returns 502 (#1384)", () => {
  const orgId = serverConfig.organizationId;
  const tenantId = serverConfig.tenantId;

  let orgAccessToken;
  let userAccessToken;
  const type = uuidv4();
  const configId = uuidv4();

  beforeAll(async () => {
    const orgAuthResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
      scope: "org-management account management",
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
        clientSecretPostClient.identityVerificationScope,
    });
    userAccessToken = accessToken;

    // apply process whose execution authenticates with oauth2; the token endpoint always returns 401.
    const create = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
      headers: {
        Authorization: `Bearer ${orgAccessToken}`,
        "Content-Type": "application/json",
      },
      body: {
        id: configId,
        type,
        attributes: { enabled: true },
        common: { auth_type: "none" },
        processes: {
          apply: {
            request: { schema: { type: "object", properties: {} } },
            execution: {
              type: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/e2e/oauth-retry/401-always`,
                method: "POST",
                auth_type: "oauth2",
                oauth_authorization: {
                  type: "client_credentials",
                  token_endpoint: `${mockApiBaseUrl}/e2e/oauth-retry/401-always`,
                  client_authentication_type: "client_secret_post",
                  client_id: "invalid-client",
                  client_secret: "invalid-secret",
                  scope: "api:access",
                },
              },
            },
          },
        },
      },
    });
    expect(create.status).toBe(201);
  });

  afterAll(async () => {
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
      headers: { Authorization: `Bearer ${orgAccessToken}` },
    });
  });

  it("returns 502 external_service_error when the external token endpoint rejects the OAuth request", async () => {
    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", type)
      .replace("{process}", "apply");

    const response = await postWithJson({
      url: applyUrl,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${userAccessToken}`,
      },
      body: {},
    });
    console.log("apply response:", response.status, JSON.stringify(response.data, null, 2));

    expect(response.status).toBe(502);
    expect(response.data).toHaveProperty("error", "external_service_error");
  });
});
