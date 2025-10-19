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

package org.idp.server.control_plane.management.security.event.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.security.event.io.SecurityEventManagementResponse;
import org.idp.server.control_plane.management.security.event.io.SecurityEventManagementStatus;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Result container for security event management operations.
 *
 * <p>Implements Result-Exception Hybrid pattern to support both:
 *
 * <ul>
 *   <li>Success case: Contains tenant and response
 *   <li>Failure case: Contains tenant (for audit logging) and exception
 * </ul>
 *
 * <p>EntryService checks {@code hasException()} and returns appropriate HTTP status.
 */
public class SecurityEventManagementResult {

  private final Tenant tenant;
  private final ManagementApiException exception;
  private final Object response;

  private SecurityEventManagementResult(
      Tenant tenant, ManagementApiException exception, Object response) {
    this.tenant = tenant;
    this.exception = exception;
    this.response = response;
  }

  public static SecurityEventManagementResult success(Tenant tenant, Object response) {
    return new SecurityEventManagementResult(tenant, null, response);
  }

  public static SecurityEventManagementResult error(
      Tenant tenant, ManagementApiException exception) {
    return new SecurityEventManagementResult(tenant, exception, null);
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

  public SecurityEventManagementResponse toResponse() {
    if (hasException()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());

      SecurityEventManagementStatus status = mapExceptionToStatus(exception);
      return new SecurityEventManagementResponse(status, errorResponse);
    }
    if (response instanceof SecurityEventManagementResponse) {
      return (SecurityEventManagementResponse) response;
    }
    throw new IllegalStateException("Response is not a SecurityEventManagementResponse");
  }

  /**
   * Maps exception type to SecurityEventManagementStatus.
   *
   * @param exception the exception to map
   * @return corresponding SecurityEventManagementStatus
   */
  private SecurityEventManagementStatus mapExceptionToStatus(ManagementApiException exception) {
    if (exception instanceof InvalidRequestException) {
      return SecurityEventManagementStatus.INVALID_REQUEST;
    } else if (exception instanceof PermissionDeniedException) {
      return SecurityEventManagementStatus.FORBIDDEN;
    } else if (exception instanceof ResourceNotFoundException) {
      return SecurityEventManagementStatus.NOT_FOUND;
    }
    return SecurityEventManagementStatus.SERVER_ERROR;
  }
}
