/*
 * Identity Verification Application Management API - Schema Compliance Tests (System Level)
 *
 * Validates that actual API responses match the OpenAPI specification
 * defined in swagger-cp-identity-verification-application-ja.yaml.
 *
 * Uses the integration test tenant (67e7eae6-...) which has data created by IDA integration tests.
 */

import { describe, expect, it, beforeAll } from "@jest/globals";
import { get, deletion } from "../../../../lib/http";
import { requestToken } from "../../../../api/oauthClient";
import { adminServerConfig, backendUrl, serverConfig } from "../../../testConfig";
import {
  isUUID,
  isISODateTime,
  validateIdentityVerificationApplication,
  validateListResponse,
} from "../../../../lib/schemaValidation";

describe("Identity Verification Application Management API - Schema Compliance Tests", () => {
  let accessToken;

  // Use integration test tenant which has data from IDA integration tests
  const tenantId = serverConfig.tenantId;
  const baseUrl = `${backendUrl}/v1/management/tenants/${tenantId}/identity-verification-applications`;

  beforeAll(async () => {
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
    expect(tokenResponse.data).toHaveProperty("access_token");
    accessToken = tokenResponse.data.access_token;
  });

  /**
   * Layer 1: List Response Structure Validation
   * Validates IdentityVerificationApplicationListResponse schema
   */
  describe("List Response Structure (IdentityVerificationApplicationListResponse)", () => {
    it("should have all required fields with correct types", async () => {
      const response = await get({
        url: `${baseUrl}?limit=10&offset=0`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(response.status).toBe(200);

      // Required fields
      expect(response.data).toHaveProperty("list");
      expect(response.data).toHaveProperty("total_count");
      expect(response.data).toHaveProperty("limit");
      expect(response.data).toHaveProperty("offset");

      // Types
      expect(Array.isArray(response.data.list)).toBe(true);
      expect(typeof response.data.total_count).toBe("number");
      expect(typeof response.data.limit).toBe("number");
      expect(typeof response.data.offset).toBe("number");

      // Values
      expect(response.data.limit).toBe(10);
      expect(response.data.offset).toBe(0);
      expect(response.data.total_count).toBeGreaterThanOrEqual(0);

      // Full list validation
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

    it("should return empty list with correct structure for non-matching filter", async () => {
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
   * Validates IdentityVerificationApplication schema
   */
  describe("Application Schema (IdentityVerificationApplication)", () => {
    it("should validate all required fields for each item in list", async () => {
      const response = await get({
        url: `${baseUrl}?limit=5`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(response.status).toBe(200);

      if (response.data.list.length === 0) {
        console.log(
          "⚠️ No data found. Run IDA integration tests first: npm test -- --testPathPattern='integration-0[1-4]-identity'"
        );
        return;
      }

      console.log(`Validating ${response.data.list.length} applications...`);

      for (const application of response.data.list) {
        const validation =
          validateIdentityVerificationApplication(application);
        if (!validation.valid) {
          console.log(
            `❌ Validation errors for ${application.id}:`,
            validation.errors
          );
        }
        expect(validation.valid).toBe(true);

        // Required fields - type check
        expect(isUUID(application.id)).toBe(true);
        expect(typeof application.type).toBe("string");
        expect(typeof application.tenant_id).toBe("string");
        expect(typeof application.client_id).toBe("string");
        expect(typeof application.user_id).toBe("string");
        expect(typeof application.status).toBe("string");
        expect(isISODateTime(application.requested_at)).toBe(true);

        // Status enum
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

        console.log(
          `✅ ${application.id} (status=${application.status}, type=${application.type})`
        );
      }
    });

    it("should validate single GET response matches list item", async () => {
      const listResponse = await get({
        url: `${baseUrl}?limit=1`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(listResponse.status).toBe(200);

      if (listResponse.data.list.length === 0) {
        console.log("⚠️ No data found - skipping detail validation.");
        return;
      }

      const applicationId = listResponse.data.list[0].id;

      const detailResponse = await get({
        url: `${baseUrl}/${applicationId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(detailResponse.status).toBe(200);

      const application = detailResponse.data;
      const validation = validateIdentityVerificationApplication(application);
      if (!validation.valid) {
        console.log("❌ Detail validation errors:", validation.errors);
      }
      expect(validation.valid).toBe(true);

      // Verify same data as list
      expect(application.id).toBe(applicationId);
      expect(isUUID(application.id)).toBe(true);

      // Optional fields type check
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
  });

  /**
   * Layer 3: ProcessResult Schema Validation
   */
  describe("ProcessResult Schema", () => {
    it("should validate call_count, success_count, failure_count as numbers", async () => {
      const response = await get({
        url: `${baseUrl}?limit=10`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(response.status).toBe(200);

      const appsWithProcesses = response.data.list.filter(
        (app) =>
          app.processes &&
          typeof app.processes === "object" &&
          Object.keys(app.processes).length > 0
      );

      if (appsWithProcesses.length === 0) {
        console.log("⚠️ No applications with process results found.");
        return;
      }

      console.log(
        `Validating ProcessResult for ${appsWithProcesses.length} applications...`
      );

      for (const application of appsWithProcesses) {
        for (const [processName, result] of Object.entries(
          application.processes
        )) {
          // Type validation
          expect(typeof result).toBe("object");
          expect(typeof result.call_count).toBe("number");
          expect(typeof result.success_count).toBe("number");
          expect(typeof result.failure_count).toBe("number");

          // Value constraints
          expect(result.call_count).toBeGreaterThanOrEqual(0);
          expect(result.success_count).toBeGreaterThanOrEqual(0);
          expect(result.failure_count).toBeGreaterThanOrEqual(0);

          console.log(
            `✅ ${application.id}/${processName}: calls=${result.call_count}, success=${result.success_count}, failure=${result.failure_count}`
          );
        }
      }
    });
  });

  /**
   * Layer 4: Query Parameter Filter Validation
   */
  describe("Query Parameter Filters", () => {
    it("should filter by status correctly", async () => {
      // Get all to find existing statuses
      const allResponse = await get({
        url: `${baseUrl}?limit=50`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      const existingStatuses = [
        ...new Set(allResponse.data.list.map((app) => app.status)),
      ];

      for (const status of existingStatuses) {
        const response = await get({
          url: `${baseUrl}?status=${status}&limit=50`,
          headers: { Authorization: `Bearer ${accessToken}` },
        });

        expect(response.status).toBe(200);

        for (const app of response.data.list) {
          expect(app.status).toBe(status);
        }

        console.log(
          `✅ status=${status}: ${response.data.total_count} results`
        );
      }
    });

    it("should filter by client_id correctly", async () => {
      const allResponse = await get({
        url: `${baseUrl}?limit=5`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      if (allResponse.data.list.length === 0) {
        console.log("⚠️ No data to test client_id filtering.");
        return;
      }

      const clientId = allResponse.data.list[0].client_id;

      const response = await get({
        url: `${baseUrl}?client_id=${encodeURIComponent(clientId)}&limit=50`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(response.status).toBe(200);

      for (const app of response.data.list) {
        expect(app.client_id).toBe(clientId);
      }

      console.log(
        `✅ client_id=${clientId}: ${response.data.total_count} results`
      );
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

    it("should support offset for pagination", async () => {
      const firstPage = await get({
        url: `${baseUrl}?limit=2&offset=0`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      const secondPage = await get({
        url: `${baseUrl}?limit=2&offset=2`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(firstPage.status).toBe(200);
      expect(secondPage.status).toBe(200);
      expect(firstPage.data.total_count).toBe(secondPage.data.total_count);

      // Pages should not overlap
      if (
        firstPage.data.list.length > 0 &&
        secondPage.data.list.length > 0
      ) {
        const firstIds = firstPage.data.list.map((app) => app.id);
        const secondIds = secondPage.data.list.map((app) => app.id);
        const overlap = firstIds.filter((id) => secondIds.includes(id));
        expect(overlap).toEqual([]);
      }
    });
  });

  /**
   * Layer 5: Error Response Validation
   */
  describe("Error Responses (ErrorResponse)", () => {
    it("should return 404 with error and error_description for non-existent ID", async () => {
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
   * Layer 6: Delete dry_run Validation
   */
  describe("Delete with dry_run (DryRunResponse)", () => {
    it("should return 200 with dry_run=true and not delete the resource", async () => {
      const listResponse = await get({
        url: `${baseUrl}?limit=1`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(listResponse.status).toBe(200);

      if (listResponse.data.list.length === 0) {
        console.log("⚠️ No data for dry_run delete test.");
        return;
      }

      const applicationId = listResponse.data.list[0].id;

      // dry_run delete
      const dryRunResponse = await deletion({
        url: `${baseUrl}/${applicationId}?dry_run=true`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(dryRunResponse.status).toBe(200);
      expect(dryRunResponse.data).toHaveProperty("dry_run", true);

      // Verify still exists
      const verifyResponse = await get({
        url: `${baseUrl}/${applicationId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(verifyResponse.status).toBe(200);
      expect(verifyResponse.data.id).toBe(applicationId);
    });
  });

  /**
   * Layer 7: List/Detail Consistency
   */
  describe("List and Detail Consistency", () => {
    it("should return identical data in list and detail responses", async () => {
      const listResponse = await get({
        url: `${baseUrl}?limit=3`,
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      expect(listResponse.status).toBe(200);

      if (listResponse.data.list.length === 0) {
        console.log("⚠️ No data for consistency test.");
        return;
      }

      for (const listItem of listResponse.data.list) {
        const detailResponse = await get({
          url: `${baseUrl}/${listItem.id}`,
          headers: { Authorization: `Bearer ${accessToken}` },
        });

        expect(detailResponse.status).toBe(200);
        const detail = detailResponse.data;

        // All fields must match
        expect(detail.id).toBe(listItem.id);
        expect(detail.type).toBe(listItem.type);
        expect(detail.tenant_id).toBe(listItem.tenant_id);
        expect(detail.client_id).toBe(listItem.client_id);
        expect(detail.user_id).toBe(listItem.user_id);
        expect(detail.status).toBe(listItem.status);
        expect(detail.requested_at).toBe(listItem.requested_at);
        expect(JSON.stringify(detail.processes)).toBe(
          JSON.stringify(listItem.processes)
        );
        expect(JSON.stringify(detail.application_details)).toBe(
          JSON.stringify(listItem.application_details)
        );
      }
    });
  });
});
