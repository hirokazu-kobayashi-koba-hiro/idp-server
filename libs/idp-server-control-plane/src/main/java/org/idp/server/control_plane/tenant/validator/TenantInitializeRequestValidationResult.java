package org.idp.server.control_plane.tenant.validator;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.control_plane.tenant.io.TenantInitializationResponse;
import org.idp.server.control_plane.tenant.io.TenantInitializationStatus;

public class TenantInitializeRequestValidationResult {

  boolean isValid;
  JsonSchemaValidationResult organizationResult;
  JsonSchemaValidationResult tenantResult;
  JsonSchemaValidationResult authorizationServerResult;
  JsonSchemaValidationResult adminUserResult;
  JsonSchemaValidationResult clientResult;
  boolean dryRun;

  public static TenantInitializeRequestValidationResult success(
      JsonSchemaValidationResult organizationResult,
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult,
      JsonSchemaValidationResult adminUserResult,
      JsonSchemaValidationResult clientResult,
      boolean dryRun) {
    return new TenantInitializeRequestValidationResult(
        true,
        organizationResult,
        tenantResult,
        authorizationServerResult,
        adminUserResult,
        clientResult,
        dryRun);
  }

  private TenantInitializeRequestValidationResult(
      boolean isValid,
      JsonSchemaValidationResult organizationResult,
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult,
      JsonSchemaValidationResult adminUserResult,
      JsonSchemaValidationResult clientResult,
      boolean dryRun) {
    this.isValid = isValid;
    this.organizationResult = organizationResult;
    this.tenantResult = tenantResult;
    this.authorizationServerResult = authorizationServerResult;
    this.adminUserResult = adminUserResult;
    this.clientResult = clientResult;
    this.dryRun = dryRun;
  }

  public static TenantInitializeRequestValidationResult error(
      JsonSchemaValidationResult organizationResult,
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult,
      JsonSchemaValidationResult adminUserResult,
      JsonSchemaValidationResult clientResult,
      boolean dryRun) {
    return new TenantInitializeRequestValidationResult(
        false,
        organizationResult,
        tenantResult,
        authorizationServerResult,
        adminUserResult,
        clientResult,
        dryRun);
  }

  public boolean isValid() {
    return isValid;
  }

  public TenantInitializationResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put("error_description", "idp-server starter validation is failed");
    Map<String, Object> details = new HashMap<>();
    if (!organizationResult.isValid()) {
      details.put("organization", organizationResult.errors());
    }
    if (!tenantResult.isValid()) {
      details.put("tenant", tenantResult.errors());
    }
    if (!authorizationServerResult.isValid()) {
      details.put("authorization_server", authorizationServerResult.errors());
    }
    if (!adminUserResult.isValid()) {
      {
        details.put("user", adminUserResult.errors());
      }
    }
    if (!clientResult.isValid()) {
      details.put("client", clientResult.errors());
    }
    response.put("details", details);
    return new TenantInitializationResponse(TenantInitializationStatus.INVALID_REQUEST, response);
  }
}
