package org.idp.server.adapters.springboot.application.service;

import java.util.HashMap;

import org.idp.server.core.UserManagementApi;
import org.idp.server.core.organization.initial.OrganizationCreator;
import org.idp.server.core.organization.initial.ServerConfigurationCreator;
import org.idp.server.core.organization.initial.TenantCreator;
import org.idp.server.adapters.springboot.application.service.organization.OrganizationService;
import org.idp.server.adapters.springboot.application.service.tenant.TenantService;
import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.ServerManagementApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.organization.Organization;
import org.idp.server.core.organization.OrganizationName;
import org.idp.server.core.tenant.PublicTenantDomain;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantName;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class OnboardingService {

  TenantService tenantService;
  OrganizationService organizationService;
  UserManagementApi userManagementApi;
  ServerManagementApi serverManagementApi;

  public OnboardingService(
      TenantService tenantService,
      OrganizationService organizationService,
      IdpServerApplication idpServerApplication) {
    this.tenantService = tenantService;
    this.organizationService = organizationService;
    this.userManagementApi = idpServerApplication.userManagementApi();
    this.serverManagementApi = idpServerApplication.serverManagementApi();
  }

  public Organization initialize(
      User operator,
      OrganizationName organizationName,
      PublicTenantDomain publicTenantDomain,
      TenantName tenantName,
      String serverConfig) {

    TenantCreator tenantCreator = new TenantCreator(publicTenantDomain, tenantName);
    Tenant tenant = tenantCreator.create();
    tenantService.register(tenant);

    OrganizationCreator organizationCreator = new OrganizationCreator(organizationName, tenant);
    Organization organization = organizationCreator.create();
    organizationService.register(organization);

    HashMap<String, Object> newCustomProperties = new HashMap<>(operator.customPropertiesValue());
    newCustomProperties.put("organization", organization.toMap());
    operator.setCustomProperties(newCustomProperties);
    userManagementApi.update(operator);

    ServerConfigurationCreator serverConfigurationCreator =
        new ServerConfigurationCreator(tenant, serverConfig);
    String config = serverConfigurationCreator.create();
    serverManagementApi.register(config);

    return organization;
  }
}
