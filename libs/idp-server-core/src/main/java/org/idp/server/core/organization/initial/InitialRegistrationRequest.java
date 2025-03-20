package org.idp.server.core.organization.initial;

import org.idp.server.core.organization.OrganizationName;
import org.idp.server.core.tenant.ServerDomain;
import org.idp.server.core.tenant.TenantName;

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
