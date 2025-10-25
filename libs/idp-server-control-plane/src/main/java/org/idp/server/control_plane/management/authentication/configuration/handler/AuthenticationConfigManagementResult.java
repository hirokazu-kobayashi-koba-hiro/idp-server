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

package org.idp.server.control_plane.management.authentication.configuration.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementStatus;
import org.idp.server.control_plane.management.exception.*;

/**
 * Result object for authentication policy configuration management operations.
 *
 * <p>Encapsulates the outcome of authentication policy configuration management Handler/Service
 * pattern operations. Follows the Result-Exception Hybrid pattern:
 *
 * <ul>
 *   <li>Service layer throws ManagementApiException on validation/verification failures
 *   <li>Handler layer catches exceptions and converts to Result
 *   <li>EntryService layer converts Result to HTTP response
 * </ul>
 *
 * @see AuthenticationConfigManagementHandler
 * @see AuthenticationConfigManagementService
 */
public class AuthenticationConfigManagementResult {

  private final AuditableContext context;
  private final ManagementApiException exception;
  private final AuthenticationConfigManagementResponse response;

  private AuthenticationConfigManagementResult(
      AuditableContext context,
      ManagementApiException exception,
      AuthenticationConfigManagementResponse response) {
    this.context = context;
    this.exception = exception;
    this.response = response;
  }

  /**
   * Creates a successful result with context.
   *
   * @param context the operation context
   * @param response the success response
   * @return successful result with context
   */
  public static AuthenticationConfigManagementResult success(
      AuditableContext context, AuthenticationConfigManagementResponse response) {
    return new AuthenticationConfigManagementResult(context, null, response);
  }

  /**
   * Creates an error result from an exception.
   *
   * @param context the operation context (may be partial)
   * @param exception the exception that occurred
   * @return error result
   */
  public static AuthenticationConfigManagementResult error(
      AuditableContext context, ManagementApiException exception) {
    return new AuthenticationConfigManagementResult(context, exception, null);
  }

  /**
   * Maps exception types to HTTP status codes.
   *
   * @param exception the exception to map
   * @return corresponding HTTP status
   */
  private static AuthenticationConfigManagementStatus mapExceptionToStatus(
      ManagementApiException exception) {
    if (exception instanceof ResourceNotFoundException) {
      return AuthenticationConfigManagementStatus.NOT_FOUND;
    }
    if (exception instanceof PermissionDeniedException
        || exception instanceof OrganizationAccessDeniedException) {
      return AuthenticationConfigManagementStatus.FORBIDDEN;
    }
    if (exception instanceof InvalidRequestException) {
      return AuthenticationConfigManagementStatus.INVALID_REQUEST;
    }
    return AuthenticationConfigManagementStatus.SERVER_ERROR;
  }

  public AuditableContext context() {
    return context;
  }

  public boolean hasException() {
    return exception != null;
  }

  public ManagementApiException getException() {
    return exception;
  }

  public AuthenticationConfigManagementResponse toResponse(boolean dryRun) {
    if (hasException()) {
      AuthenticationConfigManagementStatus status = mapExceptionToStatus(exception);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("dry_run", dryRun);
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());
      return new AuthenticationConfigManagementResponse(status, errorResponse);
    }
    return response;
  }
}
