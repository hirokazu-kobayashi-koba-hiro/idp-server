package org.idp.server.core;

import java.util.HashMap;
import java.util.UUID;
import org.idp.server.core.api.OnboardingApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.configuration.ServerConfiguration;
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
  ServerConfigurationHandler serverConfigurationHandler;

  public OnboardingEntryService(
      TenantRepository tenantRepository,
      OrganizationRepository organizationRepository,
      UserRegistrationService userRegistrationService,
      ServerConfigurationHandler serverConfigurationHandler) {
    this.tenantRepository = tenantRepository;
    this.organizationRepository = organizationRepository;
    this.userRegistrationService = userRegistrationService;
    this.serverConfigurationHandler = serverConfigurationHandler;
  }

  // TODO improve logic
  public Organization initialize(
      User operator,
      OrganizationName organizationName,
      PublicTenantDomain publicTenantDomain,
      TenantName tenantName,
      String serverConfig) {

    String tenantId = UUID.randomUUID().toString();
    TenantIdentifier tenantIdentifier = new TenantIdentifier(tenantId);
    String issuer = publicTenantDomain.value() + tenantId;
    String config = serverConfig.replaceAll("IDP_ISSUER", issuer);
    ServerConfiguration serverConfiguration = serverConfigurationHandler.handleRegistration(config);

    TenantCreator tenantCreator =
        new TenantCreator(
            tenantIdentifier,
            tenantName,
            serverConfiguration.serverIdentifier(),
            serverConfiguration.tokenIssuer());
    Tenant tenant = tenantCreator.create();
    tenantRepository.register(tenant);

    OrganizationCreator organizationCreator = new OrganizationCreator(organizationName, tenant);
    Organization organization = organizationCreator.create();
    organizationRepository.register(organization);

    HashMap<String, Object> newCustomProperties = new HashMap<>(operator.customPropertiesValue());
    newCustomProperties.put("organization", organization.toMap());
    operator.setCustomProperties(newCustomProperties);
    userRegistrationService.registerOrUpdate(tenant, operator);

    return organization;
  }
}
