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

package org.idp.server.control_plane.management.onboarding;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.onboarding.io.OnboardingRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.permission.Permissions;
import org.idp.server.core.openid.identity.role.Roles;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Unified context for onboarding operations.
 *
 * <p>This context supports onboarding operation which creates a complete tenant environment
 * including:
 *
 * <ul>
 *   <li>Organization creation
 *   <li>Tenant creation
 *   <li>Authorization server configuration
 *   <li>Default permissions and roles
 *   <li>Admin user creation
 *   <li>Admin client configuration
 * </ul>
 *
 * <p>Unlike other management contexts, onboarding only has "after" state (no "before") since it
 * creates new resources.
 */
public class OnboardingManagementContext implements AuditableContext {

  TenantIdentifier adminTenantIdentifier;
  User operator;
  OAuthToken oAuthToken;
  RequestAttributes requestAttributes;
  OnboardingRequest request;
  Tenant tenant;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  Organization organization;
  Permissions permissions;
  Roles roles;
  User createdUser;
  ClientConfiguration clientConfiguration;
  boolean dryRun;
  ManagementApiException exception;

  public OnboardingManagementContext(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      OnboardingRequest request,
      Tenant tenant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      Organization organization,
      Permissions permissions,
      Roles roles,
      User createdUser,
      ClientConfiguration clientConfiguration,
      boolean dryRun,
      ManagementApiException exception) {
    this.adminTenantIdentifier = adminTenantIdentifier;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
    this.request = request;
    this.tenant = tenant;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.organization = organization;
    this.permissions = permissions;
    this.roles = roles;
    this.createdUser = createdUser;
    this.clientConfiguration = clientConfiguration;
    this.dryRun = dryRun;
    this.exception = exception;
  }

  public Tenant tenant() {
    return tenant;
  }

  public Organization organization() {
    return organization;
  }

  public boolean hasException() {
    return exception != null;
  }

  public ManagementApiException exception() {
    return exception;
  }

  // === AuditableContext Implementation ===

  @Override
  public String type() {
    return "onboarding";
  }

  @Override
  public String description() {
    return "onboarding api - creates organization, tenant, and initial configuration";
  }

  @Override
  public String tenantId() {
    return oAuthToken.tenantIdentifier().value();
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
    return request != null ? request.toMap() : Collections.emptyMap();
  }

  @Override
  public Map<String, Object> before() {
    return Collections.emptyMap(); // Onboarding has no "before" state
  }

  @Override
  public Map<String, Object> after() {
    if (tenant == null && organization == null) {
      return Collections.emptyMap();
    }

    Map<String, Object> map = new HashMap<>();
    if (tenant != null) {
      map.put("tenant", tenant.toMap());
    }
    if (organization != null) {
      map.put("organization", organization.toMap());
    }
    if (authorizationServerConfiguration != null) {
      map.put("authorization_server", authorizationServerConfiguration.toMap());
    }
    if (createdUser != null) {
      map.put("user", createdUser.toMap());
    }
    if (clientConfiguration != null) {
      map.put("client", clientConfiguration.toMap());
    }
    if (permissions != null) {
      map.put("permissions_count", permissions.size());
    }
    if (roles != null) {
      map.put("roles_count", roles.toList().size());
    }
    return map;
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
    return tenant != null ? tenant.identifierValue() : null;
  }

  @Override
  public Map<String, Object> attributes() {
    Map<String, Object> attributes = new HashMap<>();
    if (exception != null) {
      Map<String, Object> error = new HashMap<>();
      error.put("error_code", exception.errorCode());
      error.put("error_description", exception.errorDescription());

      Map<String, Object> errorDetails = exception.errorDetails();
      if (errorDetails != null && !errorDetails.isEmpty()) {
        error.putAll(errorDetails);
      }
      attributes.put("error", error);
    }

    return attributes;
  }

  @Override
  public boolean dryRun() {
    return dryRun;
  }
}
