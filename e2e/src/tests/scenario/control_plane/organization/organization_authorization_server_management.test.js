import { describe, expect, it } from "@jest/globals";
import { get, postWithJson, putWithJson, deletion } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { backendUrl } from "../../../testConfig";
import { v4 as uuidv4 } from "uuid";

describe("organization authorization server management api", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";

  describe("success pattern", () => {
    it("create tenant, update authorization server, and delete tenant", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Step 1: Create a new tenant within the organization
      const timestamp = Date.now();
      const newTenantId = uuidv4();
      const createTenantResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          tenant: {
            "id": newTenantId,
            "name": `Test Tenant ${timestamp}`,
            "domain": "http://localhost:8080",
            "description": "Test tenant for authorization server management",
            "authorization_provider": "idp-server",
            "tenant_type": "BUSINESS"
          },
          authorization_server: {
            "issuer": `http://localhost:8080/${newTenantId}`,
            "authorization_endpoint": `http://localhost:8080/${newTenantId}/v1/authorizations`,
            "token_endpoint": `http://localhost:8080/${newTenantId}/v1/tokens`,
            "userinfo_endpoint": `http://localhost:8080/${newTenantId}/v1/userinfo`,
            "jwks_uri": `http://localhost:8080/${newTenantId}/v1/jwks`,
            "scopes_supported": [
              "openid",
              "profile",
              "email"
            ],
            "response_types_supported": [
              "code"
            ],
            "response_modes_supported": [
              "query",
              "fragment"
            ],
            "subject_types_supported": [
              "public"
            ],
            "grant_types_supported": [
              "authorization_code",
              "refresh_token"
            ],
            "token_endpoint_auth_methods_supported": [
              "client_secret_post",
              "client_secret_basic"
            ]
          }
        }
      });
      console.log("Create tenant response:", createTenantResponse.data);
      expect(createTenantResponse.status).toBe(201);
      expect(createTenantResponse.data).toHaveProperty("result");
      expect(createTenantResponse.data.result.id).toBe(newTenantId);
      console.log("✅ New tenant created successfully");

      // Step 2: Get current authorization server configuration
      const getAuthServerResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/authorization-server`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Get auth server response:", getAuthServerResponse.data);
      expect(getAuthServerResponse.status).toBe(200);
      expect(getAuthServerResponse.data).toHaveProperty("issuer");
      expect(getAuthServerResponse.data.issuer).toBe(`http://localhost:8080/${newTenantId}`);
      console.log("✅ Authorization server configuration retrieved successfully");

      // Step 3: Test dry run for authorization server update
      const dryRunUpdateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/authorization-server?dry_run=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "issuer": `https://updated-test-issuer.example.com/${newTenantId}`,
          "authorization_endpoint": `https://updated-test-issuer.example.com/${newTenantId}/authorize`,
          "token_endpoint": `https://updated-test-issuer.example.com/${newTenantId}/token`,
          "userinfo_endpoint": `https://updated-test-issuer.example.com/${newTenantId}/userinfo`,
          "jwks_uri": `https://updated-test-issuer.example.com/${newTenantId}/.well-known/jwks.json`,
          "scopes_supported": ["openid", "profile", "email", "phone"],
          "response_types_supported": ["code", "id_token"],
          "response_modes_supported": ["query", "fragment"],
          "subject_types_supported": ["public", "pairwise"],
          "grant_types_supported": ["authorization_code", "refresh_token", "client_credentials"]
        }
      });
      console.log("Dry run update response:", dryRunUpdateResponse.data);
      expect(dryRunUpdateResponse.status).toBe(200);
      expect(dryRunUpdateResponse.data).toHaveProperty("dry_run", true);
      console.log("✅ Dry run update functionality verified");

      // Step 4: Update authorization server configuration
      const updateAuthServerResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/authorization-server`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "issuer": `https://updated-test-issuer.example.com/${newTenantId}`,
          "authorization_endpoint": `https://updated-test-issuer.example.com/${newTenantId}/authorize`,
          "token_endpoint": `https://updated-test-issuer.example.com/${newTenantId}/token`,
          "userinfo_endpoint": `https://updated-test-issuer.example.com/${newTenantId}/userinfo`,
          "jwks_uri": `https://updated-test-issuer.example.com/${newTenantId}/.well-known/jwks.json`,
          "scopes_supported": ["openid", "profile", "email", "phone"],
          "response_types_supported": ["code", "id_token"],
          "response_modes_supported": ["query", "fragment"],
          "subject_types_supported": ["public", "pairwise"],
          "grant_types_supported": ["authorization_code", "refresh_token", "client_credentials"]
        }
      });
      console.log("Update auth server response:", updateAuthServerResponse.data);
      expect(updateAuthServerResponse.status).toBe(200);
      expect(updateAuthServerResponse.data).toBeDefined();
      console.log("✅ Authorization server configuration updated successfully");

      // Step 5: Verify the configuration was updated
      const verifyAuthServerResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/authorization-server`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Verify auth server response:", verifyAuthServerResponse.data);
      expect(verifyAuthServerResponse.status).toBe(200);
      expect(verifyAuthServerResponse.data).toHaveProperty("issuer", `https://updated-test-issuer.example.com/${newTenantId}`);
      expect(verifyAuthServerResponse.data).toHaveProperty("jwks_uri", `https://updated-test-issuer.example.com/${newTenantId}/.well-known/jwks.json`);
      expect(verifyAuthServerResponse.data.scopes_supported).toContain("phone");
      expect(verifyAuthServerResponse.data.response_types_supported).toContain("id_token");
      expect(verifyAuthServerResponse.data.subject_types_supported).toContain("pairwise");
      console.log("✅ Authorization server configuration update verified");

      // Step 6: Clean up - Delete the test tenant
      const deleteTenantResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Delete tenant response:", deleteTenantResponse.status);
      expect(deleteTenantResponse.status).toBe(204);
      console.log("✅ Test tenant deleted successfully");

      console.log("🎉 All authorization server management tests passed!");
    });

    it("error scenarios", async () => {
      // Get OAuth token
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const existingTenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";

      // Test 1: Try with invalid authorization
      const unauthorizedResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${existingTenantId}/authorization-server`,
        headers: {
          Authorization: "Bearer invalid-token"
        }
      });
      console.log("Unauthorized response:", unauthorizedResponse.status);
      expect(unauthorizedResponse.status).toBe(401);
      console.log("✅ Invalid token returns 401");

      // Test 2: Try with invalid organization ID
      const invalidOrgId = "00000000-0000-0000-0000-000000000000";
      const invalidOrgResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${invalidOrgId}/tenants/${existingTenantId}/authorization-server`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Invalid organization response:", invalidOrgResponse.status);
      expect(invalidOrgResponse.status).toBe(404);
      console.log("✅ Invalid organization returns 404");

      // Test 3: Try with invalid tenant ID
      const invalidTenantId = "00000000-0000-0000-0000-000000000000";
      const invalidTenantResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${invalidTenantId}/authorization-server`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Invalid tenant response:", invalidTenantResponse.status);
      expect(invalidTenantResponse.status).toBe(404);
      console.log("✅ Invalid tenant returns 404");

      // Test 4: Try with missing required field (missing jwks_uri)
      const invalidUpdateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${existingTenantId}/authorization-server`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "issuer": "https://example.com",
          "authorization_endpoint": "https://example.com/authorize",
          "token_endpoint": "https://example.com/token"
          // Missing required fields: jwks_uri, scopes_supported, response_types_supported, response_modes_supported, subject_types_supported
        }
      });
      console.log("Invalid update response:", invalidUpdateResponse.status);
      expect(invalidUpdateResponse.status).toBe(400);
      console.log("✅ Missing required fields returns 400");
    });

    it("verify OpenAPI specification fields support", async () => {
      // Get OAuth token
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Create a test tenant for OpenAPI verification
      const timestamp = Date.now();
      const newTenantId = uuidv4();
      const createTenantResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          tenant: {
            "id": newTenantId,
            "name": `OpenAPI Test Tenant ${timestamp}`,
            "domain": "http://localhost:8080",
            "description": "Test tenant for OpenAPI specification verification",
            "authorization_provider": "idp-server",
            "tenant_type": "BUSINESS"
          },
          authorization_server: {
            "issuer": `http://localhost:8080/${newTenantId}`,
            "authorization_endpoint": `http://localhost:8080/${newTenantId}/v1/authorizations`,
            "token_endpoint": `http://localhost:8080/${newTenantId}/v1/tokens`,
            "userinfo_endpoint": `http://localhost:8080/${newTenantId}/v1/userinfo`,
            "jwks_uri": `http://localhost:8080/${newTenantId}/v1/jwks`,
            "scopes_supported": ["openid", "profile", "email"],
            "response_types_supported": ["code"],
            "response_modes_supported": ["query", "fragment"],
            "subject_types_supported": ["public"],
            "grant_types_supported": ["authorization_code", "refresh_token"],
            "token_endpoint_auth_methods_supported": ["client_secret_post", "client_secret_basic"]
          }
        }
      });
      expect(createTenantResponse.status).toBe(201);
      console.log("✅ OpenAPI test tenant created successfully");

      // Test comprehensive OpenID Configuration with additional OpenAPI specification fields
      const comprehensiveConfig = {
        // Required fields per OpenAPI spec
        "issuer": `https://openapi-test.example.com/${newTenantId}`,
        "authorization_endpoint": `https://openapi-test.example.com/${newTenantId}/authorize`,
        "token_endpoint": `https://openapi-test.example.com/${newTenantId}/token`,
        "jwks_uri": `https://openapi-test.example.com/${newTenantId}/.well-known/jwks.json`,
        "scopes_supported": ["openid", "profile", "email", "phone", "address"],
        "response_types_supported": ["code", "id_token", "code id_token"],
        "response_modes_supported": ["query", "fragment"],
        "subject_types_supported": ["public", "pairwise"],

        // Additional optional fields from OpenAPI spec
        "userinfo_endpoint": `https://openapi-test.example.com/${newTenantId}/userinfo`,
        "registration_endpoint": `https://openapi-test.example.com/${newTenantId}/register`,
        "grant_types_supported": ["authorization_code", "refresh_token", "client_credentials"],
        "acr_values_supported": ["urn:mace:incommon:iap:silver", "urn:mace:incommon:iap:bronze"],
        "id_token_signing_alg_values_supported": ["RS256", "ES256"],
        "id_token_encryption_alg_values_supported": ["RSA1_5", "A128KW"],
        "id_token_encryption_enc_values_supported": ["A128CBC-HS256", "A128GCM"],
        "userinfo_signing_alg_values_supported": ["RS256", "ES256"],
        "userinfo_encryption_alg_values_supported": ["RSA1_5", "A128KW"],
        "userinfo_encryption_enc_values_supported": ["A128CBC-HS256", "A128GCM"],
        "request_object_signing_alg_values_supported": ["RS256", "ES256"],
        "request_object_encryption_alg_values_supported": ["RSA1_5", "A128KW"],
        "request_object_encryption_enc_values_supported": ["A128CBC-HS256", "A128GCM"],
        "token_endpoint_auth_methods_supported": ["client_secret_post", "client_secret_basic", "private_key_jwt"],
        "token_endpoint_auth_signing_alg_values_supported": ["RS256", "ES256"],
        "display_values_supported": ["page", "popup"],
        "claim_types_supported": ["normal"],
        "claims_supported": ["sub", "iss", "auth_time", "acr", "name", "given_name", "family_name", "nickname", "email", "email_verified", "phone_number", "phone_number_verified"],
        "service_documentation": "https://openapi-test.example.com/docs",
        "claims_locales_supported": ["en-US", "ja-JP"],
        "ui_locales_supported": ["en", "ja"],
        "claims_parameter_supported": true,
        "request_parameter_supported": true,
        "request_uri_parameter_supported": true,
        "require_request_uri_registration": false,

        // Extension configuration
        "extension": {
          "access_token_type": "JWT",
          "authorization_code_valid_duration": 300,
          "token_signed_key_id": "test-key-id",
          "id_token_signed_key_id": "test-id-token-key",
          "access_token_duration": 3600,
          "refresh_token_duration": 7200,
          "refresh_token_strategy": "ROTATE",
          "rotate_refresh_token": false,
          "id_token_duration": 1800,
          "id_token_strict_mode": true,
          "default_max_age": 43200,
          "authorization_response_duration": 120,
          "backchannel_authentication_request_expires_in": 600,
          "backchannel_authentication_polling_interval": 10,
          "required_backchannel_auth_user_code": true,
          "backchannel_auth_user_code_type": "numeric",
          "default_ciba_authentication_interaction_type": "authentication-device-polling",
          "oauth_authorization_request_expires_in": 3600,
          "fapi_baseline_scopes": ["openid", "profile"],
          "fapi_advance_scopes": ["openid", "profile", "email"],
          "required_identity_verification_scopes": ["identity_verification"],
          "custom_claims_scope_mapping": true,
          "access_token_selective_user_custom_properties": true,
          "access_token_verified_claims": true,
          "access_token_selective_verified_claims": true
        }
      };

      console.log("🚀 Sending extension configuration:");
      console.log("Extension fields being sent:", JSON.stringify(comprehensiveConfig.extension, null, 2));

      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/authorization-server`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: comprehensiveConfig
      });

      console.log("📨 Received response:");
      console.log(JSON.stringify(updateResponse.data, null, 2));
      console.log("Extension fields received:", JSON.stringify(updateResponse.data.result.extension, null, 2));
      expect(updateResponse.status).toBe(200);
      expect(updateResponse.data).toHaveProperty("result");
      console.log("✅ Comprehensive OpenID Configuration updated successfully");

      // Verify all registered fields are returned
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/authorization-server`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });

      expect(getResponse.status).toBe(200);
      const config = getResponse.data;

      console.log("\\n📋 Verifying OpenAPI specification fields:");

      // Verify required fields from OpenAPI spec
      const requiredFields = [
        "issuer", "authorization_endpoint", "token_endpoint", "jwks_uri",
        "scopes_supported", "response_types_supported", "response_modes_supported", "subject_types_supported"
      ];

      requiredFields.forEach(field => {
        expect(config).toHaveProperty(field);
        if (comprehensiveConfig[field] !== undefined) {
          expect(config[field]).toEqual(comprehensiveConfig[field]);
        }
        console.log(`✅ Required field ${field}: ${JSON.stringify(config[field])}`);
      });

      // Verify optional fields - check which ones are supported
      const optionalFields = [
        "userinfo_endpoint", "registration_endpoint", "grant_types_supported", "acr_values_supported",
        "id_token_signing_alg_values_supported", "id_token_encryption_alg_values_supported",
        "id_token_encryption_enc_values_supported", "userinfo_signing_alg_values_supported",
        "userinfo_encryption_alg_values_supported", "userinfo_encryption_enc_values_supported",
        "request_object_signing_alg_values_supported", "request_object_encryption_alg_values_supported",
        "request_object_encryption_enc_values_supported", "token_endpoint_auth_methods_supported",
        "token_endpoint_auth_signing_alg_values_supported", "display_values_supported",
        "claim_types_supported", "claims_supported", "service_documentation",
        "claims_locales_supported", "ui_locales_supported", "claims_parameter_supported",
        "request_parameter_supported", "request_uri_parameter_supported", "require_request_uri_registration",
        "extension"
      ];

      let supportedOptionalFields = 0;
      let totalOptionalFields = optionalFields.length;

      optionalFields.forEach(field => {
        if (config.hasOwnProperty(field)) {
          supportedOptionalFields++;
          console.log(`✅ Optional field ${field}: ${JSON.stringify(config[field])}`);

          // If we set a value, verify it matches
          if (comprehensiveConfig[field] !== undefined) {
            expect(config[field]).toEqual(comprehensiveConfig[field]);
          }
        } else {
          console.log(`⚠️  Optional field ${field}: not supported or not returned`);
        }
      });

      console.log("\\n📊 OpenAPI field support summary:");
      console.log(`Required fields: ${requiredFields.length}/${requiredFields.length} (100%)`);
      console.log(`Optional fields: ${supportedOptionalFields}/${totalOptionalFields} (${Math.round(supportedOptionalFields/totalOptionalFields*100)}%)`);

      // Verify extension field in detail
      if (config.extension) {
        console.log("\\n🔧 Verifying extension field details:");
        const extension = config.extension;

        // Check extension fields against what we set
        const extensionFields = [
          "access_token_type", "authorization_code_valid_duration", "token_signed_key_id",
          "id_token_signed_key_id", "access_token_duration", "refresh_token_duration",
          "refresh_token_strategy", "rotate_refresh_token", "id_token_duration",
          "id_token_strict_mode", "default_max_age", "authorization_response_duration",
          "backchannel_authentication_request_expires_in", "backchannel_authentication_polling_interval",
          "required_backchannel_auth_user_code", "backchannel_auth_user_code_type",
          "default_ciba_authentication_interaction_type", "oauth_authorization_request_expires_in",
          "fapi_baseline_scopes", "fapi_advance_scopes", "required_identity_verification_scopes",
          "custom_claims_scope_mapping", "access_token_selective_user_custom_properties",
          "access_token_verified_claims", "access_token_selective_verified_claims"
        ];

        extensionFields.forEach(field => {
          if (extension.hasOwnProperty(field)) {
            console.log(`✅ Extension field ${field}: ${JSON.stringify(extension[field])}`);
            // If we set a value, verify it matches
            if (comprehensiveConfig.extension[field] !== undefined) {
              expect(extension[field]).toEqual(comprehensiveConfig.extension[field]);
            }
          } else {
            console.log(`⚠️  Extension field ${field}: not returned`);
          }
        });

        console.log("✅ Extension configuration verified successfully!");
      } else {
        console.log("\\n❌ Extension field not found in response");
      }

      // Test specific enum values from OpenAPI spec
      console.log("\\n🔍 Testing OpenAPI enum values:");

      // Check response_types_supported enum values
      const supportedResponseTypes = ["code", "token", "id_token", "code token", "code token id_token", "token id_token", "code id_token", "none"];
      const actualResponseTypes = config.response_types_supported || [];
      actualResponseTypes.forEach(type => {
        expect(supportedResponseTypes).toContain(type);
        console.log(`✅ Valid response_type: ${type}`);
      });

      // Check grant_types_supported enum values
      const supportedGrantTypes = ["authorization_code", "implicit", "refresh_token", "password", "client_credentials", "urn:openid:params:grant-type:ciba"];
      const actualGrantTypes = config.grant_types_supported || [];
      actualGrantTypes.forEach(type => {
        expect(supportedGrantTypes).toContain(type);
        console.log(`✅ Valid grant_type: ${type}`);
      });

      // Check subject_types_supported enum values
      const supportedSubjectTypes = ["pairwise", "public"];
      const actualSubjectTypes = config.subject_types_supported || [];
      actualSubjectTypes.forEach(type => {
        expect(supportedSubjectTypes).toContain(type);
        console.log(`✅ Valid subject_type: ${type}`);
      });

      // Clean up - Delete the test tenant
      const deleteTenantResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      expect(deleteTenantResponse.status).toBe(204);
      console.log("✅ OpenAPI test tenant deleted successfully");

      console.log("🎉 OpenAPI specification field verification completed!");
    });

    it("GET response can be used directly as UPDATE request body (roundtrip)", async () => {
      // Get OAuth token
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro@gmail.com",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Step 1: Create a test tenant
      const newTenantId = uuidv4();
      const createTenantResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          tenant: {
            "id": newTenantId,
            "name": `Roundtrip Test Tenant ${Date.now()}`,
            "domain": "http://localhost:8080",
            "description": "Test tenant for GET-UPDATE roundtrip verification",
            "authorization_provider": "idp-server",
            "tenant_type": "BUSINESS"
          },
          authorization_server: {
            "issuer": `http://localhost:8080/${newTenantId}`,
            "authorization_endpoint": `http://localhost:8080/${newTenantId}/v1/authorizations`,
            "token_endpoint": `http://localhost:8080/${newTenantId}/v1/tokens`,
            "userinfo_endpoint": `http://localhost:8080/${newTenantId}/v1/userinfo`,
            "jwks_uri": `http://localhost:8080/${newTenantId}/v1/jwks`,
            "scopes_supported": ["openid", "profile", "email"],
            "response_types_supported": ["code"],
            "response_modes_supported": ["query", "fragment"],
            "subject_types_supported": ["public"],
            "grant_types_supported": ["authorization_code", "refresh_token"],
            "token_endpoint_auth_methods_supported": ["client_secret_post", "client_secret_basic"]
          }
        }
      });
      expect(createTenantResponse.status).toBe(201);
      console.log("✅ Roundtrip test tenant created");

      // Step 2: GET authorization server configuration
      const getBeforeResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/authorization-server`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      expect(getBeforeResponse.status).toBe(200);
      const originalConfig = getBeforeResponse.data;
      console.log("✅ GET authorization server configuration:", JSON.stringify(originalConfig, null, 2));

      // Step 3: PUT the GET response body directly as the update request body
      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/authorization-server`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: originalConfig
      });
      console.log("PUT response status:", updateResponse.status);
      console.log("PUT response body:", JSON.stringify(updateResponse.data, null, 2));
      expect(updateResponse.status).toBe(200);
      console.log("✅ PUT with GET response body succeeded (no validation error)");

      // Step 4: GET again and verify nothing changed
      const getAfterResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}/authorization-server`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      expect(getAfterResponse.status).toBe(200);
      const updatedConfig = getAfterResponse.data;

      // Compare original and updated configurations
      const originalJson = JSON.stringify(originalConfig);
      const updatedJson = JSON.stringify(updatedConfig);
      expect(updatedJson).toBe(originalJson);
      console.log("✅ Configuration unchanged after GET→UPDATE roundtrip");

      // Step 5: Clean up
      const deleteTenantResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${newTenantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      expect(deleteTenantResponse.status).toBe(204);
      console.log("✅ Roundtrip test tenant deleted");
    });
  });
});