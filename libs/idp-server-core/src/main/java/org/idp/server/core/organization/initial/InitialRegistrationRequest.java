package org.idp.server.core.organization.initial;

import org.idp.server.core.organization.OrganizationName;
import org.idp.server.core.tenant.PublicTenantDomain;
import org.idp.server.core.tenant.TenantName;

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
