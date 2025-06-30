import { describe, expect, it, xit } from "@jest/globals";

import {
  getUserinfo, postAuthentication,
  postAuthenticationDeviceInteraction, requestToken
} from "../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient, federationServerConfig,
  serverConfig
} from "../testConfig";
import { get, post, postWithJson } from "../../lib/http";
import { requestFederation } from "../../oauth/federation";
import { faker } from "@faker-js/faker";
import { requestAuthorizations } from "../../oauth/request";
import { verifyAndDecodeJwt } from "../../lib/jose";

describe("user - mfa registration", () => {

  it("fido-uaf", async () => {
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
        url: `${backendUrl}/${federationServerConfig.tenantId}/v1/authorizations/federations/oidc/callback`,
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

      const authenticationTransactionResponse = await get({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/authentications?authorization_id=${id}`,
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
        url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/email`,
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
      scope: "openid profile phone email" + clientSecretPostClient.scope,
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

    let userinfoResponse = await getUserinfo({
      endpoint: serverConfig.userinfoEndpoint,
      authorizationHeader: {
        "Authorization": `Bearer ${accessToken}`
      }
    });
    console.log(JSON.stringify(userinfoResponse.data, null, 2));
    expect(userinfoResponse.status).toBe(200);

    let mfaRegistrationResponse =
      await postWithJson({
        url: serverConfig.usersEndpoint + "/mfa-registration",
        body: {
          "flow": "fido-uaf-registration",
          "platform": "Android",
          "os": "Android15",
          "model": "galaxy z fold 6",
          "notification_channel": "fcm",
          "notification_token": "test token",
          "preferred_for_notification": true
        },
        headers: {
          "Authorization": `Bearer ${accessToken}`
        }
      });
    console.log(mfaRegistrationResponse.data);
    expect(mfaRegistrationResponse.status).toBe(200);

    let authenticationResponse;
    const transactionId = mfaRegistrationResponse.data.id;
    authenticationResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      id: transactionId,
      interactionType: "fido-uaf-registration-challenge",
      body: {
        username: serverConfig.ciba.username,
        password: serverConfig.ciba.userCode,
      }
    });
    expect(authenticationResponse.status).toBe(200);

    const fidoUafFacetsResponse = await get({
      url: serverConfig.fidoUafFacetsEndpoint,
      headers: {
        "Content-Type": "application/json",
      }
    });
    console.log(fidoUafFacetsResponse.data);
    expect(fidoUafFacetsResponse.status).toBe(200);

    authenticationResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      id: transactionId,
      interactionType: "fido-uaf-registration",
      body: {
        username: serverConfig.ciba.username,
        password: serverConfig.ciba.userCode,
      }
    });
    expect(authenticationResponse.status).toBe(200);

    userinfoResponse = await getUserinfo({
      endpoint: serverConfig.userinfoEndpoint,
      authorizationHeader: {
        "Authorization": `Bearer ${accessToken}`
      }
    });
    console.log(JSON.stringify(userinfoResponse.data, null, 2));
    expect(userinfoResponse.status).toBe(200);
    expect(userinfoResponse.data.sub).toEqual(registeredUser.sub);
    expect(userinfoResponse.data).toHaveProperty("authentication_devices");
    expect(userinfoResponse.data.authentication_devices.length).toBe(1);
    expect(userinfoResponse.data).toHaveProperty("mfa");

    const authenticationDeviceId = userinfoResponse.data.authentication_devices[0].id;

    mfaRegistrationResponse =
      await postWithJson({
        url: serverConfig.usersEndpoint + "/mfa-registration",
        body: {
          "flow": "fido-uaf-deregistration",
          "authentication_device_id": authenticationDeviceId
        },
        headers: {
          "Authorization": `Bearer ${accessToken}`
        }
      });
    console.log(mfaRegistrationResponse.data);
    expect(mfaRegistrationResponse.status).toBe(200);

    authenticationResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      id: transactionId,
      interactionType: "fido-uaf-deregistration",
      body: {
        authentication_device_id: authenticationDeviceId,
      }
    });
    expect(authenticationResponse.status).toBe(200);

    userinfoResponse = await getUserinfo({
      endpoint: serverConfig.userinfoEndpoint,
      authorizationHeader: {
        "Authorization": `Bearer ${accessToken}`
      }
    });
    console.log(JSON.stringify(userinfoResponse.data, null, 2));
    expect(userinfoResponse.status).toBe(200);
    console.log(registeredUser);
    expect(userinfoResponse.data.sub).toEqual(registeredUser.sub);
    expect(userinfoResponse.data).not.toHaveProperty("authentication_devices");
    expect(userinfoResponse.data).not.toHaveProperty("mfa");
  });


});
