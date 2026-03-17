/*
 * Organization Identity Verification Application Management API - Structured E2E Tests
 *
 * This test suite validates that actual API responses match the OpenAPI specification
 * defined in swagger-cp-identity-verification-application-ja.yaml.
 *
 * - List Response Structure (IdentityVerificationApplicationListResponse)
 * - Application Detail Structure (IdentityVerificationApplication)
 * - ProcessResult Structure
 * - Query Parameters (pagination, filtering)
 * - Delete with dry_run
 * - Error Responses
 */

import { describe, expect, it, beforeAll } from "@jest/globals";
import { get, deletion } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { backendUrl } from "../../../testConfig";
import {
  isUUID,
  isISODateTime,
  validateIdentityVerificationApplication,
  validateListResponse,
} from "../../../../lib/schemaValidation";

describe("Organization Identity Verification Application Management API - Schema Compliance Tests", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";
  let accessToken;

  const baseUrl = `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-applications`;

  beforeAll(async () => {
    const authResponse = await requestToken({
      endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      clientId: "org-client",
      clientSecret: "org-client-001",
      scope: "org-management account management",
    });

    expect(authResponse.status).toBe(200);
    expect(authResponse.data).toHaveProperty("access_token");
    accessToken = authResponse.data.access_token;
  });

  /**
   * Layer 1: List Response Structure Validation
   * Validates IdentityVerificationApplicationListResponse schema
   */
  describe("List Response Structure Validation", () => {
    it("should match IdentityVerificationApplicationListResponse schema", async () => {
      const response = await get({
        url: `${baseUrl}?limit=10&offset=0`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(response.status).toBe(200);

      // Required fields per OpenAPI spec
      expect(response.data).toHaveProperty("list");
      expect(response.data).toHaveProperty("total_count");
      expect(response.data).toHaveProperty("limit");
      expect(response.data).toHaveProperty("offset");

      // Type validation
      expect(Array.isArray(response.data.list)).toBe(true);
      expect(typeof response.data.total_count).toBe("number");
      expect(typeof response.data.limit).toBe("number");
      expect(typeof response.data.offset).toBe("number");

      // Value validation
      expect(response.data.limit).toBe(10);
      expect(response.data.offset).toBe(0);
      expect(response.data.total_count).toBeGreaterThanOrEqual(0);

      // Schema validation using validator
      const validation = validateListResponse(
        response.data,
        validateIdentityVerificationApplication
      );
      if (!validation.valid) {
        console.log("Schema validation errors:", validation.errors);
      }
      expect(validation.valid).toBe(true);
    });

    it("should use default limit=20 and offset=0 when not specified", async () => {
      const response = await get({
        url: baseUrl,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(response.status).toBe(200);
      expect(response.data.limit).toBe(20);
      expect(response.data.offset).toBe(0);
    });

    it("should return empty list with correct structure when no results match", async () => {
      const response = await get({
        url: `${baseUrl}?type=NonExistentType_schema_test&limit=10&offset=0`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(response.status).toBe(200);
      expect(response.data.list).toEqual([]);
      expect(response.data.total_count).toBe(0);
      expect(response.data.limit).toBe(10);
      expect(response.data.offset).toBe(0);
    });
  });

  /**
   * Layer 2: Individual Application Schema Validation
   * Validates IdentityVerificationApplication schema when data exists
   */
  describe("Application Detail Schema Validation", () => {
    it("should match IdentityVerificationApplication schema for each item in list", async () => {
      const response = await get({
        url: `${baseUrl}?limit=5`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(response.status).toBe(200);

      if (response.data.list.length === 0) {
        console.log(
          "⚠️ No identity verification applications found - skipping individual item validation. Run IDA integration tests first to create test data."
        );
        return;
      }

      for (const application of response.data.list) {
        const validation =
          validateIdentityVerificationApplication(application);
        if (!validation.valid) {
          console.log(
            `Schema validation errors for application ${application.id}:`,
            validation.errors
          );
        }
        expect(validation.valid).toBe(true);

        // Required fields per OpenAPI spec
        expect(isUUID(application.id)).toBe(true);
        expect(typeof application.type).toBe("string");
        expect(typeof application.tenant_id).toBe("string");
        expect(typeof application.client_id).toBe("string");
        expect(typeof application.user_id).toBe("string");
        expect(typeof application.status).toBe("string");
        expect(isISODateTime(application.requested_at)).toBe(true);

        // Status enum validation
        expect([
          "requested",
          "applying",
          "applied",
          "examination_processing",
          "approved",
          "rejected",
          "expired",
          "cancelled",
        ]).toContain(application.status);
      }
    });

    it("should match IdentityVerificationApplication schema for single GET", async () => {
      const listResponse = await get({
        url: `${baseUrl}?limit=1`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(listResponse.status).toBe(200);

      if (listResponse.data.list.length === 0) {
        console.log(
          "⚠️ No identity verification applications found - skipping detail validation."
        );
        return;
      }

      const applicationId = listResponse.data.list[0].id;

      const detailResponse = await get({
        url: `${baseUrl}/${applicationId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(detailResponse.status).toBe(200);

      const application = detailResponse.data;

      // Full schema validation
      const validation = validateIdentityVerificationApplication(application);
      if (!validation.valid) {
        console.log("Detail schema validation errors:", validation.errors);
      }
      expect(validation.valid).toBe(true);

      // Verify required fields
      expect(application.id).toBe(applicationId);
      expect(isUUID(application.id)).toBe(true);
      expect(typeof application.type).toBe("string");
      expect(typeof application.tenant_id).toBe("string");
      expect(typeof application.client_id).toBe("string");
      expect(typeof application.user_id).toBe("string");
      expect(typeof application.status).toBe("string");
      expect(isISODateTime(application.requested_at)).toBe(true);

      // Verify optional fields type when present
      if (application.application_details !== undefined) {
        expect(typeof application.application_details).toBe("object");
      }
      if (application.processes !== undefined) {
        expect(typeof application.processes).toBe("object");
      }
      if (application.attributes !== undefined) {
        expect(typeof application.attributes).toBe("object");
      }
    });

    it("should validate ProcessResult schema for each process", async () => {
      const listResponse = await get({
        url: `${baseUrl}?limit=10`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(listResponse.status).toBe(200);

      const appsWithProcesses = listResponse.data.list.filter(
        (app) =>
          app.processes &&
          typeof app.processes === "object" &&
          Object.keys(app.processes).length > 0
      );

      if (appsWithProcesses.length === 0) {
        console.log(
          "⚠️ No applications with process results found - skipping ProcessResult validation."
        );
        return;
      }

      for (const application of appsWithProcesses) {
        for (const [processName, result] of Object.entries(
          application.processes
        )) {
          expect(typeof result).toBe("object");
          expect(typeof result.call_count).toBe("number");
          expect(typeof result.success_count).toBe("number");
          expect(typeof result.failure_count).toBe("number");
          expect(result.call_count).toBeGreaterThanOrEqual(0);
          expect(result.success_count).toBeGreaterThanOrEqual(0);
          expect(result.failure_count).toBeGreaterThanOrEqual(0);
          expect(result.call_count).toBeGreaterThanOrEqual(
            result.success_count + result.failure_count
          );
          console.log(
            `✅ ProcessResult validated: ${processName} (calls=${result.call_count}, success=${result.success_count}, failure=${result.failure_count})`
          );
        }
      }
    });
  });

  /**
   * Layer 3: Query Parameter Validation
   * Validates that query parameters produce correctly filtered results
   */
  describe("Query Parameter Validation", () => {
    it("should filter by status and return only matching items", async () => {
      const statuses = [
        "requested",
        "applying",
        "applied",
        "examination_processing",
        "approved",
        "rejected",
        "expired",
        "cancelled",
      ];

      for (const status of statuses) {
        const response = await get({
          url: `${baseUrl}?status=${status}&limit=5`,
          headers: { Authorization: `Bearer ${accessToken}` },
        });

        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty("list");
        expect(response.data).toHaveProperty("total_count");

        for (const app of response.data.list) {
          expect(app.status).toBe(status);
        }
      }
    });

    it("should filter by type and return only matching items", async () => {
      // First get list to find existing types
      const listResponse = await get({
        url: `${baseUrl}?limit=1`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      if (listResponse.data.list.length === 0) {
        console.log("⚠️ No data to test type filtering.");
        return;
      }

      const existingType = listResponse.data.list[0].type;

      const filteredResponse = await get({
        url: `${baseUrl}?type=${encodeURIComponent(existingType)}&limit=10`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(filteredResponse.status).toBe(200);
      for (const app of filteredResponse.data.list) {
        expect(app.type).toBe(existingType);
      }
    });

    it("should filter by client_id and return only matching items", async () => {
      const listResponse = await get({
        url: `${baseUrl}?limit=1`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      if (listResponse.data.list.length === 0) {
        console.log("⚠️ No data to test client_id filtering.");
        return;
      }

      const existingClientId = listResponse.data.list[0].client_id;

      const filteredResponse = await get({
        url: `${baseUrl}?client_id=${encodeURIComponent(existingClientId)}&limit=10`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(filteredResponse.status).toBe(200);
      for (const app of filteredResponse.data.list) {
        expect(app.client_id).toBe(existingClientId);
      }
    });

    it("should respect pagination limit", async () => {
      const response = await get({
        url: `${baseUrl}?limit=2&offset=0`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(response.status).toBe(200);
      expect(response.data.list.length).toBeLessThanOrEqual(2);
      expect(response.data.limit).toBe(2);
      expect(response.data.offset).toBe(0);
    });

    it("should support time range filtering with from parameter", async () => {
      const oneYearAgo = new Date();
      oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1);
      const fromDate = oneYearAgo
        .toISOString()
        .substring(0, 19)
        .replace("T", " ");

      const response = await get({
        url: `${baseUrl}?from=${encodeURIComponent(fromDate)}&limit=10`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("list");
      expect(response.data).toHaveProperty("total_count");
    });
  });

  /**
   * Layer 4: Error Response Validation
   * Validates error response structure matches OpenAPI spec
   */
  describe("Error Response Validation", () => {
    it("should return 404 with ErrorResponse schema for non-existent application", async () => {
      const response = await get({
        url: `${baseUrl}/00000000-0000-0000-0000-000000000000`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(response.status).toBe(404);
      expect(response.data).toHaveProperty("error");
      expect(response.data).toHaveProperty("error_description");
      expect(typeof response.data.error).toBe("string");
      expect(typeof response.data.error_description).toBe("string");
    });
  });

  /**
   * Layer 5: Delete with dry_run Validation
   * Validates dry_run delete response matches OpenAPI spec
   */
  describe("Delete dry_run Response Validation", () => {
    it("should return 200 with dry_run response when dry_run=true", async () => {
      const listResponse = await get({
        url: `${baseUrl}?limit=1`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(listResponse.status).toBe(200);

      if (listResponse.data.list.length === 0) {
        console.log(
          "⚠️ No applications found - skipping dry_run delete validation."
        );
        return;
      }

      const applicationId = listResponse.data.list[0].id;

      const dryRunResponse = await deletion({
        url: `${baseUrl}/${applicationId}?dry_run=true`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(dryRunResponse.status).toBe(200);
      expect(dryRunResponse.data).toHaveProperty("dry_run", true);

      // Verify application still exists after dry_run
      const verifyResponse = await get({
        url: `${baseUrl}/${applicationId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(verifyResponse.status).toBe(200);
      expect(verifyResponse.data.id).toBe(applicationId);
    });
  });

  /**
   * Layer 6: Consistency between List and Detail
   * Validates that list items and detail responses are consistent
   */
  describe("List and Detail Consistency", () => {
    it("should return same data in list item and detail response", async () => {
      const listResponse = await get({
        url: `${baseUrl}?limit=3`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(listResponse.status).toBe(200);

      if (listResponse.data.list.length === 0) {
        console.log(
          "⚠️ No applications found - skipping consistency validation."
        );
        return;
      }

      for (const listItem of listResponse.data.list) {
        const detailResponse = await get({
          url: `${baseUrl}/${listItem.id}`,
          headers: { Authorization: `Bearer ${accessToken}` },
        });

        expect(detailResponse.status).toBe(200);

        const detail = detailResponse.data;

        // Core fields must match
        expect(detail.id).toBe(listItem.id);
        expect(detail.type).toBe(listItem.type);
        expect(detail.tenant_id).toBe(listItem.tenant_id);
        expect(detail.client_id).toBe(listItem.client_id);
        expect(detail.user_id).toBe(listItem.user_id);
        expect(detail.status).toBe(listItem.status);
        expect(detail.requested_at).toBe(listItem.requested_at);
      }
    });
  });
});
