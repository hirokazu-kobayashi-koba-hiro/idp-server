import { describe, expect, it, test } from "@jest/globals";
import {get, postWithJson} from "../../../../lib/http";
import { backendUrl, adminServerConfig } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";

describe("security event hook management api", () => {

  describe("success pattern", () => {

    it("retry ssf", async () => {

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
        url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/security-event-hooks?hook_type=SSF&limit=1`,
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
        });
        console.log("✅ All security events contain created_at field");

        const resultId = securityEventResponse.data.list[0].id
        const retryResponse = await postWithJson({
          url: `${backendUrl}/v1/management/tenants/${adminServerConfig.tenantId}/security-event-hooks/${resultId}/retry`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });

        console.log(JSON.stringify(retryResponse.data, null, 2));
        expect(retryResponse.status).toBe(200);
      }

    });

  });

});
