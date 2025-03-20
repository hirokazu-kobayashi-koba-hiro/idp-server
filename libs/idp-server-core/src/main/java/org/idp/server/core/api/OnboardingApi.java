package org.idp.server.core.api;

import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.organization.Organization;
import org.idp.server.core.organization.OrganizationName;
import org.idp.server.core.tenant.ServerDomain;
import org.idp.server.core.tenant.TenantName;

public interface OnboardingApi {

  Organization initialize(
      User operator,
      OrganizationName organizationName,
      ServerDomain serverDomain,
      TenantName tenantName,
      String serverConfig);
}
