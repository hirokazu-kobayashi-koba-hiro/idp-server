/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.control_plane.management.identity.user.validator;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;

public class UserRequestValidationResult {

  boolean isValid;
  JsonSchemaValidationResult userResult;
  boolean dryRun;

  public static UserRequestValidationResult success(
      JsonSchemaValidationResult adminUserResult, boolean dryRun) {
    return new UserRequestValidationResult(true, adminUserResult, dryRun);
  }

  private UserRequestValidationResult(
      boolean isValid, JsonSchemaValidationResult userResult, boolean dryRun) {
    this.isValid = isValid;
    this.userResult = userResult;
    this.dryRun = dryRun;
  }

  public static UserRequestValidationResult error(
      JsonSchemaValidationResult adminUserResult, boolean dryRun) {
    return new UserRequestValidationResult(false, adminUserResult, dryRun);
  }

  public boolean isValid() {
    return isValid;
  }

  public UserManagementResponse errorResponse() {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "invalid_request");
    response.put("error_description", "user registration validation is failed");
    response.put("error_messages", userResult.errors());
    return new UserManagementResponse(UserManagementStatus.INVALID_REQUEST, response);
  }

  /**
   * Converts validation result to exception for Handler/Service pattern.
   *
   * <p>This method is used when Service layer needs to throw exceptions instead of returning error
   * responses. The exception will be caught by Handler layer and wrapped in UserManagementResult.
   *
   * @return InvalidRequestException with validation error details
   */
  public InvalidRequestException toException() {
    return new InvalidRequestException(
        "user registration validation is failed", userResult.errors());
  }

  /**
   * Throws InvalidRequestException if validation failed.
   *
   * @throws InvalidRequestException if invalid
   */
  public void throwIfInvalid() {
    if (!isValid) {
      throw toException();
    }
  }
}
