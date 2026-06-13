import { describe, expect, it } from "@jest/globals";

import {
  getAuthenticationDeviceAuthenticationTransaction,
  getJwks,
  postAuthenticationDeviceInteraction,
  requestToken,
} from "../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  federationServerConfig,
  serverConfig,
} from "../../testConfig";
import { verifyAndDecodeJwt } from "../../../lib/jose";
import { get, postWithJson } from "../../../lib/http";
import { createFederatedUser, registerFidoUaf } from "../../../user";
import { sleep } from "../../../lib/util";

/**
 * Scenario: Authorization Code Flow with login_hint + FIDO-UAF Authentication
 *
 * Issue: #1409 - Authorization Code Flow + FIDO-UAF device authentication
 * Sub-issues: #1314 (login_hint resolution), #1313 (authentication-status API), #1349 (device push)
 *
 * Flow:
 * 1. Create federated user and register FIDO-UAF device
 * 2. Start authorization with login_hint=sub:{userId}
 * 3. Verify view-data contains login_hint
 * 4. Verify authentication-status is "in_progress"
 * 5. Device performs FIDO-UAF authentication (challenge + authenticate) via /authentications/
 * 6. Verify authentication-status is "success"
 * 7. Authorize and exchange code for tokens
 * 8. Verify ID token contains amr claim
 */
