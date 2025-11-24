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

import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.control_plane.base.definition.DefaultAdminRole;
import org.idp.server.control_plane.management.onboarding.OnboardingManagementContextBuilder;
import org.idp.server.control_plane.management.onboarding.io.OnboardingRequest;
import org.idp.server.control_plane.management.onboarding.io.OnboardingResponse;
import org.idp.server.control_plane.management.onboarding.io.OnboardingStatus;
import org.idp.server.control_plane.management.onboarding.io.OrganizationRegistrationRequest;
import org.idp.server.control_plane.management.onboarding.io.TenantRegistrationRequest;
import org.idp.server.control_plane.management.onboarding.validator.OnboardingRequestValidator;
import org.idp.server.control_plane.management.onboarding.verifier.OnboardingVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserRegistrator;
import org.idp.server.core.openid.identity.UserRole;
import org.idp.server.core.openid.identity.UserStatus;
import org.idp.server.core.openid.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.openid.identity.permission.PermissionCommandRepository;
import org.idp.server.core.openid.identity.permission.Permissions;
import org.idp.server.core.openid.identity.role.Role;
import org.idp.server.core.openid.identity.role.RoleCommandRepository;
import org.idp.server.core.openid.identity.role.Roles;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.organization.AssignedTenant;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantAttributes;
import org.idp.server.platform.multi_tenancy.tenant.TenantCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantType;
import org.idp.server.platform.multi_tenancy.tenant.config.CorsConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.config.SessionConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.config.UIConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;
import org.idp.server.platform.security.event.SecurityEventUserAttributeConfiguration;
import org.idp.server.platform.security.log.SecurityEventLogConfiguration;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for onboarding operations.
 *
 * <p>Handles tenant onboarding logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Request validation via OnboardingRequestValidator (throws InvalidRequestException)
 *   <li>Context creation from request data
 *   <li>Business rule verification via OnboardingVerifier (throws InvalidRequestException)
 *   <li>Tenant/Organization/User/Client registration (or dry-run simulation)
 * </ul>
 */
public class OnboardingService {

  private final TenantCommandRepository tenantCommandRepository;
  private final OrganizationRepository organizationRepository;
  private final PermissionCommandRepository permissionCommandRepository;
  private final RoleCommandRepository roleCommandRepository;
  private final UserRegistrator userRegistrator;
  private final AuthorizationServerConfigurationCommandRepository
      authorizationServerConfigurationCommandRepository;
  private final ClientConfigurationCommandRepository clientConfigurationCommandRepository;
  private final OnboardingVerifier onboardingVerifier;
  private final PasswordEncodeDelegation passwordEncodeDelegation;
  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public OnboardingService(
      TenantCommandRepository tenantCommandRepository,
      OrganizationRepository organizationRepository,
      PermissionCommandRepository permissionCommandRepository,
      RoleCommandRepository roleCommandRepository,
      UserRegistrator userRegistrator,
      AuthorizationServerConfigurationCommandRepository
          authorizationServerConfigurationCommandRepository,
      ClientConfigurationCommandRepository clientConfigurationCommandRepository,
      OnboardingVerifier onboardingVerifier,
      PasswordEncodeDelegation passwordEncodeDelegation) {
    this.tenantCommandRepository = tenantCommandRepository;
    this.organizationRepository = organizationRepository;
    this.permissionCommandRepository = permissionCommandRepository;
    this.roleCommandRepository = roleCommandRepository;
    this.userRegistrator = userRegistrator;
    this.authorizationServerConfigurationCommandRepository =
        authorizationServerConfigurationCommandRepository;
    this.clientConfigurationCommandRepository = clientConfigurationCommandRepository;
    this.onboardingVerifier = onboardingVerifier;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
  }

