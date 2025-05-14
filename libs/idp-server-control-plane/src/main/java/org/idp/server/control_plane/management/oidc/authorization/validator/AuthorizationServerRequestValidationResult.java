package org.idp.server.control_plane.management.oidc.authorization.validator;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementResponse;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementStatus;

public class AuthorizationServerRequestValidationResult {

  boolean isValid;
  JsonSchemaValidationResult clientResult;
  boolean dryRun;

  public static AuthorizationServerRequestValidationResult success(
      JsonSchemaValidationResult clientResult, boolean dryRun) {
    return new AuthorizationServerRequestValidationResult(true, clientResult, dryRun);
  }

  public static AuthorizationServerRequestValidationResult error(
      JsonSchemaValidationResult clientResul, boolean dryRun) {
    return new AuthorizationServerRequestValidationResult(false, clientResul, dryRun);
  }

  private AuthorizationServerRequestValidationResult(
      boolean isValid, JsonSchemaValidationResult clientResult, boolean dryRun) {
    this.isValid = isValid;
    this.clientResult = clientResult;
    this.dryRun = dryRun;
  }

  public boolean isValid() {
    return isValid;
  }

  public AuthorizationServerManagementResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put("error_description", "Invalid request");
    Map<String, Object> details = new HashMap<>();
    if (!clientResult.isValid()) {
      details.put("client", clientResult.errors());
    }
    response.put("details", details);
    return new AuthorizationServerManagementResponse(
        AuthorizationServerManagementStatus.INVALID_REQUEST, response);
  }
}
