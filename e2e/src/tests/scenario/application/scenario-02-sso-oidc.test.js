import { describe, expect, it } from "@jest/globals";
import { backendUrl, clientSecretPostClient, federationServerConfig, serverConfig } from "../../testConfig";
import { faker } from "@faker-js/faker";
import {
  deny, getJwks,
  getUserinfo,
  postAuthentication,
  requestToken
} from "../../../api/oauthClient";
import { get, post } from "../../../lib/http";
import { requestFederation } from "../../../oauth/federation";
import { requestAuthorizations } from "../../../oauth/request";
import { verifyAndDecodeJwt } from "../../../lib/jose";

describe("sso oidc", () => {


  describe("success pattern", () => {

    it("oauth-extension signup - signin (email)", async () => {
      const registrationUser = {
        email: faker.internet.email(),
        name: faker.person.fullName(),
        zoneinfo: "Asia/Tokyo",
        locale: "ja-JP",
        phone_number: faker.phone.number("090-####-####"),
      };
      let registeredUser;

      const interaction = async (id, user) => {

        const federationInteraction = async (id, user) => {
          const challengeResponse = await postAuthentication({
            endpoint: `${backendUrl}/${federationServerConfig.tenantId}/v1/authorizations/{id}/email-authentication-challenge`,
            id,
            body: {
              email: user.email,
              email_template: "authentication"
            },
          });
          console.log(challengeResponse.status);
          console.log(challengeResponse.data);

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

          const authenticationTransactionResponse = await get({
            url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${federationServerConfig.tenantId}/authentication-transactions?authorization_id=${id}`,
            headers: {
              Authorization: `Bearer ${accessToken}`
            }
          });
          console.log(authenticationTransactionResponse.data);
          const transactionId = authenticationTransactionResponse.data.list[0].id;

          const interactionResponse = await get({
            url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${federationServerConfig.tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
            headers: {
              Authorization: `Bearer ${accessToken}`
            }
          });
          console.log(interactionResponse.data);
          const verificationCode = interactionResponse.data.payload.verification_code;

          const verificationResponse = await postAuthentication({
            endpoint: `${backendUrl}/${federationServerConfig.tenantId}/v1/authorizations/{id}/email-authentication`,
            id,
            body: {
              verification_code: verificationCode,
            }
          });

          console.log(verificationResponse.status);
          console.log(verificationResponse.data);
        };

        const viewResponse = await get({
          url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/view-data`,
        });
        console.log(JSON.stringify(viewResponse.data, null, 2));
        expect(viewResponse.status).toBe(200);
        expect(viewResponse.data.available_federations).not.toBeNull();

        const federationSetting = viewResponse.data.available_federations.find(federation => federation.auto_selected);
        console.log(federationSetting);

        const { params } = await requestFederation({
          url: backendUrl,
          authSessionId: id,
          authSessionTenantId: serverConfig.tenantId,
          type: federationSetting.type,
          providerName: federationSetting.sso_provider,
          federationTenantId: federationServerConfig.tenantId,
          user: registrationUser,
          interaction: federationInteraction,
        });
        console.log(params);

        const federationCallbackResponse = await post({
          url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/federations/oidc/callback`,
          body: params.toString()
        });
        console.log(federationCallbackResponse.data);
        expect(federationCallbackResponse.status).toBe(200);

      };

      const { authorizationResponse } = await requestAuthorizations({
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

      const jwksResponse = await get({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/jwks`
      });

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.id_token,
        jwks: jwksResponse.data
      });
      console.log(decodedIdToken);
      registeredUser = decodedIdToken.payload;

      const siginInteraction = async (id, user) => {

        const viewResponse = await get({
          url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/view-data`,
        });
        console.log(JSON.stringify(viewResponse.data, null, 2));
        expect(viewResponse.status).toBe(200);
        expect(viewResponse.data.available_federations).not.toBeNull();

        const federationSetting = viewResponse.data.available_federations.find(federation => federation.auto_selected);
        console.log(federationSetting);

        const challengeResponse = await postAuthentication({
          endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/{id}/email-authentication-challenge`,
          id,
          body: {
            email: user.email,
            email_template: "authentication"
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);

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

        const authenticationTransactionResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-transactions?authorization_id=${id}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(authenticationTransactionResponse.data);
        const transactionId = authenticationTransactionResponse.data.list[0].id;

        const interactionResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(interactionResponse.data);
        const verificationCode = interactionResponse.data.payload.verification_code;

        const verificationResponse = await postAuthentication({
          endpoint: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/{id}/email-authentication`,
          id,
          body: {
            verification_code: verificationCode,
          }
        });

        console.log(verificationResponse.status);
        console.log(verificationResponse.data);
        registeredUser = verificationResponse.data.user;
      };

      const { authorizationResponse: signinAuthorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: "openid profile phone email claims:ex_sub claims:status " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        customParams: {
          organizationId: "123",
          organizationName: "test",
        },
        user: registrationUser,
        interaction: siginInteraction,
      });
      console.log(signinAuthorizationResponse);
      expect(signinAuthorizationResponse.code).not.toBeNull();

      const signinTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: signinAuthorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log(signinTokenResponse.data);
      expect(signinTokenResponse.data).toHaveProperty("id_token");

      const accessToken = signinTokenResponse.data.access_token;

      const decodedAccessToken = verifyAndDecodeJwt({
        jwt: accessToken,
        jwks: jwksResponse.data
      });
      console.log(JSON.stringify(decodedAccessToken, null, 2));
      expect(decodedAccessToken.payload.sub).toEqual(registeredUser.sub);
      expect(decodedAccessToken.payload.ex_sub).toEqual(registeredUser.external_user_id);
      expect(decodedAccessToken.payload.status).toEqual("INITIALIZED");

      let userinfoResponse = await getUserinfo({
        endpoint: serverConfig.userinfoEndpoint,
        authorizationHeader: {
          "Authorization": `Bearer ${accessToken}`
        }
      });
      console.log(JSON.stringify(userinfoResponse.data, null, 2));
      expect(userinfoResponse.status).toBe(200);

      console.log(registeredUser);
      expect(userinfoResponse.data.sub).toEqual(registeredUser.sub);
      expect(userinfoResponse.data.status).toEqual("REGISTERED");

    });

  });

  describe("error pattern", () => {

    it("oauth-extension signup - deny", async () => {
      const registrationUser = {
        email: faker.internet.email(),
        name: faker.person.fullName(),
        zoneinfo: "Asia/Tokyo",
        locale: "ja-JP",
        phone_number: faker.phone.number("090-####-####"),
      };

      const interaction = async (id, user) => {

        const federationInteraction = async (id, user) => {

        };

        const viewResponse = await get({
          url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/view-data`,
        });
        console.log(JSON.stringify(viewResponse.data, null, 2));
        expect(viewResponse.status).toBe(200);
        expect(viewResponse.data.available_federations).not.toBeNull();

        const federationSetting = viewResponse.data.available_federations.find(federation => federation.auto_selected);
        console.log(federationSetting);

        const { result } = await requestFederation({
          url: backendUrl,
          authSessionId: id,
          authSessionTenantId: serverConfig.tenantId,
          type: federationSetting.type,
          providerName: federationSetting.sso_provider,
          federationTenantId: federationServerConfig.tenantId,
          user: registrationUser,
          interaction: federationInteraction,
          action: "deny"
        });
        console.log(result);

      };

      const { authorizationResponse } = await requestAuthorizations({
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
        interaction,
        action: "deny"
      });

      expect(authorizationResponse.error).not.toBeNull();
    });

    it("callback is error", async () => {
      const registrationUser = {
        email: faker.internet.email(),
        name: faker.person.fullName(),
        zoneinfo: "Asia/Tokyo",
        locale: "ja-JP",
        phone_number: faker.phone.number("090-####-####"),
      };

      const interaction = async (id, user) => {

        const federationInteraction = async (id, user) => {

        };

        const viewResponse = await get({
          url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/view-data`,
        });
        console.log(JSON.stringify(viewResponse.data, null, 2));
        expect(viewResponse.status).toBe(200);
        expect(viewResponse.data.available_federations).not.toBeNull();

        const federationSetting = viewResponse.data.available_federations.find(federation => federation.auto_selected);
        console.log(federationSetting);

        const { params } = await requestFederation({
          url: backendUrl,
          authSessionId: id,
          authSessionTenantId: serverConfig.tenantId,
          type: federationSetting.type,
          providerName: federationSetting.sso_provider,
          federationTenantId: federationServerConfig.tenantId,
          user: registrationUser,
          interaction: federationInteraction,
          action: "deny"
        });

        console.log(params);

        const federationCallbackResponse = await post({
          url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/federations/oidc/callback`,
          body: params.toString()
        });
        console.log(federationCallbackResponse.data);
        expect(federationCallbackResponse.status).toBe(400);

      };

      const { authorizationResponse } = await requestAuthorizations({
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
        interaction,
        action: "deny"
      });

      expect(authorizationResponse.error).not.toBeNull();
    });

    it("callback is invalid format", async () => {
      const registrationUser = {
        email: faker.internet.email(),
        name: faker.person.fullName(),
        zoneinfo: "Asia/Tokyo",
        locale: "ja-JP",
        phone_number: faker.phone.number("090-####-####"),
      };

      const interaction = async (id, user) => {

        const federationInteraction = async (id, user) => {

        };

        const viewResponse = await get({
          url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/view-data`,
        });
        console.log(JSON.stringify(viewResponse.data, null, 2));
        expect(viewResponse.status).toBe(200);
        expect(viewResponse.data.available_federations).not.toBeNull();

        const federationSetting = viewResponse.data.available_federations.find(federation => federation.auto_selected);
        console.log(federationSetting);

        const { params } = await requestFederation({
          url: backendUrl,
          authSessionId: id,
          authSessionTenantId: serverConfig.tenantId,
          type: federationSetting.type,
          providerName: federationSetting.sso_provider,
          federationTenantId: federationServerConfig.tenantId,
          user: registrationUser,
          interaction: federationInteraction,
          action: "deny"
        });

        console.log(params);

        const federationCallbackResponse = await post({
          url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/federations/oidc/callback`,
          body: new URLSearchParams({
            "tenant_id": "1234",
            "state": "invalid_format"
          }).toString()
        });
        console.log(federationCallbackResponse.data);
        expect(federationCallbackResponse.status).toBe(400);

      };

      const { authorizationResponse } = await requestAuthorizations({
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
        interaction,
        action: "deny"
      });

      expect(authorizationResponse.error).not.toBeNull();
    });

  });
});