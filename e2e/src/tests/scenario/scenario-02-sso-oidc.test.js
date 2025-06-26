import { describe, expect, it } from "@jest/globals";
import { backendUrl, clientSecretPostClient, federationServerConfig, serverConfig } from "../testConfig";
import { faker } from "@faker-js/faker";
import { postAuthentication, requestToken } from "../../api/oauthClient";
import { get, post } from "../../lib/http";
import { requestFederation } from "../../oauth/federation";
import { requestAuthorizations } from "../../oauth/request";

describe("sso oidc", () => {


  describe("success pattern", () => {

    it("oauth-extension signup - signin (email)", async () => {
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

          const authenticationTransactionResponse = await get({
            url: `${backendUrl}/${federationServerConfig.tenantId}/v1/authentications?authorization_id=${id}`,
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
            url: `${backendUrl}/v1/management/tenants/${federationServerConfig.tenantId}/authentication-interactions/${transactionId}/email`,
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
          registeredUser = user;
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
          user: {
            email: faker.internet.email(),
            name: faker.person.fullName(),
            zoneinfo: "Asia/Tokyo",
            locale: "ja-JP",
            phone_number: faker.phone.number("090-####-####"),
          },
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

      const siginInteraction = async (id, user) => {

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

          const authenticationTransactionResponse = await get({
            url: `${backendUrl}/${federationServerConfig.tenantId}/v1/authentications?authorization_id=${id}`,
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
            url: `${backendUrl}/v1/management/tenants/${federationServerConfig.tenantId}/authentication-interactions/${transactionId}/email`,
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
          registeredUser = user;
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
          user: registeredUser,
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

      const { authorizationResponse: signinAuthorizationResponse } = await requestAuthorizations({
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
        user: registeredUser,
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

    });

    it("oauth-extension signup - signin (email + sms)", async () => {
      let registeredUser;

      const interaction = async (id, user) => {

        const federationInteraction = async (id, user) => {
          let challengeResponse = await postAuthentication({
            endpoint: `${backendUrl}/${federationServerConfig.tenantId}/v1/authorizations/{id}/email-authentication-challenge`,
            id,
            body: {
              email: user.email,
              email_template: "authentication"
            },
          });
          console.log(challengeResponse.status);
          console.log(challengeResponse.data);

          let authenticationTransactionResponse = await get({
            url: `${backendUrl}/${federationServerConfig.tenantId}/v1/authentications?authorization_id=${id}`,
          });
          console.log(authenticationTransactionResponse.data);
          let transactionId = authenticationTransactionResponse.data.list[0].id;

          let adminTokenResponse = await requestToken({
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
          let accessToken = adminTokenResponse.data.access_token;

          let interactionResponse = await get({
            url: `${backendUrl}/v1/management/tenants/${federationServerConfig.tenantId}/authentication-interactions/${transactionId}/email`,
            headers: {
              Authorization: `Bearer ${accessToken}`
            }
          });
          console.log(interactionResponse.data);
          let verificationCode = interactionResponse.data.payload.verification_code;

          let verificationResponse = await postAuthentication({
            endpoint: `${backendUrl}/${federationServerConfig.tenantId}/v1/authorizations/{id}/email-authentication`,
            id,
            body: {
              verification_code: verificationCode,
            }
          });

          console.log(verificationResponse.status);
          console.log(verificationResponse.data);

          challengeResponse = await postAuthentication({
            endpoint: `${backendUrl}/${federationServerConfig.tenantId}/v1/authorizations/{id}/sms-authentication-challenge`,
            id,
            body: {
              phone_number: user.phone_number,
              sms_template: "authentication"
            },
          });
          console.log(challengeResponse.status);
          console.log(challengeResponse.data);

          authenticationTransactionResponse = await get({
            url: `${backendUrl}/${federationServerConfig.tenantId}/v1/authentications?authorization_id=${id}`,
          });
          console.log(authenticationTransactionResponse.data);
          transactionId = authenticationTransactionResponse.data.list[0].id;

          adminTokenResponse = await requestToken({
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
          accessToken = adminTokenResponse.data.access_token;

          interactionResponse = await get({
            url: `${backendUrl}/v1/management/tenants/${federationServerConfig.tenantId}/authentication-interactions/${transactionId}/sms`,
            headers: {
              Authorization: `Bearer ${accessToken}`
            }
          });
          console.log(interactionResponse.data);
          verificationCode = interactionResponse.data.payload.verification_code;

          verificationResponse = await postAuthentication({
            endpoint: `${backendUrl}/${federationServerConfig.tenantId}/v1/authorizations/{id}/sms-authentication`,
            id,
            body: {
              verification_code: verificationCode,
            }
          });

          console.log(verificationResponse.status);
          console.log(verificationResponse.data);

          registeredUser = user;
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
          user: {
            email: faker.internet.email(),
            name: faker.person.fullName(),
            zoneinfo: "Asia/Tokyo",
            locale: "ja-JP",
            phone_number: faker.phone.number("090-####-####"),
          },
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

      const siginInteraction = async (id, user) => {

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

          const authenticationTransactionResponse = await get({
            url: `${backendUrl}/${federationServerConfig.tenantId}/v1/authentications?authorization_id=${id}`,
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
            url: `${backendUrl}/v1/management/tenants/${federationServerConfig.tenantId}/authentication-interactions/${transactionId}/email`,
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
          registeredUser = user;
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
          user: registeredUser,
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

      const { authorizationResponse: signinAuthorizationResponse } = await requestAuthorizations({
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
        user: registeredUser,
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

    });

  });
});