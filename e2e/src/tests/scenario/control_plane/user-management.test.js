import { describe, expect, it, test } from "@jest/globals";
import { deletion, get, patchWithJson, putWithJson, postWithJson } from "../../../lib/http";
import { backendUrl, clientSecretPostClient, serverConfig } from "../../testConfig";
import { requestToken } from "../../../api/oauthClient";

describe("audit log management api", () => {

  describe("success pattern", () => {

    it("crud", async () => {

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

      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/users`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "provider_id": "idp-server",
          "name": "test",
          "email": "test",
          "raw_password": "test@01234"
        }
      });
      console.log(createResponse.data);
      expect(createResponse.status).toBe(201);
      expect(createResponse.data).toHaveProperty("result");

      const userId = createResponse.data.result.sub;

      const listResponse = await get({
        url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/users?user_id=${userId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log(JSON.stringify(listResponse.data));
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("list");

      const detailResponse = await get({
        url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/users/${userId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      expect(detailResponse.status).toBe(200);
      expect(detailResponse.data).toHaveProperty("sub");

      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/users/${userId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "provider_id": "idp-server",
          "name": "test-3",
          "email": "test-3"
        }
      });
      console.log(updateResponse.data);
      expect(updateResponse.status).toBe(200);
      expect(updateResponse.data).toHaveProperty("result");

      const patchResponse = await patchWithJson({
        url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/users/${userId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": "test-2",
        }
      });
      console.log(patchResponse.data);
      expect(patchResponse.status).toBe(200);
      expect(patchResponse.data).toHaveProperty("result");


      const updatePasswordResponse = await putWithJson({
        url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/users/${userId}/password`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "provider_id": "idp-server",
          "name": "test",
          "email": "test",
          "raw_password": "test@012355"
        }
      });
      console.log(updatePasswordResponse.data);
      expect(updatePasswordResponse.status).toBe(200);
      expect(updatePasswordResponse.data).toHaveProperty("result");

      const deleteResponse = await deletion({
        url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/users/${userId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log(deleteResponse.data);
      expect(deleteResponse.status).toBe(204);
    });

    describe("find list query pattern", () => {
      const validCases = [
        ["valid user_id", "user_id", "c9cfa3c3-8534-45a6-8905-7a1f9cd3fd18"],
        ["valid from", "from", "2025-06-20T19:51:39.901577"],
        ["valid to", "to", "2025-06-21T19:51:39.901577"],
        ["valid external_user_id", "external_user_id", "external-user-001"],
        ["valid provider_id", "provider_id", "google"],
        ["valid provider_user_id", "provider_user_id", "google-user-123"],
        ["valid email", "email", "user@example.com"],
        ["valid status", "status", "REGISTERED"],
        ["valid name", "name", "ito.ichiro"],
        ["valid given_name", "given_name", "ichiro"],
        ["valid family_name", "family_name", "ito"],
        ["valid middle_name", "middle_name", "shinji"],
        ["valid nickname", "nickname", "itchy"],
        ["valid preferred_username", "preferred_username", "ichiro-ito"],
        ["valid phone_number", "phone_number", "+819012345678"],
        ["valid role", "role", "admin"],
        ["valid permission", "permission", "user.read"],
        ["user_id", "user_id", "3ec055a8-8000-44a2-8677-e70ebff414e2"],
        ["external_user_id", "external_user_id", "3ec055a8-8000-44a2-8677-e70ebff414e2"],
        ["provider_id", "provider_id", "idp-server"],
        ["provider_user_id", "provider_user_id", "3ec055a8-8000-44a2-8677-e70ebff414e2"],
        ["email", "email", "ito.ichiro@gmail.com"],
        ["name", "name", "ito ichiro"],
        ["given_name", "given_name", "ichiro"],
        ["family_name", "family_name", "ito"],
        ["middle_name", "middle_name", "mac"],
        ["nickname", "nickname", "ito"],
        ["preferred_username", "preferred_username", "ichiro"],
        ["phone_number", "phone_number", "09012345678"],
        ["role", "role", "Administrator"],
      ];
      test.each(validCases)("success case:%s param: %s, value: %s", async (description, param, value) => {
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

        expect(tokenResponse.status).toBe(200);
        const accessToken = tokenResponse.data.access_token;

        const listResponse = await get({
          url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/users?${param}=${encodeURIComponent(value)}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });

        console.log(JSON.stringify(listResponse.data));
        expect(listResponse.status).toBe(200);
        expect(Array.isArray(listResponse.data.list)).toBe(true);
      });
    });


  });

  describe("error case", () => {

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

      const listResponse = await get({
        url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/users?${param}=${value}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log(JSON.stringify(listResponse.data));
      expect(listResponse.status).toBe(400);

    });

  });

});