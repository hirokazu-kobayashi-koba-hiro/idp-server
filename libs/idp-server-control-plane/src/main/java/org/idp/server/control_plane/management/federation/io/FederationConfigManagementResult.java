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

package org.idp.server.control_plane.management.federation.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.OrganizationAccessDeniedException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;

/**
 * Result object for federation configuration management operations.
 *
 * <p>Encapsulates the outcome of federation configuration management Handler/Service pattern
 * operations. Follows the Result-Exception Hybrid pattern:
 *
 * <ul>
 *   <li>Service layer throws ManagementApiException on validation/verification failures
 *   <li>Handler layer catches exceptions and converts to Result
 *   <li>EntryService layer converts Result to HTTP response
 * </ul>
 *
 * @see org.idp.server.control_plane.management.federation.handler.FederationConfigManagementHandler
 * @see org.idp.server.control_plane.management.federation.handler.FederationConfigManagementService
 */
public class FederationConfigManagementResult {

  private final AuditableContext context;
  private final ManagementApiException exception;
  private final FederationConfigManagementResponse response;

  private FederationConfigManagementResult(
      AuditableContext context,
      ManagementApiException exception,
      FederationConfigManagementResponse response) {
    this.context = context;
    this.exception = exception;
    this.response = response;
  }

  /**
   * Creates a successful result with context.
   *
   * @param context the operation context
   * @param response the success response
   * @return successful result with context
   */
  public static FederationConfigManagementResult success(
      AuditableContext context, FederationConfigManagementResponse response) {
    return new FederationConfigManagementResult(context, null, response);
  }

  /**
   * Creates an error result from an exception.
   *
   * @param context the operation context (may be partial)
   * @param exception the exception that occurred
   * @return error result
   */
  public static FederationConfigManagementResult error(
      AuditableContext context, ManagementApiException exception) {
    return new FederationConfigManagementResult(context, exception, null);
  }

  /**
   * Maps exception types to HTTP status codes.
   *
   * @param exception the exception to map
   * @return corresponding HTTP status
   */
  private static FederationConfigManagementStatus mapExceptionToStatus(
      ManagementApiException exception) {
    if (exception instanceof ResourceNotFoundException) {
      return FederationConfigManagementStatus.NOT_FOUND;
    }
    if (exception instanceof PermissionDeniedException
        || exception instanceof OrganizationAccessDeniedException) {
      return FederationConfigManagementStatus.FORBIDDEN;
    }
    return FederationConfigManagementStatus.INVALID_REQUEST;
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

  public FederationConfigManagementResponse toResponse(boolean dryRun) {
    if (hasException()) {
      FederationConfigManagementStatus status = mapExceptionToStatus(exception);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("dry_run", dryRun);
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());
      return new FederationConfigManagementResponse(status, errorResponse);
    }
    return response;
  }
}
