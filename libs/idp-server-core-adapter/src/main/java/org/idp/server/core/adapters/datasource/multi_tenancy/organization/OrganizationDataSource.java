package org.idp.server.core.adapters.datasource.multi_tenancy.organization;

import java.util.Map;
import org.idp.server.core.multi_tenancy.organization.*;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class OrganizationDataSource implements OrganizationRepository {

  OrganizationSqlExecutors executors;

  public OrganizationDataSource() {
    this.executors = new OrganizationSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, Organization organization) {
    OrganizationSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(organization);
  }

  @Override
  public void update(Tenant tenant, Organization organization) {
    OrganizationSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(organization);
  }

  @Override
  public Organization get(Tenant tenant, OrganizationIdentifier identifier) {
    OrganizationSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(identifier);
    if (result == null || result.isEmpty()) {
      throw new OrganizationNotFoundException("Organization not found");
    }

    OrganizationName name = new OrganizationName(result.getOrDefault("name", ""));
    OrganizationDescription description = new OrganizationDescription(result.getOrDefault("description", ""));

    // TODO
    return new Organization(identifier, name, description, new AssignedTenants());
  }
}
