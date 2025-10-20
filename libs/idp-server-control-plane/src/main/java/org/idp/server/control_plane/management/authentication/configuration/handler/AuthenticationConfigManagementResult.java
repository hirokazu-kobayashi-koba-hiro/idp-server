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
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementStatus;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Result wrapper for authentication configuration management operations.
 *
 * <p>This class encapsulates the outcome of authentication configuration management Service
 * execution, including success results and exceptions.
 *
 * <h2>Purpose</h2>
 *
 * <ul>
 *   <li>Encapsulate Service execution results
 *   <li>Preserve Tenant for audit logging even on error
 *   <li>Convert exceptions to appropriate HTTP responses
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 *
 * <pre>{@code
 * // In Service: return success
 * return AuthenticationConfigManagementResult.success(tenant, context, response);
 *
 * // In Handler: catch exception and wrap
 * try {
 *   return service.execute(...);
 * } catch (ManagementApiException e) {
 *   return AuthenticationConfigManagementResult.error(tenant, e);
 * }
 *
 * // In EntryService: convert to HTTP response
 * if (result.hasException()) {
 *   return result.toResponse(dryRun);
 * }
 * }</pre>
 */
public class AuthenticationConfigManagementResult {

  private final Tenant tenant;
  private final Object context;
  private final AuthenticationConfigManagementResponse response;
  private final ManagementApiException exception;

  private AuthenticationConfigManagementResult(
      Tenant tenant,
      Object context,
      AuthenticationConfigManagementResponse response,
      ManagementApiException exception) {
    this.tenant = tenant;
    this.context = context;
    this.response = response;
    this.exception = exception;
  }

  /**
   * Creates a success result.
   *
   * @param tenant the tenant context
   * @param context the operation context (for audit logging)
   * @param response the success response
   * @return success result
   */
  public static AuthenticationConfigManagementResult success(
      Tenant tenant, Object context, AuthenticationConfigManagementResponse response) {
    return new AuthenticationConfigManagementResult(tenant, context, response, null);
  }

  /**
   * Creates an error result.
   *
   * @param tenant the tenant context (may be null if tenant retrieval failed)
   * @param exception the exception that occurred
   * @return error result
   */
  public static AuthenticationConfigManagementResult error(
      Tenant tenant, ManagementApiException exception) {
    return new AuthenticationConfigManagementResult(tenant, null, null, exception);
  }

  /**
   * Checks if this result contains an exception.
   *
   * @return true if exception exists
   */
  public boolean hasException() {
    return exception != null;
  }

  /**
   * Gets the tenant context.
   *
   * @return tenant
   */
  public Tenant tenant() {
    return tenant;
  }

  /**
   * Gets the operation context for audit logging.
   *
   * @return context object
   */
  public Object context() {
    return context;
  }

  /**
   * Gets the exception if present.
   *
   * @return exception
   */
  public ManagementApiException getException() {
    return exception;
  }

  /**
   * Converts this result to HTTP response.
   *
   * <p>If exception exists, automatically generates error response from exception details.
   *
   * @param dryRun whether this is a dry-run operation
   * @return AuthenticationConfigManagementResponse with status and data
   */
  public AuthenticationConfigManagementResponse toResponse(boolean dryRun) {
    if (hasException()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("dry_run", dryRun);
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());

      AuthenticationConfigManagementStatus status = mapExceptionToStatus(exception);
      return new AuthenticationConfigManagementResponse(status, errorResponse);
    }
    return response;
  }

  /**
   * Maps exception type to AuthenticationConfigManagementStatus.
   *
   * @param exception the exception to map
   * @return corresponding AuthenticationConfigManagementStatus
   */
  private AuthenticationConfigManagementStatus mapExceptionToStatus(
      ManagementApiException exception) {
    if (exception instanceof InvalidRequestException) {
      return AuthenticationConfigManagementStatus.INVALID_REQUEST;
    } else if (exception instanceof PermissionDeniedException) {
      return AuthenticationConfigManagementStatus.FORBIDDEN;
    } else if (exception instanceof ResourceNotFoundException) {
      return AuthenticationConfigManagementStatus.NOT_FOUND;
    }
    return AuthenticationConfigManagementStatus.SERVER_ERROR;
  }
}
