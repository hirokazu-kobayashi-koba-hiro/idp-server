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

package org.idp.server.control_plane.management.identity.user;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public class UserRegistrationContext implements AuditableContext {

  Tenant tenant; // nullable: null when Tenant retrieval fails
  TenantIdentifier tenantIdentifier;
  Organization organization; // nullable: system-level API doesn't have Organization
  OrganizationIdentifier organizationIdentifier; // nullable
  User operator;
  OAuthToken oAuthToken;
  RequestAttributes requestAttributes;
  User user; // nullable: null in error scenarios
  UserRegistrationRequest request;
  boolean dryRun;
  ManagementApiException exception; // nullable: null in success scenarios

  // Constructor 1: Tenant retrieved (existing pattern)
  public UserRegistrationContext(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      User user,
      UserRegistrationRequest request,
      boolean dryRun) {
    this(tenant, null, null, null, operator, oAuthToken, requestAttributes, user, request, dryRun, null);
  }

  // Constructor 2: With exception (existing pattern + error)
  public UserRegistrationContext(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      User user,
      UserRegistrationRequest request,
      boolean dryRun,
      ManagementApiException exception) {
    this(tenant, null, null, null, operator, oAuthToken, requestAttributes, user, request, dryRun, exception);
  }

  // Constructor 3: Full information (supports partial data for error cases)
  public UserRegistrationContext(
      Tenant tenant,
      TenantIdentifier tenantIdentifier,
      Organization organization,
      OrganizationIdentifier organizationIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      User user,
      UserRegistrationRequest request,
      boolean dryRun,
      ManagementApiException exception) {
    this.tenant = tenant;
    this.tenantIdentifier = tenantIdentifier != null ? tenantIdentifier
        : (tenant != null ? tenant.identifier() : null);
    this.organization = organization;
    this.organizationIdentifier = organizationIdentifier != null ? organizationIdentifier
        : (organization != null ? organization.identifier() : null);
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
    this.user = user;
    this.request = request;
    this.dryRun = dryRun;
    this.exception = exception;
  }

  public Tenant tenant() {
    return tenant;
  }

  /**
   * Returns the created User.
   *
   * @return created User
   * @throws IllegalStateException if User is not available (error context)
   */
  public User user() {
    if (user == null) {
      throw new IllegalStateException("User not available in error context");
    }
    return user;
  }

  /**
   * Checks if User is available.
   *
   * @return true if User was successfully created
   */
  public boolean hasUser() {
    return user != null;
  }

  public UserRegistrationRequest userRequest() {
    return request;
  }

  // === AuditableContext Implementation ===

  @Override
  public String type() {
    return "UserManagementApi.create";
  }

  @Override
  public String description() {
    return "user";
  }

  @Override
  public String tenantId() {
    return tenantIdentifier != null ? tenantIdentifier.value() : "unknown";
  }

  @Override
  public String clientId() {
    return oAuthToken.requestedClientId().value();
  }

  @Override
  public String userId() {
    return operator.sub();
  }

  @Override
  public String externalUserId() {
    return operator.externalUserId();
  }

  @Override
  public Map<String, Object> userPayload() {
    return operator.toMap();
  }

  @Override
  public String targetResource() {
    return requestAttributes.resource().value();
  }

  @Override
  public String targetResourceAction() {
    return requestAttributes.action().value();
  }

  @Override
  public String ipAddress() {
    return requestAttributes.getIpAddress().value();
  }

  @Override
  public String userAgent() {
    return requestAttributes.getUserAgent().value();
  }

  @Override
  public Map<String, Object> request() {
    return request.toMap();
  }

  @Override
  public Map<String, Object> before() {
    return Collections.emptyMap();
  }

  @Override
  public Map<String, Object> after() {
    return user != null ? user.toMaskedValueMap() : Collections.emptyMap();
  }

  @Override
  public String outcomeResult() {
    return exception != null ? "failure" : "success";
  }

  @Override
  public String outcomeReason() {
    return exception != null ? exception.errorCode() : null;
  }

  @Override
  public String targetTenantId() {
    return tenant.identifierValue();
  }

  @Override
  public Map<String, Object> attributes() {
    if (exception == null) {
      return Collections.emptyMap();
    }

    Map<String, Object> attrs = new HashMap<>();
    attrs.put("error_code", exception.errorCode());
    attrs.put("error_description", exception.errorDescription());

    // Add error details if available
    Map<String, Object> errorDetails = exception.errorDetails();
    if (errorDetails != null && !errorDetails.isEmpty()) {
      attrs.putAll(errorDetails);
    }

    return attrs;
  }

  @Override
  public boolean dryRun() {
    return dryRun;
  }

  public UserManagementResponse toResponse() {
    if (user == null) {
      throw new IllegalStateException("Cannot create response without User");
    }
    Map<String, Object> contents = Map.of("result", user.toMap(), "dry_run", dryRun);
    return new UserManagementResponse(UserManagementStatus.CREATED, contents);
  }
}
