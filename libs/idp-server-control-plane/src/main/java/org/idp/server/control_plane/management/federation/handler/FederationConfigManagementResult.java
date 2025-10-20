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

package org.idp.server.control_plane.management.federation.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.exception.PermissionDeniedException;
import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementResponse;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementStatus;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * Result wrapper for federation configuration management operations.
 *
 * <p>Encapsulates both success and error results, with automatic exception-to-HTTP status mapping.
 *
 * @see FederationConfigManagementService
 */
public class FederationConfigManagementResult {

  private final TenantIdentifier tenantIdentifier;
  private final FederationConfigManagementResponse response;
  private final ManagementApiException exception;
  private final Object context;

  private FederationConfigManagementResult(
      TenantIdentifier tenantIdentifier,
      FederationConfigManagementResponse response,
      ManagementApiException exception,
      Object context) {
    this.tenantIdentifier = tenantIdentifier;
    this.response = response;
    this.exception = exception;
    this.context = context;
  }

  /**
   * Creates a success result.
   *
   * @param tenantIdentifier the tenant identifier
   * @param response the response
   * @return the success result
   */
  public static FederationConfigManagementResult success(
      TenantIdentifier tenantIdentifier, FederationConfigManagementResponse response) {
    return new FederationConfigManagementResult(tenantIdentifier, response, null, null);
  }

  /**
   * Creates a success result with context.
   *
   * @param tenantIdentifier the tenant identifier
   * @param response the response
   * @param context the context object
   * @return the success result
   */
  public static FederationConfigManagementResult success(
      TenantIdentifier tenantIdentifier,
      FederationConfigManagementResponse response,
      Object context) {
    return new FederationConfigManagementResult(tenantIdentifier, response, null, context);
  }

  /**
   * Creates an error result from an exception.
   *
   * @param tenantIdentifier the tenant identifier
   * @param exception the exception
   * @return the error result
   */
  public static FederationConfigManagementResult error(
      TenantIdentifier tenantIdentifier, ManagementApiException exception) {
    return new FederationConfigManagementResult(tenantIdentifier, null, exception, null);
  }

  /**
   * Checks if this result has an exception.
   *
   * @return true if this result has an exception
   */
  public boolean hasException() {
    return exception != null;
  }

  /**
   * Gets the exception.
   *
   * @return the exception
   */
  public ManagementApiException getException() {
    return exception;
  }

  /**
   * Gets the context object.
   *
   * @return the context object
   */
  public Object context() {
    return context;
  }

  /**
   * Converts this result to a response.
   *
   * @param dryRun whether this is a dry run
   * @return the response
   */
  public FederationConfigManagementResponse toResponse(boolean dryRun) {
    if (exception != null) {
      FederationConfigManagementStatus status = mapExceptionToStatus(exception);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", getErrorCode(exception));
      errorResponse.put("error_description", exception.getMessage());
      errorResponse.put("dry_run", dryRun);
      return new FederationConfigManagementResponse(status, errorResponse);
    }
    return response;
  }

  private static FederationConfigManagementStatus mapExceptionToStatus(
      ManagementApiException exception) {
    if (exception instanceof ResourceNotFoundException) {
      return FederationConfigManagementStatus.NOT_FOUND;
    }
    if (exception instanceof PermissionDeniedException) {
      return FederationConfigManagementStatus.FORBIDDEN;
    }
    return FederationConfigManagementStatus.INVALID_REQUEST;
  }

  private static String getErrorCode(ManagementApiException exception) {
    if (exception instanceof ResourceNotFoundException) {
      return "not_found";
    }
    if (exception instanceof PermissionDeniedException) {
      return "access_denied";
    }
    return "invalid_request";
  }
}
