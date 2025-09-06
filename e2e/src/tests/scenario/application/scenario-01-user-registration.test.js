import { describe, expect, it } from "@jest/globals";
import { backendUrl, clientSecretPostClient, serverConfig } from "../../testConfig";
import { faker } from "@faker-js/faker";
import { postAuthentication, requestToken } from "../../../api/oauthClient";
import { get } from "../../../lib/http";
import { requestAuthorizations } from "../../../oauth/request";
import { generateRandomNumber } from "../../../lib/util";

describe("user registration", () => {


  describe("success pattern", () => {

    it("email-authentication", async () => {

      const interaction = async (id, user) => {
        const challengeResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "email-authentication-challenge",
          id,
          body: {
            email: user.email,
            template: "authentication"
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);
        expect(challengeResponse.status).toBe(200);

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
          url: serverConfig.authenticationEndpoint + `?authorization_id=${id}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(authenticationTransactionResponse.data);
        const transactionId = authenticationTransactionResponse.data.list[0].id;

        const interactionResponse = await get({
          url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(interactionResponse.data);
        const verificationCode = interactionResponse.data.payload.verification_code;

        const verificationResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "email-authentication",
          id,
          body: {
            verification_code: verificationCode,
          }
        });

        console.log(verificationResponse.status);
        console.log(verificationResponse.data);
        expect(verificationResponse.status).toBe(200);
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
        user: {
          email: faker.internet.email(),
          name: faker.person.fullName(),
          zoneinfo: "Asia/Tokyo",
          locale: "ja-JP",
          phone_number: faker.phone.number("090-####-####"),
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
    });

    it("initial-registration + email-authentication", async () => {

      const interaction = async (id, user) => {
        const initialResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "initial-registration",
          id,
          body: {
            ...user
          }
        });

        if (initialResponse.status >= 400) {
          console.error(initialResponse.data);
        }

        const challengeResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "sms-authentication-challenge",
          id,
          body: {
            phone_number: user.phone_number,
            template: "authentication"
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
          url: serverConfig.authenticationEndpoint + `?authorization_id=${id}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(authenticationTransactionResponse.data);
        const transactionId = authenticationTransactionResponse.data.list[0].id;

        const interactionResponse = await get({
          url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/sms-authentication-challenge`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(interactionResponse.data);
        const verificationCode = interactionResponse.data.payload.verification_code;

        const verificationResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "sms-authentication",
          id,
          body: {
            verification_code: verificationCode,
          }
        });

        console.log(verificationResponse.status);
        console.log(verificationResponse.data);
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
        user: {
          email: faker.internet.email(),
          password: faker.internet.password(
            12,
            false,
            "^(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()]).+$") + "!",
          name: faker.person.fullName(),
          given_name: faker.person.firstName(),
          family_name: faker.person.lastName(),
          middle_name: faker.person.middleName(),
          nickname: faker.person.lastName(),
          preferred_username: faker.person.lastName(),
          profile: faker.internet.url(),
          picture: faker.internet.url(),
          website: faker.internet.url(),
          gender: faker.person.gender(),
          birthdate: faker.date.birthdate({ min: 1, max: 100, mode: "age" }).toISOString().split("T")[0],
          zoneinfo: "Asia/Tokyo",
          locale: "ja-JP",
          phone_number: faker.phone.number("090-####-####"),
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
    });

    it("sms-authentication", async () => {

      const interaction = async (id, user) => {
        const challengeResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "sms-authentication-challenge",
          id,
          body: {
            phone_number: user.phone_number,
            template: "authentication"
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);
        expect(challengeResponse.status).toBe(200);

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
          url: serverConfig.authenticationEndpoint + `?authorization_id=${id}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(authenticationTransactionResponse.data);
        const transactionId = authenticationTransactionResponse.data.list[0].id;

        const interactionResponse = await get({
          url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/sms-authentication-challenge`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(interactionResponse.data);
        const verificationCode = interactionResponse.data.payload.verification_code;

        const verificationResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "sms-authentication",
          id,
          body: {
            verification_code: verificationCode,
          }
        });

        console.log(verificationResponse.status);
        console.log(verificationResponse.data);
        expect(verificationResponse.status).toBe(200);
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
        user: {
          email: faker.internet.email(),
          name: faker.person.fullName(),
          zoneinfo: "Asia/Tokyo",
          locale: "ja-JP",
          phone_number: faker.phone.number("090-####-####"),
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
    });

    it("initial-registration + sms-authentication", async () => {

      const interaction = async (id, user) => {
        const initialResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "initial-registration",
          id,
          body: {
            ...user
          }
        });

        if (initialResponse.status >= 400) {
          console.error(initialResponse.data);
        }

        const challengeResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "sms-authentication-challenge",
          id,
          body: {
            phone_number: user.phone_number,
            template: "authentication"
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
          url: serverConfig.authenticationEndpoint + `?authorization_id=${id}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(authenticationTransactionResponse.data);
        const transactionId = authenticationTransactionResponse.data.list[0].id;

        const interactionResponse = await get({
          url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/sms-authentication-challenge`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(interactionResponse.data);
        const verificationCode = interactionResponse.data.payload.verification_code;

        const verificationResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "sms-authentication",
          id,
          body: {
            verification_code: verificationCode,
          }
        });

        console.log(verificationResponse.status);
        console.log(verificationResponse.data);
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
        user: {
          email: faker.internet.email(),
          password: faker.internet.password(
            12,
            false,
            "^(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()]).+$") + "!",
          name: faker.person.fullName(),
          given_name: faker.person.firstName(),
          family_name: faker.person.lastName(),
          middle_name: faker.person.middleName(),
          nickname: faker.person.lastName(),
          preferred_username: faker.person.lastName(),
          profile: faker.internet.url(),
          picture: faker.internet.url(),
          website: faker.internet.url(),
          gender: faker.person.gender(),
          birthdate: faker.date.birthdate({ min: 1, max: 100, mode: "age" }).toISOString().split("T")[0],
          zoneinfo: "Asia/Tokyo",
          locale: "ja-JP",
          phone_number: faker.phone.number("090-####-####"),
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
    });

    it("email + sms authentication", async () => {

      const interaction = async (id, user) => {

        let challengeResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "email-authentication-challenge",
          id,
          body: {
            email: user.email,
            template: "authentication"
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);

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

        let authenticationTransactionResponse = await get({
          url: serverConfig.authenticationEndpoint + `?authorization_id=${id}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(authenticationTransactionResponse.data);
        let transactionId = authenticationTransactionResponse.data.list[0].id;

        let interactionResponse = await get({
          url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(interactionResponse.data);
        let verificationCode = interactionResponse.data.payload.verification_code;

        let verificationResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "email-authentication",
          id,
          body: {
            verification_code: verificationCode,
          }
        });

        console.log(verificationResponse.status);
        console.log(verificationResponse.data);

        challengeResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "sms-authentication-challenge",
          id,
          body: {
            phone_number: user.phone_number,
            template: "authentication"
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);

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

        authenticationTransactionResponse = await get({
          url: serverConfig.authenticationEndpoint + `?authorization_id=${id}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(authenticationTransactionResponse.data);
        transactionId = authenticationTransactionResponse.data.list[0].id;

        interactionResponse = await get({
          url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/sms-authentication-challenge`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(interactionResponse.data);
        verificationCode = interactionResponse.data.payload.verification_code;

        verificationResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "sms-authentication",
          id,
          body: {
            verification_code: verificationCode,
          }
        });

        console.log(verificationResponse.status);
        console.log(verificationResponse.data);
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
        user: {
          email: faker.internet.email(),
          name: faker.person.fullName(),
          phone_number: faker.phone.number("090-####-####"),
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
    });

    it("sms + email authentication", async () => {

      const interaction = async (id, user) => {

        let challengeResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "sms-authentication-challenge",
          id,
          body: {
            phone_number: user.phone_number,
            template: "authentication"
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);

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

        let authenticationTransactionResponse = await get({
          url: serverConfig.authenticationEndpoint + `?authorization_id=${id}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(authenticationTransactionResponse.data);
        let transactionId = authenticationTransactionResponse.data.list[0].id;

        let interactionResponse = await get({
          url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/sms-authentication-challenge`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(interactionResponse.data);
        let verificationCode = interactionResponse.data.payload.verification_code;

        let verificationResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "sms-authentication",
          id,
          body: {
            verification_code: verificationCode,
          }
        });

        console.log(verificationResponse.status);
        console.log(verificationResponse.data);

        challengeResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "email-authentication-challenge",
          id,
          body: {
            email: user.email,
            template: "authentication"
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);

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

        authenticationTransactionResponse = await get({
          url: serverConfig.authenticationEndpoint + `?authorization_id=${id}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(authenticationTransactionResponse.data);
        transactionId = authenticationTransactionResponse.data.list[0].id;

        interactionResponse = await get({
          url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(interactionResponse.data);
        verificationCode = interactionResponse.data.payload.verification_code;

        verificationResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "email-authentication",
          id,
          body: {
            verification_code: verificationCode,
          }
        });

        console.log(verificationResponse.status);
        console.log(verificationResponse.data);
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
        user: {
          email: faker.internet.email(),
          name: faker.person.fullName(),
          phone_number: faker.phone.number("090-####-####"),
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
    });

    it("external-token", async () => {

      const interaction = async (id, user) => {

        let challengeResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "external-token",
          id,
          body: {
            access_token: "access_token",
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);
        expect(challengeResponse.status).toBe(200);

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
        user: {
          email: faker.internet.email(),
          name: faker.person.fullName(),
          phone_number: faker.phone.number("090-####-####"),
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
    });

  });

  describe("error pattern", () => {

    it("email-authentication failure exceed 5 count", async () => {

      const interaction = async (id, user) => {
        const challengeResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "email-authentication-challenge",
          id,
          body: {
            email: user.email,
            template: "authentication"
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);
        expect(challengeResponse.status).toBe(200);

        for (let i = 0; i < 5; i++) {
          const verificationCode = generateRandomNumber(6);

          const verificationResponse = await postAuthentication({
            endpoint: serverConfig.authorizationIdEndpoint + "email-authentication",
            id,
            body: {
              verification_code: verificationCode,
            }
          });

          console.log(verificationResponse.status);
          console.log(verificationResponse.data);
          expect(verificationResponse.status).toBe(400);
          expect(verificationResponse.data.error).toContain("invalid_request");
        }

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
          url: serverConfig.authenticationEndpoint + `?authorization_id=${id}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(authenticationTransactionResponse.data);
        const transactionId = authenticationTransactionResponse.data.list[0].id;

        const interactionResponse = await get({
          url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(interactionResponse.data);
        const verificationCode = interactionResponse.data.payload.verification_code;

        const verificationResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "email-authentication",
          id,
          body: {
            verification_code: verificationCode,
          }
        });

        console.log(verificationResponse.status);
        console.log(verificationResponse.data);
        expect(verificationResponse.status).toBe(400);
        expect(verificationResponse.data.error).toEqual("invalid_request");
        expect(verificationResponse.data.error_description).toEqual("email challenge is reached limited to 5 attempts");
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
        user: {
          email: faker.internet.email(),
          name: faker.person.fullName(),
          zoneinfo: "Asia/Tokyo",
          locale: "ja-JP",
          phone_number: faker.phone.number("090-####-####"),
        },
        interaction,
        action: "deny",
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.error).not.toBeNull();

    });
  });

});