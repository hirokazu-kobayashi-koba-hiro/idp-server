package org.idp.server.core;

import java.util.HashMap;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.api.OnboardingApi;
import org.idp.server.core.handler.configuration.ServerConfigurationHandler;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.organization.Organization;
import org.idp.server.core.organization.OrganizationName;
import org.idp.server.core.organization.OrganizationService;
import org.idp.server.core.organization.initial.OrganizationCreator;
import org.idp.server.core.organization.initial.ServerConfigurationCreator;
import org.idp.server.core.organization.initial.TenantCreator;
import org.idp.server.core.tenant.PublicTenantDomain;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantName;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.user.UserRegistrationService;

@Transactional
public class OnboardingEntryService implements OnboardingApi {

  TenantRepository tenantRepository;
  OrganizationService organizationService;
  UserRegistrationService userRegistrationService;
  ServerConfigurationHandler serverConfigurationHandler;

  public OnboardingEntryService(
      TenantRepository tenantRepository,
      OrganizationService organizationService,
      UserRegistrationService userRegistrationService,
      ServerConfigurationHandler serverConfigurationHandler) {
    this.tenantRepository = tenantRepository;
    this.organizationService = organizationService;
    this.userRegistrationService = userRegistrationService;
    this.serverConfigurationHandler = serverConfigurationHandler;
  }

  public Organization initialize(
      User operator,
      OrganizationName organizationName,
      PublicTenantDomain publicTenantDomain,
      TenantName tenantName,
      String serverConfig) {

    TenantCreator tenantCreator = new TenantCreator(publicTenantDomain, tenantName);
    Tenant tenant = tenantCreator.create();
    tenantRepository.register(tenant);

    OrganizationCreator organizationCreator = new OrganizationCreator(organizationName, tenant);
    Organization organization = organizationCreator.create();
    organizationService.register(organization);

    HashMap<String, Object> newCustomProperties = new HashMap<>(operator.customPropertiesValue());
    newCustomProperties.put("organization", organization.toMap());
    operator.setCustomProperties(newCustomProperties);
    userRegistrationService.registerOrUpdate(tenant, operator);

    ServerConfigurationCreator serverConfigurationCreator =
        new ServerConfigurationCreator(tenant, serverConfig);
    String config = serverConfigurationCreator.create();
    serverConfigurationHandler.handleRegistration(config);

    return organization;
  }
}
