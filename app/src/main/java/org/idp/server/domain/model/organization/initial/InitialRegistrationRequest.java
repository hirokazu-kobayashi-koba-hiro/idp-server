package org.idp.server.domain.model.organization.initial;

import org.idp.server.domain.model.organization.OrganizationName;
import org.idp.server.domain.model.tenant.PublicTenantDomain;
import org.idp.server.domain.model.tenant.TenantName;

public class InitialRegistrationRequest {
  OrganizationName organizationName;
  PublicTenantDomain publicTenantDomain;
  TenantName tenantName;
  String serverConfig;

  public InitialRegistrationRequest(
      OrganizationName organizationName,
      PublicTenantDomain publicTenantDomain,
      TenantName tenantName,
      String serverConfig) {
    this.organizationName = organizationName;
    this.publicTenantDomain = publicTenantDomain;
    this.tenantName = tenantName;
    this.serverConfig = serverConfig;
  }
}
