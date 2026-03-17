/**
 * Simple schema validation utilities for OpenAPI compliance testing
 */

/**
 * UUID format validation
 */
export const isUUID = (value) => {
  if (typeof value !== "string") return false;
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
};

/**
 * ISO 8601 datetime format validation
 */
export const isISODateTime = (value) => {
  if (typeof value !== "string") return false;
  return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/.test(value);
};

/**
 * Security Event schema validation
 */
export const validateSecurityEvent = (event) => {
  const errors = [];

  // Required fields
  if (!event.id || !isUUID(event.id)) {
    errors.push("id must be a valid UUID");
  }

  if (!event.type || typeof event.type !== "string") {
    errors.push("type must be a string");
  }

  if (!event.description || typeof event.description !== "string") {
    errors.push("description must be a string");
  }

  if (!event.created_at || !isISODateTime(event.created_at)) {
    errors.push("created_at must be a valid ISO datetime");
  }

  // Optional nested objects
  if (event.tenant && !event.tenant.id) {
    errors.push("tenant.id is required when tenant is present");
  }

  if (event.client && !event.client.id) {
    errors.push("client.id is required when client is present");
  }

  return {
    valid: errors.length === 0,
    errors
  };
};

/**
 * Audit Log schema validation
 */
export const validateAuditLog = (log) => {
  const errors = [];

  // Required fields
  if (!log.id || !isUUID(log.id)) {
    errors.push("id must be a valid UUID");
  }

  if (!log.type || typeof log.type !== "string") {
    errors.push("type must be a string");
  }

  if (!log.description || typeof log.description !== "string") {
    errors.push("description must be a string");
  }

  if (!log.tenant_id || !isUUID(log.tenant_id)) {
    errors.push("tenant_id must be a valid UUID");
  }

  if (!log.created_at || !isISODateTime(log.created_at)) {
    errors.push("created_at must be a valid ISO datetime");
  }

  // Optional UUID fields
  if (log.user_id && !isUUID(log.user_id)) {
    errors.push("user_id must be a valid UUID when present");
  }

  // Optional string fields
  if (log.client_id && typeof log.client_id !== "string") {
    errors.push("client_id must be a string when present");
  }

  if (log.external_user_id && typeof log.external_user_id !== "string") {
    errors.push("external_user_id must be a string when present");
  }

  if (log.target_resource && typeof log.target_resource !== "string") {
    errors.push("target_resource must be a string when present");
  }

  if (log.target_resource_action && typeof log.target_resource_action !== "string") {
    errors.push("target_resource_action must be a string when present");
  }

  if (log.ip_address && typeof log.ip_address !== "string") {
    errors.push("ip_address must be a string when present");
  }

  if (log.user_agent && typeof log.user_agent !== "string") {
    errors.push("user_agent must be a string when present");
  }

  // Optional object fields
  if (log.user_payload && typeof log.user_payload !== "object") {
    errors.push("user_payload must be an object when present");
  }

  if (log.before && typeof log.before !== "object") {
    errors.push("before must be an object when present");
  }

  if (log.after && typeof log.after !== "object") {
    errors.push("after must be an object when present");
  }

  if (log.attributes && typeof log.attributes !== "object") {
    errors.push("attributes must be an object when present");
  }

  // Optional boolean field
  if (log.dry_run !== undefined && typeof log.dry_run !== "boolean") {
    errors.push("dry_run must be a boolean when present");
  }

  return {
    valid: errors.length === 0,
    errors
  };
};

/**
 * Authentication Config schema validation
 */
export const validateAuthenticationConfig = (config) => {
  const errors = [];

  // Required fields
  if (!config.id || !isUUID(config.id)) {
    errors.push("id must be a valid UUID");
  }

  if (!config.type || typeof config.type !== "string") {
    errors.push("type must be a string");
  }

  if (config.tenant_id && !isUUID(config.tenant_id)) {
    errors.push("tenant_id must be a valid UUID when present");
  }

  if (config.created_at && !isISODateTime(config.created_at)) {
    errors.push("created_at must be a valid ISO datetime when present");
  }

  if (config.updated_at && !isISODateTime(config.updated_at)) {
    errors.push("updated_at must be a valid ISO datetime when present");
  }

  // Optional object fields
  if (config.config && typeof config.config !== "object") {
    errors.push("config must be an object when present");
  }

  return {
    valid: errors.length === 0,
    errors
  };
};

/**
 * Identity Verification Application schema validation
 * Based on OpenAPI spec: swagger-cp-identity-verification-application-ja.yaml
 */
