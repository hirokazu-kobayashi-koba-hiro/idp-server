import { describe, expect, it } from "@jest/globals";
/* global Buffer, fail */
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
} from "../testConfig";
import { faker } from "@faker-js/faker";
import { postAuthentication, requestToken } from "../../api/oauthClient";
import { get } from "../../lib/http";
import { requestAuthorizations } from "../../oauth/request";

/**
 * Issue #801 - S1: 認証識別子の切り替え攻撃
 *
 * これはIssue #800で発見された脆弱性パターンのテストです:
 * 「被害者の識別子で開始 → 攻撃者の識別子に切り替え → 被害者としてログイン」
 *
 * 攻撃フロー (Issue #800の例):
 * 1. 被害者メールアドレスAで認証開始
 * 2. チャレンジ送信後、メールアドレスBに変更
 * 3. 検証コードBで認証
 * 4. [Issue #800修正前] メールアドレスAとしてログイン ❌
 * 5. [Issue #800修正後] メールアドレスBとしてログイン ✅
 *
 * 一般化された脆弱性パターン:
 * - Email認証での識別子切り替え
 * - SMS認証での識別子切り替え
 * - WebAuthn認証での識別子切り替え
 *
 * 修正内容 (Issue #800):
 * - resolveUser()でデータベース検索を優先
 * - Transaction内のユーザー再利用を後回し
 *
 * 関連ファイル:
 * - EmailAuthenticationResolver.java - resolveUser()判定順序
 * - SmsAuthenticationResolver.java - resolveUser()判定順序
 *
 * 重大度: Critical
 * CVE: CWE-287 (Improper Authentication)
 */
