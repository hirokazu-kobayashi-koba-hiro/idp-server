import { describe, expect, it } from "@jest/globals";
import { requestToken, postAuthentication, requestBackchannelAuthentications } from "../../api/oauthClient";
import { adminServerConfig, backendUrl, clientSecretPostClient, mockApiBaseUrl, serverConfig } from "../testConfig";
import { post, postWithJson, get, patchWithJson } from "../../lib/http";
import { faker } from "@faker-js/faker";
import { v4 as uuidv4 } from "uuid";
import { requestAuthorizations } from "../../oauth/request";
import { generateRS256KeyPair } from "../../lib/jose";

/**
 * Issue #741: Password Policy Full Flow E2E Test
 *
 * This comprehensive scenario test validates the complete password policy implementation:
 * 1. New tenant creation (Management API)
 * 2. Authorization request
 * 3. Initial user registration (InitialRegistrationInteractor with default schema)
 * 4. Token issuance
 * 5. Password change (Password Change API with policy validation)
 * 6. Token issuance with new password credentials
 *
 * Password Policy Validation Points:
 * - Initial registration: PasswordPolicyValidator in InitialRegistrationInteractor
 * - Password change: PasswordPolicyValidator in Password Change API
 * - Token issuance: Password authentication validates encoded password
 *
 * Default Password Policy:
 * - min_length: 8 characters
 * - max_length: 72 characters (BCrypt limitation)
 * - No complexity requirements (NIST SP 800-63B compliance)
 */
