import { describe, expect, it } from "@jest/globals";
import { requestToken, postAuthentication, requestBackchannelAuthentications } from "../../api/oauthClient";
import { adminServerConfig, backendUrl, clientSecretPostClient, mockApiBaseUrl, serverConfig } from "../testConfig";
import { post, postWithJson, get, patchWithJson, deletion } from "../../lib/http";
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

  describe("Userinfo flow - Issue #901", () => {

    it("Userinfo should fail when user status is not active", async () => {
      console.log("\n=== Starting Userinfo User Status Validation Test ===\n");

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

      // Step 2: Create new tenant
      console.log("Step 2: Creating new tenant...");
      const tenantId = uuidv4();
      const { jwks } = await generateRS256KeyPair();

      const tenantData = {
        tenant: {
          id: tenantId,
          name: "Userinfo Status Test Tenant",
          domain: "http://localhost:8080",
          description: "Test tenant for userinfo status validation",
          authorization_provider: "idp-server",
          tenant_type: "BUSINESS"
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/.well-known/jwks.json`,
          jwks: jwks,
          scopes_supported: ["openid", "profile", "email"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code", "password"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          id_token_signing_alg_values_supported: ["RS256"],
          claims_supported: ["sub", "name", "email"],
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

      // Step 3: Register client
      console.log("Step 3: Registering client...");
      const clientId = uuidv4();
      const clientSecret = uuidv4();
      const redirectUri = "http://localhost:8080/callback";

      const clientData = {
        client_id: clientId,
        client_secret: clientSecret,
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code"],
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
      expect(createClientResponse.status).toBe(201);
      console.log(`✓ Client registered: ${clientId}\n`);

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

      expect(authorizationResponse.code).not.toBeNull();
      console.log("✓ User created via authorization flow\n");

      // Step 5: Get access token
      console.log("Step 5: Getting access token...");
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: redirectUri,
        clientId: clientId,
        clientSecret: clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      const userAccessToken = tokenResponse.data.access_token;
      console.log("✓ Access token obtained\n");

      // Verify userinfo works initially
      const initialUserinfoResponse = await get({
        url: `${backendUrl}/${tenantId}/v1/userinfo`,
        headers: {
          Authorization: `Bearer ${userAccessToken}`
        }
      });
      expect(initialUserinfoResponse.status).toBe(200);
      const user = initialUserinfoResponse.data;
      console.log(`✓ Initial userinfo verified for user: ${user.sub}\n`);

      // Step 6: Test all inactive user statuses
      const inactiveStatuses = ["LOCKED", "DISABLED", "SUSPENDED", "DEACTIVATED", "DELETED_PENDING", "DELETED"];

      for (const status of inactiveStatuses) {
        console.log(`\nStep 6: Testing status ${status} with Userinfo endpoint...`);

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
        console.log(`✓ User status changed to ${status}`);

        // Attempt userinfo with inactive user
        console.log(`Attempting userinfo access with ${status} user...`);
        const userinfoResponse = await get({
          url: `${backendUrl}/${tenantId}/v1/userinfo`,
          headers: {
            Authorization: `Bearer ${userAccessToken}`
          }
        });

        console.log(`Userinfo response for ${status}:`, userinfoResponse.status, userinfoResponse.data);

        // Expect 401 Unauthorized with invalid_token error
        expect(userinfoResponse.status).toBe(401);
        expect(userinfoResponse.data.error).toBe("invalid_token");
        expect(userinfoResponse.data.error_description).toContain("user is not active");
        expect(userinfoResponse.data.error_description).toContain(status);
        console.log(`✓ Userinfo correctly rejected for ${status} user\n`);
      }

    }, 90000);

  });

  describe("Refresh Token flow - Issue #900", () => {

    it("Refresh token should fail when user status is not active", async () => {
      console.log("\n=== Starting Refresh Token User Status Validation Test ===\n");

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

      // Step 2: Create new tenant
      console.log("Step 2: Creating new tenant...");
      const tenantId = uuidv4();
      const { jwks } = await generateRS256KeyPair();

      const tenantData = {
        tenant: {
          id: tenantId,
          name: "Refresh Token Status Test Tenant",
          domain: "http://localhost:8080",
          description: "Test tenant for refresh token status validation",
          authorization_provider: "idp-server",
          tenant_type: "BUSINESS"
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/.well-known/jwks.json`,
          jwks: jwks,
          scopes_supported: ["openid", "profile", "email", "offline_access"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code", "refresh_token"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          id_token_signing_alg_values_supported: ["RS256"],
          claims_supported: ["sub", "name", "email"],
          extension: {
            access_token_type: "JWT",
            access_token_duration: 3600,
            refresh_token_duration: 86400,
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

      // Step 3: Register client with refresh token support
      console.log("Step 3: Registering client with refresh token support...");
      const clientId = uuidv4();
      const clientSecret = uuidv4();
      const redirectUri = "http://localhost:8080/callback";

      const clientData = {
        client_id: clientId,
        client_secret: clientSecret,
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code", "refresh_token"],
        response_types: ["code"],
        scope: "openid profile email offline_access",
        token_endpoint_auth_method: "client_secret_post"
      };

      const createClientResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${adminAccessToken}`,
        },
        body: clientData
      });
      expect(createClientResponse.status).toBe(201);
      console.log(`✓ Client registered: ${clientId}\n`);

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

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        authorizeEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
        denyEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/deny`,
        clientId: clientId,
        responseType: "code",
        state: `state_${Date.now()}`,
        scope: "openid profile email offline_access",
        redirectUri: redirectUri,
        user: {
          email: userEmail,
          password: password,
          name: userName
        },
        interaction,
      });

      expect(authorizationResponse.code).not.toBeNull();
      console.log("✓ User created via authorization flow\n");

      // Step 5: Get access token and refresh token
      console.log("Step 5: Getting access token and refresh token...");
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: redirectUri,
        clientId: clientId,
        clientSecret: clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("refresh_token");
      const userAccessToken = tokenResponse.data.access_token;
      let refreshToken = tokenResponse.data.refresh_token;
      console.log("✓ Access token and refresh token obtained\n");

      // Verify userinfo works initially
      const initialUserinfoResponse = await get({
        url: `${backendUrl}/${tenantId}/v1/userinfo`,
        headers: {
          Authorization: `Bearer ${userAccessToken}`
        }
      });
      expect(initialUserinfoResponse.status).toBe(200);
      const user = initialUserinfoResponse.data;
      console.log(`✓ Initial userinfo verified for user: ${user.sub}\n`);

      // Step 6: Test all inactive user statuses
      const inactiveStatuses = ["LOCKED", "DISABLED", "SUSPENDED", "DEACTIVATED", "DELETED_PENDING", "DELETED"];

      for (const status of inactiveStatuses) {
        console.log(`\nStep 6: Testing status ${status} with Refresh Token...`);

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
        console.log(`✓ User status changed to ${status}`);

        // Attempt refresh token with inactive user
        console.log(`Attempting refresh token with ${status} user...`);
        const refreshTokenResponse = await requestToken({
          endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          grantType: "refresh_token",
          refreshToken: refreshToken,
          clientId: clientId,
          clientSecret: clientSecret,
        });

        console.log(`Refresh token response for ${status}:`, refreshTokenResponse.status, refreshTokenResponse.data);

        // Expect 400 Bad Request with invalid_grant error
        expect(refreshTokenResponse.status).toBe(400);
        expect(refreshTokenResponse.data.error).toBe("invalid_grant");
        expect(refreshTokenResponse.data.error_description).toContain("user is not active");
        expect(refreshTokenResponse.data.error_description).toContain(status);
        console.log(`✓ Refresh token correctly rejected for ${status} user\n`);

        // Restore user status to REGISTERED for next iteration
        if (status !== "DELETED") {
          const restoreStatusResponse = await patchWithJson({
            url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/users/${user.sub}`,
            headers: {
              Authorization: `Bearer ${adminAccessToken}`,
            },
            body: {
              status: "REGISTERED"
            }
          });
          expect(restoreStatusResponse.status).toBe(200);
          console.log("✓ User status restored to REGISTERED");

          // Get a new refresh token for next iteration
          const newTokenResponse = await requestToken({
            endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
            grantType: "refresh_token",
            refreshToken: refreshToken,
            clientId: clientId,
            clientSecret: clientSecret,
          });
          if (newTokenResponse.status === 200 && newTokenResponse.data.refresh_token) {
            refreshToken = newTokenResponse.data.refresh_token;
            console.log("✓ New refresh token obtained for next iteration");
          }
        }
      }

    }, 90000);

  });

  describe("Password Grant flow - Issue #900", () => {

    it("Password grant should fail when user status is not active", async () => {
      console.log("\n=== Starting Password Grant User Status Validation Test ===\n");

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

      // Step 2: Create new tenant
      console.log("Step 2: Creating new tenant...");
      const tenantId = uuidv4();
      const { jwks } = await generateRS256KeyPair();

      const tenantData = {
        tenant: {
          id: tenantId,
          name: "Password Grant Status Test Tenant",
          domain: "http://localhost:8080",
          description: "Test tenant for password grant status validation",
          authorization_provider: "idp-server",
          tenant_type: "BUSINESS"
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/.well-known/jwks.json`,
          jwks: jwks,
          scopes_supported: ["openid", "profile", "email"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code", "password"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          id_token_signing_alg_values_supported: ["RS256"],
          claims_supported: ["sub", "name", "email"],
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

      // Step 3: Register client with password grant support
      console.log("Step 3: Registering client with password grant support...");
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
      expect(createClientResponse.status).toBe(201);
      console.log(`✓ Client registered: ${clientId}\n`);

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

      expect(authorizationResponse.code).not.toBeNull();
      console.log("✓ User created via authorization flow\n");

      // Step 5: Verify password grant works initially
      console.log("Step 5: Verifying password grant works initially...");
      const initialPasswordGrantResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "password",
        username: userEmail,
        password: password,
        scope: "openid profile email",
        clientId: clientId,
        clientSecret: clientSecret
      });
      expect(initialPasswordGrantResponse.status).toBe(200);
      expect(initialPasswordGrantResponse.data).toHaveProperty("access_token");
      console.log("✓ Password grant works initially\n");

      // Get user info for status changes
      const userinfoResponse = await get({
        url: `${backendUrl}/${tenantId}/v1/userinfo`,
        headers: {
          Authorization: `Bearer ${initialPasswordGrantResponse.data.access_token}`
        }
      });
      expect(userinfoResponse.status).toBe(200);
      const user = userinfoResponse.data;
      console.log(`✓ User info obtained: ${user.sub}\n`);

      // Step 6: Test all inactive user statuses
      const inactiveStatuses = ["LOCKED", "DISABLED", "SUSPENDED", "DEACTIVATED", "DELETED_PENDING", "DELETED"];

      for (const status of inactiveStatuses) {
        console.log(`\nStep 6: Testing status ${status} with Password Grant...`);

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
        console.log(`✓ User status changed to ${status}`);

        // Attempt password grant with inactive user
        console.log(`Attempting password grant with ${status} user...`);
        const passwordGrantResponse = await requestToken({
          endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          grantType: "password",
          username: userEmail,
          password: password,
          scope: "openid profile email",
          clientId: clientId,
          clientSecret: clientSecret
        });

        console.log(`Password grant response for ${status}:`, passwordGrantResponse.status, passwordGrantResponse.data);

        // Expect 400 Bad Request with invalid_grant error
        expect(passwordGrantResponse.status).toBe(400);
        expect(passwordGrantResponse.data.error).toBe("invalid_grant");
        expect(passwordGrantResponse.data.error_description).toContain("user is not active");
        expect(passwordGrantResponse.data.error_description).toContain(status);
        console.log(`✓ Password grant correctly rejected for ${status} user\n`);

        // Restore user status to REGISTERED for next iteration
        if (status !== "DELETED") {
          const restoreStatusResponse = await patchWithJson({
            url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/users/${user.sub}`,
            headers: {
              Authorization: `Bearer ${adminAccessToken}`,
            },
            body: {
              status: "REGISTERED"
            }
          });
          expect(restoreStatusResponse.status).toBe(200);
          console.log("✓ User status restored to REGISTERED");
        }
      }

    }, 90000);

  });

  describe("Token Introspection flow - Issue #900", () => {

    it("Token introspection should return active:false when user status is not active", async () => {
      console.log("\n=== Starting Token Introspection User Status Validation Test ===\n");

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

      // Step 2: Create new tenant
      console.log("Step 2: Creating new tenant...");
      const tenantId = uuidv4();
      const { jwks } = await generateRS256KeyPair();

      const tenantData = {
        tenant: {
          id: tenantId,
          name: "Token Introspection Status Test Tenant",
          domain: "http://localhost:8080",
          description: "Test tenant for token introspection status validation",
          authorization_provider: "idp-server",
          tenant_type: "BUSINESS"
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/.well-known/jwks.json`,
          jwks: jwks,
          scopes_supported: ["openid", "profile", "email"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code"],
          token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
          id_token_signing_alg_values_supported: ["RS256"],
          claims_supported: ["sub", "name", "email"],
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

      // Step 3: Register client
      console.log("Step 3: Registering client...");
      const clientId = uuidv4();
      const clientSecret = uuidv4();
      const redirectUri = "http://localhost:8080/callback";

      const clientData = {
        client_id: clientId,
        client_secret: clientSecret,
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code"],
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
      expect(createClientResponse.status).toBe(201);
      console.log(`✓ Client registered: ${clientId}\n`);

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

      expect(authorizationResponse.code).not.toBeNull();
      console.log("✓ User created via authorization flow\n");

      // Step 5: Get access token
      console.log("Step 5: Getting access token...");
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: redirectUri,
        clientId: clientId,
        clientSecret: clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      const userAccessToken = tokenResponse.data.access_token;
      console.log("✓ Access token obtained\n");

      // Verify token introspection works initially
      const initialIntrospectionResponse = await post({
        url: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
        headers: {
          "Content-Type": "application/x-www-form-urlencoded"
        },
        body: `token=${userAccessToken}&client_id=${clientId}&client_secret=${clientSecret}`
      });
      expect(initialIntrospectionResponse.status).toBe(200);
      expect(initialIntrospectionResponse.data.active).toBe(true);
      console.log("✓ Initial token introspection verified: active=true\n");

      // Get user info for status changes
      const userinfoResponse = await get({
        url: `${backendUrl}/${tenantId}/v1/userinfo`,
        headers: {
          Authorization: `Bearer ${userAccessToken}`
        }
      });
      expect(userinfoResponse.status).toBe(200);
      const user = userinfoResponse.data;

      // Step 6: Test all inactive user statuses
      const inactiveStatuses = ["LOCKED", "DISABLED", "SUSPENDED", "DEACTIVATED", "DELETED_PENDING", "DELETED"];

      for (const status of inactiveStatuses) {
        console.log(`\nStep 6: Testing status ${status} with Token Introspection...`);

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
        console.log(`✓ User status changed to ${status}`);

        // Attempt token introspection with inactive user's token
        console.log(`Attempting token introspection with ${status} user's token...`);
        const introspectionResponse = await post({
          url: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
          headers: {
            "Content-Type": "application/x-www-form-urlencoded"
          },
          body: `token=${userAccessToken}&client_id=${clientId}&client_secret=${clientSecret}`
        });

        console.log(`Token introspection response for ${status}:`, introspectionResponse.status, introspectionResponse.data);

        // Expect 200 OK with active: false (per RFC 7662)
        expect(introspectionResponse.status).toBe(200);
        expect(introspectionResponse.data.active).toBe(false);
        console.log(`✓ Token introspection correctly returned active:false for ${status} user\n`);

        // Restore user status to REGISTERED for next iteration
        if (status !== "DELETED") {
          const restoreStatusResponse = await patchWithJson({
            url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/users/${user.sub}`,
            headers: {
              Authorization: `Bearer ${adminAccessToken}`,
            },
            body: {
              status: "REGISTERED"
            }
          });
          expect(restoreStatusResponse.status).toBe(200);
          console.log("✓ User status restored to REGISTERED");
        }
      }

    }, 90000);

  });

  describe("Token Introspection Extension flow - Issue #900", () => {

    it("Token introspection extension should return active:false when user status is not active", async () => {
      console.log("\n=== Starting Token Introspection Extension User Status Validation Test ===\n");

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

      // Step 2: Create new tenant
      console.log("Step 2: Creating new tenant...");
      const tenantId = uuidv4();
      const { jwks } = await generateRS256KeyPair();

      const tenantData = {
        tenant: {
          id: tenantId,
          name: "Token Introspection Extension Status Test Tenant",
          domain: "http://localhost:8080",
          description: "Test tenant for token introspection extension status validation",
          authorization_provider: "idp-server",
          tenant_type: "BUSINESS"
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/.well-known/jwks.json`,
          jwks: jwks,
          scopes_supported: ["openid", "profile", "email"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code"],
          token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
          id_token_signing_alg_values_supported: ["RS256"],
          claims_supported: ["sub", "name", "email"],
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

      // Step 3: Register client
      console.log("Step 3: Registering client...");
      const clientId = uuidv4();
      const clientSecret = uuidv4();
      const redirectUri = "http://localhost:8080/callback";

      const clientData = {
        client_id: clientId,
        client_secret: clientSecret,
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code"],
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
      expect(createClientResponse.status).toBe(201);
      console.log(`✓ Client registered: ${clientId}\n`);

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

      expect(authorizationResponse.code).not.toBeNull();
      console.log("✓ User created via authorization flow\n");

      // Step 5: Get access token
      console.log("Step 5: Getting access token...");
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: redirectUri,
        clientId: clientId,
        clientSecret: clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      const userAccessToken = tokenResponse.data.access_token;
      console.log("✓ Access token obtained\n");

      // Verify token introspection extension works initially
      const initialIntrospectionResponse = await post({
        url: `${backendUrl}/${tenantId}/v1/tokens/introspection-extensions`,
        headers: {
          "Content-Type": "application/x-www-form-urlencoded"
        },
        body: `token=${userAccessToken}&client_id=${clientId}&client_secret=${clientSecret}`
      });
      expect(initialIntrospectionResponse.status).toBe(200);
      expect(initialIntrospectionResponse.data.active).toBe(true);
      console.log("✓ Initial token introspection extension verified: active=true\n");

      // Get user info for status changes
      const userinfoResponse = await get({
        url: `${backendUrl}/${tenantId}/v1/userinfo`,
        headers: {
          Authorization: `Bearer ${userAccessToken}`
        }
      });
      expect(userinfoResponse.status).toBe(200);
      const user = userinfoResponse.data;

      // Step 6: Test all inactive user statuses
      const inactiveStatuses = ["LOCKED", "DISABLED", "SUSPENDED", "DEACTIVATED", "DELETED_PENDING", "DELETED"];

      for (const status of inactiveStatuses) {
        console.log(`\nStep 6: Testing status ${status} with Token Introspection Extension...`);

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
        console.log(`✓ User status changed to ${status}`);

        // Attempt token introspection extension with inactive user's token
        console.log(`Attempting token introspection extension with ${status} user's token...`);
        const introspectionResponse = await post({
          url: `${backendUrl}/${tenantId}/v1/tokens/introspection-extensions`,
          headers: {
            "Content-Type": "application/x-www-form-urlencoded"
          },
          body: `token=${userAccessToken}&client_id=${clientId}&client_secret=${clientSecret}`
        });

        console.log(`Token introspection extension response for ${status}:`, introspectionResponse.status, JSON.stringify(introspectionResponse.data, null, 2));

        // Expect 200 OK with active: false and error details (per RFC 7662)
        expect(introspectionResponse.status).toBe(200);
        expect(introspectionResponse.data.active).toBe(false);
        expect(introspectionResponse.data.error).toBe("invalid_token");
        expect(introspectionResponse.data.error_description).toBeDefined();
        expect(introspectionResponse.data.status_code).toBe(401);
        console.log(`✓ Token introspection extension correctly returned active:false with error details for ${status} user\n`);

        // Restore user status to REGISTERED for next iteration
        if (status !== "DELETED") {
          const restoreStatusResponse = await patchWithJson({
            url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/users/${user.sub}`,
            headers: {
              Authorization: `Bearer ${adminAccessToken}`,
            },
            body: {
              status: "REGISTERED"
            }
          });
          expect(restoreStatusResponse.status).toBe(200);
          console.log("✓ User status restored to REGISTERED");
        }
      }

    }, 90000);

  });

  describe("User deletion after token issuance - Issue #900, #997", () => {

    it("Refresh token should fail when user is physically deleted after token issuance", async () => {
      console.log("\n=== Starting User Deletion After Token Issuance Test (Refresh Token) ===\n");

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

      // Step 2: Create new tenant
      console.log("Step 2: Creating new tenant...");
      const tenantId = uuidv4();
      const { jwks } = await generateRS256KeyPair();

      const tenantData = {
        tenant: {
          id: tenantId,
          name: "User Deletion Test Tenant (Refresh Token)",
          domain: "http://localhost:8080",
          description: "Test tenant for user deletion after token issuance",
          authorization_provider: "idp-server",
          tenant_type: "BUSINESS"
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/.well-known/jwks.json`,
          jwks: jwks,
          scopes_supported: ["openid", "profile", "email", "offline_access"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code", "refresh_token"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          id_token_signing_alg_values_supported: ["RS256"],
          claims_supported: ["sub", "name", "email"],
          extension: {
            access_token_type: "JWT",
            access_token_duration: 3600,
            refresh_token_duration: 86400,
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

      // Step 3: Register client with refresh token support
      console.log("Step 3: Registering client with refresh token support...");
      const clientId = uuidv4();
      const clientSecret = uuidv4();
      const redirectUri = "http://localhost:8080/callback";

      const clientData = {
        client_id: clientId,
        client_secret: clientSecret,
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code", "refresh_token"],
        response_types: ["code"],
        scope: "openid profile email offline_access",
        token_endpoint_auth_method: "client_secret_post"
      };

      const createClientResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/clients`,
        headers: {
          Authorization: `Bearer ${adminAccessToken}`,
        },
        body: clientData
      });
      expect(createClientResponse.status).toBe(201);
      console.log(`✓ Client registered: ${clientId}\n`);

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

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
        authorizeEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/authorize`,
        denyEndpoint: `${backendUrl}/${tenantId}/v1/authorizations/{id}/deny`,
        clientId: clientId,
        responseType: "code",
        state: `state_${Date.now()}`,
        scope: "openid profile email offline_access",
        redirectUri: redirectUri,
        user: {
          email: userEmail,
          password: password,
          name: userName
        },
        interaction,
      });

      expect(authorizationResponse.code).not.toBeNull();
      console.log("✓ User created via authorization flow\n");

      // Step 5: Get access token and refresh token
      console.log("Step 5: Getting access token and refresh token...");
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: redirectUri,
        clientId: clientId,
        clientSecret: clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("refresh_token");
      const userAccessToken = tokenResponse.data.access_token;
      const refreshToken = tokenResponse.data.refresh_token;
      console.log("✓ Access token and refresh token obtained\n");

      // Verify userinfo works initially
      const initialUserinfoResponse = await get({
        url: `${backendUrl}/${tenantId}/v1/userinfo`,
        headers: {
          Authorization: `Bearer ${userAccessToken}`
        }
      });
      expect(initialUserinfoResponse.status).toBe(200);
      const user = initialUserinfoResponse.data;
      console.log(`✓ Initial userinfo verified for user: ${user.sub}\n`);

      // Verify refresh token works initially
      console.log("Step 6: Verifying refresh token works initially...");
      const initialRefreshResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "refresh_token",
        refreshToken: refreshToken,
        clientId: clientId,
        clientSecret: clientSecret,
      });
      expect(initialRefreshResponse.status).toBe(200);
      console.log("✓ Refresh token works initially\n");

      // Step 7: Physically delete user via Management API
      console.log("Step 7: Physically deleting user via Management API...");
      const deleteUserResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/users/${user.sub}`,
        headers: {
          Authorization: `Bearer ${adminAccessToken}`,
        }
      });
      expect(deleteUserResponse.status).toBe(204);
      console.log(`✓ User ${user.sub} physically deleted\n`);

      // Step 8: Attempt refresh token with deleted user
      console.log("Step 8: Attempting refresh token with deleted user...");
      const refreshTokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "refresh_token",
        refreshToken: refreshToken,
        clientId: clientId,
        clientSecret: clientSecret,
      });

      console.log("Refresh token response:", refreshTokenResponse.status, refreshTokenResponse.data);

      // Expect 400 Bad Request with invalid_grant error
      // Note: When user is deleted, tokens may also be deleted (cascade delete)
      // So we may get "refresh token does not exists" or "not found user"
      expect(refreshTokenResponse.status).toBe(400);
      expect(refreshTokenResponse.data.error).toBe("invalid_grant");
      const validErrorMessages = ["not found user", "refresh token does not exists"];
      const hasValidErrorMessage = validErrorMessages.some(msg =>
        refreshTokenResponse.data.error_description.includes(msg)
      );
      expect(hasValidErrorMessage).toBe(true);
      console.log("✓ Refresh token correctly rejected for deleted user\n");

    }, 90000);

    it("Token introspection should return active:false when user is physically deleted after token issuance", async () => {
      console.log("\n=== Starting User Deletion After Token Issuance Test (Token Introspection) ===\n");

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

      // Step 2: Create new tenant
      console.log("Step 2: Creating new tenant...");
      const tenantId = uuidv4();
      const { jwks } = await generateRS256KeyPair();

      const tenantData = {
        tenant: {
          id: tenantId,
          name: "User Deletion Test Tenant (Introspection)",
          domain: "http://localhost:8080",
          description: "Test tenant for user deletion after token issuance (introspection)",
          authorization_provider: "idp-server",
          tenant_type: "BUSINESS"
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          introspection_endpoint: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/.well-known/jwks.json`,
          jwks: jwks,
          scopes_supported: ["openid", "profile", "email"],
          response_types_supported: ["code"],
          response_modes_supported: ["query"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code"],
          token_endpoint_auth_methods_supported: ["client_secret_post", "client_secret_basic"],
          id_token_signing_alg_values_supported: ["RS256"],
          claims_supported: ["sub", "name", "email"],
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

      // Step 3: Register client
      console.log("Step 3: Registering client...");
      const clientId = uuidv4();
      const clientSecret = uuidv4();
      const redirectUri = "http://localhost:8080/callback";

      const clientData = {
        client_id: clientId,
        client_secret: clientSecret,
        redirect_uris: [redirectUri],
        grant_types: ["authorization_code"],
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
      expect(createClientResponse.status).toBe(201);
      console.log(`✓ Client registered: ${clientId}\n`);

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

      expect(authorizationResponse.code).not.toBeNull();
      console.log("✓ User created via authorization flow\n");

      // Step 5: Get access token
      console.log("Step 5: Getting access token...");
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: redirectUri,
        clientId: clientId,
        clientSecret: clientSecret,
      });
      expect(tokenResponse.status).toBe(200);
      const userAccessToken = tokenResponse.data.access_token;
      console.log("✓ Access token obtained\n");

      // Verify token introspection works initially
      const initialIntrospectionResponse = await post({
        url: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
        headers: {
          "Content-Type": "application/x-www-form-urlencoded"
        },
        body: `token=${userAccessToken}&client_id=${clientId}&client_secret=${clientSecret}`
      });
      expect(initialIntrospectionResponse.status).toBe(200);
      expect(initialIntrospectionResponse.data.active).toBe(true);
      console.log("✓ Initial token introspection verified: active=true\n");

      // Get user info for deletion
      const userinfoResponse = await get({
        url: `${backendUrl}/${tenantId}/v1/userinfo`,
        headers: {
          Authorization: `Bearer ${userAccessToken}`
        }
      });
      expect(userinfoResponse.status).toBe(200);
      const user = userinfoResponse.data;
      console.log(`✓ User info obtained: ${user.sub}\n`);

      // Step 6: Physically delete user via Management API
      console.log("Step 6: Physically deleting user via Management API...");
      const deleteUserResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/users/${user.sub}`,
        headers: {
          Authorization: `Bearer ${adminAccessToken}`,
        }
      });
      expect(deleteUserResponse.status).toBe(204);
      console.log(`✓ User ${user.sub} physically deleted\n`);

      // Step 7: Attempt token introspection with deleted user's token
      console.log("Step 7: Attempting token introspection with deleted user's token...");
      const introspectionResponse = await post({
        url: `${backendUrl}/${tenantId}/v1/tokens/introspection`,
        headers: {
          "Content-Type": "application/x-www-form-urlencoded"
        },
        body: `token=${userAccessToken}&client_id=${clientId}&client_secret=${clientSecret}`
      });

      console.log("Token introspection response:", introspectionResponse.status, introspectionResponse.data);

      // Expect 200 OK with active: false (per RFC 7662)
      expect(introspectionResponse.status).toBe(200);
      expect(introspectionResponse.data.active).toBe(false);
      console.log("✓ Token introspection correctly returned active:false for deleted user\n");

    }, 90000);

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
