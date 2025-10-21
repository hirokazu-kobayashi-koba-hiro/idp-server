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

package org.idp.server.control_plane.management.audit.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.audit.io.AuditLogManagementResponse;
import org.idp.server.control_plane.management.audit.io.AuditLogManagementStatus;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.OrganizationAccessDeniedException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Result wrapper for audit log management operations.
 *
 * <p>This class encapsulates the result of audit log management operations, including both success
 * and error cases. It provides a consistent way to handle operation outcomes across the
 * Handler/Service pattern.
 *
 * <h2>Usage Pattern</h2>
 *
 * <pre>{@code
 * // Success case
 * AuditLogManagementResult result = AuditLogManagementResult.success(
 *     tenant, response);
 *
 * // Error case (caught in Handler)
 * try {
 *   return service.execute(...);
 * } catch (ManagementApiException e) {
 *   return AuditLogManagementResult.error(tenant, e);
 * }
 *
 * // Convert to HTTP response (in EntryService)
 * if (result.hasException()) {
 *   auditLogPublisher.publish(createErrorLog(...));
 *   return result.toResponse();
 * }
 * auditLogPublisher.publish(createSuccessLog(...));
 * return result.toResponse();
 * }</pre>
 *
 * @see AuditLogManagementService
 * @see AuditLogManagementHandler
 */
public class AuditLogManagementResult {

  private final Tenant tenant;
  private final AuditLogManagementResponse response;
  private final ManagementApiException exception;

  private AuditLogManagementResult(
      Tenant tenant, AuditLogManagementResponse response, ManagementApiException exception) {
    this.tenant = tenant;
    this.response = response;
    this.exception = exception;
  }

  /**
   * Creates a success result.
   *
   * @param tenant the tenant context
   * @param response the success response
   * @return success result
   */
  public static AuditLogManagementResult success(
      Tenant tenant, AuditLogManagementResponse response) {
    return new AuditLogManagementResult(tenant, response, null);
  }

  /**
   * Creates an error result.
   *
   * @param tenant the tenant context (may be null if tenant retrieval failed)
   * @param exception the exception that occurred
   * @return error result
   */
  public static AuditLogManagementResult error(Tenant tenant, ManagementApiException exception) {
    return new AuditLogManagementResult(tenant, null, exception);
  }

  /**
   * Checks if this result contains an exception.
   *
   * @return true if error result, false if success result
   */
  public boolean hasException() {
    return exception != null;
  }

  /**
   * Gets the tenant context.
   *
   * @return the tenant (may be null if tenant retrieval failed)
   */
  public Tenant tenant() {
    return tenant;
  }

  /**
   * Gets the exception.
   *
   * @return the exception (null for success results)
   */
  public ManagementApiException getException() {
    return exception;
  }

  /**
   * Converts this result to HTTP response.
   *
   * <p>For error results, constructs error response from exception. For success results, returns
   * the response as-is.
   *
   * @return the HTTP response
   */
  public AuditLogManagementResponse toResponse() {
    if (hasException()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());

      AuditLogManagementStatus status = mapExceptionToStatus(exception);
      return new AuditLogManagementResponse(status, errorResponse);
    }

    return response;
  }

  /**
   * Maps exception types to HTTP status codes.
   *
   * @param e the exception
   * @return the corresponding status
   */
  private AuditLogManagementStatus mapExceptionToStatus(ManagementApiException e) {
    if (e instanceof PermissionDeniedException || e instanceof OrganizationAccessDeniedException) {
      return AuditLogManagementStatus.FORBIDDEN;
    } else if (e instanceof ResourceNotFoundException) {
      return AuditLogManagementStatus.NOT_FOUND;
    }
    return AuditLogManagementStatus.SERVER_ERROR;
  }
}
