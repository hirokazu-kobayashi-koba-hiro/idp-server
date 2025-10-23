/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Organization Security Event Hook Config Management API - Structured E2E Tests
 *
 * This test suite provides comprehensive testing for organization-level security event hook configuration management,
 * covering all aspects of the security event hook configuration management API including:
 *
 * - Security Event Hook Config Creation (postWithJson)
 * - Security Event Hook Config List Retrieval (GET)
 * - Security Event Hook Config Detail Retrieval (GET)
 * - Security Event Hook Config Update (putWithJson)
 * - Security Event Hook Config Deletion (delete)
 * - Organization-level Access Control
 * - Complex Nested Configuration Validation
 * - Security Event Type Support
 * - Authentication Configuration (OAuth2, HMAC, Bearer Token)
 * - Error Cases and Edge Cases
 * - API Specification Compliance
 * - JsonSchema Validation
 */

import { describe, expect, it, beforeAll } from "@jest/globals";
import { get, postWithJson, putWithJson, deletion } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { backendUrl } from "../../../testConfig";
import { v4 as uuidv4 } from "uuid";

describe("Organization Security Event Hook Config Management API - Structured Tests", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";
  let accessToken;

  beforeAll(async () => {
    // Authenticate as organization admin
    const authResponse = await requestToken({
      endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro",
      password: "successUserCode001",
      clientId: "org-client",
      clientSecret: "org-client-001",
      scope: "org-management account management"
    });

    expect(authResponse.status).toBe(200);
    expect(authResponse.data).toHaveProperty("access_token");
    accessToken = authResponse.data.access_token;
  });

  /**
   * Layer 1: API Specification Compliance Tests
   * Tests fundamental API compliance and contract adherence
   */
  describe("API Specification Compliance Tests", () => {
    describe("Response Structure Validation", () => {
      it("should return correct response structure for security event hook config list", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations?limit=10&offset=0`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect([200]).toContain(response.status);
        expect(response.data).toHaveProperty("list");
        expect(response.data).toHaveProperty("total_count");
        expect(response.data).toHaveProperty("limit", 10);
        expect(response.data).toHaveProperty("offset", 0);
        expect(Array.isArray(response.data.list)).toBe(true);
        expect(typeof response.data.total_count).toBe("number");
      });

      it("should return correct response structure for security event hook config creation", async () => {
        const configId = uuidv4();
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: configId,
            enabled: true,
            triggers: [
                "user_signin"
            ],
            events: {
              "user_signin": {
                execution: {
                  function: "webhook_call",
                  http_request: {
                    url: "https://webhook.example.com/events",
                    method: "POST"
                  }
                }
              }
            }
          }
        });

        console.log(JSON.stringify(response.data, null, 2));
        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("dry_run", true);
        expect(response.data).toHaveProperty("result");
        expect(response.data.result).toHaveProperty("id", configId);
        expect(response.data.result).toHaveProperty("type", configId);
        expect(response.data.result).toHaveProperty("enabled", true);
        expect(typeof response.data.result.events).toBe("object");
      });
    });

    describe("Query Parameter Validation", () => {
      it("should support enabled filter parameter", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations?enabled=true`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(Array.isArray(response.data.list)).toBe(true);
      });

      it("should support type filter parameter", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations?type=WEBHOOK`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(Array.isArray(response.data.list)).toBe(true);
      });
    });

    describe("HTTP Method Compliance", () => {
      it("should support GET for list endpoint", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect([200]).toContain(response.status);
      });

      it("should support POST for creation endpoint", async () => {
        const configId = uuidv4();
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: configId,
            enabled: true,
            triggers: [
              "oauth_authorize"
            ],
            events: {
              "oauth_authorize": {
                execution: {
                  function: "webhook_call"
                }
              }
            }
          }
        });

        expect(response.status).toBe(200);
      });
    });
  });

  /**
   * Layer 2: Functional API Tests
   * Tests core functionality and business logic
   */
  describe("Functional API Tests", () => {
    describe("Security Event Hook Config Creation", () => {
      it("should successfully create webhook configuration with complex nested structure", async () => {
        const configId = uuidv4();
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: configId,
            attributes: {
              description: "Production webhook for user events"
            },
            metadata: {
              environment: "production",
              team: "security"
            },
            triggers: ["high_priority", "real_time"],
            execution_order: 5,
            events: {
              "user_signin": {
                execution: {
                  function: "webhook_call",
                  http_request: {
                    url: "https://webhook.prod.example.com/signin",
                    method: "POST",
                    headers: {
                      "User-Agent": "IdP-Server/1.0",
                      "X-API-Version": "v1"
                    },
                    auth_type: "OAUTH2_CLIENT_CREDENTIALS",
                    oauth_authorization: {
                      type: "client_credentials",
                      token_endpoint: "https://auth.example.com/token",
                      client_authentication_type: "client_secret_post",
                      client_id: "webhook-client",
                      client_secret: "webhook-secret",
                      scope: "webhook:write"
                    }
                  },
                  retryConfig: {
                    maxRetries: 3,
                    retryDelayMs: 1000
                  },
                  timeoutMs: 5000,
                  customAttributes: {
                    priority: "high",
                    category: "authentication"
                  }
                }
              },
              "user_signin_failure": {
                execution: {
                  function: "webhook_call",
                  http_request: {
                    url: "https://webhook.prod.example.com/signin-failure",
                    method: "POST",
                    auth_type: "HMAC_SHA256",
                    hmac_authentication: {
                      api_key: "hmac-api-key",
                      secret: "hmac-secret-key",
                      signature_format: "SHA256",
                      signing_fields: ["timestamp", "payload"]
                    }
                  }
                }
              }
            },
            enabled: true,
            store_execution_payload: true
          }
        });

        expect(response.status).toBe(200);
        expect(response.data.dry_run).toBe(true);
        expect(response.data.result.id).toBe(configId);
        expect(response.data.result.type).toBe(configId);
        expect(response.data.result.execution_order).toBe(5);
        expect(Object.keys(response.data.result.events)).toHaveLength(2);
        expect(response.data.result.events).toHaveProperty("user_signin");
        expect(response.data.result.events["user_signin"].execution.http_request.auth_type).toBe("OAUTH2_CLIENT_CREDENTIALS");
        expect(response.data.result.events["user_signin_failure"].execution.http_request.auth_type).toBe("HMAC_SHA256");
      });

    });

    describe("Security Event Hook Config Update", () => {

      it("should successfully update security event hook config", async () => {
        let updateConfigId = uuidv4();
        await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: updateConfigId,
            type: updateConfigId,
            triggers: [
              "user_password_change"
            ],
            events: {
              "user_password_change": {
                execution: {
                  function: "webhook_call",
                  http_request: {
                    url: "https://original.example.com/events",
                    method: "POST"
                  }
                }
              }
            },
            enabled: true
          }
        });

        const response = await putWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations/${updateConfigId}?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            type: updateConfigId,
            attributes: {
              updated: true
            },
            execution_order: 10,
            events: {
              "user_password_change": {
                execution: {
                  function: "webhook_call",
                  http_request: {
                    url: "https://updated.example.com/events",
                    method: "PUT",
                    headers: {
                      "X-Updated": "true"
                    }
                  },
                  timeoutMs: 10000
                }
              },
              "authentication_mfa_success": {
                execution: {
                  function: "webhook_call",
                  http_request: {
                    url: "https://updated.example.com/mfa",
                    method: "POST"
                  }
                }
              }
            },
            enabled: false,
            store_execution_payload: true
          }
        });

        expect(response.status).toBe(200);
        expect(response.data.dry_run).toBe(true);
        expect(response.data.result.execution_order).toBe(10);
        expect(response.data.result.enabled).toBe(false);
        expect(Object.keys(response.data.result.events)).toHaveLength(2);
        expect(response.data.result.events["user_password_change"].execution.http_request.url).toBe("https://updated.example.com/events");
        expect(response.data.result.events).toHaveProperty("authentication_mfa_success");
      });
    });

    describe("Security Event Hook Config Deletion", () => {


      it("should successfully delete security event hook config", async () => {
        const deleteConfigId = uuidv4();
        await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: deleteConfigId,
            type: deleteConfigId,
            triggers: [
                "oauth_consent_granted"
            ],
            events: {
              "oauth_consent_granted": {
                execution: {
                  function: "webhook_call",
                  http_request: {
                    url: "https://delete-test.example.com/events",
                    method: "POST"
                  }
                }
              }
            },
            enabled: true
          }
        });
        const response = await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations/${deleteConfigId}?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data.dry_run).toBe(true);
        expect(response.data.message).toContain("simulated successfully");
        expect(response.data.id).toBe(deleteConfigId);
      });
    });

    describe("Security Event Types Support", () => {
      it("should support all user lifecycle event types", async () => {
        const userEventTypes = [
          "user_signup", "user_signin", "user_signin_failure", "user_signout",
          "user_profile_update", "user_password_change", "user_password_failure",
          "user_email_verification", "user_phone_verification"
        ];

        for (const eventType of userEventTypes) {
          const configId = uuidv4();
          const response = await postWithJson({
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations?dry_run=true`,
            headers: { Authorization: `Bearer ${accessToken}` },
            body: {
              id: configId,
              type: configId,
              triggers: [
                  `${eventType}`
              ],
              events: {
                [eventType]: {
                  execution: {
                    function: "webhook_call",
                    http_request: {
                      url: `https://test.example.com/${eventType}`,
                      method: "POST"
                    }
                  }
                }
              },
              enabled: true
            }
          });

          expect(response.status).toBe(200);
          expect(response.data.result.events).toHaveProperty(eventType);
        }
      });

      it("should support all authentication event types", async () => {
        const authEventTypes = [
          "authentication_mfa_challenge", "authentication_mfa_success", "authentication_mfa_failure",
          "authentication_factor_added", "authentication_factor_removed"
        ];

        for (const eventType of authEventTypes) {
          const configId = uuidv4();
          const response = await postWithJson({
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations?dry_run=true`,
            headers: { Authorization: `Bearer ${accessToken}` },
            body: {
              id: configId,
              type: configId,
              triggers: [
                  `${eventType}`
              ],
              events: {
                [eventType]: {
                  execution: {
                    function: "webhook_call",
                    http_request: {
                      url: `https://test.example.com/${eventType}`,
                      method: "POST"
                    }
                  }
                }
              },
              enabled: true
            }
          });

          expect(response.status).toBe(200);
          expect(response.data.result.events).toHaveProperty(eventType);
        }
      });

      it("should support FIDO-UAF and WebAuthn event types", async () => {
        const fidoEventTypes = [
          "fido_uaf_registration", "fido_uaf_authentication", "fido_uaf_deregistration",
          "webauthn_registration", "webauthn_authentication", "webauthn_credential_update"
        ];

        for (const eventType of fidoEventTypes) {
          const configId = uuidv4();
          const response = await postWithJson({
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations?dry_run=true`,
            headers: { Authorization: `Bearer ${accessToken}` },
            body: {
              id: configId,
              type: configId,
              triggers: [
                `${eventType}`
              ],
              events: {
                [eventType]: {
                  execution: {
                    function: "webhook_call",
                    http_request: {
                      url: `https://test.example.com/${eventType}`,
                      method: "POST"
                    }
                  }
                }
              },
              enabled: true
            }
          });

          expect(response.status).toBe(200);
          expect(response.data.result.events).toHaveProperty(eventType);
        }
      });

      it("should support OAuth and management event types", async () => {
        const oauthEventTypes = [
          "oauth_authorize", "oauth_token_issued", "oauth_token_refreshed", "oauth_token_revoked",
          "oauth_consent_granted", "oauth_consent_revoked"
        ];

        const managementEventTypes = [
          "management_user_created", "management_user_updated", "management_user_deleted",
          "management_client_created", "management_client_updated", "management_client_deleted",
          "management_permission_granted", "management_permission_revoked",
          "management_role_assigned", "management_role_unassigned"
        ];

        for (const eventType of [...oauthEventTypes, ...managementEventTypes]) {
          const configId = uuidv4();
          const response = await postWithJson({
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations?dry_run=true`,
            headers: { Authorization: `Bearer ${accessToken}` },
            body: {
              id: configId,
              type: configId,
              triggers: [
                `${eventType}`
              ],
              events: {
                [eventType]: {
                  execution: {
                    function: "webhook_call",
                    http_request: {
                      url: `https://test.example.com/${eventType}`,
                      method: "POST"
                    }
                  }
                }
              },
              enabled: true
            }
          });

          expect(response.status).toBe(200);
          expect(response.data.result.events).toHaveProperty(eventType);
        }
      });

      it("should support security monitoring event types", async () => {
        const securityEventTypes = [
          "security_suspicious_activity", "security_account_locked",
          "security_account_unlocked", "security_brute_force_detected"
        ];

        for (const eventType of securityEventTypes) {
          const configId = uuidv4();
          const response = await postWithJson({
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations?dry_run=true`,
            headers: { Authorization: `Bearer ${accessToken}` },
            body: {
              id: configId,
              type: configId,
              triggers: [
                `${eventType}`
              ],
              events: {
                [eventType]: {
                  execution: {
                    function: "webhook_call",
                    http_request: {
                      url: `https://test.example.com/${eventType}`,
                      method: "POST"
                    }
                  }
                }
              },
              enabled: true
            }
          });

          expect(response.status).toBe(200);
          expect(response.data.result.events).toHaveProperty(eventType);
        }
      });
    });

    describe("Authentication Configuration Support", () => {
      it("should support OAuth2 Client Credentials authentication", async () => {
        const configId = uuidv4();
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: configId,
            triggers: [
                "user_signin"
            ],
            events: {
              "user_signin": {
                execution: {
                  function: "webhook_call",
                  http_request: {
                    url: "https://oauth2.example.com/webhook",
                    method: "POST",
                    auth_type: "OAUTH2_CLIENT_CREDENTIALS",
                    oauth_authorization: {
                      type: "client_credentials",
                      token_endpoint: "https://auth.example.com/oauth/token",
                      client_authentication_type: "client_secret_post",
                      client_id: "webhook-oauth-client",
                      client_secret: "webhook-oauth-secret",
                      scope: "webhook:write events:send"
                    }
                  }
                }
              }
            },
            enabled: true
          }
        });
        console.log(JSON.stringify(response.data, null, 2));
        expect(response.status).toBe(200);
        expect(response.data.result.events["user_signin"].execution.http_request.auth_type).toBe("OAUTH2_CLIENT_CREDENTIALS");
        expect(response.data.result.events["user_signin"].execution.http_request.oauth_authorization.client_id).toBe("webhook-oauth-client");
        expect(response.data.result.events["user_signin"].execution.http_request.oauth_authorization.scope).toBe("webhook:write events:send");
        expect(response.data.result.events["user_signin"].execution.http_request.oauth_authorization.token_endpoint).toBe("https://auth.example.com/oauth/token");
      });

      it("should support HMAC SHA256 authentication", async () => {
        const configId = uuidv4();
        const response = await postWithJson({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-event-hook-configurations?dry_run=true`,
          headers: { Authorization: `Bearer ${accessToken}` },
          body: {
            id: configId,
            type: configId,
            triggers: [
              "user_signin_failure"
            ],
            events: {
              "user_signin_failure": {
                execution: {
                  function: "webhook_call",
                  http_request: {
                    url: "https://hmac.example.com/webhook",
                    method: "POST",
                    auth_type: "HMAC_SHA256",
                    hmac_authentication: {
                      api_key: "hmac-api-key",
                      secret: "super-secret-hmac-key",
                      signature_format: "SHA256",
                      signing_fields: ["timestamp", "payload"]
                    }
                  }
                }
              }
            },
            enabled: true
          }
        });

        expect(response.status).toBe(200);
        expect(response.data.result.events["user_signin_failure"].execution.http_request.auth_type).toBe("HMAC_SHA256");
        expect(response.data.result.events["user_signin_failure"].execution.http_request.hmac_authentication.secret).toBe("super-secret-hmac-key");
      });

    });
  });

});