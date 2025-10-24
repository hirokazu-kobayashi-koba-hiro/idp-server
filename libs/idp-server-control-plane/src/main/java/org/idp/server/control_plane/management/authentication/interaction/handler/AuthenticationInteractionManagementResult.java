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

package org.idp.server.control_plane.management.authentication.interaction.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementResponse;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementStatus;
import org.idp.server.control_plane.management.exception.*;

/**
 * Result object for authentication interaction management operations.
 *
 * <p>Encapsulates the outcome of authentication interaction management Handler/Service pattern
 * operations. Follows the Result-Exception Hybrid pattern:
 *
 * <ul>
 *   <li>Service layer throws ManagementApiException on validation/verification failures
 *   <li>Handler layer catches exceptions and converts to Result
 *   <li>EntryService layer converts Result to HTTP response
 * </ul>
 *
 * @see AuthenticationInteractionManagementHandler
 * @see AuthenticationInteractionManagementService
 */
public class AuthenticationInteractionManagementResult {

  private final AuditableContext context;
  private final ManagementApiException exception;
  private final AuthenticationInteractionManagementResponse response;

  private AuthenticationInteractionManagementResult(
      AuditableContext context,
      ManagementApiException exception,
      AuthenticationInteractionManagementResponse response) {
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
  public static AuthenticationInteractionManagementResult success(
      AuditableContext context, AuthenticationInteractionManagementResponse response) {
    return new AuthenticationInteractionManagementResult(context, null, response);
  }

  /**
   * Creates an error result from an exception.
   *
   * @param context the operation context (may be partial)
   * @param exception the exception that occurred
   * @return error result
   */
  public static AuthenticationInteractionManagementResult error(
      AuditableContext context, ManagementApiException exception) {
    return new AuthenticationInteractionManagementResult(context, exception, null);
  }

  private static AuthenticationInteractionManagementStatus mapExceptionToStatus(
      ManagementApiException exception) {
    if (exception instanceof ResourceNotFoundException) {
      return AuthenticationInteractionManagementStatus.NOT_FOUND;
    }
    if (exception instanceof PermissionDeniedException
        || exception instanceof OrganizationAccessDeniedException) {
      return AuthenticationInteractionManagementStatus.FORBIDDEN;
    }
    return AuthenticationInteractionManagementStatus.INVALID_REQUEST;
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

  public AuthenticationInteractionManagementResponse toResponse() {
    if (hasException()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());
      AuthenticationInteractionManagementStatus status = mapExceptionToStatus(exception);
      return new AuthenticationInteractionManagementResponse(status, errorResponse);
    }
    return response;
  }
}
