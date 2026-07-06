import { describe, expect, it } from "@jest/globals";
import { faker } from "@faker-js/faker";
import {
  backendUrl,
  clientSecretPostClient,
  federationServerConfig,
  serverConfig,
} from "../testConfig";
import { postAuthentication, requestToken } from "../../api/oauthClient";
import { get, post } from "../../lib/http";
import { requestFederation } from "../../oauth/federation";
import { requestAuthorizations } from "../../oauth/request";

/**
 * Security: Federation identifier switching.
 *
 * A federation step that runs after a user is already established in the transaction MUST bind to
 * that user, not silently replace it. Here the transaction establishes user A via password
 * (1st factor), then a federation callback returns a different user B. The merge must be rejected
 * (invalid_request), mirroring the same-user guard on the standard interaction path.
 *
 * Federation is intentionally not part of this client's authentication policy, but invoking an
 * unconfigured interaction does not error on that basis — so what we observe is the identity guard,
 * not a policy rejection.
 */
describe("Security: Federation identifier switching", () => {
  it("should reject a federation step that returns a different user than the established one", async () => {
    const federatedUserB = {
      email: faker.internet.email(),
      name: faker.person.fullName(),
      zoneinfo: "Asia/Tokyo",
      locale: "ja-JP",
      phone_number: faker.phone.number("090-####-####"),
    };

    let callbackStatus;
    let callbackData;

    // Authenticate user B at the mock federation IdP (email challenge + verify), mirroring
    // scenario-02's federation login.
    const federationInteraction = async (id, user) => {
      await postAuthentication({
        endpoint: `${backendUrl}/${federationServerConfig.tenantId}/v1/authorizations/{id}/email-authentication-challenge`,
        id,
        body: { email: user.email, email_template: "authentication" },
      });

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
      const accessToken = adminTokenResponse.data.access_token;

      const transactionResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${federationServerConfig.tenantId}/authentication-transactions?authorization_id=${id}`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      const transactionId = transactionResponse.data.list[0].id;

      const interactionResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${federationServerConfig.tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      const verificationCode = interactionResponse.data.payload.verification_code;

      await postAuthentication({
        endpoint: `${backendUrl}/${federationServerConfig.tenantId}/v1/authorizations/{id}/email-authentication`,
        id,
        body: { verification_code: verificationCode },
      });
    };

    // 1st factor: password establishes user A (default seeded user ito.ichiro), then federation
    // returns user B on the same transaction.
    const interaction = async (id) => {
      const passwordResponse = await postAuthentication({
        endpoint: serverConfig.authorizationIdEndpoint.replace("{id}", id) + "password-authentication",
        id,
        body: { username: "ito.ichiro@gmail.com", password: "successUserCode001" },
      });
      expect(passwordResponse.status).toBe(200);

      const viewResponse = await get({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/view-data`,
      });
      expect(viewResponse.status).toBe(200);
      const federationSetting = viewResponse.data.available_federations.find(
        (federation) => federation.auto_selected,
      );

      const { params } = await requestFederation({
        url: backendUrl,
        authSessionId: id,
        authSessionTenantId: serverConfig.tenantId,
        type: federationSetting.type,
        providerName: federationSetting.sso_provider,
        federationTenantId: federationServerConfig.tenantId,
        user: federatedUserB,
        interaction: federationInteraction,
      });

      const callbackResponse = await post({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/federations/oidc/callback`,
        body: params.toString(),
      });
      callbackStatus = callbackResponse.status;
      callbackData = callbackResponse.data;
    };

    await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      state: "aiueo",
      scope: "openid profile phone email " + clientSecretPostClient.scope,
      redirectUri: clientSecretPostClient.redirectUri,
      customParams: { organizationId: "123", organizationName: "test" },
      interaction,
    });

    console.log("federation callback status:", callbackStatus);
    console.log("federation callback data:", JSON.stringify(callbackData));

    expect(callbackStatus).toBe(400);
  });
});
