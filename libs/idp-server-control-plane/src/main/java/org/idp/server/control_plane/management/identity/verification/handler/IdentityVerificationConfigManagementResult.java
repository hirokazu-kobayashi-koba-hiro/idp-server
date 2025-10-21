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

package org.idp.server.control_plane.management.identity.verification.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.OrganizationAccessDeniedException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementResponse;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementStatus;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * Result object for identity verification config management operations.
 *
 * <p>Encapsulates the outcome of identity verification config management Handler/Service pattern
 * operations. Follows the Result-Exception Hybrid pattern:
 *
 * <ul>
 *   <li>Service layer throws ManagementApiException on validation/verification failures
 *   <li>Handler layer catches exceptions and converts to Result
 *   <li>EntryService layer converts Result to HTTP response
 * </ul>
 *
 * @see IdentityVerificationConfigManagementHandler
 * @see IdentityVerificationConfigCreationService
 * @see IdentityVerificationConfigUpdateService
 * @see IdentityVerificationConfigDeletionService
 */
public class IdentityVerificationConfigManagementResult {

  private final TenantIdentifier tenantIdentifier;
  private final ManagementApiException exception;
  private final IdentityVerificationConfigManagementResponse response;
  private final Object context;

  private IdentityVerificationConfigManagementResult(
      TenantIdentifier tenantIdentifier,
      ManagementApiException exception,
      IdentityVerificationConfigManagementResponse response,
      Object context) {
    this.tenantIdentifier = tenantIdentifier;
    this.exception = exception;
    this.response = response;
    this.context = context;
  }

  /**
   * Creates a successful result.
   *
   * @param tenantIdentifier the tenant identifier
   * @param response the success response
   * @return successful result
   */
  public static IdentityVerificationConfigManagementResult success(
      TenantIdentifier tenantIdentifier, IdentityVerificationConfigManagementResponse response) {
    return new IdentityVerificationConfigManagementResult(tenantIdentifier, null, response, null);
  }

  /**
   * Creates a successful result with context.
   *
   * @param tenantIdentifier the tenant identifier
   * @param response the success response
   * @param context the operation context (ConfigRegistrationContext or ConfigUpdateContext)
   * @return successful result with context
   */
  public static IdentityVerificationConfigManagementResult success(
      TenantIdentifier tenantIdentifier,
      IdentityVerificationConfigManagementResponse response,
      Object context) {
    return new IdentityVerificationConfigManagementResult(
        tenantIdentifier, null, response, context);
  }

  /**
   * Creates an error result from an exception.
   *
   * @param tenantIdentifier the tenant identifier
   * @param exception the exception that occurred
   * @return error result
   */
  public static IdentityVerificationConfigManagementResult error(
      TenantIdentifier tenantIdentifier, ManagementApiException exception) {
    return new IdentityVerificationConfigManagementResult(tenantIdentifier, exception, null, null);
  }

  /**
   * Maps exception types to HTTP status codes.
   *
   * @param exception the exception to map
   * @return corresponding HTTP status
   */
  private static IdentityVerificationConfigManagementStatus mapExceptionToStatus(
      ManagementApiException exception) {
    if (exception instanceof ResourceNotFoundException) {
      return IdentityVerificationConfigManagementStatus.NOT_FOUND;
    }
    if (exception instanceof PermissionDeniedException
        || exception instanceof OrganizationAccessDeniedException) {
      return IdentityVerificationConfigManagementStatus.FORBIDDEN;
    }
    return IdentityVerificationConfigManagementStatus.INVALID_REQUEST;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenantIdentifier;
  }

  public boolean hasException() {
    return exception != null;
  }

  public ManagementApiException getException() {
    return exception;
  }

  public Object context() {
    return context;
  }

  public IdentityVerificationConfigManagementResponse toResponse(boolean dryRun) {
    if (hasException()) {
      IdentityVerificationConfigManagementStatus status = mapExceptionToStatus(exception);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("dry_run", dryRun);
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());
      return new IdentityVerificationConfigManagementResponse(status, errorResponse);
    }
    return response;
  }

  public IdentityVerificationConfigManagementResponse toResponse() {
    if (hasException()) {
      IdentityVerificationConfigManagementStatus status = mapExceptionToStatus(exception);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());
      return new IdentityVerificationConfigManagementResponse(status, errorResponse);
    }
    return response;
  }
}
