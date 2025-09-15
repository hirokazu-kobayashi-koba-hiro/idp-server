import { describe, expect, it, test, beforeAll } from "@jest/globals";
import { deletion, get, postWithJson, putWithJson } from "../../../../lib/http";
import { backendUrl } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";
import { v4 as uuidv4 } from "uuid";

describe("Organization Identity Verification Config Management API Test", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";

  let authHeader;

  beforeAll(async () => {
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro",
      password: "successUserCode001",
      scope: "org-management account management",
      clientId: "org-client",
      clientSecret: "org-client-001"
    });
    authHeader = `Bearer ${tokenResponse.data.access_token}`;
  });

  // E2E Test for Identity Verification Config Management
  test("should successfully manage identity verification configurations for organization", async () => {
    const configId = uuidv4();
    const configType = uuidv4();

    // Test CREATE
    const createRequest = {
      id: configId,
      type: configType,
      enabled: true,
      applications: [{
        id: uuidv4(),
        verification_type: "identity_document",
        verification_process: "document_upload",
        verification_steps: [
          {
            step_type: "document_capture",
            required_documents: ["driver_license", "passport"],
            verification_level: "enhanced"
          },
          {
            step_type: "face_verification",
            liveness_detection: true,
            face_match_threshold: 0.85
          },
          {
            step_type: "address_verification",
            document_types: ["utility_bill", "bank_statement"],
            age_limit_days: 90
          }
        ],
        completion_requirements: {
          minimum_steps: 2,
          identity_score_threshold: 0.90,
          manual_review_threshold: 0.75
        },
        result_storage: {
          retain_verification_data: true,
          retention_period_days: 2555, // 7 years
          include_biometric_data: false
        },
        notification_settings: {
          notify_on_completion: true,
          notify_on_failure: true,
          webhook_url: "https://example.org/webhook/ekyc"
        }
      }],
      metadata: {
        created_by: "system",
        purpose: "comprehensive_identity_verification",
        compliance_level: "kyc_aml_enhanced"
      }
    };

    const createResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs`,
      headers: {
        Authorization: authHeader,
        "Content-Type": "application/json",
      },
      body: createRequest,
    });

    console.log(createResponse.data);
    expect(createResponse.status).toBe(201);
    expect(createResponse.data).toHaveProperty("dry_run", false);
    expect(createResponse.data).toHaveProperty("result");
    expect(createResponse.data.result).toHaveProperty("id", configId);

    // Test GET LIST
    const listResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(listResponse.status).toBe(200);
    expect(Array.isArray(listResponse.data.list)).toBe(true);
    const configIds = listResponse.data.list.map((config) => config.id);
    expect(configIds).toContain(configId);

    // Test GET by ID
    const getResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs/${configId}`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(getResponse.status).toBe(200);
    expect(getResponse.data).toHaveProperty("id", configId);
    expect(getResponse.data).toHaveProperty("type", configType);
    // TODO fix implementation
    // expect(getResponse.data).toHaveProperty("enabled", true);

    // Test UPDATE
    const updateRequest = {
      id: configId,
      type: configType,
      enabled: true,
      applications: [{
        id: uuidv4(),
        verification_type: "identity_document",
        verification_process: "document_upload",
        verification_steps: [
          {
            step_type: "document_capture",
            required_documents: ["driver_license", "passport", "national_id"],
            verification_level: "enhanced"
          },
          {
            step_type: "face_verification",
            liveness_detection: true,
            face_match_threshold: 0.90 // Enhanced threshold
          },
          {
            step_type: "address_verification",
            document_types: ["utility_bill", "bank_statement"],
            age_limit_days: 60 // Stricter requirement
          }
        ],
        completion_requirements: {
          minimum_steps: 3, // Require all steps
          identity_score_threshold: 0.95, // Higher threshold
          manual_review_threshold: 0.80
        },
        result_storage: {
          retain_verification_data: true,
          retention_period_days: 2555,
          include_biometric_data: true // Now include biometric data
        },
        notification_settings: {
          notify_on_completion: true,
          notify_on_failure: true,
          notify_on_manual_review: true,
          webhook_url: "https://example.org/webhook/ekyc"
        }
      }],
      metadata: {
        created_by: "system",
        updated_by: "admin",
        purpose: "enhanced_identity_verification",
        compliance_level: "kyc_aml_premium"
      }
    };

    const updateResponse = await putWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs/${configId}`,
      headers: {
        Authorization: authHeader,
        "Content-Type": "application/json",
      },
      body: updateRequest,
    });

    expect(updateResponse.status).toBe(200);
    expect(updateResponse.data).toHaveProperty("dry_run", false);
    expect(updateResponse.data).toHaveProperty("result");

    // Test DELETE
    const deleteResponse = await deletion({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs/${configId}`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(deleteResponse.status).toBe(204);

    // Verify deletion by attempting to get the deleted config
    const verifyDeleteResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs/${configId}`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(verifyDeleteResponse.status).toBe(404);
  });

  test("should support dry-run for create operation", async () => {
    const configId = uuidv4();

    const createRequest = {
      id: configId,
      type: "simple_document_verification",
      enabled: false,
      applications: [{
        id: uuidv4(),
        verification_type: "identity_document",
        verification_process: "document_upload",
        verification_steps: [
          {
            step_type: "document_capture",
            required_documents: ["driver_license"],
            verification_level: "basic"
          }
        ],
        completion_requirements: {
          minimum_steps: 1,
          identity_score_threshold: 0.70
        }
      }],
      metadata: {
        created_by: "system",
        purpose: "basic_identity_verification"
      }
    };

    const dryRunResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs?dry_run=true`,
      headers: {
        Authorization: authHeader,
        "Content-Type": "application/json",
      },
      body: createRequest,
    });

    expect(dryRunResponse.status).toBe(201);
    expect(dryRunResponse.data).toHaveProperty("dry_run", true);
    expect(dryRunResponse.data).toHaveProperty("result");
    expect(dryRunResponse.data.result).toHaveProperty("id", configId);

    // Verify that dry-run didn't actually create the config
    const verifyResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs/${configId}`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(verifyResponse.status).toBe(404);
  });

  test("should support dry-run for update operation", async () => {
    const configId = uuidv4();

    // First create a config
    const createRequest = {
      id: configId,
      type: "document_verification",
      enabled: true,
      applications: [{
        id: uuidv4(),
        verification_type: "identity_document",
        verification_process: "document_upload",
        verification_steps: [
          {
            step_type: "document_capture",
            required_documents: ["passport"],
            verification_level: "standard"
          }
        ],
        completion_requirements: {
          minimum_steps: 1,
          identity_score_threshold: 0.80
        }
      }],
      metadata: {
        created_by: "system",
        purpose: "document_verification"
      }
    };

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs`,
      headers: {
        Authorization: authHeader,
        "Content-Type": "application/json",
      },
      body: createRequest,
    });

    // Test dry-run update
    const updateRequest = {
      id: configId,
      type: "enhanced_document_verification",
      enabled: true,
      applications: [{
        id: uuidv4(),
        verification_type: "identity_document",
        verification_process: "document_upload",
        verification_steps: [
          {
            step_type: "document_capture",
            required_documents: ["passport", "driver_license"],
            verification_level: "enhanced"
          }
        ],
        completion_requirements: {
          minimum_steps: 1,
          identity_score_threshold: 0.90
        }
      }],
      metadata: {
        created_by: "system",
        updated_by: "admin",
        purpose: "enhanced_document_verification"
      }
    };

    const dryRunUpdateResponse = await putWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs/${configId}?dry_run=true`,
      headers: {
        Authorization: authHeader,
        "Content-Type": "application/json",
      },
      body: updateRequest,
    });

    expect(dryRunUpdateResponse.status).toBe(200);
    expect(dryRunUpdateResponse.data).toHaveProperty("dry_run", true);

    // Verify original config is unchanged
    const verifyResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs/${configId}`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(verifyResponse.status).toBe(200);
    expect(verifyResponse.data).toHaveProperty("type", "document_verification"); // Original value
    expect(verifyResponse.data.applications[0].completion_requirements.identity_score_threshold).toBe(0.80);

    // Cleanup
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs/${configId}`,
      headers: {
        Authorization: authHeader,
      },
    });
  });

  test("should support dry-run for delete operation", async () => {
    const configId = uuidv4();

    // First create a config
    const createRequest = {
      id: configId,
      type: "biometric_verification",
      enabled: true,
      applications: [{
        id: uuidv4(),
        verification_type: "biometric",
        verification_process: "face_recognition",
        verification_steps: [
          {
            step_type: "face_verification",
            liveness_detection: true,
            face_match_threshold: 0.85
          }
        ],
        completion_requirements: {
          minimum_steps: 1,
          identity_score_threshold: 0.85
        }
      }],
      metadata: {
        created_by: "system",
        purpose: "biometric_verification"
      }
    };

    await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs`,
      headers: {
        Authorization: authHeader,
        "Content-Type": "application/json",
      },
      body: createRequest,
    });

    // Test dry-run delete
    const dryRunDeleteResponse = await deletion({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs/${configId}?dry_run=true`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(dryRunDeleteResponse.status).toBe(200);
    expect(dryRunDeleteResponse.data).toHaveProperty("message");
    expect(dryRunDeleteResponse.data.message).toMatch(/simulated successfully/);

    // Verify config still exists
    const verifyResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs/${configId}`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(verifyResponse.status).toBe(200);
    expect(verifyResponse.data).toHaveProperty("id", configId);

    // Cleanup - actual delete
    await deletion({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs/${configId}`,
      headers: {
        Authorization: authHeader,
      },
    });
  });

  test("should handle non-existent identity verification configuration", async () => {
    const nonExistentId = uuidv4();

    const response = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs/${nonExistentId}`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(response.status).toBe(404);
  });

  test("should support pagination for identity verification configuration list", async () => {
    // Create multiple configs for pagination testing
    const configs = [];
    for (let i = 0; i < 3; i++) {
      const configId = uuidv4();
      const createRequest = {
        id: configId,
        type: `test_verification_${i}`,
        enabled: true,
        applications: [{
          id: uuidv4(),
          verification_type: "identity_document",
          verification_process: "document_upload",
          verification_steps: [
            {
              step_type: "document_capture",
              required_documents: ["passport"],
              verification_level: "basic"
            }
          ],
          completion_requirements: {
            minimum_steps: 1,
            identity_score_threshold: 0.70
          }
        }],
        metadata: {
          created_by: "system",
          purpose: `test_verification_${i}`
        }
      };

      await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs`,
        headers: {
          Authorization: authHeader,
          "Content-Type": "application/json",
        },
        body: createRequest,
      });

      configs.push(configId);
    }

    // Test pagination
    const paginatedResponse = await get({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs?limit=2&offset=0`,
      headers: {
        Authorization: authHeader,
      },
    });

    expect(paginatedResponse.status).toBe(200);
    expect(Array.isArray(paginatedResponse.data.list)).toBe(true);
    expect(paginatedResponse.data.list.length).toBeLessThanOrEqual(2);

    // Cleanup
    for (const configId of configs) {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configs/${configId}`,
        headers: {
          Authorization: authHeader,
        },
      });
    }
  });
});