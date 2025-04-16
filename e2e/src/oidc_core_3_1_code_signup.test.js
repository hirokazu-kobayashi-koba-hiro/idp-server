import { describe, expect, it } from "@jest/globals";

import {  requestToken } from "./api/oauthClient";
import {
  clientSecretPostClient,
  serverConfig,
} from "./testConfig";
import { requestAuthorizationsForSignup } from "./oauth/signup";
import { faker } from "@faker-js/faker";

describe("OpenID Connect Core 1.0 incorporating errata set 1 code", () => {

  it("signup", async () => {
    const email = faker.internet.email();
    const password = faker.internet.password(12, true); // 長さ12, 記号含む
    const username = faker.person.fullName();

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
        email: email,
        password: password,
        username: username
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

});


