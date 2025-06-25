import { describe, expect, it } from "@jest/globals";
import { requestAuthorizationsForSignup } from "../../oauth/signup";
import { backendUrl, clientSecretPostClient, serverConfig } from "../testConfig";
import { faker } from "@faker-js/faker";
import { postAuthentication, requestToken } from "../../api/oauthClient";
import { get, post } from "../../lib/http";
import { requestFederation } from "../../oauth/federation";

describe("sso oidc", () => {


  describe("success pattern", () => {

    it("clientSecretPost", async () => {

      const interaction = async (id, user) => {
        // /{id}/federations/{federation-type}/{sso-provider-name}
        const federationInteraction = async (id, user) => {
          const challengeResponse = await postAuthentication({
            endpoint: `${backendUrl}/1e68932e-ed4a-43e7-b412-460665e42df3/v1/authorizations/{id}/email-authentication-challenge`,
            id,
            body: {
              email: user.email,
              email_template: "authentication"
            },
          });
          console.log(challengeResponse.status);
          console.log(challengeResponse.data);

          const authenticationTransactionResponse = await get({
            url: `${backendUrl}/1e68932e-ed4a-43e7-b412-460665e42df3/v1/authentications?authorization_id=${id}`,
          });
          console.log(authenticationTransactionResponse.data);
          const transactionId = authenticationTransactionResponse.data.list[0].id;

          const adminTokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "password",
            username: serverConfig.oauth.username,
            password: serverConfig.oauth.password,
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret
          });
          console.log(adminTokenResponse.data);
          expect(adminTokenResponse.status).toBe(200);
          const accessToken = adminTokenResponse.data.access_token;

          const interactionResponse = await get({
            url: `${backendUrl}/v1/management/tenants/1e68932e-ed4a-43e7-b412-460665e42df3/authentication-interactions/${transactionId}/email`,
            headers: {
              Authorization: `Bearer ${accessToken}`
            }
          });
          console.log(interactionResponse.data);
          const verificationCode = interactionResponse.data.payload.verification_code;

          const verificationResponse = await postAuthentication({
            endpoint: `${backendUrl}/1e68932e-ed4a-43e7-b412-460665e42df3/v1/authorizations/{id}/email-authentication`,
            id,
            body: {
              verification_code: verificationCode,
            }
          });

          console.log(verificationResponse.status);
          console.log(verificationResponse.data);
        };

        const viewResponse = await get({
          url: `${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/authorizations/${id}/view-data`,
        });
        console.log(JSON.stringify(viewResponse.data, null, 2));
        expect(viewResponse.status).toBe(200);
        expect(viewResponse.data.available_federations).not.toBeNull();

        const federationSetting = viewResponse.data.available_federations.find(federation => federation.auto_selected);
        console.log(federationSetting);

        const { params } = await requestFederation({
          url: "http://localhost:8080",
          authSessionId: id,
          authSessionTenantId: "67e7eae6-62b0-4500-9eff-87459f63fc66",
          type: federationSetting.type,
          providerName: federationSetting.sso_provider,
          federationTenantId: "1e68932e-ed4a-43e7-b412-460665e42df3",
          user: {
            email: faker.internet.email(),
            name: faker.person.fullName(),
            zoneinfo: "Asia/Tokyo",
            locale: "ja-JP",
            phone_number: faker.phone.number("090-####-####"),
          },
          mfa: "",
          interaction: federationInteraction,
        });
        console.log(params);

        const federationCallbackResponse = await post({
          url: `${backendUrl}/v1/authorizations/federations/oidc/callback`,
          body: params.toString()
        });
        console.log(federationCallbackResponse.data);
        expect(federationCallbackResponse.status).toBe(200);

      };

      const { authorizationResponse } = await requestAuthorizationsForSignup({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: "openid profile phone email" + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        customParams: {
          organizationId: "123",
          organizationName: "test",
        },
        mfa: "",
        interaction,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(tokenResponse.data);
      expect(tokenResponse.data).toHaveProperty("id_token");
    });


  });
});