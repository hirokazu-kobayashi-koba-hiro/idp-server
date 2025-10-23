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
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.*;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;

/**
 * Result of a user management service operation.
 *
 * <p>Encapsulates the outcome of user management operations including success/error status,
 * response data, exception, and context for audit logging. This hybrid pattern allows:
 *
 * <ul>
 *   <li>Service/Handler layers to catch exceptions and wrap them in Result
 *   <li>Protocol layer to re-throw exceptions for transaction rollback
 *   <li>Audit logging to track all operation attempts with outcomes
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 *
 * <pre>{@code
 * // Service throws exception
 * validator.validateWithException();  // throws InvalidRequestException
 *
 * // Handler catches and wraps in Result
 * try {
 *   return service.execute(...);
 * } catch (ManagementApiException e) {
 *   return UserManagementResult.error(e);
 * }
 *
 * // Protocol checks and re-throws for transaction rollback
 * if (result.hasException()) {
 *   auditBuilder.failure(result.getException().errorCode());
 *   throw result.getException();  // Triggers rollback in TenantAwareEntryServiceProxy
 * }
 * }</pre>
 */
public class UserManagementResult {

  private final UserManagementResponse response;
  private final AuditableContext context;
  private final ManagementApiException exception;

  private UserManagementResult(
      UserManagementResponse response, AuditableContext context, ManagementApiException exception) {
    this.response = response;
    this.context = context;
    this.exception = exception;
  }

  /**
   * Creates a successful result.
   *
   * @param context the operation context for audit logging
   * @param response the UserManagementResponse
   * @return UserManagementResult with success response
   */
  public static UserManagementResult success(
      AuditableContext context, UserManagementResponse response) {
    return new UserManagementResult(response, context, null);
  }

  /**
   * Creates an error result from exception with partial context.
   *
   * <p>Used when context was partially built before error occurred (e.g., Tenant retrieval failed).
   *
   * @param context the partial context with available information for audit logging
   * @param exception the exception that caused the error
   * @return UserManagementResult with exception and partial context
   */
  public static UserManagementResult error(
      AuditableContext context, ManagementApiException exception) {
    return new UserManagementResult(null, context, exception);
  }

  /**
   * Checks if this result contains an exception.
   *
   * @return true if exception exists, false otherwise
   */
  public boolean hasException() {
    return exception != null;
  }

  /**
   * Returns the exception if present.
   *
   * @return ManagementApiException or null if success
   */
  public ManagementApiException getException() {
    return exception;
  }

  /**
   * Converts to UserManagementResponse.
   *
   * <p>If exception exists, automatically generates error response from exception details.
   *
   * @param dryRun whether this is a dry-run operation
   * @return UserManagementResponse with status and data
   */
  public UserManagementResponse toResponse(boolean dryRun) {
    if (hasException()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("dry_run", dryRun);
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());

      UserManagementStatus status = mapExceptionToStatus(exception);
      return new UserManagementResponse(status, errorResponse);
    }
    return response;
  }

  /**
   * Maps exception type to UserManagementStatus.
   *
   * @param exception the exception to map
   * @return corresponding UserManagementStatus
   */
  private UserManagementStatus mapExceptionToStatus(ManagementApiException exception) {
    if (exception instanceof InvalidRequestException) {
      return UserManagementStatus.INVALID_REQUEST;
    } else if (exception instanceof PermissionDeniedException
        || exception instanceof OrganizationAccessDeniedException) {
      return UserManagementStatus.FORBIDDEN;
    } else if (exception instanceof ResourceNotFoundException) {
      return UserManagementStatus.NOT_FOUND;
    }
    return UserManagementStatus.SERVER_ERROR;
  }

  /**
   * Returns the operation context for audit logging.
   *
   * @return operation context (e.g., UserRegistrationContext) or null if error result
   */
  public AuditableContext context() {
    return context;
  }
}
