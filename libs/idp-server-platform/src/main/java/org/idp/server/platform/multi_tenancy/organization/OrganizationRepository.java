package org.idp.server.platform.multi_tenancy.organization;

import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface OrganizationRepository {
  void register(Tenant tenant, Organization organization);

  void update(Tenant tenant, Organization organization);

  Organization get(Tenant tenant, OrganizationIdentifier identifier);
}
