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

package org.idp.server.control_plane.management.identity.verification.result.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.OrganizationAccessDeniedException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.identity.verification.result.io.IdentityVerificationResultManagementResponse;
import org.idp.server.control_plane.management.identity.verification.result.io.IdentityVerificationResultManagementStatus;

public class IdentityVerificationResultManagementResult {

  private final AuditableContext context;
  private final ManagementApiException exception;
  private final IdentityVerificationResultManagementResponse response;

  private IdentityVerificationResultManagementResult(
      AuditableContext context,
      ManagementApiException exception,
      IdentityVerificationResultManagementResponse response) {
    this.context = context;
    this.exception = exception;
    this.response = response;
  }

  public static IdentityVerificationResultManagementResult success(
      AuditableContext context, IdentityVerificationResultManagementResponse response) {
    return new IdentityVerificationResultManagementResult(context, null, response);
  }

  public static IdentityVerificationResultManagementResult error(
      AuditableContext context, ManagementApiException exception) {
    return new IdentityVerificationResultManagementResult(context, exception, null);
  }

  private static IdentityVerificationResultManagementStatus mapExceptionToStatus(
      ManagementApiException exception) {
    if (exception instanceof ResourceNotFoundException) {
      return IdentityVerificationResultManagementStatus.NOT_FOUND;
    }
    if (exception instanceof PermissionDeniedException
        || exception instanceof OrganizationAccessDeniedException) {
      return IdentityVerificationResultManagementStatus.FORBIDDEN;
    }
    return IdentityVerificationResultManagementStatus.INVALID_REQUEST;
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

  public IdentityVerificationResultManagementResponse toResponse(boolean dryRun) {
    if (hasException()) {
      IdentityVerificationResultManagementStatus status = mapExceptionToStatus(exception);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("dry_run", dryRun);
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());
      return new IdentityVerificationResultManagementResponse(status, errorResponse);
    }
    return response;
  }
}
