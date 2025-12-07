import { describe, expect, it, test } from "@jest/globals";
import { get } from "../../../../lib/http";
import { backendUrl, adminServerConfig } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";

describe("security event management api", () => {

  describe("success pattern", () => {

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

      const securityEventResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/security-events`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log(JSON.stringify(securityEventResponse.data));
      expect(securityEventResponse.status).toBe(200);
      expect(securityEventResponse.data).toHaveProperty("list");

      // Check that created_at field is included in each security event
      if (securityEventResponse.data.list.length > 0) {
        securityEventResponse.data.list.forEach(securityEvent => {
          expect(securityEvent).toHaveProperty("created_at");
          expect(typeof securityEvent.created_at).toBe("string");
          expect(securityEvent.created_at).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/); // ISO 8601 format

          // user.name should be string if present (preferred_username of the user)
          if (securityEvent.user && securityEvent.user.name) {
            expect(typeof securityEvent.user.name).toBe("string");
          }
        });
        console.log("✅ All security events contain created_at field");
      }

    });

    it("individual security event detail contains created_at", async () => {
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
      const accessToken = tokenResponse.data.access_token;

      // First get the list to find an existing security event ID
      const listResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/security-events?limit=1`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(listResponse.status).toBe(200);

      if (listResponse.data.list.length > 0) {
        const securityEventId = listResponse.data.list[0].id;
        console.log(securityEventId);

        // Get individual security event detail
        const detailResponse = await get({
          url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/security-events/${securityEventId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });

        console.log("Security event detail:", JSON.stringify(detailResponse.data));
        expect(detailResponse.status).toBe(200);
        expect(detailResponse.data).toHaveProperty("created_at");
        expect(typeof detailResponse.data.created_at).toBe("string");
        expect(detailResponse.data.created_at).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/); // ISO 8601 format
        console.log("✅ Individual security event contains created_at field");
      } else {
        console.log("⚠️ No security events found to test individual detail");
      }
    });

    const successCases = [
      ["id", "id", "3ec055a8-8000-44a2-8677-e70ebff414e2"],
      ["ex-sub", "external_user_id", "3ec055a8-8000-44a2-8677-e70ebff414e2"],
      ["user-id", "user_id", "3ec055a8-8000-44a2-8677-e70ebff414e2"],
      ["user-name", "user_name", "admin"],
      ["client-id", "client_id", "client"],
      ["event-type", "event_type", "oauth_deny"],
      ["ip-address", "ip_address", "127.0.0.1"],
      ["user-agent", "user_agent", "Mozilla"],
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

      const securityEventResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/security-events?${param}=${value}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log(JSON.stringify(securityEventResponse.data));
      expect(securityEventResponse.status).toBe(200);
      expect(securityEventResponse.data).toHaveProperty("list");

    });
  });

  const errorCases = [
    ["user_id", "user_id", "123"],
    ["from", "from", "2025-06-20C19:51:39.901577"],
    ["to", "to", "2025-06-20-19:51:39.901577"],
    ["ip_address-invalid", "ip_address", "invalid-ip"],
    ["ip_address-out-of-range", "ip_address", "999.999.999.999"],
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

    const securityEventResponse = await get({
      url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/security-events?${param}=${value}`,
      headers: {
        Authorization: `Bearer ${accessToken}`
      }
    });

    console.log(JSON.stringify(securityEventResponse.data));
    expect(securityEventResponse.status).toBe(400);

  });

})
;