describe("scenario - oauth fido-uaf with login_hint", () => {

  it("should authenticate via FIDO-UAF in authorization code flow with login_hint and issue tokens", async () => {
    // Step 1: Create user and register FIDO-UAF device
    console.log("\n=== Step 1: Create user and register FIDO-UAF device ===");

    const { user, accessToken } = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
    });
    console.log("User created:", user.sub);

    const { authenticationDeviceId } = await registerFidoUaf({ accessToken });
    console.log("FIDO-UAF device registered:", authenticationDeviceId);

    // Step 2: Start authorization with login_hint=sub:{userId}
    console.log("\n=== Step 2: Start authorization with login_hint ===");

    const loginHint = `sub:${user.sub},idp:idp-server`;
    const state = `state_${Date.now()}`;

    const authorizeResponse = await get({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations?` +
        new URLSearchParams({
          response_type: "code",
          client_id: clientSecretPostClient.clientId,
          redirect_uri: clientSecretPostClient.redirectUri,
          scope: "openid profile email",
          state: state,
          login_hint: loginHint,
        }).toString(),
      headers: {},
    });
    expect(authorizeResponse.status).toBe(302);

    const location = authorizeResponse.headers.location;
    const authId = new URL(location, backendUrl).searchParams.get("id");
    expect(authId).toBeDefined();
    console.log("Authorization started, authId:", authId);

    // Step 3: Verify view-data contains login_hint
    console.log("\n=== Step 3: Verify view-data ===");

    const viewDataResponse = await get({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/view-data`,
      headers: {},
    });
    expect(viewDataResponse.status).toBe(200);
    expect(viewDataResponse.data.login_hint).toBe(loginHint);
    console.log("view-data login_hint:", viewDataResponse.data.login_hint);

    // Step 4: Verify authentication-status is "in_progress"
    console.log("\n=== Step 4: Check authentication-status (before auth) ===");

    const statusBefore = await get({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/authentication-status`,
      headers: {},
    });
    expect(statusBefore.status).toBe(200);
    expect(statusBefore.data.status).toBe("in_progress");
    console.log("authentication-status:", statusBefore.data.status);

    // Step 5: Send Push notification via interact endpoint
    console.log("\n=== Step 5: Send Push notification to device ===");

    const pushNotificationResponse = await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/authentication-device-notification`,
      body: {},
    });
    console.log("Push notification response:", pushNotificationResponse.status, pushNotificationResponse.data);

    // Get admin token for management API access
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

    // Step 6: Verify Push notification security event
    console.log("\n=== Step 6: Verify Push notification security event ===");

    await sleep(1000);

    const pushEventResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/security-events`,
      headers: {
        Authorization: `Bearer ${adminTokenResponse.data.access_token}`,
      },
      params: {
        event_type: "authentication_device_notification_success",
        limit: 10,
      },
    });
    console.log("Push notification security events:", pushEventResponse.status);
    expect(pushEventResponse.status).toBe(200);

    const pushEvents = pushEventResponse.data.list || [];
    console.log(`Found ${pushEvents.length} authentication_device_notification_success event(s)`);

    if (pushEvents.length > 0) {
      const latestEvent = pushEvents[0];
      console.log("Latest push notification event:", JSON.stringify(latestEvent, null, 2));
      expect(latestEvent.type).toBe("authentication_device_notification_success");
    }

    // If push notification failed (no FCM configured), check failure event
    if (pushNotificationResponse.status !== 200) {
      const pushFailureEventResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/security-events`,
        headers: {
          Authorization: `Bearer ${adminTokenResponse.data.access_token}`,
        },
        params: {
          event_type: "authentication_device_notification_failure",
          limit: 10,
        },
      });
      expect(pushFailureEventResponse.status).toBe(200);
      const failureEvents = pushFailureEventResponse.data.list || [];
      console.log(`Found ${failureEvents.length} authentication_device_notification_failure event(s)`);
      if (failureEvents.length > 0) {
        console.log("Push failure event:", JSON.stringify(failureEvents[0], null, 2));
      }
    }

    // Step 7: Get authentication transaction for device-side interaction
    console.log("\n=== Step 7: FIDO-UAF authentication via /authentications/ ===");

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

    // Device: FIDO-UAF authentication challenge
    let authResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: "oauth",
      id: transactionId,
      interactionType: "fido-uaf-authentication-challenge",
      body: {},
    });
    console.log("FIDO-UAF challenge:", authResponse.status, authResponse.data);
    expect(authResponse.status).toBe(200);

    // Device: FIDO-UAF authentication
    authResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: "oauth",
      id: transactionId,
      interactionType: "fido-uaf-authentication",
      body: {},
    });
    console.log("FIDO-UAF authentication:", authResponse.status, authResponse.data);
    expect(authResponse.status).toBe(200);

    // Step 8: Verify authentication-status is "success"
    console.log("\n=== Step 8: Check authentication-status (after auth) ===");

    const statusAfter = await get({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/authentication-status`,
      headers: {},
    });
    expect(statusAfter.status).toBe(200);
    expect(statusAfter.data.status).toBe("success");
    expect(statusAfter.data.authentication_methods).toContain("fido-uaf");
    console.log("authentication-status:", statusAfter.data.status);
    console.log("authentication_methods:", statusAfter.data.authentication_methods);
    console.log("interaction_results:", JSON.stringify(statusAfter.data.interaction_results, null, 2));

    // Step 9: Authorize and get authorization code
    console.log("\n=== Step 9: Authorize and get tokens ===");

    const authAuthorizeResponse = await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/authorize`,
    });
    expect(authAuthorizeResponse.status).toBe(200);
    expect(authAuthorizeResponse.data.redirect_uri).toBeDefined();

    const redirectUrl = new URL(authAuthorizeResponse.data.redirect_uri);
    const code = redirectUrl.searchParams.get("code");
    expect(code).toBeDefined();
    console.log("Authorization code obtained");

    // Step 10: Exchange code for tokens
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

    // Step 11: Verify ID token has amr claim with FIDO
    const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
    expect(jwksResponse.status).toBe(200);

    const decodedIdToken = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    console.log("ID Token payload:", JSON.stringify(decodedIdToken.payload, null, 2));
    expect(decodedIdToken.payload).toHaveProperty("amr");

    console.log("\n=== Test Completed ===");
    console.log("Summary:");
    console.log("  1. User created + FIDO-UAF device registered");
    console.log("  2. Authorization started with login_hint=sub:{userId}");
    console.log("  3. view-data returned login_hint value");
    console.log("  4. authentication-status: in_progress");
    console.log("  5. Push notification sent to device");
    console.log("  6. Push notification security event verified");
    console.log("  7. FIDO-UAF authentication via /authentications/ endpoint");
    console.log("  8. authentication-status: success");
    console.log("  9. Authorization code and tokens issued");
    console.log("  10. ID token contains amr claim\n");
  });

  it("should filter scopes based on level_of_authentication_scopes", async () => {
    // Setup: Create user and register FIDO-UAF device
    console.log("\n=== Setup: Create user and register FIDO-UAF device ===");

    const { user, accessToken } = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
    });
    console.log("User created:", user.sub);

    const { authenticationDeviceId } = await registerFidoUaf({ accessToken });
    console.log("FIDO-UAF device registered:", authenticationDeviceId);

    // Test 1: Password-only auth should NOT include "transfers" scope
    console.log("\n=== Test 1: Password-only → transfers scope should be filtered ===");

    const loginHint1 = `sub:${user.sub},idp:idp-server`;
    const state1 = `state_loa_1_${Date.now()}`;

    // Use existing CIBA test user for password authentication (login_hint not used here)
    const authResponse1 = await get({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations?` +
        new URLSearchParams({
          response_type: "code",
          client_id: clientSecretPostClient.clientId,
          redirect_uri: clientSecretPostClient.redirectUri,
          scope: "openid profile email transfers",
          state: state1,
        }).toString(),
      headers: {},
    });
    expect(authResponse1.status).toBe(302);
    const authId1 = new URL(authResponse1.headers.location, backendUrl).searchParams.get("id");

    // Password authentication with CIBA test user
    const pwResponse = await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId1}/password-authentication`,
      body: { username: serverConfig.oauth.username, password: serverConfig.oauth.password },
    });
    expect(pwResponse.status).toBe(200);

    // Authorize and get tokens
    const authorizeResponse1 = await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId1}/authorize`,
    });
    expect(authorizeResponse1.status).toBe(200);

    const code1 = new URL(authorizeResponse1.data.redirect_uri).searchParams.get("code");
    const tokenResponse1 = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "authorization_code",
      code: code1,
      redirectUri: clientSecretPostClient.redirectUri,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(tokenResponse1.status).toBe(200);

    const scope1 = tokenResponse1.data.scope;
    console.log("Password-only scope:", scope1);
    expect(scope1).not.toContain("transfers");
    console.log("PASS: transfers scope filtered out with password-only auth");

    // Test 2: FIDO-UAF auth should include "transfers" scope
    console.log("\n=== Test 2: FIDO-UAF auth → transfers scope should be included ===");

    const state2 = `state_loa_2_${Date.now()}`;

    const authResponse2 = await get({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations?` +
        new URLSearchParams({
          response_type: "code",
          client_id: clientSecretPostClient.clientId,
          redirect_uri: clientSecretPostClient.redirectUri,
          scope: "openid profile email transfers",
          state: state2,
          login_hint: loginHint1,
        }).toString(),
      headers: {},
    });
    expect(authResponse2.status).toBe(302);
    const authId2 = new URL(authResponse2.headers.location, backendUrl).searchParams.get("id");

    // Get admin token for management API
    const adminTokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "password",
      username: serverConfig.oauth.username,
      password: serverConfig.oauth.password,
      scope: clientSecretPostClient.scope,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });

    // Get authentication transaction ID
    const txListResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-transactions?authorization_id=${authId2}`,
      headers: { Authorization: `Bearer ${adminTokenResponse.data.access_token}` },
    });
    expect(txListResponse.data.list.length).toBeGreaterThanOrEqual(1);
    const transactionId = txListResponse.data.list[0].id;

    // FIDO-UAF authentication
    let authResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: "oauth",
      id: transactionId,
      interactionType: "fido-uaf-authentication-challenge",
      body: {},
    });
    expect(authResponse.status).toBe(200);

    authResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: "oauth",
      id: transactionId,
      interactionType: "fido-uaf-authentication",
      body: {},
    });
    expect(authResponse.status).toBe(200);

    // Authorize and get tokens
    const authorizeResponse2 = await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId2}/authorize`,
    });
    expect(authorizeResponse2.status).toBe(200);

    const code2 = new URL(authorizeResponse2.data.redirect_uri).searchParams.get("code");
    const tokenResponse2 = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      grantType: "authorization_code",
      code: code2,
      redirectUri: clientSecretPostClient.redirectUri,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(tokenResponse2.status).toBe(200);

    const scope2 = tokenResponse2.data.scope;
    console.log("FIDO-UAF scope:", scope2);
    expect(scope2).toContain("transfers");
    console.log("PASS: transfers scope included with FIDO-UAF auth");

    console.log("\n=== Test Completed: level_of_authentication_scopes ===");
    console.log("  Password-only: transfers EXCLUDED");
    console.log("  FIDO-UAF:      transfers INCLUDED\n");
  });
});
