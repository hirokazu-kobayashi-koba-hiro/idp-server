package org.idp.server.adapters.springboot.domain.model.organization.initial;

import org.idp.server.adapters.springboot.domain.model.organization.OrganizationName;
import org.idp.server.adapters.springboot.domain.model.tenant.PublicTenantDomain;
import org.idp.server.adapters.springboot.domain.model.tenant.Tenant;
import org.idp.server.adapters.springboot.domain.model.tenant.TenantName;

public class InitialRegistrationVerifier {
  Tenant tenant;
  OrganizationName organizationName;
  PublicTenantDomain publicTenantDomain;
  TenantName tenantName;
  String serverConfig;

  public InitialRegistrationVerifier(
      Tenant tenant,
      OrganizationName organizationName,
      PublicTenantDomain publicTenantDomain,
      TenantName tenantName,
      String serverConfig) {
    this.tenant = tenant;
    this.organizationName = organizationName;
    this.publicTenantDomain = publicTenantDomain;
    this.tenantName = tenantName;
    this.serverConfig = serverConfig;
  }

  public void verify() {
    if (!tenant.isAdmin()) {
      throw new InitialRegistrationForbiddenException(
          "tenant is not admin. admin tenant only allowed initial registration request");
    }
  }
}
