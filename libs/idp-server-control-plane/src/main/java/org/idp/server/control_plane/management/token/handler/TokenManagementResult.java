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

package org.idp.server.control_plane.management.token.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.OrganizationAccessDeniedException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.token.io.TokenManagementResponse;
import org.idp.server.control_plane.management.token.io.TokenManagementStatus;

public class TokenManagementResult {

  private final AuditableContext context;
  private final ManagementApiException exception;
  private final TokenManagementResponse response;

  private TokenManagementResult(
      AuditableContext context,
      ManagementApiException exception,
      TokenManagementResponse response) {
    this.context = context;
    this.exception = exception;
    this.response = response;
  }

  public static TokenManagementResult success(
      AuditableContext context, TokenManagementResponse response) {
    return new TokenManagementResult(context, null, response);
  }

  public static TokenManagementResult error(
      AuditableContext context, ManagementApiException exception) {
    return new TokenManagementResult(context, exception, null);
  }

  private static TokenManagementStatus mapExceptionToStatus(ManagementApiException exception) {
    if (exception instanceof ResourceNotFoundException) {
      return TokenManagementStatus.NOT_FOUND;
    }
    if (exception instanceof PermissionDeniedException
        || exception instanceof OrganizationAccessDeniedException) {
      return TokenManagementStatus.FORBIDDEN;
    }
    return TokenManagementStatus.INVALID_REQUEST;
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

  public TokenManagementResponse toResponse(boolean dryRun) {
    if (hasException()) {
      TokenManagementStatus status = mapExceptionToStatus(exception);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      Map<String, Object> errorDetails = exception.errorDetails();
      if (errorDetails != null && !errorDetails.isEmpty()) {
        errorResponse.putAll(errorDetails);
      }
      return new TokenManagementResponse(status, errorResponse);
    }
    return response;
  }
}
