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

package org.idp.server.control_plane.management.onboarding.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.onboarding.OnboardingContext;
import org.idp.server.control_plane.management.onboarding.io.OnboardingResponse;
import org.idp.server.control_plane.management.onboarding.io.OnboardingStatus;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * Result object for onboarding management operations.
 *
 * <p>Encapsulates the outcome of onboarding management Handler/Service pattern operations. Follows
 * the Result-Exception Hybrid pattern:
 *
 * <ul>
 *   <li>Service layer throws ManagementApiException on validation/verification failures
 *   <li>Handler layer catches exceptions and converts to Result
 *   <li>EntryService layer converts Result to HTTP response
 * </ul>
 *
 * @see OnboardingManagementHandler
 * @see OnboardingService
 */
public class OnboardingManagementResult {

  private final TenantIdentifier adminTenantIdentifier;
  private final ManagementApiException exception;
  private final OnboardingResponse response;
  private final OnboardingContext context;

  private OnboardingManagementResult(
      TenantIdentifier adminTenantIdentifier,
      ManagementApiException exception,
      OnboardingResponse response,
      OnboardingContext context) {
    this.adminTenantIdentifier = adminTenantIdentifier;
    this.exception = exception;
    this.response = response;
    this.context = context;
  }

  /**
   * Creates a successful result.
   *
   * @param adminTenantIdentifier the admin tenant identifier
   * @param response the success response
   * @return successful result
   */
  public static OnboardingManagementResult success(
      TenantIdentifier adminTenantIdentifier, OnboardingResponse response) {
    return new OnboardingManagementResult(adminTenantIdentifier, null, response, null);
  }

  /**
   * Creates a successful result with context.
   *
   * @param adminTenantIdentifier the admin tenant identifier
   * @param response the success response
   * @param context the operation context
   * @return successful result with context
   */
  public static OnboardingManagementResult success(
      TenantIdentifier adminTenantIdentifier,
      OnboardingResponse response,
      OnboardingContext context) {
    return new OnboardingManagementResult(adminTenantIdentifier, null, response, context);
  }

  /**
   * Creates an error result from an exception.
   *
   * @param adminTenantIdentifier the admin tenant identifier
   * @param exception the exception that occurred
   * @return error result
   */
  public static OnboardingManagementResult error(
      TenantIdentifier adminTenantIdentifier, ManagementApiException exception) {
    return new OnboardingManagementResult(adminTenantIdentifier, exception, null, null);
  }

  /**
   * Maps exception types to HTTP status codes.
   *
   * @param exception the exception to map
   * @return corresponding HTTP status
   */
  private static OnboardingStatus mapExceptionToStatus(ManagementApiException exception) {
    return OnboardingStatus.INVALID_REQUEST;
  }

  public TenantIdentifier adminTenantIdentifier() {
    return adminTenantIdentifier;
  }

  public boolean hasException() {
    return exception != null;
  }

  public ManagementApiException getException() {
    return exception;
  }

  public OnboardingContext context() {
    return context;
  }

  public OnboardingResponse toResponse(boolean dryRun) {
    if (hasException()) {
      OnboardingStatus status = mapExceptionToStatus(exception);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("dry_run", dryRun);
      errorResponse.put("error", exception.errorCode());
      errorResponse.put("error_description", exception.errorDescription());
      errorResponse.putAll(exception.errorDetails());
      return new OnboardingResponse(status, errorResponse);
    }
    return response;
  }
}
