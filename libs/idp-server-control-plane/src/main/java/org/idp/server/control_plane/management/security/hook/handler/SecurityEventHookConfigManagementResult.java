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

package org.idp.server.control_plane.management.security.hook.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.OrganizationAccessDeniedException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementResponse;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementStatus;

/**
 * Result wrapper for security event hook configuration management operations.
 *
 * <p>Encapsulates operation outcomes with Result-Exception Hybrid pattern:
 *
 * <ul>
 *   <li>Services throw ManagementApiException for domain errors
 *   <li>Handler catches exceptions and wraps in Result
 *   <li>EntryService converts Result to HTTP responses
 * </ul>
 *
 * <p>This pattern enables:
 *
 * <ul>
 *   <li>Consistent exception handling across all operations
 *   <li>Audit logging with tenant context even on errors
 *   <li>Type-safe error-to-HTTP status mapping
 *   <li>Context preservation for create/update operations
 * </ul>
 */
public class SecurityEventHookConfigManagementResult {

  private final ManagementApiException exception;
  private final SecurityEventHookConfigManagementResponse response;
  private final AuditableContext context;

  private SecurityEventHookConfigManagementResult(
      ManagementApiException exception,
      SecurityEventHookConfigManagementResponse response,
      AuditableContext context) {
    this.exception = exception;
    this.response = response;
    this.context = context;
  }

  /**
   * Creates a successful result.
   *
   * @param context operation context (for audit logging)
   * @param response operation-specific response object
   * @return success result
   */
  public static SecurityEventHookConfigManagementResult success(
      AuditableContext context, SecurityEventHookConfigManagementResponse response) {
    return new SecurityEventHookConfigManagementResult(null, response, context);
  }

  /**
   * Creates an error result.
   *
   * @param context partial context (for audit logging even on errors)
   * @param exception domain exception that occurred
   * @return error result
   */
  public static SecurityEventHookConfigManagementResult error(
      AuditableContext context, ManagementApiException exception) {
    return new SecurityEventHookConfigManagementResult(exception, null, context);
  }

  /**
   * Checks if this result contains an exception.
   *
   * @return true if error result, false if success
   */
  public boolean hasException() {
    return exception != null;
  }

  /**
   * Gets the exception for error handling.
   *
   * @return exception (null for success results)
   */
  public ManagementApiException getException() {
    return exception;
  }

  /**
   * Gets the context for audit logging.
   *
   * @return context (SecurityEventHookConfigManagementContext)
   */
  public AuditableContext context() {
    return context;
  }

  /**
   * Converts this result to HTTP response.
   *
   * <p>Maps exceptions to appropriate HTTP status codes:
   *
   * <ul>
   *   <li>ResourceNotFoundException → 404 NOT_FOUND
   *   <li>PermissionDeniedException → 403 FORBIDDEN
   *   <li>Other ManagementApiException → 400 INVALID_REQUEST
   * </ul>
   *
   * @param dryRun whether this is a dry-run operation
   * @return HTTP response object
   */
  public SecurityEventHookConfigManagementResponse toResponse(boolean dryRun) {
    if (hasException()) {
      SecurityEventHookConfigManagementStatus status = mapExceptionToStatus(exception);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("dry_run", dryRun);
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());
      return new SecurityEventHookConfigManagementResponse(status, errorResponse);
    }
    return response;
  }

  /**
   * Maps exception types to HTTP status codes.
   *
   * @param exception the exception to map
   * @return corresponding HTTP status
   */
  private SecurityEventHookConfigManagementStatus mapExceptionToStatus(
      ManagementApiException exception) {
    if (exception instanceof ResourceNotFoundException) {
      return SecurityEventHookConfigManagementStatus.NOT_FOUND;
    }
    if (exception instanceof PermissionDeniedException
        || exception instanceof OrganizationAccessDeniedException) {
      return SecurityEventHookConfigManagementStatus.FORBIDDEN;
    }
    return SecurityEventHookConfigManagementStatus.INVALID_REQUEST;
  }
}
