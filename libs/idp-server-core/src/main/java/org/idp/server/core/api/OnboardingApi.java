package org.idp.server.core.api;

import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.organization.Organization;
import org.idp.server.core.organization.OrganizationName;
import org.idp.server.core.tenant.PublicTenantDomain;
import org.idp.server.core.tenant.TenantName;

public interface OnboardingApi {

  Organization initialize(
      User operator,
      OrganizationName organizationName,
      PublicTenantDomain publicTenantDomain,
      TenantName tenantName,
      String serverConfig);
}