export const validateIdentityVerificationApplication = (application) => {
  const errors = [];

  const validStatuses = [
    "requested",
    "applying",
    "applied",
    "examination_processing",
    "approved",
    "rejected",
    "expired",
    "cancelled",
  ];

  // Required fields
  if (!application.id || !isUUID(application.id)) {
    errors.push("id must be a valid UUID");
  }

  if (!application.type || typeof application.type !== "string") {
    errors.push("type must be a string");
  }

  if (!application.tenant_id || typeof application.tenant_id !== "string") {
    errors.push("tenant_id must be a string");
  }

  if (!application.client_id || typeof application.client_id !== "string") {
    errors.push("client_id must be a string");
  }

  if (!application.user_id || typeof application.user_id !== "string") {
    errors.push("user_id must be a string");
  }

  if (!application.status || !validStatuses.includes(application.status)) {
    errors.push(
      `status must be one of: ${validStatuses.join(", ")}, got: ${application.status}`
    );
  }

  if (
    !application.requested_at ||
    !isISODateTime(application.requested_at)
  ) {
    errors.push("requested_at must be a valid ISO datetime");
  }

  // Optional object fields
  if (
    application.application_details &&
    typeof application.application_details !== "object"
  ) {
    errors.push("application_details must be an object when present");
  }

  if (application.processes && typeof application.processes !== "object") {
    errors.push("processes must be an object when present");
  }

  // Validate process results structure
  if (application.processes && typeof application.processes === "object") {
    for (const [processName, result] of Object.entries(
      application.processes
    )) {
      if (typeof result !== "object" || result === null) {
        errors.push(`processes.${processName} must be an object`);
        continue;
      }
      if (typeof result.call_count !== "number") {
        errors.push(`processes.${processName}.call_count must be a number`);
      }
      if (typeof result.success_count !== "number") {
        errors.push(
          `processes.${processName}.success_count must be a number`
        );
      }
      if (typeof result.failure_count !== "number") {
        errors.push(
          `processes.${processName}.failure_count must be a number`
        );
      }
    }
  }

  if (application.attributes && typeof application.attributes !== "object") {
    errors.push("attributes must be an object when present");
  }

  return {
    valid: errors.length === 0,
    errors,
  };
};

/**
 * Identity Verification Result schema validation
 * Based on OpenAPI spec: swagger-cp-identity-verification-result-ja.yaml
 */
export const validateIdentityVerificationResult = (result) => {
  const errors = [];

  const validSources = ["application", "direct", "manual", "import"];

  // Required fields
  if (!result.id || !isUUID(result.id)) {
    errors.push("id must be a valid UUID");
  }

  if (!result.type || typeof result.type !== "string") {
    errors.push("type must be a string");
  }

  if (!result.tenant_id || typeof result.tenant_id !== "string") {
    errors.push("tenant_id must be a string");
  }

  if (!result.user_id || typeof result.user_id !== "string") {
    errors.push("user_id must be a string");
  }

  if (!result.source || !validSources.includes(result.source)) {
    errors.push(
      `source must be one of: ${validSources.join(", ")}, got: ${result.source}`
    );
  }

  if (!result.verified_at || !isISODateTime(result.verified_at)) {
    errors.push("verified_at must be a valid ISO datetime");
  }

  if (
    result.verified_claims === undefined ||
    typeof result.verified_claims !== "object"
  ) {
    errors.push("verified_claims must be an object");
  }

  // Optional fields
  if (
    result.application_id !== undefined &&
    typeof result.application_id !== "string"
  ) {
    errors.push("application_id must be a string when present");
  }

  if (
    result.verified_until !== undefined &&
    result.verified_until !== null &&
    !isISODateTime(result.verified_until)
  ) {
    errors.push("verified_until must be a valid ISO datetime when present");
  }

  if (
    result.source_details !== undefined &&
    typeof result.source_details !== "object"
  ) {
    errors.push("source_details must be an object when present");
  }

  if (
    result.attributes !== undefined &&
    typeof result.attributes !== "object"
  ) {
    errors.push("attributes must be an object when present");
  }

  return {
    valid: errors.length === 0,
    errors,
  };
};

/**
 * List response schema validation
 */
export const validateListResponse = (response, itemValidator) => {
  const errors = [];

  // Required fields
  if (!Array.isArray(response.list)) {
    errors.push("list must be an array");
  }

  if (typeof response.total_count !== "number") {
    errors.push("total_count must be a number");
  }

  if (typeof response.limit !== "number") {
    errors.push("limit must be a number");
  }

  if (typeof response.offset !== "number") {
    errors.push("offset must be a number");
  }

  // Validate each item in the list
  if (Array.isArray(response.list) && itemValidator) {
    response.list.forEach((item, index) => {
      const itemValidation = itemValidator(item);
      if (!itemValidation.valid) {
        errors.push(`Item ${index}: ${itemValidation.errors.join(", ")}`);
      }
    });
  }

  return {
    valid: errors.length === 0,
    errors
  };
};