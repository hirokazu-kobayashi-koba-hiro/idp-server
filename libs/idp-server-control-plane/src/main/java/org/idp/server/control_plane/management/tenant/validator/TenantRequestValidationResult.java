package org.idp.server.control_plane.management.tenant.validator;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.control_plane.management.onboarding.io.OnboardingResponse;
import org.idp.server.control_plane.management.onboarding.io.OnboardingStatus;

public class TenantRequestValidationResult {

  boolean isValid;
  JsonSchemaValidationResult tenantResult;
  JsonSchemaValidationResult authorizationServerResult;
  boolean dryRun;

  public static TenantRequestValidationResult success(
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult,
      boolean dryRun) {
    return new TenantRequestValidationResult(true, tenantResult, authorizationServerResult, dryRun);
  }

  public static TenantRequestValidationResult error(
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult,
      boolean dryRun) {
    return new TenantRequestValidationResult(
        false, tenantResult, authorizationServerResult, dryRun);
  }

  private TenantRequestValidationResult(
      boolean isValid,
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult,
      boolean dryRun) {
    this.isValid = isValid;
    this.tenantResult = tenantResult;
    this.authorizationServerResult = authorizationServerResult;
    this.dryRun = dryRun;
  }

  public boolean isValid() {
    return isValid;
  }

  public OnboardingResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put("error_description", "Invalid request");
    Map<String, Object> details = new HashMap<>();
    if (!tenantResult.isValid()) {
      details.put("tenant", tenantResult.errors());
    }
    if (!authorizationServerResult.isValid()) {
      details.put("authorization_server", authorizationServerResult.errors());
    }

    response.put("details", details);
    return new OnboardingResponse(OnboardingStatus.INVALID_REQUEST, response);
  }
}
