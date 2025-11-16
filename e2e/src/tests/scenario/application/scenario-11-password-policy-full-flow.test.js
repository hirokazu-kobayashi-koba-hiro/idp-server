import { describe, expect, it } from "@jest/globals";
import { requestToken, postAuthentication } from "../../../api/oauthClient";
import { backendUrl, clientSecretPostClient, mockApiBaseUrl, serverConfig } from "../../testConfig";
import { post, postWithJson, get } from "../../../lib/http";
import { faker } from "@faker-js/faker";
import { v4 as uuidv4 } from "uuid";
import { requestAuthorizations } from "../../../oauth/request";
import { generateRS256KeyPair } from "../../../lib/jose";

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
describe("Issue #741: Password Policy Full Flow", () => {


  /**
   * Helper function to change password
   */
  const changePassword = async (tenantId, accessToken, currentPassword, newPassword) => {
    return await post({
      url: `${backendUrl}/${tenantId}/v1/me/password/change`,
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${accessToken}`,
      },
      body: {
        current_password: currentPassword,
        new_password: newPassword,
      },
    });
  };

  describe("Complete Password Policy Flow", () => {

    it("should validate password policy through entire user lifecycle", async () => {
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
      console.log("✓ User registered and authorization code obtained\n");

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

      // Step 6: Change password (validates password policy)
      console.log("Step 6: Changing password...");
      const newPassword = "NewPass456!";
      const changePasswordResponse = await changePassword(
        tenantId,
        userAccessToken,
        initialPassword,
        newPassword
      );

      console.log("Password change response:", changePasswordResponse.status, changePasswordResponse.data);
      expect(changePasswordResponse.status).toBe(200);
      expect(changePasswordResponse.data.message).toEqual("Password changed successfully.");
      console.log("✓ Password changed successfully\n");

      // Step 7: Verify login with new password using password grant
      console.log("Step 7: Verifying login with new password...");
      // Note: Issue #897 - password grant currently searches by 'name' column instead of 'preferred_username'
      const newPasswordTokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "password",
        username: preferredUsername,  // Use userName (name field) instead of userEmail due to Issue #897
        password: newPassword,
        scope: "openid profile email",
        clientId: clientId,
        clientSecret: clientSecret,
      });

      console.log("Login with new password response:", newPasswordTokenResponse.status, newPasswordTokenResponse.data);
      expect(newPasswordTokenResponse.status).toBe(200);
      expect(newPasswordTokenResponse.data).toHaveProperty("access_token");
      expect(newPasswordTokenResponse.data).toHaveProperty("id_token");
      console.log("✓ Login with new password successful\n");

      // Step 8: Verify old password no longer works
      console.log("Step 8: Verifying old password is invalid...");
      const oldPasswordTokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        grantType: "password",
        username: userName,  // Use userName (name field) due to Issue #897
        password: initialPassword,
        scope: "openid profile email",
        clientId: clientId,
        clientSecret: clientSecret,
      });

      console.log("Login with old password response:", oldPasswordTokenResponse.status);
      expect(oldPasswordTokenResponse.status).toBe(400);
      expect(oldPasswordTokenResponse.data.error).toEqual("invalid_grant");
      console.log("✓ Old password correctly rejected\n");

      // Step 9: Verify user info includes mapped data from external service
      console.log("Step 7: Verifying user info from external service mapping...");
      const userInfoResponse = await get({
        url: `${backendUrl}/${tenantId}/v1/userinfo`,
        headers: {
          Authorization: `Bearer ${tokenResponse.data.access_token}`,
        },
      });

      console.log("UserInfo response:", userInfoResponse.status, userInfoResponse.data);
      expect(userInfoResponse.status).toBe(200);
      expect(userInfoResponse.data).toHaveProperty("sub");
      expect(userInfoResponse.data).toHaveProperty("email", userEmail);
      console.log("✓ User info verified (mapped from external service)\n");

      console.log("=== Password Policy Full Flow Test Complete ===\n");
    }, 90000); // Extended timeout for full flow with tenant creation

  });

  describe("External Service Password Authentication (Issue #898)", () => {

    it("should authenticate via external service and map user with http_request", async () => {
      console.log("\n=== Starting External Service Password Authentication Test ===\n");

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
          name: "External Auth Test Tenant",
          domain: "http://localhost:8080",
          description: "Test tenant for external password authentication",
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
          claims_supported: ["sub", "name", "email", "email_verified", "preferred_username"],
          claim_types_supported: ["normal"],
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

      // Step 4: Configure external password authentication with http_request (Issue #898)
      console.log("Step 4: Configuring external password authentication...");
      const authConfigId = uuidv4();

      const authenticationConfig = {
        id: authConfigId,
        type: "password",
        attributes: {},
        metadata: {
          description: "External password authentication via Mockoon for Issue #898"
        },
        interactions: {
          "password-authentication": {
            execution: {
              function: "http_request",
              http_request: {
                url: `${mockApiBaseUrl}/auth/password`,
                method: "POST",
                header_mapping_rules: [
                  { "static_value": "application/json", "to": "Content-Type" }
                ],
                body_mapping_rules: [
                  { "from": "$.request_body.username", "to": "username" },
                  { "from": "$.request_body.password", "to": "password" }
                ]
              }
            },
            user_resolve: {
              user_mapping_rules: [
                { "from": "$.execution_http_request.response_body.user_id", "to": "external_user_id" },
                { "from": "$.execution_http_request.response_body.email", "to": "email" },
                { "from": "$.execution_http_request.response_body.name", "to": "name" },
                { "static_value": "mockoon", "to": "provider_id" }
              ]
            },
            response: {
              body_mapping_rules: [
                { "from": "$.execution_http_request.response_body.user_id", "to": "user_id" },
                { "from": "$.execution_http_request.response_body.email", "to": "email" }
              ]
            }
          }
        }
      };

      const createAuthConfigResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${tenantId}/authentication-configurations`,
        headers: {
          Authorization: `Bearer ${adminAccessToken}`,
        },
        body: authenticationConfig
      });

      console.log("Authentication config response:", createAuthConfigResponse.status);
      console.log(JSON.stringify( createAuthConfigResponse.data, null, 2));
      expect(createAuthConfigResponse.status).toBe(201);
      console.log(`✓ Authentication configuration created: ${authConfigId}\n`);

      // Step 5: Password authentication via external service (Authorization Flow)
      console.log("Step 5: Password authentication via external service...");
      const userEmail = faker.internet.email();
      const testPassword = "ExternalPass123!";

      const interaction = async (id, user) => {
        const passwordAuthResponse = await postWithJson({
          url: `${backendUrl}/${tenantId}/v1/authorizations/${id}/password-authentication`,
          body: {
            username: user.email,
            password: user.password
          }
        });

        console.log("Password authentication response:", passwordAuthResponse.status, passwordAuthResponse.data);
        expect(passwordAuthResponse.status).toBe(200);
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
          password: testPassword
        },
        interaction,
      });

      expect(authorizationResponse.code).not.toBeNull();
      console.log("✓ Password authentication via external service successful\n");

      // Step 6: Exchange authorization code for token
      console.log("Step 6: Exchanging authorization code for token...");
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: redirectUri,
        clientId: clientId,
        clientSecret: clientSecret,
      });

      console.log(JSON.stringify(tokenResponse.data, null, 2));
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("access_token");
      expect(tokenResponse.data).toHaveProperty("id_token");
      console.log("✓ Token obtained\n");

      // Step 7: Verify user info includes mapped data from external service
      console.log("Step 7: Verifying user info from external service mapping...");
      const userInfoResponse = await get({
        url: `${backendUrl}/${tenantId}/v1/userinfo`,
        headers: {
          Authorization: `Bearer ${tokenResponse.data.access_token}`,
        },
      });

      console.log("UserInfo response:", userInfoResponse.status, userInfoResponse.data);
      expect(userInfoResponse.status).toBe(200);
      expect(userInfoResponse.data).toHaveProperty("sub");
      expect(userInfoResponse.data).toHaveProperty("email", userEmail);
      console.log("✓ User info verified (mapped from external service)\n");

      console.log("=== External Service Password Authentication Test Complete ===\n");
    }, 90000);

  });

});
