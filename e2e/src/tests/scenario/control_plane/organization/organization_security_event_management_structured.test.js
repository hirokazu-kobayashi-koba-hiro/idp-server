/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Organization Security Event Management API - Structured E2E Tests
 *
 * This test suite provides comprehensive testing for organization-level security event management,
 * covering all aspects of the security event retrieval API including:
 *
 * - Security Event List Retrieval (GET)
 * - Security Event Detail Retrieval (GET)
 * - Pagination and Filtering
 * - Organization-level Access Control
 * - Error Cases and Edge Cases
 * - API Specification Compliance
 */

import { describe, expect, it, beforeAll } from "@jest/globals";
import { get } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { backendUrl } from "../../../testConfig";
import { validateSecurityEvent, validateListResponse } from "../../../../lib/schemaValidation";

describe("Organization Security Event Management API - Structured Tests", () => {
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
      it("should return correct response structure for security events list", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=10&offset=0`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect([200]).toContain(response.status);
        expect(response.data).toHaveProperty("list");
        expect(response.data).toHaveProperty("total_count");
        expect(response.data).toHaveProperty("limit", 10);
        expect(response.data).toHaveProperty("offset", 0);
        expect(Array.isArray(response.data.list)).toBe(true);
        expect(typeof response.data.total_count).toBe("number");

        // OpenAPI schema validation
        const validation = validateListResponse(response.data, validateSecurityEvent);
        if (!validation.valid) {
          console.log("Schema validation errors:", validation.errors);
        }
        expect(validation.valid).toBe(true);
      });

      it("should return correct response structure for security event detail when event exists", async () => {
        // First get list to find an event ID
        const listResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=1`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        if (listResponse.status === 200 && listResponse.data.list.length > 0) {
          const eventId = listResponse.data.list[0].id;

          const response = await get({
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events/${eventId}`,
            headers: { Authorization: `Bearer ${accessToken}` }
          });

          expect(response.status).toBe(200);
          expect(response.data).toHaveProperty("id");
          expect(response.data).toHaveProperty("type");
          expect(response.data).toHaveProperty("description");
          expect(response.data).toHaveProperty("tenant");
          expect(response.data).toHaveProperty("client");
          expect(response.data).toHaveProperty("detail");
          expect(response.data).toHaveProperty("created_at");

          // OpenAPI schema validation
          const validation = validateSecurityEvent(response.data);
          if (!validation.valid) {
            console.log("Schema validation errors:", validation.errors);
          }
          expect(validation.valid).toBe(true);
        }
      });
    });

    describe("Error Response Structure Validation", () => {
      it("should return standard error structure for 404 Not Found", async () => {
        const nonExistentEventId = "00000000-0000-0000-0000-000000000000";
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events/${nonExistentEventId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(404);
      });

      //TODO backend must implement validation
      xit("should return standard error structure for 400 Bad Request", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=invalid`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toContain(400);
        // Some systems may return 500 for invalid query parameters
      });
    });
  });

  /**
   * Layer 2: Functional API Tests
   * Tests core functionality and business logic
   */
  describe("Functional API Tests", () => {
    describe("Security Event List Retrieval", () => {
      it("should successfully retrieve security events list", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(Array.isArray(response.data.list)).toBe(true);
        expect(typeof response.data.total_count).toBe("number");
        
      });

      it("should support pagination with limit parameter", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=5`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("limit", 5);
        expect(response.data.list.length).toBeLessThanOrEqual(5);
      });

      it("should support pagination with offset parameter", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=5&offset=2`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("limit", 5);
        expect(response.data).toHaveProperty("offset", 2);
      });

      it("should support event_type filtering", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?event_type=authentication_failure`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        response.data.list.forEach(event => {
          expect(event.type).toBe("authentication_failure");
        });
      });

      it("should support date range filtering with from and to parameters", async () => {
        const fromDate = new Date(Date.now() - 24 * 60 * 60 * 1000);
        const toDate = new Date();

        // Format as LocalDateTime (without timezone Z)
        const from = fromDate.toISOString().replace("Z", "");
        const to = toDate.toISOString().replace("Z", "");

        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(Array.isArray(response.data.list)).toBe(true);
      });

      it("should support client_id filtering", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?client_id=test-client`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(Array.isArray(response.data.list)).toBe(true);
      });

      it("should support user_id filtering", async () => {
        const testUserId = "12345678-1234-1234-1234-123456789012";

        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?user_id=${testUserId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(Array.isArray(response.data.list)).toBe(true);
      });

      it("should support external_user_id filtering", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?external_user_id=external-user-123`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(Array.isArray(response.data.list)).toBe(true);
      });
    });

    describe("Security Event Detail Retrieval", () => {
      it("should return 404 for non-existent security event", async () => {
        const nonExistentId = "00000000-0000-0000-0000-000000000000";
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events/${nonExistentId}`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(404);
      });
    });
  });

  /**
   * Layer 3: Organization Access Control Tests
   * Tests organization-level security and isolation
   */
  describe("Organization Access Control Tests", () => {
    it("should return 401 for unauthenticated requests", async () => {
      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events`,
        headers: {}
      });

      expect(response.status).toBe(401);
    });

    it("should return 400/404 for invalid organization ID", async () => {
      const invalidOrgId = "invalid-org-id";
      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${invalidOrgId}/tenants/${tenantId}/security-events`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });

      expect([400, 404]).toContain(response.status);
    });

    it("should return 400 for invalid tenant ID", async () => {
      const invalidTenantId = "invalid-tenant-id";
      const response = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${invalidTenantId}/security-events`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });

      expect(response.status).toBe(400);
    });
  });

  /**
   * Layer 4: Integration Tests
   * Tests complete workflows and integrations
   */
  describe("Integration Tests", () => {
    describe("Complete Security Event Monitoring Workflow", () => {
      it("should successfully retrieve and paginate through security events", async () => {
        let offset = 0;
        const limit = 5;
        let hasMoreEvents = true;
        let allEvents = [];

        while (hasMoreEvents && allEvents.length < 20) { // Limit to prevent infinite loops
          const response = await get({
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=${limit}&offset=${offset}`,
            headers: { Authorization: `Bearer ${accessToken}` }
          });

          expect(response.status).toBe(200);

          if (response.status === 404) {
            hasMoreEvents = false;
            break;
          }

          expect(response.data).toHaveProperty("list");
          expect(response.data).toHaveProperty("total_count");
          expect(response.data).toHaveProperty("limit", limit);
          expect(response.data).toHaveProperty("offset", offset);

          allEvents = allEvents.concat(response.data.list);

          // Check if there are more events
          if (response.data.list.length < limit || allEvents.length >= response.data.total_count) {
            hasMoreEvents = false;
          } else {
            offset += limit;
          }
        }

        // Verify that we can retrieve events and they have proper structure
        if (allEvents.length > 0) {
          const firstEvent = allEvents[0];
          expect(firstEvent).toHaveProperty("id");
          expect(firstEvent).toHaveProperty("type");
          expect(firstEvent).toHaveProperty("description");
          expect(firstEvent).toHaveProperty("created_at");
        }
      });

      it("should successfully retrieve specific security event details", async () => {
        // First get a list of events
        const listResponse = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=1`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        if (listResponse.status === 200 && listResponse.data.list.length > 0) {
          const eventId = listResponse.data.list[0].id;

          // Then get the specific event detail
          const detailResponse = await get({
            url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events/${eventId}`,
            headers: { Authorization: `Bearer ${accessToken}` }
          });

          expect(detailResponse.status).toBe(200);
          expect(detailResponse.data.id).toBe(eventId);
          expect(detailResponse.data).toHaveProperty("tenant");
          expect(detailResponse.data.tenant).toHaveProperty("id");
          expect(detailResponse.data).toHaveProperty("client");
          expect(detailResponse.data.client).toHaveProperty("id");
          expect(detailResponse.data).toHaveProperty("detail");
        }
      });
    });

    describe("Event Filtering and Search", () => {
      it("should support multiple query parameters simultaneously", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=3&offset=1&event_type=authentication_success`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("limit", 3);
        expect(response.data).toHaveProperty("offset", 1);
        expect(response.data.list.length).toBeLessThanOrEqual(3);
      });

      it("should support complex filtering with multiple parameters", async () => {
        const from = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(); // 7 days ago
        const to = new Date().toISOString(); // now

        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?limit=5&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&client_id=test-client`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });
        if (response.status !== 200) {
          console.log(JSON.stringify(response.data, null, 2));
        }
        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("limit", 5);
        expect(response.data).toHaveProperty("list");
        expect(Array.isArray(response.data.list)).toBe(true);
        expect(response.data.list.length).toBeLessThanOrEqual(5);
      });

      it("should handle empty results gracefully with filtering", async () => {
        const response = await get({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/security-events?event_type=non_existent_event_type&user_id=00000000-0000-0000-0000-000000000000`,
          headers: { Authorization: `Bearer ${accessToken}` }
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(response.data).toHaveProperty("total_count");
        expect(Array.isArray(response.data.list)).toBe(true);
      });
    });
  });
});