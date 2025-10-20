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

package org.idp.server.control_plane.management.security.hook_result.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookManagementResponse;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookManagementStatus;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Result wrapper for security event hook management operations.
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
 * </ul>
 */
public class SecurityEventHookManagementResult {

  private final Tenant tenant;
  private final ManagementApiException exception;
  private final Object response;

  private SecurityEventHookManagementResult(
      Tenant tenant, ManagementApiException exception, Object response) {
    this.tenant = tenant;
    this.exception = exception;
    this.response = response;
  }

  /**
   * Creates a successful result.
   *
   * @param tenant tenant context (for audit logging)
   * @param response operation-specific response object
   * @return success result
   */
  public static SecurityEventHookManagementResult success(Tenant tenant, Object response) {
    return new SecurityEventHookManagementResult(tenant, null, response);
  }

  /**
   * Creates an error result.
   *
   * @param tenant tenant context (for audit logging)
   * @param exception domain exception that occurred
   * @return error result
   */
  public static SecurityEventHookManagementResult error(
      Tenant tenant, ManagementApiException exception) {
    return new SecurityEventHookManagementResult(tenant, exception, null);
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
   * Gets the tenant context.
   *
   * @return tenant (for audit logging)
   */
  public Tenant tenant() {
    return tenant;
  }

  /**
   * Converts this result to HTTP response.
   *
   * <p>Maps exceptions to appropriate HTTP status codes:
   *
   * <ul>
   *   <li>ResourceNotFoundException → 404 NOT_FOUND
   *   <li>PermissionDeniedException → 403 FORBIDDEN
   *   <li>Other ManagementApiException → 400 BAD_REQUEST
   * </ul>
   *
   * @return HTTP response object
   */
  public SecurityEventHookManagementResponse toResponse() {
    if (hasException()) {
      SecurityEventHookManagementStatus status = mapExceptionToStatus(exception);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());
      return new SecurityEventHookManagementResponse(status, errorResponse);
    }
    return (SecurityEventHookManagementResponse) response;
  }

  /**
   * Maps exception types to HTTP status codes.
   *
   * @param exception the exception to map
   * @return corresponding HTTP status
   */
  private SecurityEventHookManagementStatus mapExceptionToStatus(ManagementApiException exception) {
    if (exception instanceof ResourceNotFoundException) {
      return SecurityEventHookManagementStatus.NOT_FOUND;
    }
    if (exception instanceof PermissionDeniedException) {
      return SecurityEventHookManagementStatus.FORBIDDEN;
    }
    return SecurityEventHookManagementStatus.INVALID_REQUEST;
  }
}