describe("Issue #801 - S1: Authentication Identifier Switching Attack", () => {
  describe("Critical: Email Authentication Identifier Switching", () => {
    it("Should authenticate as the FINAL email, not the INITIAL email (Issue #800 fix verification)", async () => {
      /**
       * Attack Scenario:
       * 1. Start authentication with victim@example.com
       * 2. Switch to attacker@example.com during the flow
       * 3. Complete authentication with attacker's verification code
       * 4. Expected: Authenticate as attacker@example.com (final identifier)
       *    Vulnerable: Authenticate as victim@example.com (initial identifier)
       *
       * This test verifies the Issue #800 fix:
       * - Database search is prioritized over transaction user reuse
       */

      const victimEmail = faker.internet.email();
      const attackerEmail = faker.internet.email();

      console.log(`[Test] Victim email: ${victimEmail}`);
      console.log(`[Test] Attacker email: ${attackerEmail}`);

      let switchOccurred = false;

      const interaction = async (id) => {
        // Step 1: Send challenge to victim's email
        console.log(`\n[Step 1] Sending challenge to VICTIM: ${victimEmail}`);
        const victimChallengeResponse = await postAuthentication({
          endpoint:
            serverConfig.authorizationIdEndpoint +
            "email-authentication-challenge",
          id,
          body: {
            email: victimEmail,
            template: "authentication",
          },
        });

        console.log(
          `[Step 1] Victim challenge: ${victimChallengeResponse.status}`
        );
        expect(victimChallengeResponse.status).toBe(200);

        // Step 2: Switch to attacker's email (identifier switching!)
        console.log(`\n[Step 2] SWITCHING to ATTACKER: ${attackerEmail}`);
        switchOccurred = true;

        const attackerChallengeResponse = await postAuthentication({
          endpoint:
            serverConfig.authorizationIdEndpoint +
            "email-authentication-challenge",
          id,
          body: {
            email: attackerEmail,
            template: "authentication",
          },
        });

        console.log(
          `[Step 2] Attacker challenge: ${attackerChallengeResponse.status}`
        );
        expect(attackerChallengeResponse.status).toBe(200);

        // Step 3: Get attacker's verification code from Management API
        const adminTokenResponse = await requestToken({
          endpoint: serverConfig.tokenEndpoint,
          grantType: "password",
          username: serverConfig.oauth.username,
          password: serverConfig.oauth.password,
          scope: clientSecretPostClient.scope,
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret,
        });
        const accessToken = adminTokenResponse.data.access_token;

        const authenticationTransactionResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-transactions?authorization_id=${id}`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        });
        const transactionId = authenticationTransactionResponse.data.list[0].id;

        const interactionResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        });
        const verificationCode =
          interactionResponse.data.payload.verification_code;

        console.log(
          `[Step 3] Got verification code for attacker: ${verificationCode}`
        );

        // Step 4: Verify with attacker's code
        const verificationResponse = await postAuthentication({
          endpoint:
            serverConfig.authorizationIdEndpoint + "email-authentication",
          id,
          body: {
            verification_code: verificationCode,
          },
        });

        console.log(`[Step 4] Verification: ${verificationResponse.status}`);
        expect(verificationResponse.status).toBe(200);
      };

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "state_" + Date.now(),
        scope: "openid profile email " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        user: {
          email: attackerEmail, // Will be used for final registration
          name: faker.person.fullName(),
        },
        interaction,
      });

      expect(switchOccurred).toBe(true);
      expect(authorizationResponse.code).not.toBeNull();

      // Step 5: Get ID token and verify which email is in the token
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      expect(tokenResponse.data).toHaveProperty("id_token");

      // Decode ID token to check email claim
      const idToken = tokenResponse.data.id_token;
      const payload = JSON.parse(
        Buffer.from(idToken.split(".")[1], "base64").toString()
      );

      console.log(`\n[Verification] ID Token email claim: ${payload.email}`);
      console.log(`[Verification] Expected (attacker): ${attackerEmail}`);
      console.log(`[Verification] NOT expected (victim): ${victimEmail}`);

      // Critical assertion: Must authenticate as the FINAL email (attacker), not INITIAL (victim)
      if (payload.email === victimEmail) {
        console.log(
          "❌❌❌ CRITICAL FAIL: Authenticated as VICTIM (initial email)!"
        );
        console.log("   → Issue #800 vulnerability exists");
        console.log("   → Identifier switching attack succeeded");
        fail(
          "CRITICAL VULNERABILITY: User authenticated as initial identifier, not final"
        );
      } else if (payload.email === attackerEmail) {
        console.log("✅ PASS: Authenticated as ATTACKER (final email)");
        console.log("   → Issue #800 fix is working correctly");
        console.log(
          "   → Database search is prioritized over transaction user"
        );
        expect(payload.email).toBe(attackerEmail);
      } else {
        console.log(`⚠️  Unexpected email: ${payload.email}`);
        fail(`Unexpected email in ID token: ${payload.email}`);
      }
    });
  });

  describe("Critical: SMS Authentication Identifier Switching (2FA)", () => {
    it("Should authenticate as the FINAL phone number, not the INITIAL phone number", async () => {
      /**
       * Attack Scenario (SMS as 2nd factor):
       * 1. Complete Email authentication (1st factor) with email A
       * 2. Start SMS authentication with victim's phone number
       * 3. Switch to attacker's phone number during SMS flow
       * 4. Complete authentication with attacker's verification code
       * 5. Expected: Authenticate with attacker's phone number (final identifier)
       *    Vulnerable: Authenticate with victim's phone number (initial identifier)
       *
       * Note: SMS authentication is configured as 2nd factor (requires_user=true)
       * So we must complete Email authentication first.
       */

      const baseEmail = faker.internet.email();
      const victimPhone = faker.phone.number("090-####-####");
      const attackerPhone = faker.phone.number("080-####-####");

      console.log(`[Test] Base email (1st factor): ${baseEmail}`);
      console.log(`[Test] Victim phone (initial): ${victimPhone}`);
      console.log(`[Test] Attacker phone (final): ${attackerPhone}`);

      let switchOccurred = false;

      const interaction = async (id) => {
        // Step 1: Complete Email authentication (1st factor)
        console.log(
          `\n[Step 1] Completing 1st factor (Email): ${baseEmail}`
        );
        let challengeResponse = await postAuthentication({
          endpoint:
            serverConfig.authorizationIdEndpoint +
            "email-authentication-challenge",
          id,
          body: {
            email: baseEmail,
            template: "authentication",
          },
        });
        expect(challengeResponse.status).toBe(200);

        // Get verification code for email
        let adminTokenResponse = await requestToken({
          endpoint: serverConfig.tokenEndpoint,
          grantType: "password",
          username: serverConfig.oauth.username,
          password: serverConfig.oauth.password,
          scope: clientSecretPostClient.scope,
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret,
        });
        let accessToken = adminTokenResponse.data.access_token;

        let authenticationTransactionResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-transactions?authorization_id=${id}`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        });
        let transactionId = authenticationTransactionResponse.data.list[0].id;

        let interactionResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        });
        let verificationCode =
          interactionResponse.data.payload.verification_code;

        let verificationResponse = await postAuthentication({
          endpoint:
            serverConfig.authorizationIdEndpoint + "email-authentication",
          id,
          body: {
            verification_code: verificationCode,
          },
        });
        expect(verificationResponse.status).toBe(200);
        console.log("[Step 1] Email authentication completed");

        // Step 2: Start SMS authentication (2nd factor) with victim's phone
        console.log(
          `\n[Step 2] Sending SMS challenge to VICTIM: ${victimPhone}`
        );
        challengeResponse = await postAuthentication({
          endpoint:
            serverConfig.authorizationIdEndpoint +
            "sms-authentication-challenge",
          id,
          body: {
            phone_number: victimPhone,
            template: "authentication",
          },
        });

        console.log(
          `[Step 2] Victim SMS challenge: ${challengeResponse.status}`
        );
        expect(challengeResponse.status).toBe(200);

        // Step 3: Switch to attacker's phone (identifier switching!)
        console.log(`\n[Step 3] SWITCHING to ATTACKER: ${attackerPhone}`);
        switchOccurred = true;

        challengeResponse = await postAuthentication({
          endpoint:
            serverConfig.authorizationIdEndpoint +
            "sms-authentication-challenge",
          id,
          body: {
            phone_number: attackerPhone,
            template: "authentication",
          },
        });

        console.log(
          `[Step 3] Attacker SMS challenge: ${challengeResponse.status}`
        );
        expect(challengeResponse.status).toBe(200);

        // Step 4: Get attacker's verification code
        adminTokenResponse = await requestToken({
          endpoint: serverConfig.tokenEndpoint,
          grantType: "password",
          username: serverConfig.oauth.username,
          password: serverConfig.oauth.password,
          scope: clientSecretPostClient.scope,
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret,
        });
        accessToken = adminTokenResponse.data.access_token;

        authenticationTransactionResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-transactions?authorization_id=${id}`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        });
        transactionId = authenticationTransactionResponse.data.list[0].id;

        interactionResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/sms-authentication-challenge`,
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        });
        verificationCode = interactionResponse.data.payload.verification_code;

        console.log(
          `[Step 4] Got verification code for attacker: ${verificationCode}`
        );

        // Step 5: Verify with attacker's code
        verificationResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "sms-authentication",
          id,
          body: {
            verification_code: verificationCode,
          },
        });

        console.log(`[Step 5] Verification: ${verificationResponse.status}`);
        expect(verificationResponse.status).toBe(200);
      };

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "state_" + Date.now(),
        scope: "openid profile phone email " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        user: {
          email: baseEmail,
          phone_number: attackerPhone,
          name: faker.person.fullName(),
        },
        interaction,
      });

      expect(switchOccurred).toBe(true);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      expect(tokenResponse.data).toHaveProperty("id_token");

      const idToken = tokenResponse.data.id_token;
      const payload = JSON.parse(
        Buffer.from(idToken.split(".")[1], "base64").toString()
      );

      console.log(
        `\n[Verification] ID Token phone_number claim: ${payload.phone_number}`
      );
      console.log(`[Verification] Expected (attacker): ${attackerPhone}`);
      console.log(`[Verification] NOT expected (victim): ${victimPhone}`);
      expect(payload).toHaveProperty("phone_number");

      if (payload.phone_number === victimPhone) {
        console.log(
          "❌❌❌ CRITICAL FAIL: Authenticated as VICTIM (initial phone)!"
        );
        fail(
          "CRITICAL VULNERABILITY: SMS identifier switching attack succeeded"
        );
      } else if (payload.phone_number === attackerPhone) {
        console.log("✅ PASS: Authenticated as ATTACKER (final phone)");
        expect(payload.phone_number).toBe(attackerPhone);
      } else {
        console.log(`⚠️  Unexpected phone_number: ${payload.phone_number}`);
      }
    });
  });

  describe("Edge Cases: Multiple Identifier Switches", () => {
    it("Should handle multiple email switches and use the FINAL email", async () => {
      /**
       * Scenario: Multiple identifier switches during authentication
       * email1 → email2 → email3
       * Expected: Authenticate as email3 (final)
       */

      const email1 = faker.internet.email();
      const email2 = faker.internet.email();
      const email3 = faker.internet.email();

      console.log(`[Test] Email sequence: ${email1} → ${email2} → ${email3}`);

      const interaction = async (id) => {
        // Challenge 1
        await postAuthentication({
          endpoint:
            serverConfig.authorizationIdEndpoint +
            "email-authentication-challenge",
          id,
          body: { email: email1, template: "authentication" },
        });

        // Challenge 2
        await postAuthentication({
          endpoint:
            serverConfig.authorizationIdEndpoint +
            "email-authentication-challenge",
          id,
          body: { email: email2, template: "authentication" },
        });

        // Challenge 3 (final)
        await postAuthentication({
          endpoint:
            serverConfig.authorizationIdEndpoint +
            "email-authentication-challenge",
          id,
          body: { email: email3, template: "authentication" },
        });

        // Get verification code for email3
        const adminTokenResponse = await requestToken({
          endpoint: serverConfig.tokenEndpoint,
          grantType: "password",
          username: serverConfig.oauth.username,
          password: serverConfig.oauth.password,
          scope: clientSecretPostClient.scope,
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret,
        });
        const accessToken = adminTokenResponse.data.access_token;

        const authenticationTransactionResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-transactions?authorization_id=${id}`,
          headers: { Authorization: `Bearer ${accessToken}` },
        });
        const transactionId = authenticationTransactionResponse.data.list[0].id;

        const interactionResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
          headers: { Authorization: `Bearer ${accessToken}` },
        });
        const verificationCode =
          interactionResponse.data.payload.verification_code;

        await postAuthentication({
          endpoint:
            serverConfig.authorizationIdEndpoint + "email-authentication",
          id,
          body: { verification_code: verificationCode },
        });
      };

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "state_" + Date.now(),
        scope: "openid profile email " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        user: {
          email: email3,
          name: faker.person.fullName(),
        },
        interaction,
      });

      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      const idToken = tokenResponse.data.id_token;
      const payload = JSON.parse(
        Buffer.from(idToken.split(".")[1], "base64").toString()
      );

      console.log(`\n[Verification] Final email in token: ${payload.email}`);
      console.log(`[Verification] Expected (email3): ${email3}`);

      expect(payload.email).toBe(email3);
      console.log("✅ PASS: Multiple switches handled correctly");
    });
  });
});
