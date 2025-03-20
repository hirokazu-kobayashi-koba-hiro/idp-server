package org.idp.server.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.idp.server.core.api.OnboardingApi;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.handler.admin.OrganizationRegistrationRequest;
import org.idp.server.core.handler.admin.TenantRegistrationRequest;
import org.idp.server.core.handler.configuration.ServerConfigurationHandler;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.organization.Organization;
import org.idp.server.core.organization.OrganizationName;
import org.idp.server.core.organization.OrganizationRepository;
import org.idp.server.core.organization.initial.OrganizationCreator;
import org.idp.server.core.organization.initial.TenantCreator;
import org.idp.server.core.tenant.*;
import org.idp.server.core.user.UserRegistrationService;

@Transactional
public class OnboardingEntryService implements OnboardingApi {

  TenantRepository tenantRepository;
  OrganizationRepository organizationRepository;
  UserRegistrationService userRegistrationService;
  ServerConfigurationRepository serverConfigurationRepository;
  JsonConverter jsonConverter;

  public OnboardingEntryService(
      TenantRepository tenantRepository,
      OrganizationRepository organizationRepository,
      UserRegistrationService userRegistrationService,
      ServerConfigurationRepository serverConfigurationRepository) {
    this.tenantRepository = tenantRepository;
    this.organizationRepository = organizationRepository;
    this.userRegistrationService = userRegistrationService;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  // TODO improve logic
  public Map<String, Object> initialize(
          User operator,
          Map<String, Object> request) {

    OrganizationRegistrationRequest organizationRequest =
            jsonConverter.read(request.get("organization"), OrganizationRegistrationRequest.class);
    TenantRegistrationRequest tenantRequest =
            jsonConverter.read(request.get("tenant"), TenantRegistrationRequest.class);
    ServerConfiguration serverConfiguration =
            jsonConverter.read(request.get("server_configuration"), ServerConfiguration.class);

    Organization organization = organizationRequest.toOrganization();
    Tenant tenant =
            new Tenant(
                    new TenantIdentifier(UUID.randomUUID().toString()),
                    tenantRequest.tenantName(),
                    TenantType.ADMIN,
                    new TenantDomain(serverConfiguration.tokenIssuer().value()));
    organization.assign(tenant);

    tenantRepository.register(tenant);
    serverConfigurationRepository.register(serverConfiguration);
    organizationRepository.register(organization);


    HashMap<String, Object> newCustomProperties = new HashMap<>(operator.customPropertiesValue());
    newCustomProperties.put("organization", organization.toMap());
    operator.setCustomProperties(newCustomProperties);
    userRegistrationService.registerOrUpdate(tenant, operator);

    return Map.of("organization", organization.toMap(), "tenant", tenant.toMap());
  }
}
