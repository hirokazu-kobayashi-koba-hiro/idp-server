package org.idp.server.core.multi_tenancy.organization.initial;

import org.idp.server.core.multi_tenancy.organization.OrganizationName;
import org.idp.server.core.multi_tenancy.tenant.ServerDomain;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantName;

public class InitialRegistrationVerifier {
  Tenant tenant;
  OrganizationName organizationName;
  ServerDomain serverDomain;
  TenantName tenantName;
  String serverConfig;

  public InitialRegistrationVerifier(Tenant tenant, OrganizationName organizationName, ServerDomain serverDomain, TenantName tenantName, String serverConfig) {
    this.tenant = tenant;
    this.organizationName = organizationName;
    this.serverDomain = serverDomain;
    this.tenantName = tenantName;
    this.serverConfig = serverConfig;
  }

  public void verify() {
    if (!tenant.isAdmin()) {
      throw new InitialRegistrationForbiddenException("tenant is not admin. admin tenant only allowed initial registration request");
    }
  }
}
