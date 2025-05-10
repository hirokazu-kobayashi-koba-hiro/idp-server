package org.idp.server.control_plane.validator;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;

public class IdpServerInitializeRequestValidationResult {

  boolean isValid;
  JsonSchemaValidationResult organizationResult;
  JsonSchemaValidationResult tenantResult;
  JsonSchemaValidationResult authorizationServerResult;

  public static IdpServerInitializeRequestValidationResult success(
      JsonSchemaValidationResult organizationResult,
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult) {
    return new IdpServerInitializeRequestValidationResult(
        true, organizationResult, tenantResult, authorizationServerResult);
  }

  private IdpServerInitializeRequestValidationResult(
      boolean isValid,
      JsonSchemaValidationResult organizationResult,
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult) {
    this.isValid = isValid;
    this.organizationResult = organizationResult;
    this.tenantResult = tenantResult;
    this.authorizationServerResult = authorizationServerResult;
  }

  public static IdpServerInitializeRequestValidationResult error(
      JsonSchemaValidationResult organizationResult,
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult) {
    return new IdpServerInitializeRequestValidationResult(
        false, organizationResult, tenantResult, authorizationServerResult);
  }

  public boolean isValid() {
    return isValid;
  }

  public Map<String, Object> errorResponse() {
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
    response.put("details", details);
    return response;
  }
}
