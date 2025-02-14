package org.idp.sample.application.service;

import org.idp.sample.application.service.organization.OrganizationService;
import org.idp.sample.application.service.tenant.TenantService;
import org.idp.sample.domain.model.organization.Organization;
import org.idp.sample.domain.model.organization.OrganizationName;
import org.idp.sample.domain.model.organization.initial.*;
import org.idp.sample.domain.model.tenant.PublicTenantDomain;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.sample.domain.model.tenant.TenantName;
import org.idp.server.IdpServerApplication;
import org.idp.server.api.ServerManagementApi;
import org.idp.server.basic.sql.Transactional;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class InitialRegistrationService {

  TenantService tenantService;
  OrganizationService organizationService;
  ServerManagementApi serverManagementApi;

  public InitialRegistrationService(
      TenantService tenantService,
      OrganizationService organizationService,
      IdpServerApplication idpServerApplication) {
    this.tenantService = tenantService;
    this.organizationService = organizationService;
    this.serverManagementApi = idpServerApplication.serverManagementApi();
  }

  public void initialize(
      TenantIdentifier tenantIdentifier,
      OrganizationName organizationName,
      PublicTenantDomain publicTenantDomain,
      TenantName tenantName,
      String serverConfig) {
    Tenant adminTenant = tenantService.get(tenantIdentifier);
    InitialRegistrationVerifier initialRegistrationVerifier =
        new InitialRegistrationVerifier(
            adminTenant, organizationName, publicTenantDomain, tenantName, serverConfig);
    initialRegistrationVerifier.verify();

    TenantCreator tenantCreator = new TenantCreator(publicTenantDomain, tenantName);
    Tenant tenant = tenantCreator.create();
    tenantService.register(tenant);

    OrganizationCreator organizationCreator = new OrganizationCreator(organizationName, tenant);
    Organization organization = organizationCreator.create();
    organizationService.register(organization);

    ServerConfigurationCreator serverConfigurationCreator =
            new ServerConfigurationCreator(tenant, serverConfig);
    String config = serverConfigurationCreator.create();
    serverManagementApi.register(config);
  }
}