  public OnboardingResponse execute(
      OnboardingManagementContextBuilder builder,
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      OnboardingRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Request validation
    new OnboardingRequestValidator(request, dryRun).validate();

    // 2. Create resources from request
    OrganizationRegistrationRequest organizationRequest =
        jsonConverter.read(request.get("organization"), OrganizationRegistrationRequest.class);
    TenantRegistrationRequest tenantRequest =
        jsonConverter.read(request.get("tenant"), TenantRegistrationRequest.class);
    AuthorizationServerConfiguration authorizationServerConfiguration =
        jsonConverter.read(
            request.get("authorization_server"), AuthorizationServerConfiguration.class);
    User user = jsonConverter.read(request.get("user"), User.class);
    if (!user.hasStatus()) {
      user.setStatus(UserStatus.REGISTERED);
    }

    ClientConfiguration clientConfiguration =
        jsonConverter.read(request.get("client"), ClientConfiguration.class);

    Permissions permissions = DefaultAdminPermission.toPermissions();
    Roles roles = DefaultAdminRole.create(permissions);

    Organization organization = organizationRequest.toOrganization();
    Tenant tenant = createTenant(tenantRequest, organization.identifier());

    AssignedTenant assignedTenant =
        new AssignedTenant(tenant.identifierValue(), tenant.name().value(), tenant.type().name());
    Organization assignedOrganization = organization.updateWithTenant(assignedTenant);

    // Apply tenant identity policy
    if (user.preferredUsername() == null || user.preferredUsername().isBlank()) {
      TenantIdentityPolicy policy = tenant.identityPolicyConfig();
      user.applyIdentityPolicy(policy);
    }

    List<Role> rolesList = roles.toList();
    List<UserRole> userRoles =
        rolesList.stream().map(role -> new UserRole(role.id(), role.name())).toList();
    User updatedUser =
        user.setRoles(userRoles)
            .setAssignedTenants(List.of(tenant.identifierValue()))
            .setAssignedOrganizations(List.of(organization.identifier().value()));
    String hashedPassword = passwordEncodeDelegation.encode(user.rawPassword());
    updatedUser.setHashedPassword(hashedPassword);

    // 3. Populate builder with created resources
    builder
        .setTenant(tenant)
        .setAuthorizationServerConfiguration(authorizationServerConfiguration)
        .setOrganization(assignedOrganization)
        .setPermissions(permissions)
        .setRoles(roles)
        .setCreatedUser(updatedUser)
        .setClientConfiguration(clientConfiguration);

    // 4. Business rule verification
    onboardingVerifier.verify(tenant);

    // 5. Build response
    Map<String, Object> contents =
        Map.of(
            "organization", assignedOrganization.toMap(),
            "tenant", tenant.toMap(),
            "user", updatedUser.toMap(),
            "client", clientConfiguration.toMap(),
            "dry_run", dryRun);

    if (dryRun) {
      return new OnboardingResponse(OnboardingStatus.OK, contents);
    }

    // 6. Repository operations
    organizationRepository.register(organization);
    tenantCommandRepository.register(tenant);
    authorizationServerConfigurationCommandRepository.register(
        tenant, authorizationServerConfiguration);
    organizationRepository.update(assignedOrganization);
    permissionCommandRepository.bulkRegister(tenant, permissions);
    roleCommandRepository.bulkRegister(tenant, roles);
    userRegistrator.registerOrUpdate(tenant, updatedUser);
    clientConfigurationCommandRepository.register(tenant, clientConfiguration);

    return new OnboardingResponse(OnboardingStatus.CREATED, contents);
  }

  private Tenant createTenant(
      TenantRegistrationRequest tenantRequest, OrganizationIdentifier mainOrganizationIdentifier) {
    TenantAttributes attributes =
        tenantRequest.attributes() != null
            ? new TenantAttributes(tenantRequest.attributes())
            : new TenantAttributes();

    UIConfiguration uiConfiguration =
        tenantRequest.uiConfig() != null
            ? new UIConfiguration(tenantRequest.uiConfig())
            : new UIConfiguration();

    CorsConfiguration corsConfiguration =
        tenantRequest.corsConfig() != null
            ? new CorsConfiguration(tenantRequest.corsConfig())
            : new CorsConfiguration();

    SessionConfiguration sessionConfiguration =
        tenantRequest.sessionConfig() != null
            ? new SessionConfiguration(tenantRequest.sessionConfig())
            : new SessionConfiguration();

    SecurityEventLogConfiguration securityEventLogConfiguration =
        tenantRequest.securityEventLogConfig() != null
            ? new SecurityEventLogConfiguration(tenantRequest.securityEventLogConfig())
            : new SecurityEventLogConfiguration();

    SecurityEventUserAttributeConfiguration securityEventUserAttributeConfiguration =
        tenantRequest.securityEventUserConfig() != null
            ? new SecurityEventUserAttributeConfiguration(tenantRequest.securityEventUserConfig())
            : new SecurityEventUserAttributeConfiguration();

    TenantIdentityPolicy identityPolicyConfig =
        TenantIdentityPolicy.fromMap(tenantRequest.identityPolicyConfig());

    return new Tenant(
        tenantRequest.tenantIdentifier(),
        tenantRequest.tenantName(),
        TenantType.ORGANIZER,
        tenantRequest.tenantDomain(),
        tenantRequest.authorizationProvider(),
        attributes,
        uiConfiguration,
        corsConfiguration,
        sessionConfiguration,
        securityEventLogConfiguration,
        securityEventUserAttributeConfiguration,
        identityPolicyConfig,
        mainOrganizationIdentifier,
        true);
  }
}
