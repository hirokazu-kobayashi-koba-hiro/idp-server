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

package org.idp.server.control_plane.management.tenant.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.exception.*;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Result container for tenant management operations.
 *
 * <p>Implements Result-Exception Hybrid pattern to support both:
 *
 * <ul>
 *   <li>Success case: Contains tenant, context, and response
 *   <li>Failure case: Contains tenant (for audit logging) and exception
 * </ul>
 *
 * <p>EntryService checks {@code hasException()} and re-throws for transaction rollback while still
 * being able to record audit logs.
 */
public class TenantManagementResult {

  private final Tenant tenant;
  private final Object context;
  private final ManagementApiException exception;
  private final Object response;

  private TenantManagementResult(
      Tenant tenant, Object context, ManagementApiException exception, Object response) {
    this.tenant = tenant;
    this.context = context;
    this.exception = exception;
    this.response = response;
  }

  public static TenantManagementResult success(Tenant tenant, Object context, Object response) {
    return new TenantManagementResult(tenant, context, null, response);
  }

  public static TenantManagementResult error(Tenant tenant, ManagementApiException exception) {
    return new TenantManagementResult(tenant, null, exception, null);
  }

  public boolean hasException() {
    return exception != null;
  }

  public ManagementApiException getException() {
    return exception;
  }

  public Tenant tenant() {
    return tenant;
  }

  public Object context() {
    return context;
  }

  public TenantManagementResponse toResponse(boolean dryRun) {
    if (hasException()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("dry_run", dryRun);
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());

      TenantManagementStatus status = mapExceptionToStatus(exception);
      return new TenantManagementResponse(status, errorResponse);
    }
    if (response instanceof TenantManagementResponse) {
      return (TenantManagementResponse) response;
    }
    throw new IllegalStateException("Response is not a TenantManagementResponse");
  }

  /**
   * Maps exception type to TenantManagementStatus.
   *
   * @param exception the exception to map
   * @return corresponding TenantManagementStatus
   */
  private TenantManagementStatus mapExceptionToStatus(ManagementApiException exception) {
    if (exception instanceof InvalidRequestException) {
      return TenantManagementStatus.INVALID_REQUEST;
    } else if (exception instanceof PermissionDeniedException
        || exception instanceof OrganizationAccessDeniedException) {
      return TenantManagementStatus.FORBIDDEN;
    } else if (exception instanceof ResourceNotFoundException) {
      return TenantManagementStatus.NOT_FOUND;
    }
    return TenantManagementStatus.SERVER_ERROR;
  }
}