describe("User Status verification. Can not authorize when user status is not active.", () => {


  describe("status verification", () => {

    it("Status lock is can not authorize", async () => {
      console.log("\n=== Starting Password Policy Full Flow Test ===\n");

      // Step 1: Get admin access token
      console.log("Step 1: Getting admin access token...");
      const adminTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "password",
        username: serverConfig.oauth.username,
        password: serverConfig.oauth.password,
        scope: clientSecretPostClient.scope,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret
      });
      console.log(JSON.stringify(adminTokenResponse.data, null, 2));
      expect(adminTokenResponse.status).toBe(200);
      const adminAccessToken = adminTokenResponse.data.access_token;
      console.log("✓ Admin access token obtained\n");

      // Step 2: Create new tenant with auto-generated JWKS
      console.log("Step 2: Creating new tenant...");
      const tenantId = uuidv4();

      // Generate RS256 key pair for JWKS
      const { jwks } = await generateRS256KeyPair();
      console.log(jwks);
      console.log("✓ JWKS generated for tenant");

      const tenantData = {
        tenant: {
          id: tenantId,
          name: "Password Policy Test Tenant",
          domain: "http://localhost:8080",
          description: "Test tenant for password policy validation",
          authorization_provider: "idp-server",
          tenant_type: "BUSINESS"
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/.well-known/jwks.json`,
          jwks: jwks,  // Add generated JWKS
          scopes_supported: ["openid", "profile", "email"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code", "password"],
          token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
          id_token_signing_alg_values_supported: ["RS256"],
          claims_supported: ["sub", "name", "email", "email_verified", "preferred_username"],
          extension: {
            access_token_type: "JWT",
            access_token_duration: 3600,
            id_token_duration: 3600
          }
        }
      };

      const createTenantResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants`,
        headers: {
          Authorization: `Bearer ${adminAccessToken}`,
        },
        body: tenantData
      });

      console.log("Tenant creation response:", createTenantResponse.status);
      expect(createTenantResponse.status).toBe(201);
      console.log(`✓ Tenant created: ${tenantId}\n`);

      // Step 3: Register client for the tenant
      console.log("Step 3: Registering client...");
      const clientId = uuidv4();
      const clientSecret = uuidv4();
      const redirectUri = "http://localhost:8080/callback";

      const clientData = {
        client_id: clientId,
        client_secret: clientSecret,
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code", "password"],
        response_types: ["code"],
        scope: "openid profile email",
        token_endpoint_auth_method: "client_secret_post"
      };

      const createClientResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${adminAccessToken}`,
        },
        body: clientData
      });

      console.log("Client registration response:", createClientResponse.status);
      expect(createClientResponse.status).toBe(201);
      console.log(`✓ Client registered: ${clientId}\n`);

      // Step 4: Initial user registration via authorization flow with password policy validation
      console.log("Step 4: Performing initial user registration with authorization flow...");
      const initialPassword = "Initial12!";
      const userEmail = faker.internet.email();
      const preferredUsername = userEmail;
      const userName = faker.person.fullName();

      const interaction = async (id, user) => {
        const registrationResponse = await postWithJson({
          url: `${backendUrl}/${tenantId}/v1/authorizations/${id}/initial-registration`,
          body: {
            email: user.email,
            password: user.password,
            name: user.name
          }
        });

        console.log("Initial registration response:", registrationResponse.status, registrationResponse.data);
        expect(registrationResponse.status).toBe(200);
      };

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        authorizeEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
        denyEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/deny`,
        clientId: clientId,
        responseType: "code",
        state: `state_${Date.now()}`,
        scope: "openid profile email",
        redirectUri: redirectUri,
        user: {
          email: userEmail,
          password: initialPassword,
          name: userName
        },
        interaction,
      });

      console.log("Authorization response:", authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();
      console.log("✓ Can not authorize\n");

      // Step 5: Exchange authorization code for access token
      console.log("Step 5: Exchanging authorization code for token...");
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: redirectUri,
        clientId: clientId,
        clientSecret: clientSecret,
      });

      console.log("Token response:", tokenResponse.status, tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
      expect(tokenResponse.data).toHaveProperty("id_token");
      const userAccessToken = tokenResponse.data.access_token;
      console.log("✓ Access token obtained\n");

      const userinfoResponse = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/userinfo`,
        headers: {
          Authorization: `Bearer ${userAccessToken}`
        }
      });
      console.log(JSON.stringify(userinfoResponse.data, null, 2));
      expect(userinfoResponse.status).toBe(200);

      const user = userinfoResponse.data;

      // Step 6: Test all inactive user statuses with OAuth Authorization Flow
      const inactiveStatuses = ["LOCKED", "DISABLED", "SUSPENDED", "DEACTIVATED", "DELETED_PENDING", "DELETED"];

      for (const status of inactiveStatuses) {
        console.log(`\nStep 6: Testing status ${status} with OAuth Authorization Flow...`);

        // Change user status
        console.log(`Changing user status to ${status}...`);
        const changeStatusResponse = await patchWithJson({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/users/${user.sub}`,
          headers: {
            Authorization: `Bearer ${adminAccessToken}`,
          },
          body: {
            status
          }
        });
        console.log(JSON.stringify(changeStatusResponse.data, null, 2));
        expect(changeStatusResponse.status).toBe(200);
        expect(changeStatusResponse.data.diff).toHaveProperty("status");
        console.log(`✓ User status changed to ${status}`);

        // Attempt OAuth authorization with inactive user
        const passwordInteraction = async (id, user) => {
          const passwordAuthenticationResponse = await postWithJson({
            url: `${backendUrl}/${tenantId}/v1/authorizations/${id}/password-authentication`,
            body: {
              username: preferredUsername,
              password: user.password,
            }
          });

          console.log(JSON.stringify(passwordAuthenticationResponse.data, null, 2));
          expect(passwordAuthenticationResponse.status).toBe(200);
        };

        console.log(`Attempting OAuth authorization with ${status} user...`);
        const { authorizationResponse } = await requestAuthorizations({
          endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          authorizeEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
          denyEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/deny`,
          clientId: clientId,
          responseType: "code",
          state: `state_${Date.now()}`,
          scope: "openid profile email",
          redirectUri: redirectUri,
          user: {
            email: userEmail,
            password: initialPassword,
            name: userName
          },
          interaction: passwordInteraction,
        });

        console.log(`Authorization response for ${status}:`, authorizationResponse);
        expect(authorizationResponse.code).toBeUndefined();
        console.log(`✓ OAuth authorization correctly rejected for ${status} user\n`);
      }


    }, 90000); // Extended timeout for full flow with tenant creation

  });

  describe("CIBA flow", () => {

    it("CIBA authentication request should fail when user status is LOCKED", async () => {
      console.log("\n=== Starting CIBA User Status Validation Test ===\n");

      // Step 1: Get admin access token
      console.log("Step 1: Getting admin access token...");
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
      const adminAccessToken = adminTokenResponse.data.access_token;
      console.log("✓ Admin access token obtained\n");

      // Step 2: Create new tenant with CIBA support
      console.log("Step 2: Creating new tenant with CIBA support...");
      const tenantId = uuidv4();
      const { jwks } = await generateRS256KeyPair();

      const tenantData = {
        tenant: {
          id: tenantId,
          name: "CIBA User Status Test Tenant",
          domain: "http://localhost:8080",
          description: "Test tenant for CIBA user status validation",
          authorization_provider: "idp-server",
          tenant_type: "BUSINESS"
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          backchannel_authentication_endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/.well-known/jwks.json`,
          jwks: jwks,
          scopes_supported: ["openid", "profile", "email"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code", "password", "urn:openid:params:grant-type:ciba"],
          token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
          id_token_signing_alg_values_supported: ["RS256"],
          claims_supported: ["sub", "name", "email", "email_verified", "preferred_username"],
          backchannel_token_delivery_modes_supported: ["poll"],
          backchannel_authentication_request_signing_alg_values_supported: ["RS256"],
          backchannel_user_code_parameter_supported: true,
          extension: {
            access_token_type: "JWT",
            access_token_duration: 3600,
            id_token_duration: 3600
          }
        }
      };

      const createTenantResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants`,
        headers: {
          Authorization: `Bearer ${adminAccessToken}`,
        },
        body: tenantData
      });
      expect(createTenantResponse.status).toBe(201);
      console.log(`✓ Tenant created: ${tenantId}\n`);

      // Step 3: Register CIBA client
      console.log("Step 3: Registering CIBA client...");
      const clientId = uuidv4();
      const clientSecret = uuidv4();

      const clientData = {
        client_id: clientId,
        client_secret: clientSecret,
        redirect_uris: ["http://localhost:8080/callback"],
        grant_types: ["authorization_code", "urn:openid:params:grant-type:ciba"],
        response_types: ["code"],
        scope: "openid profile email",
        token_endpoint_auth_method: "client_secret_post",
        backchannel_token_delivery_mode: "poll",
        backchannel_user_code_parameter_supported: true
      };

      const createClientResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${adminAccessToken}`,
        },
        body: clientData
      });
      console.log(JSON.stringify(createClientResponse.data, null, 2));
      expect(createClientResponse.status).toBe(201);
      console.log(`✓ CIBA client registered: ${clientId}\n`);

      // Step 4: Create user via authorization flow
      console.log("Step 4: Creating user via authorization flow...");
      const userEmail = faker.internet.email();
      const userName = faker.person.fullName();
      const password = "TestPassword123!";

      const interaction = async (id, user) => {
        const registrationResponse = await postWithJson({
          url: `${backendUrl}/${tenantId}/v1/authorizations/${id}/initial-registration`,
          body: {
            email: user.email,
            password: user.password,
            name: user.name
          }
        });
        expect(registrationResponse.status).toBe(200);
      };

      const redirectUri = "http://localhost:8080/callback";
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        authorizeEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
        denyEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/deny`,
        clientId: clientId,
        responseType: "code",
        state: `state_${Date.now()}`,
        scope: "openid profile email",
        redirectUri: redirectUri,
        user: {
          email: userEmail,
          password: password,
          name: userName
        },
        interaction,
      });

      console.log(authorizationResponse);
      expect(authorizationResponse.code).not.toBeNull();
      console.log("✓ User created via authorization flow\n");

      // Step 5: Get user info
      console.log("Step 5: Getting user info...");
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: redirectUri,
        clientId: clientId,
        clientSecret: clientSecret,
      });
      expect(tokenResponse.status).toBe(200);

      const userinfoResponse = await postWithJson({
        url: `${backendUrl}/${tenantId}/v1/userinfo`,
        headers: {
          Authorization: `Bearer ${tokenResponse.data.access_token}`
        }
      });
      expect(userinfoResponse.status).toBe(200);
      const user = userinfoResponse.data;
      console.log(`✓ User info obtained: ${user.sub}\n`);

      // Step 6-7: Test all inactive user statuses
      const inactiveStatuses = ["LOCKED", "DISABLED", "SUSPENDED", "DEACTIVATED", "DELETED_PENDING", "DELETED"];

      for (const status of inactiveStatuses) {
        console.log(`\nStep 6-7: Testing status ${status}...`);

        // Change user status
        console.log(`Changing user status to ${status}...`);
        const changeStatusResponse = await patchWithJson({
          url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/users/${user.sub}`,
          headers: {
            Authorization: `Bearer ${adminAccessToken}`,
          },
          body: {
            status: status
          }
        });
        expect(changeStatusResponse.status).toBe(200);
        expect(changeStatusResponse.data.diff).toHaveProperty("status");
        console.log(`✓ User status changed to ${status}`);

        // Attempt CIBA authentication with inactive user
        console.log(`Attempting CIBA authentication with ${status} user...`);
        const cibaResponse = await requestBackchannelAuthentications({
          endpoint: `${backendUrl}/${tenantId}/v1/backchannel/authentications`,
          clientId: clientId,
          clientSecret: clientSecret,
          scope: "openid profile email",
          loginHint: `sub:${user.sub},idp:idp-server`,
          bindingMessage: "1234",
        });

        console.log(`CIBA response for ${status}:`, cibaResponse.status, cibaResponse.data);

        // Expect 403 Forbidden with access_denied error
        expect(cibaResponse.status).toBe(403);
        expect(cibaResponse.data.error).toBe("access_denied");
        expect(cibaResponse.data.error_description).toContain(status);
        console.log(`✓ CIBA authentication correctly rejected for ${status} user\n`);
      }

    }, 90000); // Extended timeout for full flow with tenant creation

  });


});
