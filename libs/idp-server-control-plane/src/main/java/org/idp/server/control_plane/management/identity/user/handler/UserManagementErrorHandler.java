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

package org.idp.server.control_plane.management.identity.user.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Error handler for user management operations.
 *
 * <p>Converts exceptions thrown by Handler/Service layers into UserManagementResponse. Follows the
 * Token API pattern where Protocol layer catches exceptions and formats error responses.
 *
 * <h2>Error Mapping</h2>
 *
 * <pre>{@code
 * InvalidRequestException → INVALID_REQUEST (400)
 * PermissionDeniedException → FORBIDDEN (403)
 * Other exceptions → SERVER_ERROR (500)
 * }</pre>
 */
public class UserManagementErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(UserManagementErrorHandler.class);

  /**
   * Handles exceptions and converts to UserManagementResponse.
   *
   * @param exception the exception to handle
   * @param dryRun if the operation was a dry-run
   * @return UserManagementResponse with appropriate status and error details
   */
  public UserManagementResponse handle(Exception exception, boolean dryRun) {
    log.trace(
        "User management error handling started: exception_type={}",
        exception.getClass().getSimpleName());

    if (exception instanceof InvalidRequestException invalidRequest) {
      log.warn(
          "User management validation failed: error={}, description={}",
          invalidRequest.errorCode(),
          invalidRequest.errorDescription());
      return new UserManagementResponse(
          UserManagementStatus.INVALID_REQUEST, createErrorResponse(invalidRequest, dryRun));
    }

    if (exception instanceof PermissionDeniedException permissionDenied) {
      log.warn(
          "Permission denied: required={}, actual={}",
          permissionDenied.requiredPermissions(),
          permissionDenied.actualPermissions());
      return new UserManagementResponse(
          UserManagementStatus.FORBIDDEN, createErrorResponse(permissionDenied, dryRun));
    }

    if (exception instanceof ManagementApiException managementException) {
      log.warn(
          "Management API error: error={}, description={}",
          managementException.errorCode(),
          managementException.errorDescription());
      return new UserManagementResponse(
          UserManagementStatus.SERVER_ERROR, createErrorResponse(managementException, dryRun));
    }

    log.error("User management server error: error={}", exception.getMessage(), exception);
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", "server_error");
    response.put("error_description", exception.getMessage());
    return new UserManagementResponse(UserManagementStatus.SERVER_ERROR, response);
  }

  private Map<String, Object> createErrorResponse(
      ManagementApiException exception, boolean dryRun) {
    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    response.put("error", exception.errorCode());
    response.put("error_description", exception.errorDescription());
    response.putAll(exception.errorDetails());
    return response;
  }
}
