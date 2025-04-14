package org.idp.server.core.organization;

import org.idp.server.core.tenant.Tenant;

public interface OrganizationRepository {
  void register(Tenant tenant, Organization organization);

  void update(Tenant tenant, Organization organization);

  Organization get(Tenant tenant, OrganizationIdentifier identifier);
}
