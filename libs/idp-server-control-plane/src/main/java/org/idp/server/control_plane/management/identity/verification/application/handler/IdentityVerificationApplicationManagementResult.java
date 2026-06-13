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

package org.idp.server.control_plane.management.identity.verification.application.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.OrganizationAccessDeniedException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.identity.verification.application.io.IdentityVerificationApplicationManagementResponse;
import org.idp.server.control_plane.management.identity.verification.application.io.IdentityVerificationApplicationManagementStatus;

public class IdentityVerificationApplicationManagementResult {

  private final AuditableContext context;
  private final ManagementApiException exception;
  private final IdentityVerificationApplicationManagementResponse response;

  private IdentityVerificationApplicationManagementResult(
      AuditableContext context,
      ManagementApiException exception,
      IdentityVerificationApplicationManagementResponse response) {
    this.context = context;
    this.exception = exception;
    this.response = response;
  }

  public static IdentityVerificationApplicationManagementResult success(
      AuditableContext context, IdentityVerificationApplicationManagementResponse response) {
    return new IdentityVerificationApplicationManagementResult(context, null, response);
  }

  public static IdentityVerificationApplicationManagementResult error(
      AuditableContext context, ManagementApiException exception) {
    return new IdentityVerificationApplicationManagementResult(context, exception, null);
  }

  private static IdentityVerificationApplicationManagementStatus mapExceptionToStatus(
      ManagementApiException exception) {
    if (exception instanceof ResourceNotFoundException) {
      return IdentityVerificationApplicationManagementStatus.NOT_FOUND;
    }
    if (exception instanceof PermissionDeniedException
        || exception instanceof OrganizationAccessDeniedException) {
      return IdentityVerificationApplicationManagementStatus.FORBIDDEN;
    }
    return IdentityVerificationApplicationManagementStatus.INVALID_REQUEST;
  }

  public AuditableContext context() {
    return context;
  }

  public boolean hasException() {
    return exception != null;
  }

  public ManagementApiException getException() {
    return exception;
  }

  public IdentityVerificationApplicationManagementResponse toResponse(boolean dryRun) {
    if (hasException()) {
      IdentityVerificationApplicationManagementStatus status = mapExceptionToStatus(exception);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("dry_run", dryRun);
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());
      return new IdentityVerificationApplicationManagementResponse(status, errorResponse);
    }
    return response;
  }
}
