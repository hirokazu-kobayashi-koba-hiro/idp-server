package org.idp.server.control_plane.starter.validator;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.control_plane.starter.io.IdpServerStarterResponse;
import org.idp.server.control_plane.starter.io.IdpServerStarterStatus;

public class IdpServerInitializeRequestValidationResult {

  boolean isValid;
  JsonSchemaValidationResult organizationResult;
  JsonSchemaValidationResult tenantResult;
  JsonSchemaValidationResult authorizationServerResult;
  JsonSchemaValidationResult adminUserResult;

  public static IdpServerInitializeRequestValidationResult success(
      JsonSchemaValidationResult organizationResult,
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult,
      JsonSchemaValidationResult adminUserResult) {
    return new IdpServerInitializeRequestValidationResult(
        true, organizationResult, tenantResult, authorizationServerResult, adminUserResult);
  }

  private IdpServerInitializeRequestValidationResult(
      boolean isValid,
      JsonSchemaValidationResult organizationResult,
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult,
      JsonSchemaValidationResult adminUserResult) {
    this.isValid = isValid;
    this.organizationResult = organizationResult;
    this.tenantResult = tenantResult;
    this.authorizationServerResult = authorizationServerResult;
    this.adminUserResult = adminUserResult;
  }

  public static IdpServerInitializeRequestValidationResult error(
      JsonSchemaValidationResult organizationResult,
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult,
      JsonSchemaValidationResult adminUserResult) {
    return new IdpServerInitializeRequestValidationResult(
        false, organizationResult, tenantResult, authorizationServerResult, adminUserResult);
  }

  public boolean isValid() {
    return isValid;
  }

  public IdpServerStarterResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("error", "invalid_request");
    response.put("error_description", "Invalid request");
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
    response.put("details", details);
    return new IdpServerStarterResponse(IdpServerStarterStatus.INVALID_REQUEST, response);
  }
}
