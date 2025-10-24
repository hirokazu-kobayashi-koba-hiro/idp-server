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
 * Builder for OnboardingManagementContext.
 *
 * <p>Allows incremental construction of context, supporting error scenarios where data creation may
 * fail.
 *
 * <h2>Usage Pattern</h2>
 *
 * <pre>{@code
 * // 1. Create builder early (before potential failures)
 * OnboardingManagementContextBuilder builder =
 *     new OnboardingManagementContextBuilder(...);
 *
 * try {
 *   // 2. Service populates builder during execution
 *   builder.setTenant(tenant);
 *   builder.setOrganization(organization);
 *   builder.setAuthorizationServerConfiguration(authzConfig);
 *   // ... other setters
 *
 *   // 3. Build complete context on success
 *   AuditableContext context = builder.build();
 * } catch (ManagementApiException e) {
 *   // 4. Build partial context on error (for audit logging)
 *   AuditableContext errorContext = builder.buildPartial(e);
 * }
 * }</pre>
 */
public class OnboardingManagementContextBuilder {

  private final TenantIdentifier adminTenantIdentifier;
  private final User operator;
  private final OAuthToken oAuthToken;
  private final RequestAttributes requestAttributes;
  private final OnboardingRequest request;
  private final boolean dryRun;

  private Tenant tenant;
  private AuthorizationServerConfiguration authorizationServerConfiguration;
  private Organization organization;
  private Permissions permissions;
  private Roles roles;
  private User createdUser;
  private ClientConfiguration clientConfiguration;

  public OnboardingManagementContextBuilder(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes,
      OnboardingRequest request,
      boolean dryRun) {
    this.adminTenantIdentifier = adminTenantIdentifier;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
    this.request = request;
    this.dryRun = dryRun;
  }

  public OnboardingManagementContextBuilder setTenant(Tenant tenant) {
    this.tenant = tenant;
    return this;
  }

  public OnboardingManagementContextBuilder setAuthorizationServerConfiguration(
      AuthorizationServerConfiguration authorizationServerConfiguration) {
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    return this;
  }

  public OnboardingManagementContextBuilder setOrganization(Organization organization) {
    this.organization = organization;
    return this;
  }

  public OnboardingManagementContextBuilder setPermissions(Permissions permissions) {
    this.permissions = permissions;
    return this;
  }

  public OnboardingManagementContextBuilder setRoles(Roles roles) {
    this.roles = roles;
    return this;
  }

  public OnboardingManagementContextBuilder setCreatedUser(User createdUser) {
    this.createdUser = createdUser;
    return this;
  }

  public OnboardingManagementContextBuilder setClientConfiguration(
      ClientConfiguration clientConfiguration) {
    this.clientConfiguration = clientConfiguration;
    return this;
  }

  /**
   * Builds complete OnboardingManagementContext.
   *
   * @return full context with created resources
   */
  public AuditableContext build() {
    return new OnboardingManagementContext(
        adminTenantIdentifier,
        operator,
        oAuthToken,
        requestAttributes,
        request,
        tenant,
        authorizationServerConfiguration,
        organization,
        permissions,
        roles,
        createdUser,
        clientConfiguration,
        dryRun,
        null);
  }

  /**
   * Builds partial OnboardingManagementContext for error scenarios.
   *
   * <p>Enables audit logging even when operation fails early.
   *
   * @param exception the exception that caused the failure
   * @return partial context with error information
   */
  public AuditableContext buildPartial(ManagementApiException exception) {
    return new OnboardingManagementContext(
        adminTenantIdentifier,
        operator,
        oAuthToken,
        requestAttributes,
        request,
        tenant,
        authorizationServerConfiguration,
        organization,
        permissions,
        roles,
        createdUser,
        clientConfiguration,
        dryRun,
        exception);
  }
}
