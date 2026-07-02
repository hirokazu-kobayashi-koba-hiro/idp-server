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
 * #1630: an oauth_authorization that omits an OPTIONAL field (scope is OPTIONAL for
 * client_credentials) must NOT crash while assembling the token request.
 *
 * Before the fix, OAuthAuthorizationConfiguration.toRequestValues() put scope=null and
 * HttpQueryParams.add() passed it to URLEncoder.encode(null) → NullPointerException → 500
 * server_error ("unexpected error is occurred"), before the token request was even sent.
 *
 * After the fix (HttpQueryParams.add skips null/empty), the request is assembled and sent. The token
 * endpoint here is the always-401 mock (same as integration-17), so a successfully-sent request
 * surfaces as the #1384 external-service error (502) — crucially NOT a 500 NPE. Asserting 502 (and
 * explicitly not 500) proves assembly reached the network with scope omitted.
 */
describe("Identity Verification - external OAuth with optional field (scope) omitted does not NPE (#1630)", () => {
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

    // oauth_authorization intentionally omits `scope` (OPTIONAL for client_credentials). The token
    // endpoint is the always-401 mock, matching integration-17.
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
                  // scope omitted on purpose (#1630)
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

  it("omitting scope does not NPE: the request is assembled and sent (502, not 500 server_error)", async () => {
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

    // Key assertion: NOT a 500 NPE. Before the #1630 fix, omitting scope threw NPE → 500.
    expect(response.status).not.toBe(500);
    // The request was assembled and sent; the always-401 mock surfaces as 502 (#1384 behavior).
    expect(response.status).toBe(502);
    expect(response.data).toHaveProperty("error", "external_service_error");
  });
});
