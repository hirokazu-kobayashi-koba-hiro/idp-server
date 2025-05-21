package org.idp.server.core.adapters.datasource.multi_tenancy.organization;

import java.util.Map;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;

public interface OrganizationSqlExecutor {
  void insert(Organization organization);

  void upsertAssignedTenants(Organization organization);

  void update(Organization organization);

  Map<String, String> selectOne(OrganizationIdentifier identifier);
}
