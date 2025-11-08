import { expect } from "@jest/globals";
import { faker } from "@faker-js/faker";
import { backendUrl, clientSecretPostClient, serverConfig } from "../tests/testConfig";
import { postAuthentication, postAuthenticationDeviceInteraction, requestToken } from "../api/oauthClient";
import { get, post, postWithJson } from "../lib/http";
import { requestFederation } from "../oauth/federation";
import { requestAuthorizations } from "../oauth/request";
import { verifyAndDecodeJwt } from "../lib/jose";
import { generatePassword } from "../lib/util";
import { generateRandomUser, generateValidCredentialFromChallenge } from "../lib/fido/fido2";


export const createFederatedUser = async ({
  serverConfig,
  federationServerConfig,
  client,
  adminClient = clientSecretPostClient,
  scope = "openid profile phone email " + client.scope
}) => {

  const registrationUser = {
    email: faker.internet.email(),
    password: generatePassword(12),
    name: faker.person.fullName(),
    given_name: faker.person.firstName(),
    family_name: faker.person.lastName(),
    middle_name: faker.person.middleName(),
    nickname: faker.person.lastName(),
    profile: faker.internet.url(),
    picture: faker.internet.url(),
    website: faker.internet.url(),
    gender: faker.person.gender(),
    birthdate: faker.date.birthdate({ min: 1, max: 100, mode: "age" }).toISOString().split("T")[0],
    zoneinfo: "Asia/Tokyo",
    locale: "ja-JP",
    phone_number: faker.phone.number("090-####-####"),
  };

  const interaction = async (id, user) => {

    const federationInteraction = async (id, user) => {

      const initialResponse = await postAuthentication({
        endpoint: `${backendUrl}/${federationServerConfig.tenantId}/v1/authorizations/{id}/initial-registration`,
        id,
        body: user,
      });

      console.log(initialResponse.data);
      expect(initialResponse.status).toBe(200);

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
      expect(challengeResponse.status).toBe(200);

      const adminTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
        scope: adminClient.scope,
        clientId: adminClient.clientId,
        clientSecret: adminClient.clientSecret
      });
      console.log(adminTokenResponse.data);
      expect(adminTokenResponse.status).toBe(200);
      const accessToken = adminTokenResponse.data.access_token;

      const authenticationTransactionResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${federationServerConfig.organizationId}/tenants/${federationServerConfig.tenantId}/authentication-transactions?authorization_id=${id}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log(authenticationTransactionResponse.data);
      expect(authenticationTransactionResponse.status).toBe(200);
      const transactionId = authenticationTransactionResponse.data.list[0].id;

      const interactionResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${federationServerConfig.organizationId}/tenants/${federationServerConfig.tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log(interactionResponse.data);
      expect(interactionResponse.status).toBe(200);
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
      expect(verificationResponse.status).toBe(200);
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
    clientId: client.clientId,
    responseType: "code",
    state: "aiueo",
    scope: scope,
    redirectUri: client.redirectUri,
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
    redirectUri: client.redirectUri,
    clientId: client.clientId,
    clientSecret: client.clientSecret,
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
  return  {
    user: decodedIdToken.payload,
    accessToken: tokenResponse.data.access_token,
    refreshToken: tokenResponse.data.refresh_token
  };
};

export const registerFidoUaf = async ({
  accessToken,
}) => {

    let mfaRegistrationResponse =
      await postWithJson({
        url: serverConfig.resourceOwnerEndpoint + "/mfa/fido-uaf-registration",
        body: {
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

    const transactionId = mfaRegistrationResponse.data.id;

    let authenticationResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      id: transactionId,
      interactionType: "fido-uaf-registration-challenge",
      body: {
        username: serverConfig.ciba.username,
        password: serverConfig.ciba.userCode,
      }
    });
    console.log(authenticationResponse.data);
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
    expect(authenticationResponse.data).toHaveProperty("device_id");

    return {
      authenticationDeviceId: authenticationResponse.data.device_id
    };
};

export const registerFido2 = async ({
  accessToken,
}) => {

  let mfaRegistrationResponse =
    await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/me/mfa/fido2-registration`,
      body: {
        "app_name": "idp-server-app",
        "platform": "Android",
        "os": "Android15",
        "model": "galaxy z fold 6",
        "locale": "ja",
        "notification_channel": "fcm",
        "notification_token": "test token",
        "priority": 1,
        action: "reset"
      },
      headers: {
        "Authorization": `Bearer ${accessToken}`
      }
    });
  console.log(mfaRegistrationResponse.data);
  expect(mfaRegistrationResponse.status).toBe(200);

  const discoverableUser = generateRandomUser();
  const requestBody = {
    username: discoverableUser.username,
    displayName: discoverableUser.displayName,
    authenticatorSelection: {
      authenticatorAttachment: "platform",
      requireResidentKey: true,
      userVerification: "required"
    },
    attestation: "none",
    extensions: {
      credProps: true
    }
  };

  console.log(JSON.stringify(requestBody, null, 2));

  let authenticationResponse;
  let transactionId = mfaRegistrationResponse.data.id;
  authenticationResponse = await postWithJson({
    url: `${backendUrl}/${serverConfig.tenantId}/v1/authentications/${transactionId}/fido2-registration-challenge`,
    body: requestBody
  });
  console.log(JSON.stringify(authenticationResponse.data, null, 2));
  expect(authenticationResponse.status).toBe(200);

  const validCredential = generateValidCredentialFromChallenge(authenticationResponse.data);
  console.log(JSON.stringify(validCredential, null, 2));

  authenticationResponse = await postWithJson({
    url: `${backendUrl}/${serverConfig.tenantId}/v1/authentications/${transactionId}/fido2-registration`,
    body: validCredential
  });
  console.log(JSON.stringify(authenticationResponse.data, null, 2));
  expect(authenticationResponse.status).toBe(200);

  return {
    authenticationDeviceId: authenticationResponse.data.device_id,
    userId: authenticationResponse.data.id
  };
};