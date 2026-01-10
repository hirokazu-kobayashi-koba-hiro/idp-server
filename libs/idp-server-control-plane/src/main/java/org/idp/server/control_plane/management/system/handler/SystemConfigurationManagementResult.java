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

package org.idp.server.control_plane.management.system.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.InvalidRequestException;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.system.io.SystemConfigurationManagementResponse;
import org.idp.server.control_plane.management.system.io.SystemConfigurationManagementStatus;

/**
 * Result container for system configuration management operations.
 *
 * <p>Implements Result-Exception Hybrid pattern to support both:
 *
 * <ul>
 *   <li>Success case: Contains context and response
 *   <li>Failure case: Contains context and exception for error response formatting
 * </ul>
 */
public class SystemConfigurationManagementResult {

  private final AuditableContext context;
  private final ManagementApiException exception;
  private final SystemConfigurationManagementResponse response;

  private SystemConfigurationManagementResult(
      AuditableContext context,
      ManagementApiException exception,
      SystemConfigurationManagementResponse response) {
    this.context = context;
    this.exception = exception;
    this.response = response;
  }

  public static SystemConfigurationManagementResult success(
      AuditableContext context, SystemConfigurationManagementResponse response) {
    return new SystemConfigurationManagementResult(context, null, response);
  }

  public static SystemConfigurationManagementResult error(
      AuditableContext context, ManagementApiException exception) {
    return new SystemConfigurationManagementResult(context, exception, null);
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

  public SystemConfigurationManagementResponse toResponse(boolean dryRun) {
    if (hasException()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("dry_run", dryRun);
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());

      SystemConfigurationManagementStatus status = mapExceptionToStatus(exception);
      return new SystemConfigurationManagementResponse(status, errorResponse);
    }
    return response;
  }

  private SystemConfigurationManagementStatus mapExceptionToStatus(
      ManagementApiException exception) {
    if (exception instanceof InvalidRequestException) {
      return SystemConfigurationManagementStatus.BAD_REQUEST;
    } else if (exception instanceof PermissionDeniedException) {
      return SystemConfigurationManagementStatus.FORBIDDEN;
    } else if (exception instanceof ResourceNotFoundException) {
      return SystemConfigurationManagementStatus.BAD_REQUEST;
    }
    return SystemConfigurationManagementStatus.ERROR;
  }
}
