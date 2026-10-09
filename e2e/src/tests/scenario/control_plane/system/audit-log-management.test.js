import { describe, expect, it, test } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import { deletion, get, postWithJson } from "../../../../lib/http";
import { backendUrl, adminServerConfig } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";

describe("audit log management api", () => {

  describe("success pattern", () => {

    it("masks secrets in the response, in the list and in a single log", async () => {
      const tokenResponse = await requestToken({
        endpoint: adminServerConfig.tokenEndpoint,
        grantType: "password",
        username: adminServerConfig.oauth.username,
        password: adminServerConfig.oauth.password,
        scope: adminServerConfig.adminClient.scope,
        clientId: adminServerConfig.adminClient.clientId,
        clientSecret: adminServerConfig.adminClient.clientSecret
      });
      expect(tokenResponse.status).toBe(200);
      const headers = { Authorization: `Bearer ${tokenResponse.data.access_token}` };

      const clientId = uuidv4();
      const clientSecret = `audit-mask-secret-${uuidv4()}`;
      const clientsUrl = `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/clients`;
      const createResponse = await postWithJson({
        url: clientsUrl,
        headers,
        body: {
          client_id: clientId,
          client_name: "Audit Log Mask Client",
          client_secret: clientSecret,
          grant_types: ["authorization_code"],
          redirect_uris: ["http://localhost:3000/callback"]
        }
      });
      expect(createResponse.status).toBe(201);

      try {
        // The audit log is written asynchronously.
        let listed;
        let auditLogResponse;
        for (let attempt = 0; attempt < 20 && !listed; attempt++) {
          auditLogResponse = await get({
            url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/audit-logs?limit=50`,
            headers
          });
          expect(auditLogResponse.status).toBe(200);
          listed = auditLogResponse.data.list.find(
            (log) => log.request && log.request.client_id === clientId
          );
          if (!listed) await new Promise((resolve) => setTimeout(resolve, 500));
        }
        expect(listed).toBeDefined();
        expect(listed.request.client_secret).toBe("[SCRUBBED]");
        expect(listed.request.client_name).toBe("Audit Log Mask Client");
        expect(JSON.stringify(auditLogResponse.data)).not.toContain(clientSecret);

        const detailResponse = await get({
          url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/audit-logs/${listed.id}`,
          headers
        });
        expect(detailResponse.status).toBe(200);
        expect(detailResponse.data.request.client_secret).toBe("[SCRUBBED]");
        expect(JSON.stringify(detailResponse.data)).not.toContain(clientSecret);
      } finally {
        await deletion({ url: `${clientsUrl}/${clientId}`, headers });
      }
    });

    it("no queries", async () => {

      const tokenResponse = await requestToken({
        endpoint: adminServerConfig.tokenEndpoint,
        grantType: "password",
        username: adminServerConfig.oauth.username,
        password: adminServerConfig.oauth.password,
        scope: adminServerConfig.adminClient.scope,
        clientId: adminServerConfig.adminClient.clientId,
        clientSecret: adminServerConfig.adminClient.clientSecret
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const auditLogResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/audit-logs`,
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
        endpoint: adminServerConfig.tokenEndpoint,
        grantType: "password",
        username: adminServerConfig.oauth.username,
        password: adminServerConfig.oauth.password,
        scope: adminServerConfig.adminClient.scope,
        clientId: adminServerConfig.adminClient.clientId,
        clientSecret: adminServerConfig.adminClient.clientSecret
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const auditLogResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/audit-logs?${param}=${value}`,
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
      endpoint: adminServerConfig.tokenEndpoint,
      grantType: "password",
      username: adminServerConfig.oauth.username,
      password: adminServerConfig.oauth.password,
      scope: adminServerConfig.adminClient.scope,
      clientId: adminServerConfig.adminClient.clientId,
      clientSecret: adminServerConfig.adminClient.clientSecret
    });
    console.log(tokenResponse.data);
    expect(tokenResponse.status).toBe(200);
    const accessToken = tokenResponse.data.access_token;

    const auditLogResponse = await get({
      url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/audit-logs?${param}=${value}`,
      headers: {
        Authorization: `Bearer ${accessToken}`
      }
    });

    console.log(JSON.stringify(auditLogResponse.data));
    expect(auditLogResponse.status).toBe(400);

  });

})
;