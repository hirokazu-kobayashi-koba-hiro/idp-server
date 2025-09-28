import { describe, expect, it, test } from "@jest/globals";
import { get } from "../../../../lib/http";
import { backendUrl, clientSecretPostClient, serverConfig } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";

describe("audit log management api", () => {

  describe("success pattern", () => {

    it("no queries", async () => {

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
        scope: clientSecretPostClient.scope,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const auditLogResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/audit-logs`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log(JSON.stringify(auditLogResponse.data));
      expect(auditLogResponse.status).toBe(200);
      expect(auditLogResponse.data).toHaveProperty("list");

    });

    const successCases = [
      ["ex-sub", "external_user_id", "3ec055a8-8000-44a2-8677-e70ebff414e2"],
      ["user-id", "user_id", "3ec055a8-8000-44a2-8677-e70ebff414e2"],
      ["client-id", "client_id", "client"],
      ["from", "from", "2025-06-20 19:51:39.901577"],
      ["to", "to", "2025-06-20 19:51:39.901577"],
      ["limit", "limit", "1"],
      ["offset", "offset", "100000000"]
    ];

    test.each(successCases)("case:%s param: %s, value: %s", async (description, param, value) => {
      console.log(description, param, value);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
        scope: clientSecretPostClient.scope,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const auditLogResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/audit-logs?${param}=${value}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log(JSON.stringify(auditLogResponse.data));
      expect(auditLogResponse.status).toBe(200);
      expect(auditLogResponse.data).toHaveProperty("list");

    });
  });

  const errorCases = [
    ["user_id", "user_id", "123"],
    ["from", "from", "2025-06-20C19:51:39.901577"],
    ["to", "to", "2025-06-20-19:51:39.901577"],
  ];
  test.each(errorCases)("error case:%s param: %s, value: %s", async (description, param, value) => {
    console.log(description, param, value);

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "password",
      username: serverConfig.oauth.username,
      password: serverConfig.oauth.password,
      scope: clientSecretPostClient.scope,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret
    });
    console.log(tokenResponse.data);
    expect(tokenResponse.status).toBe(200);
    const accessToken = tokenResponse.data.access_token;

    const auditLogResponse = await get({
      url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/audit-logs?${param}=${value}`,
      headers: {
        Authorization: `Bearer ${accessToken}`
      }
    });

    console.log(JSON.stringify(auditLogResponse.data));
    expect(auditLogResponse.status).toBe(400);

  });

})
;