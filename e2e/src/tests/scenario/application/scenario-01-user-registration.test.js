import { describe, expect, it } from "@jest/globals";
import { backendUrl, clientSecretPostClient, serverConfig } from "../../testConfig";
import { faker } from "@faker-js/faker";
import { getJwks, postAuthentication, requestToken } from "../../../api/oauthClient";
import { get } from "../../../lib/http";
import { requestAuthorizations } from "../../../oauth/request";
import { verifyAndDecodeJwt } from "../../../lib/jose";

/**
 * Issue #800 Test Suite: Authentication Step Definitions (1st/2nd Factor)
 *
 * This test suite validates the fix for Issue #800, which ensures:
 * 1. Database search is prioritized over transaction user reuse
 * 2. 2nd factor authentication properly enforces requiresUser flag
 * 3. User registration is controlled by allowRegistration flag
 *
 * Configuration: /config/examples/e2e/test-tenant/authentication-policy/oauth.json
 * - step_definitions[0]: email-authentication (1st factor, allow_registration=true)
 * - step_definitions[1]: sms-authentication (2nd factor, requires_user=true, allow_registration=false)
 */
describe("Issue #800: Authentication Step Definitions (1st/2nd Factor)", () => {

  describe("Success Patterns", () => {

    it("1st factor only: Email authentication with new user registration", async () => {
      /**
       * Test: Email authentication (1st factor) with allow_registration=true
       * Expected: New user is created and authentication succeeds
       */
      const testUser = {
        email: faker.internet.email(),
        name: faker.person.fullName(),
        zoneinfo: "Asia/Tokyo",
        locale: "ja-JP",
        phone_number: faker.phone.number("090-####-####"),
      };

      const interaction = async (id, user) => {
        const challengeResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "email-authentication-challenge",
          id,
          body: {
            email: user.email,
            template: "authentication"
          },
        });
        console.log("Challenge response:", challengeResponse.status, challengeResponse.data);
        expect(challengeResponse.status).toBe(200);

        // Get verification code from Management API
        const adminTokenResponse = await requestToken({
          endpoint: serverConfig.tokenEndpoint,
          grantType: "password",
          username: serverConfig.oauth.username,
          password: serverConfig.oauth.password,
          scope: clientSecretPostClient.scope,
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret
        });
        expect(adminTokenResponse.status).toBe(200);
        const accessToken = adminTokenResponse.data.access_token;

        const authenticationTransactionResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-transactions?authorization_id=${id}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        const transactionId = authenticationTransactionResponse.data.list[0].id;

        const interactionResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        const verificationCode = interactionResponse.data.payload.verification_code;

        const verificationResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "email-authentication",
          id,
          body: {
            verification_code: verificationCode,
          }
        });

        console.log("Verification response:", verificationResponse.status, verificationResponse.data);
        expect(verificationResponse.status).toBe(200);
      };

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "state_" + Date.now(),
        scope: "openid profile phone email " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        user: testUser,
        interaction,
      });
      console.log("Authorization response:", authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.data).toHaveProperty("id_token");
    });

    it("2FA: Email (1st factor) -> SMS (2nd factor) with new user registration", async () => {
      /**
       * Test: Email -> SMS 2FA authentication
       * Expected:
       * - 1st factor: New user created via email authentication
       * - 2nd factor: Same user continues via SMS authentication (requires_user=true)
       * - Issue #800 Fix: Database search prioritized over transaction user
       */
      const testUser = {
        email: faker.internet.email(),
        name: faker.person.fullName(),
        phone_number: faker.phone.number("090-####-####"),
      };

      const interaction = async (id, user) => {
        // 1st Factor: Email authentication
        let challengeResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "email-authentication-challenge",
          id,
          body: {
            email: user.email,
            template: "authentication"
          },
        });
        console.log("Email challenge:", challengeResponse.status, challengeResponse.data);
        expect(challengeResponse.status).toBe(200);

        let adminTokenResponse = await requestToken({
          endpoint: serverConfig.tokenEndpoint,
          grantType: "password",
          username: serverConfig.oauth.username,
          password: serverConfig.oauth.password,
          scope: clientSecretPostClient.scope,
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret
        });
        expect(adminTokenResponse.status).toBe(200);
        let accessToken = adminTokenResponse.data.access_token;

        let authenticationTransactionResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-transactions?authorization_id=${id}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        let transactionId = authenticationTransactionResponse.data.list[0].id;

        let interactionResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        let verificationCode = interactionResponse.data.payload.verification_code;

        let verificationResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "email-authentication",
          id,
          body: {
            verification_code: verificationCode,
          }
        });

        console.log("Email verification:", verificationResponse.status, verificationResponse.data);
        expect(verificationResponse.status).toBe(200);

        // 2nd Factor: SMS authentication (requires_user=true)
        challengeResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "sms-authentication-challenge",
          id,
          body: {
            phone_number: user.phone_number,
            template: "authentication"
          },
        });
        console.log("SMS challenge:", challengeResponse.status, challengeResponse.data);
        expect(challengeResponse.status).toBe(200);

        adminTokenResponse = await requestToken({
          endpoint: serverConfig.tokenEndpoint,
          grantType: "password",
          username: serverConfig.oauth.username,
          password: serverConfig.oauth.password,
          scope: clientSecretPostClient.scope,
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret
        });
        expect(adminTokenResponse.status).toBe(200);
        accessToken = adminTokenResponse.data.access_token;

        authenticationTransactionResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-transactions?authorization_id=${id}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        transactionId = authenticationTransactionResponse.data.list[0].id;

        interactionResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/sms-authentication-challenge`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        verificationCode = interactionResponse.data.payload.verification_code;

        verificationResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "sms-authentication",
          id,
          body: {
            verification_code: verificationCode,
          }
        });

        console.log("SMS verification:", verificationResponse.status, verificationResponse.data);
        expect(verificationResponse.status).toBe(200);
      };

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "state_" + Date.now(),
        scope: "openid profile phone email " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        user: testUser,
        interaction,
      });
      console.log("Authorization response:", authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.data).toHaveProperty("id_token");

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      console.log(jwksResponse.data);
      expect(jwksResponse.status).toBe(200);

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.id_token,
        jwks: jwksResponse.data,
      });
      console.log(decodedIdToken);
      const payload = decodedIdToken.payload;
      expect(decodedIdToken.verifyResult).toBe(true);
      expect(payload).toHaveProperty("email");
      expect(payload).toHaveProperty("phone_number");
    });

  });

  describe("Concurrency Tests (Issue #729 Race Condition Prevention)", () => {

    it("Concurrent OAuth flows with same email should not create duplicate users", async () => {
      /**
       * Test: Two OAuth flows execute simultaneously with the same email address
       * Expected: Only one user is created (Issue #729 UNIQUE constraint enforcement)
       *
       * This test validates:
       * - Database UNIQUE constraint (tenant_id, provider_id, preferred_username)
       * - Race condition handling during user registration
       * - Transaction isolation prevents duplicate user creation
       */
      const sharedEmail = faker.internet.email();
      const testUser = {
        email: sharedEmail,
        name: faker.person.fullName(),
        zoneinfo: "Asia/Tokyo",
        locale: "ja-JP",
        phone_number: faker.phone.number("090-####-####"),
      };

      const performAuthFlow = async () => {
        const interaction = async (id, user) => {
          const challengeResponse = await postAuthentication({
            endpoint: serverConfig.authorizationIdEndpoint + "email-authentication-challenge",
            id,
            body: {
              email: user.email,
              template: "authentication"
            },
          });

          // One flow will succeed, the other may fail or succeed with same user
          if (challengeResponse.status !== 200) {
            console.log("Challenge failed (expected for race condition):", challengeResponse.status);
            return;
          }

          const adminTokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "password",
            username: serverConfig.oauth.username,
            password: serverConfig.oauth.password,
            scope: clientSecretPostClient.scope,
            clientId: clientSecretPostClient.clientId,
            clientSecret: clientSecretPostClient.clientSecret
          });
          const accessToken = adminTokenResponse.data.access_token;

          const authenticationTransactionResponse = await get({
            url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-transactions?authorization_id=${id}`,
            headers: {
              Authorization: `Bearer ${accessToken}`
            }
          });
          const transactionId = authenticationTransactionResponse.data.list[0].id;

          const interactionResponse = await get({
            url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
            headers: {
              Authorization: `Bearer ${accessToken}`
            }
          });
          const verificationCode = interactionResponse.data.payload.verification_code;

          const verificationResponse = await postAuthentication({
            endpoint: serverConfig.authorizationIdEndpoint + "email-authentication",
            id,
            body: {
              verification_code: verificationCode,
            }
          });

          console.log("Verification response:", verificationResponse.status);
        };

        try {
          const { authorizationResponse } = await requestAuthorizations({
            endpoint: serverConfig.authorizationEndpoint,
            clientId: clientSecretPostClient.clientId,
            responseType: "code",
            state: "state_" + Date.now() + "_" + Math.random(),
            scope: "openid profile phone email " + clientSecretPostClient.scope,
            redirectUri: clientSecretPostClient.redirectUri,
            user: testUser,
            interaction,
          });

          if (authorizationResponse.code) {
            const tokenResponse = await requestToken({
              endpoint: serverConfig.tokenEndpoint,
              code: authorizationResponse.code,
              grantType: "authorization_code",
              redirectUri: clientSecretPostClient.redirectUri,
              clientId: clientSecretPostClient.clientId,
              clientSecret: clientSecretPostClient.clientSecret,
            });
            return tokenResponse.data.id_token;
          }
        } catch (error) {
          console.log("Auth flow error (expected for race condition):", error.message);
          return null;
        }
      };

      // Execute two OAuth flows concurrently with the same email
      const results = await Promise.allSettled([
        performAuthFlow(),
        performAuthFlow()
      ]);

      const idToken1 = results[0].status === 'fulfilled' ? results[0].value : null;
      const idToken2 = results[1].status === 'fulfilled' ? results[1].value : null;

      console.log("Flow 1 result:", idToken1 ? "SUCCESS" : "FAILED",
                  results[0].status === 'rejected' ? results[0].reason?.message : "");
      console.log("Flow 2 result:", idToken2 ? "SUCCESS" : "FAILED",
                  results[1].status === 'rejected' ? results[1].reason?.message : "");

      // Get Management API token for verification
      const adminTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
        scope: clientSecretPostClient.scope,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret
      });
      const accessToken = adminTokenResponse.data.access_token;

      // CRITICAL VERIFICATION: Only one user should exist with this email
      const usersResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/users?email=${encodeURIComponent(sharedEmail)}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      console.log("Total users with email", sharedEmail, ":", usersResponse.data.list?.length || 0);

      // Filter by idp-server provider (local users)
      const idpServerUsers = usersResponse.data.list?.filter(u => u.provider_id === "idp-server") || [];

      console.log("idp-server provider users:", idpServerUsers.length);
      if (idpServerUsers.length > 0) {
        console.log("User sub:", idpServerUsers[0].sub);
        console.log("User preferred_username:", idpServerUsers[0].preferred_username);
      }

      // ASSERT: Exactly one user created (race condition prevented by UNIQUE constraint)
      expect(idpServerUsers.length).toBe(1);

      // Expected behavior: One flow succeeds, one fails (UNIQUE constraint violation)
      const successCount = [idToken1, idToken2].filter(t => t !== null).length;
      console.log("Success count:", successCount);

      // Exactly one flow should succeed
      expect(successCount).toBe(1);

      // Verify the successful ID token
      const successToken = idToken1 || idToken2;
      expect(successToken).toBeTruthy();

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      const decoded = verifyAndDecodeJwt({ jwt: successToken, jwks: jwksResponse.data });

      expect(decoded.payload.sub).toBe(idpServerUsers[0].sub);
      expect(decoded.payload.email).toBe(sharedEmail);

      console.log("✓ One flow succeeded (sub:", decoded.payload.sub, "), one failed (UNIQUE constraint)");
    }, 30000); // Extended timeout for concurrent operations

  });

  describe("Failure Patterns (Issue #800 Security Fixes)", () => {

    it("2nd factor without authenticated user should fail", async () => {
      /**
       * Test: Call 2nd factor (SMS) without completing 1st factor
       * Expected: user_not_found error (requires_user=true enforced)
       */
      const testUser = {
        email: faker.internet.email(),
        phone_number: faker.phone.number("090-####-####"),
      };

      const interaction = async (id, user) => {
        // Skip 1st factor and directly call 2nd factor
        const challengeResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "sms-authentication-challenge",
          id,
          body: {
            phone_number: user.phone_number,
            template: "authentication"
          },
        });

        console.log("SMS challenge (no 1st factor):", challengeResponse.status, challengeResponse.data);

        // Expected: Should return error because requires_user=true but no authenticated user
        expect(challengeResponse.status).toBe(400);
        expect(challengeResponse.data).toHaveProperty("error");
        expect(challengeResponse.data.error).toBe("invalid_request");
      };

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "state_" + Date.now(),
        scope: "openid profile phone email " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        user: testUser,
        interaction,
        action: "deny", // Authorization will be denied due to authentication failure
      });

      console.log("Authorization response:", authorizationResponse);
      expect(authorizationResponse.error).not.toBeNull();
    });

  });

  describe("Legacy Tests (Keep for backward compatibility)", () => {

    it("initial-registration + sms-authentication", async () => {
      const interaction = async (id, user) => {
        const initialResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "initial-registration",
          id,
          body: {
            ...user
          }
        });

        if (initialResponse.status >= 400) {
          console.error(initialResponse.data);
        }

        const challengeResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "sms-authentication-challenge",
          id,
          body: {
            phone_number: user.phone_number,
            template: "authentication"
          },
        });
        console.log(challengeResponse.status);
        console.log(challengeResponse.data);

        const adminTokenResponse = await requestToken({
          endpoint: serverConfig.tokenEndpoint,
          grantType: "password",
          username: serverConfig.oauth.username,
          password: serverConfig.oauth.password,
          scope: clientSecretPostClient.scope,
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret
        });
        console.log(adminTokenResponse.data);
        expect(adminTokenResponse.status).toBe(200);
        const accessToken = adminTokenResponse.data.access_token;

        const authenticationTransactionResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-transactions?authorization_id=${id}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(authenticationTransactionResponse.data);
        const transactionId = authenticationTransactionResponse.data.list[0].id;

        const interactionResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/authentication-interactions/${transactionId}/sms-authentication-challenge`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });
        console.log(interactionResponse.data);
        const verificationCode = interactionResponse.data.payload.verification_code;

        const verificationResponse = await postAuthentication({
          endpoint: serverConfig.authorizationIdEndpoint + "sms-authentication",
          id,
          body: {
            verification_code: verificationCode,
          }
        });

        console.log(verificationResponse.status);
        console.log(verificationResponse.data);
      };

      const { authorizationResponse } = await requestAuthorizations({
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
        interaction,
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

});
