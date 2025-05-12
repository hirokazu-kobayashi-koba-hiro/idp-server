package org.idp.server.control_plane.management.user.validator;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.control_plane.management.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.user.io.UserManagementStatus;

public class UserRegistrationRequestValidationResult {

  boolean isValid;
  JsonSchemaValidationResult userResult;
  boolean dryRun;

  public static UserRegistrationRequestValidationResult success(
      JsonSchemaValidationResult adminUserResult, boolean dryRun) {
    return new UserRegistrationRequestValidationResult(true, adminUserResult, dryRun);
  }

  private UserRegistrationRequestValidationResult(
      boolean isValid, JsonSchemaValidationResult userResult, boolean dryRun) {
    this.isValid = isValid;
    this.userResult = userResult;
    this.dryRun = dryRun;
  }

  public static UserRegistrationRequestValidationResult error(
      JsonSchemaValidationResult adminUserResult, boolean dryRun) {
    return new UserRegistrationRequestValidationResult(false, adminUserResult, dryRun);
  }

  public boolean isValid() {
    return isValid;
  }

  public UserManagementResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put("error_description", "user registration validation is failed");
    Map<String, Object> details = new HashMap<>();
    if (!userResult.isValid()) {
      {
        details.put("user", userResult.errors());
      }
    }
    response.put("details", details);
    return new UserManagementResponse(UserManagementStatus.INVALID_REQUEST, response);
  }
}
