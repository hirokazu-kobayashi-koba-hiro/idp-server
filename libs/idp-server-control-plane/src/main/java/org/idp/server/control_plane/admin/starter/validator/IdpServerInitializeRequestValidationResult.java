package org.idp.server.control_plane.admin.starter.validator;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.control_plane.admin.starter.io.IdpServerStarterResponse;
import org.idp.server.control_plane.admin.starter.io.IdpServerStarterStatus;

public class IdpServerInitializeRequestValidationResult {

  boolean isValid;
  JsonSchemaValidationResult organizationResult;
  JsonSchemaValidationResult tenantResult;
  JsonSchemaValidationResult authorizationServerResult;
  JsonSchemaValidationResult adminUserResult;
  JsonSchemaValidationResult clientResult;
  boolean dryRun;

  public static IdpServerInitializeRequestValidationResult success(
      JsonSchemaValidationResult organizationResult,
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult,
      JsonSchemaValidationResult adminUserResult,
      JsonSchemaValidationResult clientResult,
      boolean dryRun) {
    return new IdpServerInitializeRequestValidationResult(
        true,
        organizationResult,
        tenantResult,
        authorizationServerResult,
        adminUserResult,
        clientResult,
        dryRun);
  }

  private IdpServerInitializeRequestValidationResult(
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

  public static IdpServerInitializeRequestValidationResult error(
      JsonSchemaValidationResult organizationResult,
      JsonSchemaValidationResult tenantResult,
      JsonSchemaValidationResult authorizationServerResult,
      JsonSchemaValidationResult adminUserResult,
      JsonSchemaValidationResult clientResult,
      boolean dryRun) {
    return new IdpServerInitializeRequestValidationResult(
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

  public IdpServerStarterResponse errorResponse() {
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
    return new IdpServerStarterResponse(IdpServerStarterStatus.INVALID_REQUEST, response);
  }
}
