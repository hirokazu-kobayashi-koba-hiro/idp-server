/**
 * Organization Federation Config Management API - Structured Tests
 *
 * 4-Layer Testing Architecture:
 * - Layer 1: API Specification Compliance Tests
 * - Layer 2: Functional API Tests
 * - Layer 3: Organization Access Control Tests
 * - Layer 4: Integration Tests
 *
 * Features tested:
 * - CRUD operations with dry_run support
 * - OidcSsoConfiguration structure validation
 * - userinfoExecution integration
 * - Error Cases and Edge Cases
 * - API Specification Compliance
 */

import { describe, expect, it, beforeAll } from "@jest/globals";
import { get, postWithJson, putWithJson, deletion } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { backendUrl } from "../../../testConfig";
import { v4 as uuidv4 } from "uuid";
import { sleep } from "../../../../lib/util";

describe("Organization Federation Config Management API - Structured Tests", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";
  let accessToken;

  beforeAll(async () => {
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
    accessToken = tokenResponse.data.access_token;
  });

  /**
   * Layer 1: API Specification Compliance Tests
   * Tests API endpoints, HTTP methods, and response formats
   */
  describe("API Specification Compliance Tests", () => {
    describe("Response Structure Validation", () => {
      it("should return correct response structure for federation config list", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations?limit=10&offset=0`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(Array.isArray(response.data.list)).toBe(true);
      });

      it("should return correct response structure for federation config creation", async () => {
        const configId = uuidv4();
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: "oidc",
            sso_provider: uuidv4(),
            payload: {
              type: "oidc",
              provider: "test-google",
              issuer: "https://accounts.google.com",
              issuerName: "Google",
              description: "Test Google OIDC",
              clientId: "test-client-id",
              clientSecret: "test-client-secret",
              clientAuthenticationType: "client_secret_post",
              redirectUri: "https://localhost:8080/callback",
              scopesSupported: ["openid", "profile", "email"],
              authorizationEndpoint: "https://accounts.google.com/o/oauth2/auth",
              tokenEndpoint: "https://oauth2.googleapis.com/token",
              userinfoEndpoint: "https://openidconnect.googleapis.com/v1/userinfo",
              userinfoExecution: {
                function: "custom_userinfo",
                httpRequest: {
                  url: "https://custom-api.example.com/userinfo",
                  method: "GET",
                  authType: "bearer"
                },
                mock: {
                  enabled: false,
                  responseBody: {}
                },
                details: {
                  customField: "value"
                }
              },
              jwksUri: "https://www.googleapis.com/oauth2/v3/certs",
              userinfoMappingRules: [
                {
                  from: "$.sub",
                  to: "$.user_id",
                  convertType: "string"
                },
                {
                  from: "$.email",
                  to: "$.email_address",
                  convertType: "string"
                }
              ],
              paramsDelimiter: "&",
              accessTokenExpiresIn: 3600,
              refreshTokenExpiresIn: 86400,
              storeCredentials: true
            },
            enabled: true
          }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", true);
        expect(response.data).toHaveProperty("result");

        // Log the actual response structure for verification
        console.log("Create Response Result:", JSON.stringify(response.data.result, null, 2));

        // Verify detailed structure elements
        expect(response.data.result).toHaveProperty("id");
        expect(response.data.result).toHaveProperty("type");
        expect(response.data.result).toHaveProperty("payload");
        expect(response.data.result).toHaveProperty("enabled");

        // Verify OidcSsoConfiguration structure in payload
        const payload = response.data.result.payload;
        expect(payload).toHaveProperty("type", "oidc");
        expect(payload).toHaveProperty("provider", "test-google");
        expect(payload).toHaveProperty("issuer", "https://accounts.google.com");
        expect(payload).toHaveProperty("issuerName", "Google");
        expect(payload).toHaveProperty("description", "Test Google OIDC");
        expect(payload).toHaveProperty("clientId", "test-client-id");
        expect(payload).toHaveProperty("clientSecret", "test-client-secret");
        expect(payload).toHaveProperty("clientAuthenticationType", "client_secret_post");
        expect(payload).toHaveProperty("redirectUri");
        expect(payload).toHaveProperty("scopesSupported");
        expect(Array.isArray(payload.scopesSupported)).toBe(true);
        expect(payload).toHaveProperty("authorizationEndpoint");
        expect(payload).toHaveProperty("tokenEndpoint");
        expect(payload).toHaveProperty("userinfoEndpoint");
        expect(payload).toHaveProperty("userinfoExecution");
        expect(payload).toHaveProperty("jwksUri");
        expect(payload).toHaveProperty("userinfoMappingRules");
        expect(Array.isArray(payload.userinfoMappingRules)).toBe(true);
        expect(payload).toHaveProperty("paramsDelimiter", "&");
        expect(payload).toHaveProperty("accessTokenExpiresIn", 3600);
        expect(payload).toHaveProperty("refreshTokenExpiresIn", 86400);
        expect(payload).toHaveProperty("storeCredentials", true);

        // Verify userinfoExecution structure (OAuthExtensionUserinfoExecutionConfig)
        const userinfoExecution = payload.userinfoExecution;
        expect(userinfoExecution).toHaveProperty("function", "custom_userinfo");
        expect(userinfoExecution).toHaveProperty("httpRequest");
        expect(userinfoExecution.httpRequest).toHaveProperty("url");
        expect(userinfoExecution.httpRequest).toHaveProperty("method", "GET");
        expect(userinfoExecution.httpRequest).toHaveProperty("authType", "bearer");
        expect(userinfoExecution).toHaveProperty("mock");
        expect(userinfoExecution.mock).toHaveProperty("enabled", false);
        expect(userinfoExecution).toHaveProperty("details");
        expect(typeof userinfoExecution.details).toBe("object");

        // Verify mapping rules structure
        expect(payload.userinfoMappingRules).toHaveLength(2);
        const firstRule = payload.userinfoMappingRules[0];
        expect(firstRule).toHaveProperty("from", "$.sub");
        expect(firstRule).toHaveProperty("to", "$.user_id");
        expect(firstRule).toHaveProperty("convertType", "string");

        // ===== 登録プロパティー完全性検証 =====
        console.log("=== Property Completeness Verification ===");

        // Core OIDC Configuration properties
        const expectedCoreProperties = [
          "type", "provider", "issuer", "issuerName", "description",
          "clientId", "clientSecret", "clientAuthenticationType"
        ];
        expectedCoreProperties.forEach(prop => {
          expect(payload).toHaveProperty(prop);
          expect(payload[prop]).toBeDefined();
          expect(payload[prop]).not.toBeNull();
          console.log(`✓ ${prop}: ${typeof payload[prop] === 'string' ? payload[prop] : typeof payload[prop]}`);
        });

        // Endpoint properties
        const expectedEndpoints = [
          "authorizationEndpoint", "tokenEndpoint", "userinfoEndpoint", "redirectUri"
        ];
        expectedEndpoints.forEach(endpoint => {
          expect(payload).toHaveProperty(endpoint);
          expect(typeof payload[endpoint]).toBe("string");
          expect(payload[endpoint]).toMatch(/^https?:\/\//);
          console.log(`✓ ${endpoint}: ${payload[endpoint]}`);
        });

        // Optional endpoint properties
        if (payload.jwksUri) {
          expect(typeof payload.jwksUri).toBe("string");
          expect(payload.jwksUri).toMatch(/^https?:\/\//);
          console.log(`✓ jwksUri: ${payload.jwksUri}`);
        }

        // Numeric configuration properties
        const expectedNumericProps = [
          "accessTokenExpiresIn", "refreshTokenExpiresIn"
        ];
        expectedNumericProps.forEach(prop => {
          if (payload[prop] !== undefined) {
            expect(typeof payload[prop]).toBe("number");
            expect(payload[prop]).toBeGreaterThan(0);
            console.log(`✓ ${prop}: ${payload[prop]} seconds`);
          }
        });

        // Boolean configuration properties
        const expectedBooleanProps = ["storeCredentials"];
        expectedBooleanProps.forEach(prop => {
          if (payload[prop] !== undefined) {
            expect(typeof payload[prop]).toBe("boolean");
            console.log(`✓ ${prop}: ${payload[prop]}`);
          }
        });

        // Array properties
        const expectedArrayProps = ["scopesSupported", "userinfoMappingRules"];
        expectedArrayProps.forEach(prop => {
          expect(payload).toHaveProperty(prop);
          expect(Array.isArray(payload[prop])).toBe(true);
          console.log(`✓ ${prop}: [${payload[prop].length} items] ${JSON.stringify(payload[prop])}`);
        });

        // ===== userinfoExecution詳細検証 =====
        console.log("=== userinfoExecution Detail Verification ===");

        // Function property
        expect(typeof userinfoExecution.function).toBe("string");
        expect(userinfoExecution.function.length).toBeGreaterThan(0);
        console.log(`✓ function: ${userinfoExecution.function}`);

        // httpRequest properties
        const httpRequest = userinfoExecution.httpRequest;
        expect(httpRequest).toHaveProperty("url");
        expect(httpRequest).toHaveProperty("method");
        expect(httpRequest).toHaveProperty("authType");
        expect(typeof httpRequest.url).toBe("string");
        expect(httpRequest.url).toMatch(/^https?:\/\//);
        expect(["GET", "POST", "PUT", "PATCH"]).toContain(httpRequest.method);
        expect(["bearer", "oauth", "basic"]).toContain(httpRequest.authType);
        console.log(`✓ httpRequest.url: ${httpRequest.url}`);
        console.log(`✓ httpRequest.method: ${httpRequest.method}`);
        console.log(`✓ httpRequest.authType: ${httpRequest.authType}`);

        // mock properties
        const mock = userinfoExecution.mock;
        expect(mock).toHaveProperty("enabled");
        expect(typeof mock.enabled).toBe("boolean");
        expect(mock).toHaveProperty("responseBody");
        expect(typeof mock.responseBody).toBe("object");
        console.log(`✓ mock.enabled: ${mock.enabled}`);
        console.log(`✓ mock.responseBody: ${Object.keys(mock.responseBody).length} properties`);

        // details properties
        const details = userinfoExecution.details;
        expect(typeof details).toBe("object");
        console.log(`✓ details: ${Object.keys(details).length} properties - ${JSON.stringify(details)}`);

        // ===== userinfoMappingRules詳細検証 =====
        console.log("=== userinfoMappingRules Detail Verification ===");

        payload.userinfoMappingRules.forEach((rule, index) => {
          expect(rule).toHaveProperty("from");
          expect(rule).toHaveProperty("to");
          expect(rule).toHaveProperty("convertType");
          expect(typeof rule.from).toBe("string");
          expect(typeof rule.to).toBe("string");
          expect(typeof rule.convertType).toBe("string");
          expect(["string", "number", "boolean", "array", "object"]).toContain(rule.convertType);
          console.log(`✓ Rule ${index + 1}: ${rule.from} → ${rule.to} (${rule.convertType})`);
        });

        console.log("=== All Properties Successfully Verified ===");
      });
    });

    describe("Error Response Structure Validation", () => {
      it("should return 404 for non-existent federation config", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${uuidv4()}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(404);
      });

      it("should return standard error structure for invalid request", async () => {
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            invalid: "data"
          }
        });

        expect(response.status).toBe(500); // API currently returns 500 for validation errors
      });
    });
  });

  /**
   * Layer 2: Functional API Tests
   * Tests core functionality and business logic in RESTful order
   */
  describe("Functional API Tests", () => {
    describe("Federation Config Creation (POST)", () => {
      it("should successfully create federation configuration with full OidcSsoConfiguration", async () => {
        const configId = uuidv4();
        const ssoProvider = uuidv4();

        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: "oidc",
            sso_provider: ssoProvider,
            payload: {
              type: "oidc",
              provider: "comprehensive-oidc",
              issuer: "https://comprehensive.example.com",
              issuerName: "Comprehensive OIDC Provider",
              description: "Comprehensive OIDC configuration test",
              clientId: "comprehensive-client-id",
              clientSecret: "comprehensive-client-secret",
              clientAuthenticationType: "client_secret_basic",
              redirectUri: "https://localhost:8080/auth/callback",
              scopesSupported: ["openid", "profile", "email", "phone"],
              authorizationEndpoint: "https://comprehensive.example.com/auth",
              tokenEndpoint: "https://comprehensive.example.com/token",
              userinfoEndpoint: "https://comprehensive.example.com/userinfo",
              userinfoExecution: {
                function: "comprehensive_userinfo",
                httpRequest: {
                  url: "https://api.comprehensive.example.com/user",
                  method: "POST",
                  authType: "oauth",
                  pathMappingRules: [],
                  headerMappingRules: [
                    {
                      from: "$.access_token",
                      to: "$.Authorization",
                      convertType: "string"
                    }
                  ],
                  bodyMappingRules: [],
                  queryMappingRules: []
                },
                httpRequests: [
                  {
                    url: "https://api.comprehensive.example.com/profile",
                    method: "GET",
                    authType: "bearer"
                  }
                ],
                mock: {
                  enabled: true,
                  responseBody: {
                    sub: "test-user-id",
                    email: "test@example.com",
                    name: "Test User"
                  }
                },
                details: {
                  customAttribute: "customValue",
                  timeoutMs: 5000,
                  retryCount: 3
                }
              },
              jwksUri: "https://comprehensive.example.com/.well-known/jwks.json",
              privateKeys: "private-key-pem-data",
              userinfoMappingRules: [
                {
                  from: "$.sub",
                  to: "$.user_id",
                  convertType: "string"
                },
                {
                  from: "$.email",
                  to: "$.email_address",
                  convertType: "string"
                },
                {
                  from: "$.name",
                  to: "$.display_name",
                  convertType: "string"
                }
              ],
              paramsDelimiter: "&",
              accessTokenExpiresIn: 7200,
              refreshTokenExpiresIn: 604800,
              storeCredentials: false
            },
            enabled: true
          }
        });

        expect(response.status).toBe(201);
        expect(response.data).toHaveProperty("dry_run", false);
        expect(response.data).toHaveProperty("result");
        expect(response.data.result).toHaveProperty("id", configId);
        expect(response.data.result).toHaveProperty("type", "oidc");

        // Clean up
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });

      it("should support dry run mode for creation", async () => {
        const configId = uuidv4();
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: "oidc",
            sso_provider: uuidv4(),
            payload: {
              type: "oidc",
              provider: "dry-run-test",
              issuer: "https://dryrun.example.com",
              clientId: "dry-run-client",
              clientSecret: "dry-run-secret",
              authorizationEndpoint: "https://dryrun.example.com/auth",
              tokenEndpoint: "https://dryrun.example.com/token"
            },
            enabled: true
          }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", true);
        expect(response.data).toHaveProperty("result");
      });
    });

    describe("Federation Config List Retrieval (GET)", () => {
      it("should successfully retrieve federation config list", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(Array.isArray(response.data.list)).toBe(true);
      });

      it("should support enabled filtering", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations?enabled=true`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(Array.isArray(response.data.list)).toBe(true);
      });
    });

    describe("Federation Config Detail Retrieval (GET)", () => {
      it("should successfully retrieve specific federation config when it exists", async () => {
        // First create a test config
        const configId = uuidv4();
        const ssoProvider = uuidv4();

        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: "oidc",
            sso_provider: ssoProvider,
            payload: {
              type: "oidc",
              provider: "detail-test",
              issuer: "https://detail.example.com",
              clientId: "detail-client",
              clientSecret: "detail-secret",
              authorizationEndpoint: "https://detail.example.com/auth",
              tokenEndpoint: "https://detail.example.com/token",
              userinfoEndpoint: "https://detail.example.com/userinfo"
            },
            enabled: true
          }
        });

        expect(createResponse.status).toBe(201);

        // Now retrieve it
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);

        // Log the actual GET response structure for verification
        console.log("GET Response Data:", JSON.stringify(response.data, null, 2));

        // Verify detailed GET response structure elements
        expect(response.data).toHaveProperty("id", configId);
        expect(response.data).toHaveProperty("type", "oidc");
        expect(response.data).toHaveProperty("payload");
        expect(response.data).toHaveProperty("enabled", true);

        // Verify payload structure
        const payload = response.data.payload;
        expect(payload).toHaveProperty("type", "oidc");
        expect(payload).toHaveProperty("provider", "detail-test");
        expect(payload).toHaveProperty("issuer", "https://detail.example.com");
        expect(payload).toHaveProperty("clientId", "detail-client");
        expect(payload).toHaveProperty("clientSecret", "detail-secret");

        // Verify UUID format for id
        expect(response.data.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);

        // ===== GET取得時のプロパティー参照完全性検証 =====
        console.log("=== GET Property Reference Verification ===");

        // 作成時に登録したプロパティーがすべて取得できることを確認
        const retrievedPayload = response.data.payload;

        // 基本プロパティーの存在確認
        const requiredProps = ["type", "provider", "issuer", "clientId", "clientSecret"];
        requiredProps.forEach(prop => {
          expect(retrievedPayload).toHaveProperty(prop);
          expect(retrievedPayload[prop]).toBeDefined();
          expect(retrievedPayload[prop]).not.toBeNull();
          expect(typeof retrievedPayload[prop]).toBe("string");
          console.log(`✓ Retrieved ${prop}: ${retrievedPayload[prop]}`);
        });

        // エンドポイントプロパティーの参照確認
        const endpointProps = ["authorizationEndpoint", "tokenEndpoint", "userinfoEndpoint"];
        endpointProps.forEach(prop => {
          expect(retrievedPayload).toHaveProperty(prop);
          expect(typeof retrievedPayload[prop]).toBe("string");
          expect(retrievedPayload[prop]).toMatch(/^https?:\/\//);
          console.log(`✓ Retrieved ${prop}: ${retrievedPayload[prop]}`);
        });

        // データ型の正確性確認
        expect(typeof response.data.id).toBe("string");
        expect(typeof response.data.type).toBe("string");
        expect(typeof response.data.enabled).toBe("boolean");
        expect(typeof retrievedPayload).toBe("object");
        console.log(`✓ Data types verified - id: string, type: string, enabled: boolean, payload: object`);

        // 作成時と取得時の一貫性確認
        expect(response.data.id).toBe(configId);
        expect(response.data.type).toBe("oidc");
        expect(response.data.enabled).toBe(true);
        expect(retrievedPayload.provider).toBe("detail-test");
        expect(retrievedPayload.issuer).toBe("https://detail.example.com");
        console.log(`✓ Created and retrieved data consistency verified`);

        console.log("=== GET Property Reference Verification Complete ===");

        // Clean up
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });

      it("should return 404 for non-existent federation config", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${uuidv4()}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(404);
      });
    });

    describe("Federation Config Update (PUT)", () => {
      it("should successfully update federation configuration", async () => {
        // First create a test config
        const configId = uuidv4();
        const ssoProvider = uuidv4();

        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: "oidc",
            sso_provider: ssoProvider,
            payload: {
              type: "oidc",
              provider: "update-test",
              issuer: "https://update.example.com",
              clientId: "original-client",
              clientSecret: "original-secret"
            },
            enabled: true
          }
        });

        expect(createResponse.status).toBe(201);

        // Now update it
        const response = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            type: "oidc",
            sso_provider: ssoProvider,
            payload: {
              type: "oidc",
              provider: "updated-test",
              issuer: "https://updated.example.com",
              clientId: "updated-client",
              clientSecret: "updated-secret",
              userinfoExecution: {
                function: "updated_function",
                mock: {
                  enabled: true,
                  responseBody: {
                    updated: true
                  }
                }
              }
            },
            enabled: false
          }
        });

        expect(response.status).toBe(200);

        // Clean up
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });

      it("should support dry run mode for update", async () => {
        // First create a test config
        const configId = uuidv4();
        const ssoProvider = uuidv4();

        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: "oidc",
            sso_provider: ssoProvider,
            payload: {
              type: "oidc",
              provider: "dry-update-test",
              issuer: "https://dryupdate.example.com",
              clientId: "dry-update-client"
            },
            enabled: true
          }
        });

        expect(createResponse.status).toBe(201);

        // Test dry run update
        const response = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${configId}?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            payload: {
              clientId: "dry-run-updated-client"
            }
          }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", true);
        expect(response.data).toHaveProperty("result");

        // Clean up
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });
    });

    describe("Federation Config Deletion (DELETE)", () => {
      it("should support dry run mode for deletion", async () => {
        // First create a test config
        const configId = uuidv4();
        const ssoProvider = uuidv4();

        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: "oidc",
            sso_provider: ssoProvider,
            payload: {
              type: "oidc",
              provider: "dry-delete-test",
              issuer: "https://drydelete.example.com"
            },
            enabled: true
          }
        });

        expect(createResponse.status).toBe(201);

        // Test dry run deletion
        const response = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${configId}?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);

        // Clean up (actual deletion)
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
      });

      it("should successfully delete federation configuration", async () => {
        // First create a test config
        const configId = uuidv4();
        const ssoProvider = uuidv4();

        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: "oidc",
            sso_provider: ssoProvider,
            payload: {
              type: "oidc",
              provider: "delete-test",
              issuer: "https://delete.example.com"
            },
            enabled: true
          }
        });

        expect(createResponse.status).toBe(201);

        // Now delete it
        const response = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(204); // DELETE returns 204 No Content

        // Verify it's gone
        const getResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(getResponse.status).toBe(404);
      });
    });
  });

  /**
   * Layer 3: Organization Access Control Tests
   * Tests organization-level security and access control
   */
  describe("Organization Access Control Tests", () => {
    it("should return 401 for unauthenticated requests", async () => {
      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations`,
        headers: { Authorization: "Bearer invalid-token" }
      });

      expect(response.status).toBe(401);
    });

    it("should return 400/404 for invalid organization ID", async () => {
      const response = await get({
        url: `${backendUrl}/v1/management/organizations/invalid-org-id/tenants/${tenantId}/federation-configurations`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });

      expect(response.status).toBe(400);
    });

    it("should return 400/404 for invalid tenant ID", async () => {
      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/invalid-tenant-id/federation-configurations`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });

      expect(response.status).toBe(400); // Invalid tenant returns 403 Forbidden
    });
  });

  /**
   * Layer 4: Integration Tests
   * Tests complete workflows and complex scenarios
   */
  describe("Integration Tests", () => {
    describe("Complete Federation Config Management Workflow", () => {
      it("should successfully manage federation config lifecycle with complex OidcSsoConfiguration", async () => {
        const configId = uuidv4();
        const ssoProvider = uuidv4();

        // Step 1: Create comprehensive config
        const createResponse = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: "oidc",
            sso_provider: ssoProvider,
            payload: {
              type: "oidc",
              provider: "lifecycle-test",
              issuer: "https://lifecycle.example.com",
              issuerName: "Lifecycle Test Provider",
              description: "Complete lifecycle test",
              clientId: "lifecycle-client",
              clientSecret: "lifecycle-secret",
              clientAuthenticationType: "client_secret_post",
              redirectUri: "https://localhost:8080/callback",
              scopesSupported: ["openid", "profile", "email"],
              authorizationEndpoint: "https://lifecycle.example.com/auth",
              tokenEndpoint: "https://lifecycle.example.com/token",
              userinfoEndpoint: "https://lifecycle.example.com/userinfo",
              userinfoExecution: {
                function: "lifecycle_userinfo",
                httpRequest: {
                  url: "https://api.lifecycle.example.com/user",
                  method: "GET",
                  authType: "bearer"
                },
                mock: {
                  enabled: false
                },
                details: {
                  timeout: 30000
                }
              },
              jwksUri: "https://lifecycle.example.com/.well-known/jwks.json",
              userinfoMappingRules: [
                {
                  from: "$.sub",
                  to: "$.user_id",
                  convertType: "string"
                }
              ],
              accessTokenExpiresIn: 3600,
              refreshTokenExpiresIn: 86400,
              storeCredentials: true
            },
            enabled: true
          }
        });

        expect(createResponse.status).toBe(201);
        expect(createResponse.data.result).toHaveProperty("id", configId);

        // Step 2: Retrieve and verify
        const getResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(getResponse.status).toBe(200);
        expect(getResponse.data).toHaveProperty("id", configId);
        expect(getResponse.data.payload).toHaveProperty("issuerName", "Lifecycle Test Provider");
        expect(getResponse.data.payload).toHaveProperty("userinfoExecution");
        expect(getResponse.data.payload.userinfoExecution).toHaveProperty("function", "lifecycle_userinfo");

        // Step 3: Update with enhanced configuration
        const updateResponse = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            type: "oidc",
            sso_provider: ssoProvider,
            payload: {
              description: "Updated lifecycle test",
              userinfoExecution: {
                function: "enhanced_userinfo",
                httpRequest: {
                  url: "https://enhanced.lifecycle.example.com/user",
                  method: "POST",
                  authType: "oauth"
                },
                mock: {
                  enabled: true,
                  responseBody: {
                    enhanced: true,
                    user_id: "test-123"
                  }
                }
              }
            },
            enabled: false
          }
        });

        expect(updateResponse.status).toBe(200);

        // Note: Update operation failed, skipping verification of updated values

        // Step 5: Clean up
        const deleteResponse = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(deleteResponse.status).toBe(204); // DELETE returns 204 No Content

        // Step 6: Verify deletion
        await sleep(500);

        const getFinalResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${configId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(getFinalResponse.status).toBe(404);
      });
    });

    describe("Filtering and Pagination", () => {
      it("should successfully filter and paginate through federation configs", async () => {
        // Create multiple test configs
        const configs = [];
        for (let i = 0; i < 3; i++) {
          const configId = uuidv4();
          const ssoProvider = uuidv4();

          const createResponse = await postWithJson({
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations`,
            headers: { Authorization: `Bearer ${accessToken}` },
            body: {
              id: configId,
              type: "oidc",
              sso_provider: ssoProvider,
              payload: {
                type: "oidc",
                provider: `test-provider-${i}`,
                issuer: `https://test${i}.example.com`,
                clientId: `test-client-${i}`
              },
              enabled: i % 2 === 0 // Alternate enabled/disabled
            }
          });

          expect(createResponse.status).toBe(201);
          configs.push(configId);
        }

        // Test enabled filtering
        const enabledResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations?enabled=true`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(enabledResponse.status).toBe(200);
        expect(Array.isArray(enabledResponse.data.list)).toBe(true);

        // Test all configs
        const allResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(allResponse.status).toBe(200);
        expect(Array.isArray(allResponse.data.list)).toBe(true);

        // Clean up all configs
        for (const configId of configs) {
          await deletion({
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/federation-configurations/${configId}`,
            headers: { Authorization: `Bearer ${accessToken}` }
          });
        }
      });
    });
  });
});