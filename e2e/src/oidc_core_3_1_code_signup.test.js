import { describe, expect, it, xit } from "@jest/globals";

import {  requestToken } from "./api/oauthClient";
import {
  clientSecretPostClient,
  serverConfig,
} from "./testConfig";
import { requestAuthorizationsForSignup } from "./oauth/signup";
import { faker } from "@faker-js/faker";

describe("OpenID Connect Core 1.0 incorporating errata set 1 code", () => {

  xit("signup", async () => {

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
      mfa: "email",
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

  xit("signup webauthn", async () => {

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
      mfa: "webauthn",
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


