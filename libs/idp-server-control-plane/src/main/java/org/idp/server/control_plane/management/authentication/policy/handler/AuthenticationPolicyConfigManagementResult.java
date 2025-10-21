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

package org.idp.server.control_plane.management.authentication.policy.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementStatus;
import org.idp.server.control_plane.management.exception.*;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Result of an authentication policy configuration management service operation.
 *
 * <p>Encapsulates the outcome of authentication policy configuration management operations
 * including success/error status, response data, exception, and context for audit logging.
 *
 * <h2>Usage Pattern</h2>
 *
 * <pre>{@code
 * // Service throws exception
 * validator.validate();  // throws InvalidRequestException
 *
 * // Handler catches and wraps in Result
 * try {
 *   return service.execute(...);
 * } catch (ManagementApiException e) {
 *   return AuthenticationPolicyConfigManagementResult.error(tenant, e);
 * }
 *
 * // Protocol checks and re-throws for transaction rollback
 * if (result.hasException()) {
 *   auditBuilder.failure(result.getException().errorCode());
 *   throw result.getException();
 * }
 * }</pre>
 */
public class AuthenticationPolicyConfigManagementResult {

  private final AuthenticationPolicyConfigManagementResponse response;
  private final Tenant tenant;
  private final Object context;
  private final ManagementApiException exception;

  private AuthenticationPolicyConfigManagementResult(
      AuthenticationPolicyConfigManagementResponse response,
      Tenant tenant,
      Object context,
      ManagementApiException exception) {
    this.response = response;
    this.tenant = tenant;
    this.context = context;
    this.exception = exception;
  }

  /**
   * Creates a successful result.
   *
   * @param tenant the tenant context
   * @param context the operation context for audit logging
   * @param response the AuthenticationPolicyConfigManagementResponse
   * @return AuthenticationPolicyConfigManagementResult with success response
   */
  public static AuthenticationPolicyConfigManagementResult success(
      Tenant tenant, Object context, AuthenticationPolicyConfigManagementResponse response) {
    return new AuthenticationPolicyConfigManagementResult(response, tenant, context, null);
  }

  /**
   * Creates an error result from exception.
   *
   * <p>The exception will be re-thrown by Protocol layer to trigger transaction rollback.
   *
   * @param tenant the tenant context (needed for audit logging)
   * @param exception the exception that caused the error
   * @return AuthenticationPolicyConfigManagementResult with exception
   */
  public static AuthenticationPolicyConfigManagementResult error(
      Tenant tenant, ManagementApiException exception) {
    return new AuthenticationPolicyConfigManagementResult(null, tenant, null, exception);
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
   * Converts to AuthenticationPolicyConfigManagementResponse.
   *
   * <p>If exception exists, automatically generates error response from exception details.
   *
   * @param dryRun whether this is a dry-run operation
   * @return AuthenticationPolicyConfigManagementResponse with status and data
   */
  public AuthenticationPolicyConfigManagementResponse toResponse(boolean dryRun) {
    if (hasException()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("dry_run", dryRun);
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());

      AuthenticationPolicyConfigManagementStatus status = mapExceptionToStatus(exception);
      return new AuthenticationPolicyConfigManagementResponse(status, errorResponse);
    }
    return response;
  }

  /**
   * Maps exception type to AuthenticationPolicyConfigManagementStatus.
   *
   * @param exception the exception to map
   * @return corresponding AuthenticationPolicyConfigManagementStatus
   */
  private AuthenticationPolicyConfigManagementStatus mapExceptionToStatus(
      ManagementApiException exception) {
    if (exception instanceof InvalidRequestException) {
      return AuthenticationPolicyConfigManagementStatus.INVALID_REQUEST;
    } else if (exception instanceof PermissionDeniedException
        || exception instanceof OrganizationAccessDeniedException) {
      return AuthenticationPolicyConfigManagementStatus.FORBIDDEN;
    } else if (exception instanceof ResourceNotFoundException) {
      return AuthenticationPolicyConfigManagementStatus.NOT_FOUND;
    }
    return AuthenticationPolicyConfigManagementStatus.SERVER_ERROR;
  }

  /**
   * Returns the tenant context for audit logging.
   *
   * @return Tenant or null if error result
   */
  public Tenant tenant() {
    return tenant;
  }

  /**
   * Returns the operation context for audit logging.
   *
   * @return operation context (e.g., AuthenticationPolicyConfigRegistrationContext) or null if
   *     error result
   */
  public Object context() {
    return context;
  }
}
