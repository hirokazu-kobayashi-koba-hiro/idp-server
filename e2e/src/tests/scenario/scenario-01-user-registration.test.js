import { describe, expect, it } from "@jest/globals";
import { requestAuthorizationsForSignup } from "../../oauth/signup";
import { backendUrl, clientSecretPostClient, serverConfig } from "../testConfig";
import { faker } from "@faker-js/faker";
import { postAuthentication, requestToken } from "../../api/oauthClient";
import { get } from "../../lib/http";

describe("user registration", () => {


  describe("success pattern", () => {

    it("email-authentication", async () => {

      const interaction = async (id, user) => {
        const challengeResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "email-authentication-challenge",
          id,
          body: {
            email: user.email,
            email_template: "authentication"
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);

        const authenticationTransactionResponse = await get({
          url: serverConfig.authenticationEndpoint + `?authorization_id=${id}`,
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
          url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/authentication-interactions/${transactionId}/email`,
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
        user: {
          email: faker.internet.email(),
          name: faker.person.fullName(),
          zoneinfo: "Asia/Tokyo",
          locale: "ja-JP",
          phone_number: faker.phone.number("090-####-####"),
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
            email_template: "authentication"
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);

        const authenticationTransactionResponse = await get({
          url: serverConfig.authenticationEndpoint + `?authorization_id=${id}`,
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
          url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/authentication-interactions/${transactionId}/sms`,
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

    it("sms-authentication", async () => {

      const interaction = async (id, user) => {
        const challengeResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "sms-authentication-challenge",
          id,
          body: {
            phone_number: user.phone_number,
            sms_template: "authentication"
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);

        const authenticationTransactionResponse = await get({
          url: serverConfig.authenticationEndpoint + `?authorization_id=${id}`,
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
          url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/authentication-interactions/${transactionId}/sms`,
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
        user: {
          email: faker.internet.email(),
          name: faker.person.fullName(),
          zoneinfo: "Asia/Tokyo",
          locale: "ja-JP",
          phone_number: faker.phone.number("090-####-####"),
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
            sms_template: "authentication"
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);

        const authenticationTransactionResponse = await get({
          url: serverConfig.authenticationEndpoint + `?authorization_id=${id}`,
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
          url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/authentication-interactions/${transactionId}/sms`,
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

    it("email + sms authentication", async () => {

      const interaction = async (id, user) => {

        let challengeResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "email-authentication-challenge",
          id,
          body: {
            email: user.email,
            email_template: "authentication"
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);

        let authenticationTransactionResponse = await get({
          url: serverConfig.authenticationEndpoint + `?authorization_id=${id}`,
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
          url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/authentication-interactions/${transactionId}/email`,
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
            sms_template: "authentication"
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);

        authenticationTransactionResponse = await get({
          url: serverConfig.authenticationEndpoint + `?authorization_id=${id}`,
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
          url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/authentication-interactions/${transactionId}/sms`,
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
        user: {
          email: faker.internet.email(),
          name: faker.person.fullName(),
          phone_number: faker.phone.number("090-####-####"),
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

    it("sms + email authentication", async () => {

      const interaction = async (id, user) => {

        let challengeResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "sms-authentication-challenge",
          id,
          body: {
            phone_number: user.phone_number,
            sms_template: "authentication"
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);

        let authenticationTransactionResponse = await get({
          url: serverConfig.authenticationEndpoint + `?authorization_id=${id}`,
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
          url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/authentication-interactions/${transactionId}/sms`,
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
            email_template: "authentication"
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);

        authenticationTransactionResponse = await get({
          url: serverConfig.authenticationEndpoint + `?authorization_id=${id}`,
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
          url: `${backendUrl}/v1/management/tenants/67e7eae6-62b0-4500-9eff-87459f63fc66/authentication-interactions/${transactionId}/email`,
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
        user: {
          email: faker.internet.email(),
          name: faker.person.fullName(),
          phone_number: faker.phone.number("090-####-####"),
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