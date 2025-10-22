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

import java.util.Map;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Builder for UserUpdateContext.
 *
 * <p>Allows incremental construction of update context, supporting error scenarios where data
 * retrieval may fail.
 */
public class UserUpdateContextBuilder implements UserManagementContextBuilder {

  private final User operator;
  private final OAuthToken oAuthToken;
  private final RequestAttributes requestAttributes;

  // Optional: set later
  private Tenant tenant;
  private TenantIdentifier tenantIdentifier;
  private Organization organization;
  private OrganizationIdentifier organizationIdentifier;

  private User before; // nullable: null in error scenarios
  private User after; // nullable: null in error scenarios
  private Map<String, Object> requestPayload;
  private boolean dryRun;
  private ManagementApiException exception; // nullable: null in success scenarios

  // Constructor 1: Tenant already retrieved (existing pattern)
  public UserUpdateContextBuilder(
      Tenant tenant, User operator, OAuthToken oAuthToken, RequestAttributes requestAttributes) {
    this.tenant = tenant;
    this.tenantIdentifier = tenant != null ? tenant.identifier() : null;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
  }

  // Constructor 2: TenantIdentifier only (before retrieval)
  public UserUpdateContextBuilder(
      TenantIdentifier tenantIdentifier,
      OrganizationIdentifier organizationIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes) {
    this.tenantIdentifier = tenantIdentifier;
    this.organizationIdentifier = organizationIdentifier;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
  }

  @Override
  public UserUpdateContextBuilder withTenant(Tenant tenant) {
    this.tenant = tenant;
    if (tenant != null && this.tenantIdentifier == null) {
      this.tenantIdentifier = tenant.identifier();
    }
    return this;
  }

  @Override
  public UserUpdateContextBuilder withOrganization(Organization organization) {
    this.organization = organization;
    if (organization != null && this.organizationIdentifier == null) {
      this.organizationIdentifier = organization.identifier();
    }
    return this;
  }

  public UserUpdateContextBuilder withBefore(User before) {
    this.before = before;
    return this;
  }

  public UserUpdateContextBuilder withAfter(User after) {
    this.after = after;
    return this;
  }

  public UserUpdateContextBuilder withRequestPayload(Map<String, Object> requestPayload) {
    this.requestPayload = requestPayload;
    return this;
  }

  public UserUpdateContextBuilder withDryRun(boolean dryRun) {
    this.dryRun = dryRun;
    return this;
  }

  /**
   * Builds complete UserUpdateContext.
   *
   * @return full context with before/after users
   * @throws IllegalStateException if required data is missing
   */
  @Override
  public AuditableContext build() {
    if (tenant == null) {
      throw new IllegalStateException("Tenant must be set before creating complete context");
    }
    if (before == null || after == null) {
      throw new IllegalStateException(
          "Before and after users must be set before creating complete context");
    }
    return new UserUpdateContext(
        tenant, operator, oAuthToken, requestAttributes, before, after, requestPayload, dryRun);
  }

  /**
   * Builds partial UserUpdateContext for error scenarios.
   *
   * @param exception the exception that caused the failure
   * @return partial context with error information
   */
  @Override
  public AuditableContext buildPartial(ManagementApiException exception) {
    this.exception = exception;
    // For update operations, we need at least tenant for audit logging
    // If tenant is null, we cannot create proper context - return minimal error context
    if (tenant == null && tenantIdentifier == null) {
      throw new IllegalStateException("At least TenantIdentifier is required for error context");
    }
    // Use available data, even if incomplete
    return new UserUpdateContext(
        tenant, operator, oAuthToken, requestAttributes, before, after, requestPayload, dryRun);
  }
}
