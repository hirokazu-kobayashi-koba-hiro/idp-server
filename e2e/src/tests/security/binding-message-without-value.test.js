import { describe, expect, it } from "@jest/globals";
import { get } from "../../lib/http";
import {
  getAuthenticationDeviceAuthenticationTransaction,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  requestToken,
} from "../../api/oauthClient";
import { backendUrl, clientSecretPostClient, federationServerConfig, serverConfig } from "../testConfig";
import { createFederatedUser, registerFidoUaf } from "../../user";

/**
 * Issue #1950: the binding message step compares what the device submits with the binding message
 * of the transaction. A transaction that has none — a CIBA request sent without binding_message, or
 * an authorization code flow — holds an empty value, so there is nothing to compare against and
 * the step must not pass, whatever is submitted.
 */
describe("Security: the binding message step needs a binding message to compare against (#1950)", () => {
  /** Starts CIBA for a fresh user with a device, and returns the device's authentication transaction. */
  const startCiba = async ({ bindingMessage } = {}) => {
    const { accessToken } = await createFederatedUser({
      serverConfig,
      federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
      scope: "openid claims:authentication_devices",
    });
    const { authenticationDeviceId } = await registerFidoUaf({ accessToken });

    const backchannelResponse = await requestBackchannelAuthentications({
      endpoint: serverConfig.backchannelAuthenticationEndpoint,
      clientId: clientSecretPostClient.clientId,
      scope: "openid profile",
      ...(bindingMessage ? { bindingMessage } : {}),
      loginHint: `device:${authenticationDeviceId},idp:${federationServerConfig.providerName}`,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(backchannelResponse.status).toBe(200);

    const transactionResponse = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: serverConfig.authenticationDeviceEndpoint,
      deviceId: authenticationDeviceId,
      params: { "attributes.auth_req_id": backchannelResponse.data.auth_req_id },
    });
    expect(transactionResponse.status).toBe(200);
    const transaction = transactionResponse.data.list[0];
    return { flowType: transaction.flow, transactionId: transaction.id };
  };

  /** Starts an authorization code flow and returns its authentication transaction. */
  const startAuthorizationCodeFlow = async () => {
    const authorizeResponse = await get({
      url:
        `${backendUrl}/${serverConfig.tenantId}/v1/authorizations?` +
        new URLSearchParams({
          response_type: "code",
          client_id: clientSecretPostClient.clientId,
          redirect_uri: clientSecretPostClient.redirectUri,
          scope: "openid profile",
          state: `binding-message-${Date.now()}`,
        }).toString(),
    });
    expect(authorizeResponse.status).toBe(302);
    const authId = new URL(authorizeResponse.headers.location, backendUrl).searchParams.get("id");

    const adminToken = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "password",
      username: serverConfig.oauth.username,
      password: serverConfig.oauth.password,
      scope: clientSecretPostClient.scope,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(adminToken.status).toBe(200);
    const transactions = await get({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-transactions?authorization_id=${authId}`,
      headers: { Authorization: `Bearer ${adminToken.data.access_token}` },
    });
    expect(transactions.status).toBe(200);
    return { flowType: "oauth", transactionId: transactions.data.list[0].id };
  };

  const submitBindingMessage = ({ flowType, transactionId }, body) =>
    postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType,
      id: transactionId,
      interactionType: "authentication-device-binding-message",
      body,
    });

  it("accepts the binding message the CIBA request carried", async () => {
    const transaction = await startCiba({ bindingMessage: "999" });

    const response = await submitBindingMessage(transaction, { binding_message: "999" });

    expect(response.status).toBe(200);
  }, 90000);

  it("rejects an empty binding message for a CIBA request sent without one", async () => {
    const transaction = await startCiba();

    const response = await submitBindingMessage(transaction, { binding_message: "" });

    expect(response.status).toBe(400);
    expect(response.data.error).toBe("invalid_request");
  }, 90000);

  it("rejects a missing binding message for a CIBA request sent without one", async () => {
    const transaction = await startCiba();

    const response = await submitBindingMessage(transaction, {});

    expect(response.status).toBe(400);
  }, 90000);

  it("rejects an empty binding message in an authorization code flow", async () => {
    const transaction = await startAuthorizationCodeFlow();

    const response = await submitBindingMessage(transaction, { binding_message: "" });

    expect(response.status).toBe(400);
    expect(response.data.error).toBe("invalid_request");
  }, 90000);
});
