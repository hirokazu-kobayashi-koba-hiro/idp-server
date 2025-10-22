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

package org.idp.server.control_plane.management.identity.user.handler;

import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.identity.user.UserManagementContextBuilder;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Builder for UserDeletionContext.
 *
 * <p>Allows incremental construction of deletion context, supporting error scenarios where data
 * retrieval may fail.
 */
public class UserDeletionContextBuilder implements UserManagementContextBuilder {

  private final User operator;
  private final OAuthToken oAuthToken;
  private final RequestAttributes requestAttributes;

  // Optional: set later
  private Tenant tenant;
  private TenantIdentifier tenantIdentifier;
  private Organization organization;
  private OrganizationIdentifier organizationIdentifier;

  private User user; // nullable: null in error scenarios
  private boolean dryRun;

  // Constructor 1: Tenant already retrieved
  public UserDeletionContextBuilder(
      Tenant tenant, User operator, OAuthToken oAuthToken, RequestAttributes requestAttributes) {
    this.tenant = tenant;
    this.tenantIdentifier = tenant != null ? tenant.identifier() : null;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
  }

  // Constructor 2: TenantIdentifier only (before retrieval)
  public UserDeletionContextBuilder(
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
  public UserDeletionContextBuilder withTenant(Tenant tenant) {
    this.tenant = tenant;
    if (tenant != null && this.tenantIdentifier == null) {
      this.tenantIdentifier = tenant.identifier();
    }
    return this;
  }

  @Override
  public UserDeletionContextBuilder withOrganization(Organization organization) {
    this.organization = organization;
    if (organization != null && this.organizationIdentifier == null) {
      this.organizationIdentifier = organization.identifier();
    }
    return this;
  }

  public UserDeletionContextBuilder withUser(User user) {
    this.user = user;
    return this;
  }

  public UserDeletionContextBuilder withDryRun(boolean dryRun) {
    this.dryRun = dryRun;
    return this;
  }

  @Override
  public AuditableContext build() {
    if (tenant == null) {
      throw new IllegalStateException("Tenant must be set before creating complete context");
    }
    if (user == null) {
      throw new IllegalStateException("User must be set before creating complete context");
    }
    return new UserDeletionContext(tenant, operator, oAuthToken, requestAttributes, user, dryRun);
  }

  @Override
  public AuditableContext buildPartial(ManagementApiException exception) {
    // For deletion, we can create partial context even without user (if lookup failed)
    if (tenant == null && tenantIdentifier == null) {
      throw new IllegalStateException("At least TenantIdentifier is required for error context");
    }
    // Use tenant if available, otherwise null (audit log will handle it)
    return new UserDeletionContext(tenant, operator, oAuthToken, requestAttributes, user, dryRun);
  }
}
