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

import org.idp.server.control_plane.base.ApiPermissionVerifier;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.onboarding.OnboardingApi;
import org.idp.server.control_plane.management.onboarding.io.OnboardingRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Handler for onboarding management operations.
 *
 * <p>Orchestrates onboarding requests following the Handler/Service pattern. Responsibilities
 * include:
 *
 * <ul>
 *   <li>Permission verification
 *   <li>Delegating to OnboardingService implementation
 *   <li>Exception handling and conversion to Result objects
 * </ul>
 *
 * <h2>Handler/Service Pattern Flow</h2>
 *
 * <ol>
 *   <li>Handler verifies operator permissions
 *   <li>Handler delegates to Service implementation
 *   <li>Service throws ManagementApiException on validation/verification failures
 *   <li>Handler catches exception and converts to Result
 *   <li>EntryService converts Result to HTTP response
 * </ol>
 *
 * @see OnboardingService
 * @see OnboardingManagementResult
 */
public class OnboardingManagementHandler {

  private final OnboardingService service;
  private final OnboardingApi api;
  private final ApiPermissionVerifier apiPermissionVerifier;

  /**
   * Creates a new onboarding management handler.
   *
   * @param service the onboarding service implementation
   * @param api the onboarding API (for permission definitions)
   */
  public OnboardingManagementHandler(OnboardingService service, OnboardingApi api) {
    this.service = service;
    this.api = api;
    this.apiPermissionVerifier = new ApiPermissionVerifier();
  }

  /**
   * Handles an onboarding request.
   *
   * @param adminTenantIdentifier the admin tenant identifier
   * @param operator the user performing the operation
   * @param oAuthToken the OAuth token
   * @param request the onboarding request
   * @param requestAttributes HTTP request attributes
   * @param dryRun whether to perform a dry run (preview only)
   * @return the operation result
   */
  public OnboardingManagementResult handle(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      OnboardingRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    try {
      AdminPermissions requiredPermissions = api.getRequiredPermissions("onboard");
      apiPermissionVerifier.verify(operator, requiredPermissions);

      return service.execute(
          adminTenantIdentifier, operator, oAuthToken, request, requestAttributes, dryRun);
    } catch (ManagementApiException e) {
      return OnboardingManagementResult.error(adminTenantIdentifier, e);
    }
  }
}
