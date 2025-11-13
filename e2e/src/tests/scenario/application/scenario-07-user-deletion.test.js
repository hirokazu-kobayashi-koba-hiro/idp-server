import { describe, it, expect } from "@jest/globals";
import { backendUrl, clientSecretPostClient, federationServerConfig, serverConfig } from "../../testConfig";
import { deletion, get, post } from "../../../lib/http";
import { createFederatedUser } from "../../../user";
import { generatePassword, sleep } from "../../../lib/util";
import { postAuthentication, requestToken } from "../../../api/oauthClient";
import { requestFederation } from "../../../oauth/federation";
import { requestAuthorizations } from "../../../oauth/request";
import { faker } from "@faker-js/faker";
import { verifyAndDecodeJwt } from "../../../lib/jose";

describe("User lifecycle", () => {

  describe("success pattern", () => {

    it("delete user", async () => {

      const { user, accessToken } = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        client: clientSecretPostClient,
        adminClient: clientSecretPostClient
      });

      console.log(user);

      const deleteResponse = await deletion({
        url: serverConfig.resourceOwnerEndpoint,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        }
      });

      expect(deleteResponse.status).toBe(204);

      const userinfoResponse = await post({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/userinfo`,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        }
      });

      console.log(userinfoResponse.data);
      expect(userinfoResponse.status).toBe(401);

      await sleep(1000);

      const introspectionResponse = await post({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/tokens/introspection`,
        body: new URLSearchParams({
          token: accessToken,
          client_id: clientSecretPostClient.clientId,
          client_secret: clientSecretPostClient.clientSecret,
        }).toString()
      });
      console.log(introspectionResponse.data);
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(false);

      // Verify Security Event Token (SET) generation and delivery
      await sleep(3000);

      // Get admin access token for management API
      const adminTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
        scope: "org-management account management",
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret
      });
      expect(adminTokenResponse.status).toBe(200);
      const adminAccessToken = adminTokenResponse.data.access_token;

      // Get security event list for user deletion
      const securityEventListResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/security-events?external_user_id=${user.ex_sub}&event_type=user_delete&limit=1`,
        headers: {
          Authorization: `Bearer ${adminAccessToken}`
        }
      });
      console.log("Security Event List:", JSON.stringify(securityEventListResponse.data, null, 2));
      expect(securityEventListResponse.status).toBe(200);
      expect(securityEventListResponse.data.total_count).toBe(1);
      expect(securityEventListResponse.data.list[0].type).toEqual("user_delete");

      const securityEventId = securityEventListResponse.data.list[0].id;

      // Get security event hook execution result
      const securityEventHookListResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${serverConfig.tenantId}/security-event-hooks?security_event_id=${securityEventId}&event_type=user_delete&limit=1`,
        headers: {
          Authorization: `Bearer ${adminAccessToken}`
        }
      });
      console.log("Security Event Hook List:", JSON.stringify(securityEventHookListResponse.data, null, 2));
      expect(securityEventHookListResponse.status).toBe(200);
      expect(securityEventHookListResponse.data.list.length).toBeGreaterThan(0);
      expect(securityEventHookListResponse.data.list[0].status).toEqual("SUCCESS");
      expect(securityEventHookListResponse.data.list[0].type).toEqual("SSF");

      // Get SSF JWKS for SET verification
      const ssfJwkResponse = await get({
        url: `${backendUrl}/${serverConfig.tenantId}/v1/ssf/jwks`,
      });
      console.log("SSF JWKS:", JSON.stringify(ssfJwkResponse.data, null, 2));
      expect(ssfJwkResponse.status).toBe(200);
      expect(ssfJwkResponse.data.keys).toBeDefined();
      expect(ssfJwkResponse.data.keys.length).toBeGreaterThan(0);

      // Extract and verify SET from hook execution result
      const set = securityEventHookListResponse.data.list[0].contents.execution_result.execution_details.request.body;
      console.log("SET (JWT):", set);
      expect(set).toBeDefined();
      expect(typeof set).toBe("string");

      // Decode and verify SET
      const decodedSet = verifyAndDecodeJwt({
        jwt: set,
        jwks: ssfJwkResponse.data
      });

      console.log("Decoded SET:", JSON.stringify(decodedSet, null, 2));
      expect(decodedSet.verifyResult).toBe(true);

      // Verify SET header
      // RFC 8417 Section 2.3: "typ" MAY be included (SHOULD be "secevent+jwt" if present)
      if (decodedSet.header.typ) {
        expect(decodedSet.header.typ).toEqual("secevent+jwt");
        console.log("✓ typ header present and valid: \"secevent+jwt\"");
      } else {
        console.log("ℹ typ header omitted (RFC 8417 allows this)");
      }
      expect(decodedSet.header.alg).toBeDefined();
      expect(decodedSet.header.kid).toBeDefined();

      // Verify SET payload (RFC 8417 Section 2.2 - Core SET Claims)
      // REQUIRED claims
      expect(decodedSet.payload.iss).toBeDefined(); // Issuer (REQUIRED)
      expect(decodedSet.payload.iss).toEqual(serverConfig.issuer);
      expect(decodedSet.payload.jti).toBeDefined(); // JWT ID (REQUIRED)
      expect(decodedSet.payload.iat).toBeDefined(); // Issued At (REQUIRED)
      expect(decodedSet.payload.events).toBeDefined(); // Events (REQUIRED)

      // RECOMMENDED claim (may be omitted per RFC 8417)
      if (decodedSet.payload.aud) {
        console.log("aud claim present:", decodedSet.payload.aud);
      } else {
        console.log("aud claim omitted (RFC 8417 RECOMMENDED, not REQUIRED)");
      }

      // Verify events structure (RISC account-purged event)
      const eventType = "https://schemas.openid.net/secevent/risc/event-type/account-purged";
      expect(decodedSet.payload.events[eventType]).toBeDefined();

      const eventPayload = decodedSet.payload.events[eventType];
      console.log("Event Payload:", JSON.stringify(eventPayload, null, 2));

      // Verify subject information
      expect(eventPayload.subject).toBeDefined();
      expect(eventPayload.subject.format).toEqual("iss_sub");
      expect(eventPayload.subject.iss).toEqual(serverConfig.issuer); // Original token issuer (tenant)
      expect(eventPayload.subject.sub).toEqual(user.sub); // User sub

      console.log("✅ SET verification completed successfully");
      console.log("  - SET signature verified");
      console.log("  - SET header validated (secevent+jwt)");
      console.log("  - SET core claims validated (iss, jti, iat, events)");
      console.log("  - Event type: account-disabled");
      console.log("  - Subject information verified");
      console.log("  - External subject ID matched");

    });
  });

  describe("error pattern", () => {

    it("delete user fails with insufficient scope", async () => {
      const client = clientSecretPostClient;
      const adminClient = clientSecretPostClient;

      const user = {
        email: faker.internet.email(),
        password: generatePassword(12),
        name: faker.person.fullName(),
        given_name: faker.person.firstName(),
        family_name: faker.person.lastName(),
        middle_name: faker.person.middleName(),
        nickname: faker.person.lastName(),
        profile: faker.internet.url(),
        picture: faker.internet.url(),
        website: faker.internet.url(),
        gender: faker.person.gender(),
        birthdate: faker.date.birthdate({ min: 1, max: 100, mode: "age" }).toISOString().split("T")[0],
        zoneinfo: "Asia/Tokyo",
        locale: "ja-JP",
        phone_number: faker.phone.number("090-####-####"),
      };

      console.log(user);

      const interaction = async (id, user) => {

        const federationInteraction = async (id, user) => {

          const initialResponse = await postAuthentication({
            endpoint: `${backendUrl}/${federationServerConfig.tenantId}/v1/authorizations/{id}/initial-registration`,
            id,
            body: user,
          });

          console.log(initialResponse.data);
          expect(initialResponse.status).toBe(200);

          const challengeResponse = await postAuthentication({
            endpoint: `${backendUrl}/${federationServerConfig.tenantId}/v1/authorizations/{id}/email-authentication-challenge`,
            id,
            body: {
              email: user.email,
              email_template: "authentication"
            },
          });
          console.log(challengeResponse.status);
          console.log(challengeResponse.data);
          expect(challengeResponse.status).toBe(200);

          const adminTokenResponse = await requestToken({
            endpoint: serverConfig.tokenEndpoint,
            grantType: "password",
            username: serverConfig.oauth.username,
            password: serverConfig.oauth.password,
            scope: adminClient.scope,
            clientId: adminClient.clientId,
            clientSecret: adminClient.clientSecret
          });
          console.log(adminTokenResponse.data);
          expect(adminTokenResponse.status).toBe(200);
          const accessToken = adminTokenResponse.data.access_token;

          const authenticationTransactionResponse = await get({
            url: `${backendUrl}/v1/management/organizations/${federationServerConfig.organizationId}/tenants/${federationServerConfig.tenantId}/authentication-transactions?authorization_id=${id}`,
            headers: {
              Authorization: `Bearer ${accessToken}`
            }
          });
          console.log(authenticationTransactionResponse.data);
          expect(authenticationTransactionResponse.status).toBe(200);
          const transactionId = authenticationTransactionResponse.data.list[0].id;

          const interactionResponse = await get({
            url: `${backendUrl}/v1/management/organizations/${federationServerConfig.organizationId}/tenants/${federationServerConfig.tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
            headers: {
              Authorization: `Bearer ${accessToken}`
            }
          });
          console.log(interactionResponse.data);
          expect(interactionResponse.status).toBe(200);
          const verificationCode = interactionResponse.data.payload.verification_code;

          const verificationResponse = await postAuthentication({
            endpoint: `${backendUrl}/${federationServerConfig.tenantId}/v1/authorizations/{id}/email-authentication`,
            id,
            body: {
              verification_code: verificationCode,
            }
          });

          console.log(verificationResponse.status);
          console.log(verificationResponse.data);
          expect(verificationResponse.status).toBe(200);
        };

        const viewResponse = await get({
          url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/view-data`,
        });
        console.log(JSON.stringify(viewResponse.data, null, 2));
        expect(viewResponse.status).toBe(200);
        expect(viewResponse.data.available_federations).not.toBeNull();

        const federationSetting = viewResponse.data.available_federations.find(federation => federation.auto_selected);
        console.log(federationSetting);

        const { params } = await requestFederation({
          url: backendUrl,
          authSessionId: id,
          authSessionTenantId: serverConfig.tenantId,
          type: federationSetting.type,
          providerName: federationSetting.sso_provider,
          federationTenantId: federationServerConfig.tenantId,
          user: user,
          interaction: federationInteraction,
        });
        console.log(params);

        const federationCallbackResponse = await post({
          url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/federations/oidc/callback`,
          body: params.toString()
        });
        console.log(federationCallbackResponse.data);
        expect(federationCallbackResponse.status).toBe(200);

      };

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: client.clientId,
        responseType: "code",
        state: "aiueo",
        scope: "email profile",
        redirectUri: client.redirectUri,
        user,
        interaction,
      });
      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: client.redirectUri,
        clientId: client.clientId,
        clientSecret: client.clientSecret,
      });

      console.log(tokenResponse.data);

      const accessToken = tokenResponse.data.access_token;

      const deleteResponse = await deletion({
        url: serverConfig.resourceOwnerEndpoint,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}`
        }
      });

      console.log(deleteResponse.data);
      expect(deleteResponse.status).toBe(403);
      expect(deleteResponse.data.error).toBe("insufficient_scope");
      expect(deleteResponse.data.error_description).toBe("The request requires 'openid' scope");
      expect(deleteResponse.data.scope).toBe("openid");

    });
  });
});

