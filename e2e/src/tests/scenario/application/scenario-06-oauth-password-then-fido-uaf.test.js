import { describe, expect, it } from "@jest/globals";

import {
  getJwks,
  postAuthenticationDeviceInteraction,
  requestToken,
} from "../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
} from "../../testConfig";
import { verifyAndDecodeJwt } from "../../../lib/jose";
import { get, postWithJson } from "../../../lib/http";
import { registerFidoUaf } from "../../../user";
import { requestAuthorizations } from "../../../oauth/request";
import { faker } from "@faker-js/faker";

/**
 * Scenario: Authorization Code Flow without login_hint - Password + FIDO-UAF MFA
 *
 * Tests the MFA pattern where:
 * 1. User registers via initial-registration on the main tenant
 * 2. User registers a FIDO-UAF device
 * 3. New authorization flow WITHOUT login_hint
 * 4. Password authentication (1st factor) → user resolved
 * 5. FIDO-UAF authentication (2nd factor) via device
 * 6. authentication-status: success
 * 7. Authorize → tokens with amr containing both "password" and "fido-uaf"
 */
describe("scenario - oauth password then fido-uaf (no login_hint)", () => {

  it("should authenticate with password + FIDO-UAF MFA without login_hint", async () => {
    // Step 1: Create user via initial-registration
    console.log("\n=== Step 1: Create user via initial-registration ===");

    const userEmail = faker.internet.email();
    const userPassword = "TestPassword123!";
    const userName = faker.person.fullName();

    const { authorizationResponse: regAuthResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      state: `state_reg_${Date.now()}`,
      scope: "openid profile email claims:authentication_devices",
      redirectUri: clientSecretPostClient.redirectUri,
      prompt: "create",
      user: { email: userEmail, password: userPassword, name: userName },
      interaction: async (id) => {
        const regResponse = await postWithJson({
          url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/initial-registration`,
          body: { email: userEmail, password: userPassword, name: userName },
        });
        console.log("Registration response:", regResponse.status, regResponse.data);
        expect(regResponse.status).toBe(200);
      },
    });
    expect(regAuthResponse.code).not.toBeNull();
    console.log("User registered, authorization code obtained");

    // Exchange code for tokens to get access token for FIDO-UAF registration
    const regTokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "authorization_code",
      code: regAuthResponse.code,
      redirectUri: clientSecretPostClient.redirectUri,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(regTokenResponse.status).toBe(200);
    const userAccessToken = regTokenResponse.data.access_token;
    console.log("Access token obtained for user");

    // Get user sub from ID token
    const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
    const regIdToken = verifyAndDecodeJwt({
      jwt: regTokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    const userSub = regIdToken.payload.sub;
    console.log("User sub:", userSub);

    // Step 2: Register FIDO-UAF device
    console.log("\n=== Step 2: Register FIDO-UAF device ===");

    const { authenticationDeviceId } = await registerFidoUaf({ accessToken: userAccessToken });
    console.log("FIDO-UAF device registered:", authenticationDeviceId);

    // Step 3: Start new authorization WITHOUT login_hint
    console.log("\n=== Step 3: Start authorization without login_hint ===");

    const state = `state_mfa_${Date.now()}`;

    const authorizeResponse = await get({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations?` +
        new URLSearchParams({
          response_type: "code",
          client_id: clientSecretPostClient.clientId,
          redirect_uri: clientSecretPostClient.redirectUri,
          scope: "openid profile email",
          state: state,
        }).toString(),
      headers: {},
    });
    expect(authorizeResponse.status).toBe(302);

    const location = authorizeResponse.headers.location;
    const authId = new URL(location, backendUrl).searchParams.get("id");
    expect(authId).toBeDefined();
    console.log("Authorization started, authId:", authId);

    // Verify view-data does NOT contain login_hint
    const viewDataResponse = await get({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/view-data`,
      headers: {},
    });
    expect(viewDataResponse.status).toBe(200);
    expect(viewDataResponse.data.login_hint).toBeUndefined();
    console.log("view-data confirmed: no login_hint");

    // Step 4: Password authentication (1st factor)
    console.log("\n=== Step 4: Password authentication ===");

    const pwResponse = await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/password-authentication`,
      body: { username: userEmail, password: userPassword },
    });
    console.log("Password auth response:", pwResponse.status, pwResponse.data);
    expect(pwResponse.status).toBe(200);

    // Verify authentication-status after password auth
    const statusAfterPw = await get({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/authentication-status`,
      headers: {},
    });
    expect(statusAfterPw.status).toBe(200);
    console.log("Status after password:", statusAfterPw.data.status);
    console.log("Authentication methods:", statusAfterPw.data.authentication_methods);

    // Step 5: Get authentication transaction for FIDO-UAF
    console.log("\n=== Step 5: FIDO-UAF authentication (2nd factor) ===");

    // Get admin token
    const adminTokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "password",
      username: serverConfig.oauth.username,
      password: serverConfig.oauth.password,
      scope: clientSecretPostClient.scope,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(adminTokenResponse.status).toBe(200);

    const txListResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-transactions?authorization_id=${authId}`,
      headers: {
        Authorization: `Bearer ${adminTokenResponse.data.access_token}`,
      },
    });
    expect(txListResponse.status).toBe(200);
    expect(txListResponse.data.list.length).toBeGreaterThanOrEqual(1);

    const transactionId = txListResponse.data.list[0].id;
    console.log("Authentication transaction ID:", transactionId);

    // FIDO-UAF authentication challenge
    let authResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: "oauth",
      id: transactionId,
      interactionType: "fido-uaf-authentication-challenge",
      body: {},
    });
    console.log("FIDO-UAF challenge:", authResponse.status);
    expect(authResponse.status).toBe(200);

    // FIDO-UAF authentication
    authResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: "oauth",
      id: transactionId,
      interactionType: "fido-uaf-authentication",
      body: {},
    });
    console.log("FIDO-UAF authentication:", authResponse.status);
    expect(authResponse.status).toBe(200);

    // Step 6: Verify authentication-status is "success"
    console.log("\n=== Step 6: Verify authentication-status ===");

    const statusAfterFido = await get({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/authentication-status`,
      headers: {},
    });
    expect(statusAfterFido.status).toBe(200);
    expect(statusAfterFido.data.status).toBe("success");
    expect(statusAfterFido.data.authentication_methods).toContain("fido-uaf");
    expect(statusAfterFido.data.authentication_methods).toContain("password");
    console.log("authentication-status:", statusAfterFido.data.status);
    console.log("authentication_methods:", statusAfterFido.data.authentication_methods);

    // Step 7: Authorize and get tokens
    console.log("\n=== Step 7: Authorize and get tokens ===");

    const authAuthorizeResponse = await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/authorize`,
    });
    expect(authAuthorizeResponse.status).toBe(200);
    expect(authAuthorizeResponse.data.redirect_uri).toBeDefined();

    const redirectUrl = new URL(authAuthorizeResponse.data.redirect_uri);
    const code = redirectUrl.searchParams.get("code");
    expect(code).toBeDefined();
    console.log("Authorization code obtained");

    // Step 8: Exchange code for tokens
    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "authorization_code",
      code: code,
      redirectUri: clientSecretPostClient.redirectUri,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data.access_token).toBeDefined();
    expect(tokenResponse.data.id_token).toBeDefined();
    console.log("Tokens issued successfully");

    // Step 9: Verify ID token has amr with both password and fido-uaf
    const jwksResponse2 = await getJwks({ endpoint: serverConfig.jwksEndpoint });
    const decodedIdToken = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse2.data,
    });
    console.log("ID Token amr:", decodedIdToken.payload.amr);
    expect(decodedIdToken.payload).toHaveProperty("amr");
    expect(decodedIdToken.payload.amr).toContain("password");
    expect(decodedIdToken.payload.amr).toContain("fido-uaf");

    console.log("\n=== Test Completed ===");
    console.log("Summary:");
    console.log("  1. User created via initial-registration");
    console.log("  2. FIDO-UAF device registered");
    console.log("  3. Authorization started WITHOUT login_hint");
    console.log("  4. Password authentication (1st factor)");
    console.log("  5. FIDO-UAF authentication (2nd factor)");
    console.log("  6. authentication-status: success (password + fido-uaf)");
    console.log("  7. Tokens issued with amr: [password, fido-uaf]\n");
  });
});
