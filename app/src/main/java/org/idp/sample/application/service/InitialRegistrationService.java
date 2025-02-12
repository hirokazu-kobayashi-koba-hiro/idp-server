package org.idp.sample.application.service;

import org.idp.sample.application.service.organization.OrganizationService;
import org.idp.sample.application.service.tenant.TenantService;
import org.idp.server.basic.sql.Transactional;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class InitialRegistrationService {

  TenantService tenantService;
  OrganizationService organizationService;

  public InitialRegistrationService(
      TenantService tenantService, OrganizationService organizationService) {
    this.tenantService = tenantService;
    this.organizationService = organizationService;
  }

  public void initialize() {}
}
