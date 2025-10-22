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

package org.idp.server.control_plane.management.role.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.OrganizationAccessDeniedException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.role.io.RoleManagementResponse;
import org.idp.server.control_plane.management.role.io.RoleManagementStatus;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Result object for role management operations.
 *
 * <p>Encapsulates the outcome of role management Handler/Service pattern operations. Follows the
 * Result-Exception Hybrid pattern:
 *
 * <ul>
 *   <li>Service layer throws ManagementApiException on validation/verification failures
 *   <li>Handler layer catches exceptions and converts to Result
 *   <li>EntryService layer converts Result to HTTP response
 * </ul>
 *
 * @see RoleManagementHandler
 * @see RoleManagementService
 */
public class RoleManagementResult {

  private final Tenant tenant;
  private final ManagementApiException exception;
  private final RoleManagementResponse response;
  private final AuditableContext context;

  private RoleManagementResult(
      Tenant tenant, ManagementApiException exception, RoleManagementResponse response, AuditableContext context) {
    this.tenant = tenant;
    this.exception = exception;
    this.response = response;
    this.context = context;
  }

  /**
   * Creates a successful result.
   *
   * @param tenant the tenant
   * @param response the success response
   * @return successful result
   */
  public static RoleManagementResult success(Tenant tenant, RoleManagementResponse response) {
    return new RoleManagementResult(tenant, null, response, null);
  }

  /**
   * Creates a successful result with context.
   *
   * @param tenant the tenant
   * @param response the success response
   * @param context the operation context
   * @return successful result with context
   */
  public static RoleManagementResult success(
      Tenant tenant, RoleManagementResponse response, AuditableContext context) {
    return new RoleManagementResult(tenant, null, response, context);
  }

  /**
   * Creates an error result from an exception.
   *
   * @param tenant the tenant
   * @param exception the exception that occurred
   * @return error result
   */
  public static RoleManagementResult error(Tenant tenant, ManagementApiException exception) {
    return new RoleManagementResult(tenant, exception, null, null);
  }

  /**
   * Maps exception types to HTTP status codes.
   *
   * @param exception the exception to map
   * @return corresponding HTTP status
   */
  private static RoleManagementStatus mapExceptionToStatus(ManagementApiException exception) {
    if (exception instanceof ResourceNotFoundException) {
      return RoleManagementStatus.NOT_FOUND;
    }
    if (exception instanceof PermissionDeniedException
        || exception instanceof OrganizationAccessDeniedException) {
      return RoleManagementStatus.FORBIDDEN;
    }
    return RoleManagementStatus.INVALID_REQUEST;
  }

  public Tenant tenant() {
    return tenant;
  }

  public boolean hasException() {
    return exception != null;
  }

  public ManagementApiException getException() {
    return exception;
  }

  public AuditableContext context() {
    return context;
  }

  public RoleManagementResponse toResponse(boolean dryRun) {
    if (hasException()) {
      RoleManagementStatus status = mapExceptionToStatus(exception);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("dry_run", dryRun);
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());
      return new RoleManagementResponse(status, errorResponse);
    }
    return response;
  }
}
