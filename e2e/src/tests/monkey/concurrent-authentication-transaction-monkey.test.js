import { describe, expect, it } from "@jest/globals";

import {
  getAuthenticationDeviceAuthenticationTransaction,
  postAuthenticationDeviceInteraction,
  requestBackchannelAuthentications,
  getAuthorizations,
} from "../../api/oauthClient";
import {
  clientSecretPostClient,
  serverConfig,
} from "../testConfig";
import { postWithJson } from "../../lib/http";
import { convertNextAction } from "../../lib/util";

/**
 * Monkey tests for concurrent access to authentication_transaction
 *
 * Purpose:
 * - Issue #1454: Verify that concurrent interact requests on the same
 *   authentication_transaction do not cause deadlock (500) or unhandled errors
 * - Issue #1452: Verify that FK constraint violations return 404, not 500
 *
 * These tests verify that concurrent operations return appropriate HTTP status codes
 * (200, 400, 404, 409) and never return 500.
 */
describe("Monkey test Concurrent Authentication Transaction", () => {
  const ciba = serverConfig.ciba;

  describe("CIBA - concurrent interact on same transaction", () => {
    /**
     * Issue #1454: Two concurrent fido-uaf-authentication requests on the same
     * authentication_transaction. One should succeed (200) and complete the transaction,
     * the other should get a non-500 error (404 because transaction was deleted, or 400).
     *
     * Before fix: deadlock detected (500) due to CASCADE delete on authentication_interactions
     * After fix: one succeeds, the other gets 404 (transaction already deleted/locked)
     */
    it("concurrent fido-uaf-authentication should not cause deadlock", async () => {
      const ROUNDS = 10;
      let deadlockCount = 0;
      let errorCount = 0;
      let skipCount = 0;

      for (let round = 1; round <= ROUNDS; round++) {
        if (round % 100 === 0 || round === 1) {
          console.log(`--- Round ${round}/${ROUNDS} (deadlocks: ${deadlockCount}, errors: ${errorCount}, skips: ${skipCount}) ---`);
        }

        // 1. Start CIBA flow
        const backchannelAuthResponse = await requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid profile phone email",
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHint,
          acrValues: "urn:mace:incommon:iap:gold",
          clientSecret: clientSecretPostClient.clientSecret,
        });
        if (backchannelAuthResponse.status !== 200) {
          skipCount++;
          continue;
        }

        // 2. Get authentication transaction
        const txResponse = await getAuthenticationDeviceAuthenticationTransaction({
          endpoint: serverConfig.authenticationDeviceEndpoint,
          deviceId: ciba.authenticationDeviceId,
          params: {
            "attributes.auth_req_id": backchannelAuthResponse.data.auth_req_id,
          },
        });
        if (txResponse.status !== 200 || !txResponse.data.list[0]) {
          skipCount++;
          continue;
        }

        const transaction = txResponse.data.list[0];

        // 3. Create interaction rows (more CASCADE lock targets)
        const setupTypes = [
          { type: "fido-uaf-authentication-challenge", body: { username: ciba.username, password: ciba.userCode } },
          { type: "sms-authentication-challenge", body: { phone_number: "09012345678", provider_id: "idp-server" } },
          { type: "password-authentication", body: { username: ciba.username, password: ciba.userCode } },
        ];
        for (const { type, body } of setupTypes) {
          await postAuthenticationDeviceInteraction({
            endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
            flowType: transaction.flow,
            id: transaction.id,
            interactionType: type,
            body,
          });
        }

        // 4. Reproduce lock conflict on authentication_interactions:
        //    fido-auth: 200ms mock → INSERT auth_interactions → DELETE auth_tx → CASCADE
        //    email-challenges: instant mock → INSERT auth_interactions (same tx, diff type)
        //    20 concurrent email-challenges create a queue of INSERTs on auth_interactions.
        //    When fido-auth returns at 200ms and CASCADE DELETEs, it hits ongoing INSERTs.
        const fidoAuth = postAuthenticationDeviceInteraction({
          endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
          flowType: transaction.flow,
          id: transaction.id,
          interactionType: "fido-uaf-authentication",
          body: { username: ciba.username, password: ciba.userCode },
        });

        // Rapid-fire email challenges - all INSERT into auth_interactions
        const emailChallenges = [];
        for (let i = 0; i < 20; i++) {
          emailChallenges.push(
            postAuthenticationDeviceInteraction({
              endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
              flowType: transaction.flow,
              id: transaction.id,
              interactionType: "email-authentication-challenge",
              body: { email: `test${i}@example.com`, provider_id: "idp-server" },
            })
          );
        }

        const results = await Promise.allSettled([fidoAuth, ...emailChallenges]);

        const statuses = results
          .filter((r) => r.status === "fulfilled")
          .map((r) => r.value.status);

        const has500 = statuses.some((s) => s === 500);
        const hasUnexpected = statuses.some((s) => ![200, 400, 404, 409].includes(s));

        if (has500) {
          deadlockCount++;
          console.error(`*** Round ${round}: 500 DETECTED *** statuses=[${statuses.join(",")}]`);
          results.forEach((result, index) => {
            if (result.status === "fulfilled" && result.value.status === 500) {
              const label = index === 0 ? "fido-auth" : `email-${index}`;
              console.error(`  ${label}:`, JSON.stringify(result.value.data));
            }
          });
          break; // Stop on first deadlock
        } else if (hasUnexpected) {
          errorCount++;
          console.warn(`Round ${round}: unexpected statuses=[${statuses.join(",")}]`);
        }
      }

      console.log(`\n========================================`);
      console.log(`  Total rounds: ${ROUNDS}`);
      console.log(`  Skipped:      ${skipCount}`);
      console.log(`  500 (deadlock/error): ${deadlockCount}`);
      console.log(`  Unexpected status:    ${errorCount}`);
      console.log(`========================================\n`);

      expect(deadlockCount).toBe(0);
    }, 3600000);

    /**
     * Variant: concurrent fido-uaf-authentication-challenge requests.
     * Both write to authentication_interactions, risking FK violation
     * if one triggers transaction deletion.
     */
    it("concurrent challenge requests should not cause 500", async () => {
      const backchannelAuthResponse = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid profile phone email",
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        loginHint: ciba.loginHint,
        acrValues: "urn:mace:incommon:iap:gold",
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(backchannelAuthResponse.status).toBe(200);

      const txResponse = await getAuthenticationDeviceAuthenticationTransaction({
        endpoint: serverConfig.authenticationDeviceEndpoint,
        deviceId: ciba.authenticationDeviceId,
        params: {
          "attributes.auth_req_id": backchannelAuthResponse.data.auth_req_id,
        },
      });
      expect(txResponse.status).toBe(200);

      const transaction = txResponse.data.list[0];

      // Send 5 concurrent challenge requests
      const concurrentChallenges = Array.from({ length: 5 }).map(() =>
        postAuthenticationDeviceInteraction({
          endpoint: serverConfig.authenticationDeviceInteractionEndpoint,
          flowType: transaction.flow,
          id: transaction.id,
          interactionType: "fido-uaf-authentication-challenge",
          body: {
            username: ciba.username,
            password: ciba.userCode,
          },
        })
      );

      const results = await Promise.allSettled(concurrentChallenges);

      results.forEach((result, index) => {
        if (result.status === "fulfilled") {
          const res = result.value;
          console.log(`Challenge ${index + 1}: status=${res.status}`);
          expect(res.status).not.toBe(500);
          expect([200, 400, 404, 409]).toContain(res.status);
        }
      });
    });
  });

  describe("OAuth - concurrent authorize on same authorization request", () => {
    /**
     * Issue #1454: Simulate double-click on authorize button.
     * Two concurrent authorize requests on the same authorization_request_id.
     * Both attempt to DELETE authentication_transaction, risking deadlock.
     *
     * Before fix: potential deadlock on CASCADE delete
     * After fix: one succeeds (302), the other gets 404 (transaction already deleted)
     */
    it("double-click authorize should not cause 500", async () => {
      // 1. Start authorization flow
      const authzResponse = await getAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        scope: "openid profile",
        responseType: "code",
        clientId: clientSecretPostClient.clientId,
        redirectUri: clientSecretPostClient.redirectUri,
        state: "state-concurrent-test",
        nonce: "nonce-concurrent-test",
      });

      const { location } = authzResponse.headers;
      expect(location).toBeDefined();

      const { params } = convertNextAction(location);
      const authorizationId = params.get("id");
      expect(authorizationId).toBeDefined();
      console.log("Authorization ID:", authorizationId);

      // 2. Authenticate (password)
      const authEndpoint =
        serverConfig.authorizationIdEndpoint.replace("{id}", authorizationId)
        + "password-authentication";
      const authResponse = await postWithJson({
        url: authEndpoint,
        body: {
          username: serverConfig.oauth.username,
          password: serverConfig.oauth.password,
        },
      });
      console.log("Auth response:", authResponse.status, authResponse.data);
      expect(authResponse.status).toBe(200);

      // 3. Send two concurrent authorize requests (double-click simulation)
      const authorizeUrl =
        serverConfig.authorizeEndpoint.replace("{id}", authorizationId);
      const concurrentAuthorizes = [
        postWithJson({ url: authorizeUrl }),
        postWithJson({ url: authorizeUrl }),
      ];

      const results = await Promise.allSettled(concurrentAuthorizes);

      results.forEach((result, index) => {
        if (result.status === "fulfilled") {
          const res = result.value;
          console.log(`Authorize ${index + 1}: status=${res.status}`, res.data);
          // Must not be 500
          expect(res.status).not.toBe(500);
          // 200/302 = success, 404 = transaction already deleted, 400 = invalid state
          expect([200, 302, 400, 404]).toContain(res.status);
        } else {
          console.error(`Authorize ${index + 1}: rejected`, result.reason);
        }
      });

      const fulfilled = results.filter((r) => r.status === "fulfilled");
      expect(fulfilled.length).toBeGreaterThanOrEqual(1);
    });
  });
});
