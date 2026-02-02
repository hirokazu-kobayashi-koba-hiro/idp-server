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

package org.idp.server.control_plane.management.oidc.grant.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.OrganizationAccessDeniedException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;

public class GrantManagementResult {
  AuditableContext context;
  ManagementApiException exception;
  GrantManagementResponse response;

  public GrantManagementResult() {}

  public GrantManagementResult(
      AuditableContext context,
      ManagementApiException exception,
      GrantManagementResponse response) {
    this.context = context;
    this.exception = exception;
    this.response = response;
  }

  public static GrantManagementResult success(
      AuditableContext context, GrantManagementResponse response) {
    return new GrantManagementResult(context, null, response);
  }

  public static GrantManagementResult error(
      AuditableContext context, ManagementApiException exception) {
    return new GrantManagementResult(context, exception, null);
  }

  public AuditableContext context() {
    return context;
  }

  public boolean hasException() {
    return exception != null;
  }

  public GrantManagementResponse toResponse(boolean dryRun) {
    if (hasException()) {
      GrantManagementStatus status = mapExceptionToStatus(exception);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("dry_run", dryRun);
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());
      return new GrantManagementResponse(status, errorResponse);
    }
    return response;
  }

  private static GrantManagementStatus mapExceptionToStatus(ManagementApiException exception) {
    if (exception instanceof ResourceNotFoundException) {
      return GrantManagementStatus.NOT_FOUND;
    }
    if (exception instanceof PermissionDeniedException
        || exception instanceof OrganizationAccessDeniedException) {
      return GrantManagementStatus.FORBIDDEN;
    }
    return GrantManagementStatus.INVALID_REQUEST;
  }
}
