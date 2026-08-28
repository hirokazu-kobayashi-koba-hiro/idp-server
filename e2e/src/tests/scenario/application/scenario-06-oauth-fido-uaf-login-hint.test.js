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

  it("should issue a number-matching code, verify it, then complete FIDO-UAF to issue tokens (push fatigue mitigation, #1505)", async () => {
    // Setup: user + FIDO-UAF device
    const { user, accessToken } = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
    });
    await registerFidoUaf({ accessToken });

    // Start authorization code flow with login_hint
    const state = `state_${Date.now()}`;
    const loginHint = `sub:${user.sub},idp:idp-server`;
    const authorizeResponse = await get({
      url:
        `${backendUrl}/${serverConfig.tenantId}/v1/authorizations?` +
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
    const authId = new URL(authorizeResponse.headers.location, backendUrl).searchParams.get("id");

    // Admin token + device-facing transaction view, used to assert the number_matching_required flag.
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

    const txUrl = `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-transactions?authorization_id=${authId}`;
    const txHeaders = { Authorization: `Bearer ${adminTokenResponse.data.access_token}` };

    // Before any challenge no code has been issued, so the device must NOT be prompted.
    const txBeforeChallenge = await get({ url: txUrl, headers: txHeaders });
    expect(txBeforeChallenge.status).toBe(200);
    expect(txBeforeChallenge.data.list[0].number_matching_required).toBe(false);

    // Verifying before any challenge is a distinct failure from a wrong code. The two are told
    // apart by error_description, which is part of the documented contract (#1754).
    const notIssuedResponse = await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/authentication-device-number-matching`,
      body: { number_matching_code: "0000" },
    });
    expect(notIssuedResponse.status).toBe(400);
    expect(notIssuedResponse.data.error).toBe("invalid_request");
    expect(notIssuedResponse.data.error_description).toBe("number_matching_code has not been issued");

    // Issue the number-matching code. Generation is separate from push (FCM): this call only
    // generates + stores the code and returns it for the sign-in screen (SPA) to display. The code
    // is never sent to the device; the user transcribes it. (Push is optional and lives in the
    // shared authentication-device-notification interactor, so it is not exercised here.)
    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/authentication-device-number-matching-challenge`,
      body: {},
    });
    expect(challengeResponse.status).toBe(200);
    const numberMatchingCode = challengeResponse.data.number_matching_code;
    expect(numberMatchingCode).toBeDefined();
    expect(numberMatchingCode).toMatch(/^[0-9]{4}$/);

    // Issuing the code is what tells the device to prompt for it.
    const txAfterChallenge = await get({ url: txUrl, headers: txHeaders });
    expect(txAfterChallenge.status).toBe(200);
    expect(txAfterChallenge.data.list[0].number_matching_required).toBe(true);

    // The whole mechanism rests on the code never reaching the device: the device is told THAT a
    // code is required, never WHICH one. If it leaked into the device-facing transaction the user
    // would no longer have to read the sign-in screen, which is what number-matching is for.
    expect(JSON.stringify(txAfterChallenge.data.list[0])).not.toContain(numberMatchingCode);
    expect(txAfterChallenge.data.list[0]).not.toHaveProperty("number_matching_code");

    // A value that differs from the issued code must not match.
    const wrongCode = numberMatchingCode === "0000" ? "1111" : "0000";
    const wrongResponse = await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/authentication-device-number-matching`,
      body: { number_matching_code: wrongCode },
    });
    expect(wrongResponse.status).toBe(400);
    expect(wrongResponse.data.error).toBe("invalid_request");
    expect(wrongResponse.data.error_description).toBe("number_matching_code does not match");

    // The value the user transcribed from the sign-in screen matches the stored one.
    const okResponse = await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/authentication-device-number-matching`,
      body: { number_matching_code: numberMatchingCode },
    });
    expect(okResponse.status).toBe(200);

    // Number-matching is an anti-push-fatigue binding, NOT an authentication factor: on its own it
    // must not complete authentication. The flow stays in_progress until the actual FIDO-UAF step
    // runs (the shipped mfa-fido-uaf template requires number-matching AND fido-uaf).
    const statusAfterNumberMatching = await get({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/authentication-status`,
      headers: {},
    });
    expect(statusAfterNumberMatching.status).toBe(200);
    expect(statusAfterNumberMatching.data.status).toBe("in_progress");

    // After the code is verified the flag deliberately STAYS true: it is keyed on the challenge, not
    // on verify success, so an attacker who satisfied the verify himself cannot make the victim's
    // device skip the prompt (#1505). Whether the code was verified is tracked separately.
    const txAfterVerify = await get({ url: txUrl, headers: txHeaders });
    expect(txAfterVerify.status).toBe(200);
    expect(txAfterVerify.data.list.length).toBeGreaterThanOrEqual(1);
    expect(txAfterVerify.data.list[0].number_matching_required).toBe(true);

    // FIDO-UAF is the authentication factor. Resolve the transaction id and run the device-side
    // challenge + authentication (same path as the FIDO-UAF-only scenario).
    const transactionId = txAfterVerify.data.list[0].id;

    let fidoResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: "oauth",
      id: transactionId,
      interactionType: "fido-uaf-authentication-challenge",
      body: {},
    });
    expect(fidoResponse.status).toBe(200);

    fidoResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: "oauth",
      id: transactionId,
      interactionType: "fido-uaf-authentication",
      body: {},
    });
    expect(fidoResponse.status).toBe(200);

    // With FIDO-UAF done, authentication succeeds and the authorization code flow can issue tokens.
    const statusAfter = await get({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/authentication-status`,
      headers: {},
    });
    expect(statusAfter.status).toBe(200);
    expect(statusAfter.data.status).toBe("success");
    expect(statusAfter.data.authentication_methods).toContain("fido-uaf");

    const authorizeResp = await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/authorize`,
    });
    expect(authorizeResp.status).toBe(200);
    const code = new URL(authorizeResp.data.redirect_uri).searchParams.get("code");
    expect(code).toBeDefined();

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
  });

  it("should let the device verify the number-matching code with only the ids it can actually obtain (#1770)", async () => {
    // The test above verifies the code through /authorizations/{authorization-id}/, which is fine
    // for a test that already holds that id but is not reachable for a real device: the
    // device-facing transaction carries the transaction id and never the authorization id
    // (AuthenticationRequest#toMapForPublic). This walks the device's actual path — discover the
    // transaction, then interact on /authentications/{transaction-id}/ — so the documented flow and
    // the overview diagram describe something a device can do.
    const { user, accessToken } = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
    });
    const { authenticationDeviceId } = await registerFidoUaf({ accessToken });

    const authorizeResponse = await get({
      url:
        `${backendUrl}/${serverConfig.tenantId}/v1/authorizations?` +
        new URLSearchParams({
          response_type: "code",
          client_id: clientSecretPostClient.clientId,
          redirect_uri: clientSecretPostClient.redirectUri,
          scope: "openid profile email",
          state: `state_${Date.now()}`,
          login_hint: `sub:${user.sub},idp:idp-server`,
        }).toString(),
      headers: {},
    });
    expect(authorizeResponse.status).toBe(302);
    const authId = new URL(authorizeResponse.headers.location, backendUrl).searchParams.get("id");

    // Sign-in screen issues the code. This half is unambiguously the SPA's, and the authorization
    // id is the id it holds.
    const challengeResponse = await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/authentication-device-number-matching-challenge`,
      body: {},
    });
    expect(challengeResponse.status).toBe(200);
    const numberMatchingCode = challengeResponse.data.number_matching_code;
    expect(numberMatchingCode).toMatch(/^[0-9]{4}$/);

    // Device discovers its own transaction. This is the only lookup a device has.
    const deviceTxResponse = await getAuthenticationDeviceAuthenticationTransaction({
      endpoint: serverConfig.authenticationDeviceEndpoint,
      deviceId: authenticationDeviceId,
      params: { flow: "oauth" },
    });
    expect(deviceTxResponse.status).toBe(200);
    const deviceTx = deviceTxResponse.data.list[0];
    console.log("device-facing transaction:", JSON.stringify(deviceTx, null, 2));

    // What the device is told: prompt for a code, and which transaction. Not which code, and not
    // the authorization id — pinning the absence is the point, because the diagram routes the
    // device's call by which id it can hold.
    expect(deviceTx.number_matching_required).toBe(true);
    expect(deviceTx).not.toHaveProperty("number_matching_code");
    expect(deviceTx).not.toHaveProperty("authorization_id");
    expect(JSON.stringify(deviceTx)).not.toContain(authId);

    // #1833: the tenant's client custom_properties reach the device too. They ride in
    // client_attributes rather than context, so unlike scopes/acr_values they are returned even
    // though this lookup performed no device authentication — that is the documented contract and
    // the reason the configuration reference tells operators not to put secrets in them.
    expect(deviceTx.client_attributes.custom_properties).toEqual({
      app_label: "e2e-client-secret-post",
      brand_color: "#0075ca",
    });
    expect(deviceTx).not.toHaveProperty("context");

    const transactionId = deviceTx.id;
    expect(transactionId).toBeDefined();
    expect(transactionId).not.toBe(authId);

    // The transcribed value, submitted on the path the device can address. Both paths converge on
    // OAuthFlowEntryService#interactInternal, so this is the same verification, reached the way a
    // device reaches it.
    const verifyResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: "oauth",
      id: transactionId,
      interactionType: "authentication-device-number-matching",
      body: { number_matching_code: numberMatchingCode },
    });
    console.log("number-matching verify via transaction id:", verifyResponse.status, verifyResponse.data);
    expect(verifyResponse.status).toBe(200);

    // A wrong code fails the same way here as on the other path.
    const wrongCode = numberMatchingCode === "0000" ? "1111" : "0000";
    const wrongResponse = await postAuthenticationDeviceInteraction({
      endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
      flowType: "oauth",
      id: transactionId,
      interactionType: "authentication-device-number-matching",
      body: { number_matching_code: wrongCode },
    });
    expect(wrongResponse.status).toBe(400);
    expect(wrongResponse.data.error_description).toBe("number_matching_code does not match");

    // FIDO-UAF on the same path completes the authentication and the flow issues tokens.
    for (const interactionType of ["fido-uaf-authentication-challenge", "fido-uaf-authentication"]) {
      const fidoResponse = await postAuthenticationDeviceInteraction({
        endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
        flowType: "oauth",
        id: transactionId,
        interactionType,
        body: {},
      });
      expect(fidoResponse.status).toBe(200);
    }

    const statusAfter = await get({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/authentication-status`,
      headers: {},
    });
    expect(statusAfter.status).toBe(200);
    expect(statusAfter.data.status).toBe("success");

    const authorizeResp = await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${authId}/authorize`,
    });
    expect(authorizeResp.status).toBe(200);
    expect(authorizeResp.data.redirect_uri).toContain("code=");
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
