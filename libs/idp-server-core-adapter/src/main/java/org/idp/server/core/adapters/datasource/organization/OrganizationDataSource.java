package org.idp.server.core.adapters.datasource.organization;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.sql.SqlExecutor;
import org.idp.server.core.organization.*;
import org.idp.server.core.tenant.Tenant;

public class OrganizationDataSource implements OrganizationRepository {

  OrganizationSqlExecutors executors;

  public OrganizationDataSource() {
    this.executors = new OrganizationSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, Organization organization) {
    OrganizationSqlExecutor executor = executors.get(tenant.dialect());
    executor.insert(organization);
  }

  @Override
  public void update(Tenant tenant, Organization organization) {
    OrganizationSqlExecutor executor = executors.get(tenant.dialect());
    executor.update(organization);
  }

  @Override
  public Organization get(Tenant tenant, OrganizationIdentifier identifier) {
    OrganizationSqlExecutor executor = executors.get(tenant.dialect());

    Map<String, String> result = executor.selectOne(identifier);
    if (result == null || result.isEmpty()) {
      throw new OrganizationNotFoundException("Organization not found");
    }

    OrganizationName name = new OrganizationName(result.getOrDefault("name", ""));
    OrganizationDescription description =
        new OrganizationDescription(result.getOrDefault("description", ""));

    // TODO
    return new Organization(identifier, name, description, new AssignedTenants());
  }
}
