package org.idp.server.core.adapters.datasource.organization;

import org.idp.server.core.organization.Organization;
import org.idp.server.core.organization.OrganizationIdentifier;

import java.util.Map;

public interface OrganizationSqlExecutor {
  void insert(Organization organization);

  void update(Organization organization);

  Map<String, String> selectOne(OrganizationIdentifier identifier);
}
