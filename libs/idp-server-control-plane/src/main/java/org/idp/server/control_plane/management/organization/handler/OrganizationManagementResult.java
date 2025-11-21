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

package org.idp.server.control_plane.management.organization.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.*;
import org.idp.server.control_plane.management.organization.io.OrganizationManagementResponse;
import org.idp.server.control_plane.management.organization.io.OrganizationManagementStatus;

/**
 * Result container for organization management operations.
 *
 * <p>Implements Result-Exception Hybrid pattern to support both:
 *
 * <ul>
 *   <li>Success case: Contains organization, context, and response
 *   <li>Failure case: Contains organization (for audit logging) and exception
 * </ul>
 *
 * <p>EntryService checks {@code hasException()} and re-throws for transaction rollback while still
 * being able to record audit logs.
 */
public class OrganizationManagementResult {

  private final AuditableContext context;
  private final ManagementApiException exception;
  private final OrganizationManagementResponse response;

  private OrganizationManagementResult(
      AuditableContext context,
      ManagementApiException exception,
      OrganizationManagementResponse response) {
    this.context = context;
    this.exception = exception;
    this.response = response;
  }

  public static OrganizationManagementResult success(
      AuditableContext context, OrganizationManagementResponse response) {
    return new OrganizationManagementResult(context, null, response);
  }

  public static OrganizationManagementResult error(
      AuditableContext context, ManagementApiException exception) {
    return new OrganizationManagementResult(context, exception, null);
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

  public OrganizationManagementResponse toResponse(boolean dryRun) {
    if (hasException()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("dry_run", dryRun);
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());

      OrganizationManagementStatus status = mapExceptionToStatus(exception);
      return new OrganizationManagementResponse(status, errorResponse);
    }
    return response;
  }

  /**
   * Maps exception type to OrganizationManagementStatus.
   *
   * @param exception the exception to map
   * @return corresponding OrganizationManagementStatus
   */
  private OrganizationManagementStatus mapExceptionToStatus(ManagementApiException exception) {
    if (exception instanceof InvalidRequestException) {
      return OrganizationManagementStatus.INVALID_REQUEST;
    } else if (exception instanceof PermissionDeniedException
        || exception instanceof OrganizationAccessDeniedException) {
      return OrganizationManagementStatus.FORBIDDEN;
    } else if (exception instanceof ResourceNotFoundException) {
      return OrganizationManagementStatus.NOT_FOUND;
    }
    return OrganizationManagementStatus.SERVER_ERROR;
  }
}
