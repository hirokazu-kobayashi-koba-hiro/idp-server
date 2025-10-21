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

import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementResponse;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementStatus;
import org.idp.server.control_plane.management.exception.*;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Result wrapper for authentication interaction management operations.
 *
 * <p>This class encapsulates the outcome of authentication interaction management Service
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
 * return AuthenticationInteractionManagementResult.success(tenant, context, response);
 *
 * // In Handler: catch exception and wrap
 * try {
 *   return service.execute(...);
 * } catch (ManagementApiException e) {
 *   return AuthenticationInteractionManagementResult.error(tenant, e);
 * }
 *
 * // In EntryService: convert to HTTP response
 * if (result.hasException()) {
 *   return result.toResponse();
 * }
 * }</pre>
 */
public class AuthenticationInteractionManagementResult {

  private final Tenant tenant;
  private final Object context;
  private final AuthenticationInteractionManagementResponse response;
  private final ManagementApiException exception;

  private AuthenticationInteractionManagementResult(
      Tenant tenant,
      Object context,
      AuthenticationInteractionManagementResponse response,
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
  public static AuthenticationInteractionManagementResult success(
      Tenant tenant, Object context, AuthenticationInteractionManagementResponse response) {
    return new AuthenticationInteractionManagementResult(tenant, context, response, null);
  }

  /**
   * Creates an error result.
   *
   * @param tenant the tenant context (may be null if tenant retrieval failed)
   * @param exception the exception that occurred
   * @return error result
   */
  public static AuthenticationInteractionManagementResult error(
      Tenant tenant, ManagementApiException exception) {
    return new AuthenticationInteractionManagementResult(tenant, null, null, exception);
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
   * <p>For success: returns the stored response For error: converts exception to appropriate HTTP
   * status
   *
   * @return HTTP response
   */
  public AuthenticationInteractionManagementResponse toResponse() {
    if (exception != null) {
      java.util.Map<String, Object> errorResponse = new java.util.HashMap<>();
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());

      AuthenticationInteractionManagementStatus status = mapExceptionToStatus(exception);
      return new AuthenticationInteractionManagementResponse(status, errorResponse);
    }
    return response;
  }

  private AuthenticationInteractionManagementStatus mapExceptionToStatus(ManagementApiException e) {
    if (e instanceof InvalidRequestException) {
      return AuthenticationInteractionManagementStatus.INVALID_REQUEST;
    } else if (e instanceof PermissionDeniedException
        || e instanceof OrganizationAccessDeniedException) {
      return AuthenticationInteractionManagementStatus.FORBIDDEN;
    } else if (e instanceof ResourceNotFoundException) {
      return AuthenticationInteractionManagementStatus.NOT_FOUND;
    }
    return AuthenticationInteractionManagementStatus.SERVER_ERROR;
  }
}
