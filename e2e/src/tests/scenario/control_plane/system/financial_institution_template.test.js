import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get, postWithJson, putWithJson } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { adminServerConfig, backendUrl } from "../../../testConfig";
import { v4 as uuidv4 } from "uuid";

/**
 * Financial Institution Template - E2E Tests
 *
 * Tests for financial institution configuration template
 * Validates FAPI compliance, security settings, and authentication policies
 */
describe("Financial Institution Template", () => {
  let accessToken;
  let tenantId;
  let clientId;

  beforeAll(async () => {
    // Get OAuth token with system admin privileges
    const tokenResponse = await requestToken({
      endpoint: adminServerConfig.tokenEndpoint,
      grantType: "password",
      username: adminServerConfig.oauth.username,
      password: adminServerConfig.oauth.password,
      scope: adminServerConfig.adminClient.scope,
      clientId: adminServerConfig.adminClient.clientId,
      clientSecret: adminServerConfig.adminClient.clientSecret,
    });
    expect(tokenResponse.status).toBe(200);
    accessToken = tokenResponse.data.access_token;
  });

  describe("Tenant Configuration", () => {
    it("should create financial institution tenant with FAPI compliance", async () => {
      tenantId = uuidv4();

      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/tenants`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          tenant: {
            id: tenantId,
            name: `financial-institution-${tenantId}`,
            domain: "http://localhost:8080",
            authorization_provider: "idp-server",
            database_type: "postgresql",
            ui_config: {
              base_url: "",
              signup_page: "/auth-views/signup/index.html",
              signin_page: "/auth-views/signin/index.html"
            },
            cors_config: {
              allow_origins: [],
              allow_headers: "Authorization, Content-Type, Accept, x-device-id",
              allow_methods: "GET, POST, PUT, PATCH, DELETE, OPTIONS",
              allow_credentials: true
            },
            session_config: {
              cookie_name: "session",
              cookie_same_site: "strict",
              use_secure_cookie: true,
              use_http_only_cookie: true,
              cookie_path: "/"
            },
            security_event_log_config: {
              format: "structured_json",
              debug_logging: false,
              stage: "production",
              include_user_id: true,
              include_user_ex_sub: true,
              include_client_id: true,
              include_ip_address: true,
              include_user_agent: true,
              include_event_detail: true,
              include_user_detail: true,
              include_user_pii: false,
              allowed_user_pii_keys: "",
              include_trace_context: true,
              service_name: "idp-server-financial",
              custom_tags: "environment:test,industry:financial",
              tracing_enabled: true,
              persistence_enabled: true,
              detail_scrub_keys: "authorization,cookie,set-cookie,proxy-authorization,password,secret,token,refresh_token,access_token,id_token,client_secret,api_key,api_secret,bearer"
            },
            security_event_user_config: {
              include_id: true,
              include_name: true,
              include_external_user_id: true,
              include_email: true,
              include_phone_number: true,
              include_given_name: true,
              include_family_name: true,
              include_preferred_username: false,
              include_profile: false,
              include_picture: false,
              include_website: false,
              include_gender: false,
              include_birthdate: true,
              include_zoneinfo: false,
              include_locale: false,
              include_address: true,
              include_roles: true,
              include_permissions: true,
              include_current_tenant: true,
              include_assigned_tenants: true,
              include_verified_claims: true
            }
          },
          authorization_server: {
            issuer: `http://localhost:8080/${tenantId}`,
            authorization_endpoint: `http://localhost:8080/${tenantId}/v1/authorizations`,
            token_endpoint: `http://localhost:8080/${tenantId}/v1/tokens`,
            token_endpoint_auth_methods_supported: [
              "private_key_jwt",
              "tls_client_auth",
              "self_signed_tls_client_auth"
            ],
            token_endpoint_auth_signing_alg_values_supported: [
              "RS256",
              "ES256"
            ],
            userinfo_endpoint: `http://localhost:8080/${tenantId}/v1/userinfo`,
            jwks_uri: `http://localhost:8080/${tenantId}/v1/jwks`,
            grant_types_supported: [
              "authorization_code",
              "refresh_token",
              "urn:openid:params:grant-type:ciba"
            ],
            scopes_supported: [
              "openid",
              "profile",
              "email",
              "address",
              "phone",
              "offline_access",
              "account",
              "transfers",
              "read",
              "write",
              "identity_verification_application",
              "identity_credentials_update",
              "management"
            ],
            response_types_supported: [
              "code",
              "code id_token",
              "code token id_token"
            ],
            response_modes_supported: [
              "query",
              "fragment"
            ],
            subject_types_supported: [
              "pairwise"
            ],
            id_token_signing_alg_values_supported: [
              "RS256",
              "ES256"
            ],
            request_object_signing_alg_values_supported: [
              "RS256",
              "ES256"
            ],
            authorization_details_types_supported: [
              "payment_initiation",
              "account_information"
            ],
            tls_client_certificate_bound_access_tokens: true,
            extension: {
              token_signed_key_id: "id_token_financial",
              id_token_signed_key_id: "id_token_financial",
              access_token_duration: 900,
              id_token_duration: 3600,
              refresh_token_duration: 2592000,
              id_token_strict_mode: true,
              default_max_age: 3600,
              fapi_baseline_scopes: [
                "read",
                "account"
              ],
              fapi_advance_scopes: [
                "write",
                "transfers"
              ],
              required_identity_verification_scopes: [
                "transfers"
              ],
              authentication_policies: [
                {
                  id: "financial_high_security_policy",
                  priority: 1,
                  conditions: {
                    scopes: [
                      "openid",
                      "transfers"
                    ]
                  },
                  available_methods: [
                    "webauthn",
                    "fido-uaf"
                  ],
                  success_conditions: {
                    any_of: [
                      {
                        type: "webauthn",
                        success_count: 1
                      }
                    ]
                  },
                  failure_conditions: {
                    any_of: [
                      {
                        type: "webauthn",
                        failure_count: 3
                      }
                    ]
                  },
                  lock_conditions: {
                    any_of: [
                      {
                        type: "webauthn",
                        failure_count: 5
                      }
                    ]
                  }
                },
                {
                  id: "financial_standard_policy",
                  priority: 2,
                  conditions: {
                    scopes: [
                      "openid",
                      "read",
                      "account"
                    ]
                  },
                  available_methods: [
                    "webauthn",
                    "fido-uaf",
                    "sms"
                  ],
                  success_conditions: {
                    any_of: [
                      {
                        type: "webauthn",
                        success_count: 1
                      },
                      {
                        type: "fido-uaf-authentication",
                        success_count: 1
                      },
                      {
                        type: "sms-authentication",
                        success_count: 1
                      }
                    ]
                  },
                  failure_conditions: {
                    any_of: [
                      {
                        type: "webauthn",
                        failure_count: 5
                      }
                    ]
                  },
                  lock_conditions: {
                    any_of: [
                      {
                        type: "webauthn",
                        failure_count: 5
                      }
                    ]
                  }
                }
              ]
            }
          }
        }
      });

      console.log("Create financial tenant response:", createResponse.status);
      expect(createResponse.status).toBe(201);
      expect(createResponse.data).toHaveProperty("result");
      expect(createResponse.data.result.id).toBe(tenantId);
    });

    it("should verify FAPI configuration", async () => {
      const discoveryResponse = await get({
        url: `${backendUrl}/${tenantId}/.well-known/openid-configuration`
      });

      expect(discoveryResponse.status).toBe(200);
      const config = discoveryResponse.data;

      // FAPI Advanced requirements
      expect(config.tls_client_certificate_bound_access_tokens).toBe(true);
      expect(config.token_endpoint_auth_methods_supported).toContain("private_key_jwt");
      expect(config.token_endpoint_auth_methods_supported).toContain("tls_client_auth");
      expect(config.token_endpoint_auth_methods_supported).not.toContain("client_secret_post");
      expect(config.token_endpoint_auth_methods_supported).not.toContain("client_secret_basic");

      // Request Object signing
      expect(config.request_object_signing_alg_values_supported).toContain("ES256");
      expect(config.request_object_signing_alg_values_supported).toContain("PS256");

      // Response modes
      expect(config.response_modes_supported).toContain("jwt");

      // Subject types (pairwise for privacy)
      expect(config.subject_types_supported).toContain("pairwise");
    });

    it("should verify FAPI scopes configuration", async () => {
      const discoveryResponse = await get({
        url: `${backendUrl}/${tenantId}/.well-known/openid-configuration`
      });

      expect(discoveryResponse.status).toBe(200);
      const config = discoveryResponse.data;

      // FAPI Baseline Scopes
      expect(config.extension.fapi_baseline_scopes).toContain("read");
      expect(config.extension.fapi_baseline_scopes).toContain("account");

      // FAPI Advance Scopes
      expect(config.extension.fapi_advance_scopes).toContain("write");
      expect(config.extension.fapi_advance_scopes).toContain("transfers");

      // Required identity verification scopes
      expect(config.extension.required_identity_verification_scopes).toContain("transfers");
    });

    it("should verify authentication policies", async () => {
      const discoveryResponse = await get({
        url: `${backendUrl}/${tenantId}/.well-known/openid-configuration`
      });

      expect(discoveryResponse.status).toBe(200);
      const config = discoveryResponse.data;
      const policies = config.extension.authentication_policies;

      expect(policies).toHaveLength(2);

      // High security policy
      const highSecPolicy = policies.find(p => p.id === "financial_high_security_policy");
      expect(highSecPolicy).toBeDefined();
      expect(highSecPolicy.priority).toBe(1);
      expect(highSecPolicy.conditions.scopes).toContain("transfers");
      expect(highSecPolicy.available_methods).toContain("webauthn");
      expect(highSecPolicy.available_methods).not.toContain("password");

      // Standard policy
      const standardPolicy = policies.find(p => p.id === "financial_standard_policy");
      expect(standardPolicy).toBeDefined();
      expect(standardPolicy.priority).toBe(2);
      expect(standardPolicy.conditions.scopes).toContain("read");
      expect(standardPolicy.conditions.scopes).toContain("account");
    });

    it("should verify security event logging configuration", async () => {
      const getTenantResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${tenantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      expect(getTenantResponse.status).toBe(200);
      const tenant = getTenantResponse.data.result;

      // Security event log config
      const logConfig = tenant.security_event_log_config;
      expect(logConfig.format).toBe("structured_json");
      expect(logConfig.stage).toBe("production");
      expect(logConfig.include_user_detail).toBe(true);
      expect(logConfig.include_trace_context).toBe(true);
      expect(logConfig.tracing_enabled).toBe(true);
      expect(logConfig.persistence_enabled).toBe(true);

      // Security event user config
      const userConfig = tenant.security_event_user_config;
      expect(userConfig.include_verified_claims).toBe(true);
      expect(userConfig.include_roles).toBe(true);
      expect(userConfig.include_permissions).toBe(true);
    });

    it("should verify session configuration", async () => {
      const getTenantResponse = await get({
        url: `${backendUrl}/v1/management/tenants/${tenantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });

      expect(getTenantResponse.status).toBe(200);
      const tenant = getTenantResponse.data.result;

      // Session security settings
      const sessionConfig = tenant.session_config;
      expect(sessionConfig.cookie_same_site).toBe("strict");
      expect(sessionConfig.use_secure_cookie).toBe(true);
      expect(sessionConfig.use_http_only_cookie).toBe(true);
    });
  });

  describe("Cleanup", () => {
    it("should delete test tenant", async () => {
      if (tenantId) {
        const deleteResponse = await deletion({
          url: `${backendUrl}/v1/management/tenants/${tenantId}`,
          headers: {
            Authorization: `Bearer ${accessToken}`
          }
        });

        expect(deleteResponse.status).toBe(200);
      }
    });
  });
});
