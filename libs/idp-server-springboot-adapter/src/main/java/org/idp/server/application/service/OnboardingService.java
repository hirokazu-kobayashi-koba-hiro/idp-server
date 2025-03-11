package org.idp.server.application.service;

import java.util.HashMap;
import org.idp.server.application.service.organization.OrganizationService;
import org.idp.server.application.service.tenant.TenantService;
import org.idp.server.application.service.user.internal.UserService;
import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.api.ServerManagementApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.domain.model.organization.Organization;
import org.idp.server.domain.model.organization.OrganizationName;
import org.idp.server.domain.model.organization.initial.*;
import org.idp.server.domain.model.tenant.PublicTenantDomain;
import org.idp.server.domain.model.tenant.Tenant;
import org.idp.server.domain.model.tenant.TenantName;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class OnboardingService {

  TenantService tenantService;
  OrganizationService organizationService;
  UserService userService;
  ServerManagementApi serverManagementApi;

  public OnboardingService(
      TenantService tenantService,
      OrganizationService organizationService,
      UserService userService,
      IdpServerApplication idpServerApplication) {
    this.tenantService = tenantService;
    this.organizationService = organizationService;
    this.userService = userService;
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
    userService.update(operator);

    ServerConfigurationCreator serverConfigurationCreator =
        new ServerConfigurationCreator(tenant, serverConfig);
    String config = serverConfigurationCreator.create();
    serverManagementApi.register(config);

    return organization;
  }
}
