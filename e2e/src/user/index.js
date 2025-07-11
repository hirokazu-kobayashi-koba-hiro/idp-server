import { expect } from "@jest/globals";
import { faker } from "@faker-js/faker";
import { backendUrl, clientSecretPostClient, serverConfig } from "../tests/testConfig";
import { postAuthentication, requestToken } from "../api/oauthClient";
import { get, post } from "../lib/http";
import { requestFederation } from "../oauth/federation";
import { requestAuthorizations } from "../oauth/request";
import { verifyAndDecodeJwt } from "../lib/jose";
import { generatePassword } from "../lib/util";


export const createFederatedUser = async ({
  serverConfig,
  federationServerConfig,
  client,
  adminClient = clientSecretPostClient
}) => {

  const registrationUser = {
    email: faker.internet.email(),
    password: generatePassword(12),
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
        url: `${backendUrl}/v1/management/tenants/${federationServerConfig.tenantId}/authentication-transactions?authorization_id=${id}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log(authenticationTransactionResponse.data);
      expect(authenticationTransactionResponse.status).toBe(200);
      const transactionId = authenticationTransactionResponse.data.list[0].id;

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
    clientId: client.clientId,
    responseType: "code",
    state: "aiueo",
    scope: "openid profile phone email" + client.scope,
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
    accessToken: tokenResponse.data.access_token
  };
};
