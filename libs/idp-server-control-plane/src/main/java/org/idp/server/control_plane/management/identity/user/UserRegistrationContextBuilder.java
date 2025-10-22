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

import java.util.UUID;
import org.idp.server.control_plane.base.AuditableContext;
import org.idp.server.control_plane.management.exception.ManagementApiException;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Builder for UserRegistrationContext.
 *
 * <p>Allows incremental construction of context, supporting error scenarios where User creation
 * may fail during validation.
 *
 * <h2>Usage Pattern</h2>
 *
 * <pre>{@code
 * UserRegistrationContextBuilder builder =
 *     new UserRegistrationContextBuilder(tenant, operator, oAuthToken, requestAttributes)
 *         .withRequest(request)
 *         .withDryRun(dryRun);
 *
 * try {
 *   // Validation
 *   validator.validate();
 *
 *   // User creation
 *   User user = builder.buildUser(passwordEncodeDelegation);
 *   builder.withUser(user);
 *
 *   // Verification
 *   verifier.verify(builder.build());
 *
 *   // Full context available
 *   UserRegistrationContext context = builder.build();
 * } catch (ManagementApiException e) {
 *   // Partial context for audit logging (User may be null, with exception)
 *   UserRegistrationContext errorContext = builder.buildPartial(e);
 * }
 * }</pre>
 */
public class UserRegistrationContextBuilder implements UserManagementContextBuilder {

  private final User operator;
  private final OAuthToken oAuthToken;
  private final RequestAttributes requestAttributes;
  private final JsonConverter jsonConverter;

  // Optional: set later
  private Tenant tenant;
  private TenantIdentifier tenantIdentifier;
  private Organization organization;
  private OrganizationIdentifier organizationIdentifier;

  private UserRegistrationRequest request;
  private User user; // nullable: null in error scenarios
  private boolean dryRun;

  // Constructor 1: Tenant already retrieved (existing pattern)
  public UserRegistrationContextBuilder(
      Tenant tenant, User operator, OAuthToken oAuthToken, RequestAttributes requestAttributes) {
    this.tenant = tenant;
    this.tenantIdentifier = tenant != null ? tenant.identifier() : null;
    this.operator = operator;
    this.oAuthToken = oAuthToken;
    this.requestAttributes = requestAttributes;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  // Constructor 2: TenantIdentifier only (before retrieval)
  public UserRegistrationContextBuilder(
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
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public UserRegistrationContextBuilder withTenant(Tenant tenant) {
    this.tenant = tenant;
    if (tenant != null && this.tenantIdentifier == null) {
      this.tenantIdentifier = tenant.identifier();
    }
    return this;
  }

  @Override
  public UserRegistrationContextBuilder withOrganization(Organization organization) {
    this.organization = organization;
    if (organization != null && this.organizationIdentifier == null) {
      this.organizationIdentifier = organization.identifier();
    }
    return this;
  }

  public UserRegistrationContextBuilder withRequest(UserRegistrationRequest request) {
    this.request = request;
    return this;
  }

  public UserRegistrationContextBuilder withUser(User user) {
    this.user = user;
    return this;
  }

  public UserRegistrationContextBuilder withDryRun(boolean dryRun) {
    this.dryRun = dryRun;
    return this;
  }

  /**
   * Builds User from request with password encoding and policy application.
   *
   * @param passwordEncodeDelegation password encoder
   * @return created User
   */
  public User buildUser(PasswordEncodeDelegation passwordEncodeDelegation) {
    User user = jsonConverter.read(request.toMap(), User.class);

    // Generate sub if not provided
    if (!user.hasSub()) {
      user.setSub(UUID.randomUUID().toString());
    }

    // Apply tenant identity policy to set preferred_username if not set
    if (user.preferredUsername() == null || user.preferredUsername().isBlank()) {
      TenantIdentityPolicy policy = tenant.identityPolicyConfig();
      user.applyIdentityPolicy(policy);
    }

    // Encode password
    String encoded = passwordEncodeDelegation.encode(user.rawPassword());
    user.setHashedPassword(encoded);
    user.setStatus(UserStatus.REGISTERED);

    return user;
  }

  /**
   * Builds complete UserRegistrationContext.
   *
   * @return full context with User
   * @throws IllegalStateException if User has not been set
   */
  @Override
  public AuditableContext build() {
    if (user == null) {
      throw new IllegalStateException(
          "User must be built and set before creating complete context");
    }
    return new UserRegistrationContext(
        tenant,
        tenantIdentifier,
        organization,
        organizationIdentifier,
        operator,
        oAuthToken,
        requestAttributes,
        user,
        request,
        dryRun,
        null);
  }

  /**
   * Builds partial UserRegistrationContext for error scenarios.
   *
   * <p>Creates context without User, suitable for audit logging when validation fails.
   *
   * @param exception the exception that caused the failure
   * @return partial context (User may be null, with error information)
   */
  @Override
  public AuditableContext buildPartial(ManagementApiException exception) {
    return new UserRegistrationContext(
        tenant,
        tenantIdentifier,
        organization,
        organizationIdentifier,
        operator,
        oAuthToken,
        requestAttributes,
        user,
        request,
        dryRun,
        exception);
  }
}
