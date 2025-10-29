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

package org.idp.server.control_plane.management.tenant.handler;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.onboarding.io.TenantRegistrationRequest;
import org.idp.server.control_plane.management.tenant.TenantManagementContextBuilder;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
import org.idp.server.control_plane.management.tenant.io.TenantRequest;
import org.idp.server.control_plane.management.tenant.validator.TenantRequestValidator;
import org.idp.server.control_plane.management.tenant.verifier.TenantManagementVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.organization.AssignedTenant;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantAttributes;
import org.idp.server.platform.multi_tenancy.tenant.TenantCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantType;
import org.idp.server.platform.multi_tenancy.tenant.config.CorsConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.config.SessionConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.config.UIConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.policy.TenantIdentityPolicy;
import org.idp.server.platform.security.event.SecurityEventUserAttributeConfiguration;
import org.idp.server.platform.security.log.SecurityEventLogConfiguration;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for creating new tenants.
 *
 * <p>Handles tenant creation logic following the Handler/Service pattern.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Request validation
 *   <li>Context creation
 *   <li>Business rule verification
 *   <li>Tenant registration in repository
 *   <li>Organization update
 *   <li>Authorization server configuration registration
 *   <li>User update (operator's assigned tenants)
 * </ul>
 */
public class TenantCreationService implements TenantManagementService<TenantRequest> {

  private final TenantCommandRepository tenantCommandRepository;
  private final OrganizationRepository organizationRepository;
  private final AuthorizationServerConfigurationCommandRepository
      authorizationServerConfigurationCommandRepository;
  private final UserCommandRepository userCommandRepository;
  private final TenantManagementVerifier tenantManagementVerifier;

  public TenantCreationService(
      TenantCommandRepository tenantCommandRepository,
      OrganizationRepository organizationRepository,
      AuthorizationServerConfigurationCommandRepository
          authorizationServerConfigurationCommandRepository,
      UserCommandRepository userCommandRepository,
      TenantManagementVerifier tenantManagementVerifier) {
    this.tenantCommandRepository = tenantCommandRepository;
    this.organizationRepository = organizationRepository;
    this.authorizationServerConfigurationCommandRepository =
        authorizationServerConfigurationCommandRepository;
    this.userCommandRepository = userCommandRepository;
    this.tenantManagementVerifier = tenantManagementVerifier;
  }

  @Override
  public TenantManagementResponse execute(
      TenantManagementContextBuilder builder,
      Tenant adminTenant,
      User operator,
      OAuthToken oAuthToken,
      TenantRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Request validation
    new TenantRequestValidator(request, dryRun).validate();

    // 2. Get organization
    OrganizationIdentifier organizationIdentifier = operator.currentOrganizationIdentifier();
    Organization organization = organizationRepository.get(organizationIdentifier);

    Tenant newTenant = createTenant(request, organizationIdentifier);
    AuthorizationServerConfiguration newAuthorizationServer = createAuthorization(request);

    builder.withAfter(newTenant);

    // 4. Business rule verification
    tenantManagementVerifier.verify(newTenant);

    AssignedTenant assignedTenant =
        new AssignedTenant(
            newTenant.identifierValue(), newTenant.name().value(), newTenant.type().name());
    Organization assignedOrganization = organization.updateWithTenant(assignedTenant);
    operator.addAssignedTenant(newTenant.identifier());

    Map<String, Object> contents = new HashMap<>();
    contents.put("result", newTenant.toMap());
    contents.put("dry_run", dryRun);

    // 5. Dry-run check
    if (dryRun) {
      return new TenantManagementResponse(TenantManagementStatus.OK, contents);
    }

    // 6. Repository operations
    tenantCommandRepository.register(newTenant);
    organizationRepository.update(assignedOrganization);
    authorizationServerConfigurationCommandRepository.register(newTenant, newAuthorizationServer);
    userCommandRepository.update(adminTenant, operator);

    return new TenantManagementResponse(TenantManagementStatus.CREATED, contents);
  }

  public Tenant createTenant(
      TenantRequest request, OrganizationIdentifier mainOrganizationIdentifier) {

    TenantRegistrationRequest tenantRequest =
        JsonConverter.snakeCaseInstance()
            .read(request.get("tenant"), TenantRegistrationRequest.class);

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
        convertIdentityPolicyConfig(tenantRequest.identityPolicyConfig());

    return new Tenant(
        tenantRequest.tenantIdentifier(),
        tenantRequest.tenantName(),
        TenantType.PUBLIC,
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

  private AuthorizationServerConfiguration createAuthorization(TenantRequest request) {
    return JsonConverter.snakeCaseInstance()
        .read(request.get("authorization_server"), AuthorizationServerConfiguration.class);
  }

  private TenantIdentityPolicy convertIdentityPolicyConfig(Map<String, Object> configMap) {
    return TenantIdentityPolicy.fromMap(configMap);
  }
}
