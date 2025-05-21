package org.idp.server.platform.multi_tenancy.organization.initial;

import org.idp.server.platform.multi_tenancy.organization.OrganizationName;
import org.idp.server.platform.multi_tenancy.tenant.ServerDomain;
import org.idp.server.platform.multi_tenancy.tenant.TenantName;

public class InitialRegistrationRequest {
  OrganizationName organizationName;
  ServerDomain serverDomain;
  TenantName tenantName;
  String serverConfig;

  public InitialRegistrationRequest(
      OrganizationName organizationName,
      ServerDomain serverDomain,
      TenantName tenantName,
      String serverConfig) {
    this.organizationName = organizationName;
    this.serverDomain = serverDomain;
    this.tenantName = tenantName;
    this.serverConfig = serverConfig;
  }
}
