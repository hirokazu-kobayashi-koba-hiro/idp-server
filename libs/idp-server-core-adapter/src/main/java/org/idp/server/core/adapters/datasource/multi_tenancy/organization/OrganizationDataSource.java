/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.multi_tenancy.organization;

import java.util.Map;
import org.idp.server.platform.multi_tenancy.organization.*;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OrganizationDataSource implements OrganizationRepository {

  OrganizationSqlExecutors executors;

  public OrganizationDataSource() {
    this.executors = new OrganizationSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, Organization organization) {
    OrganizationSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(organization);
    if (organization.hasAssignedTenants()) {
      executor.upsertAssignedTenants(organization);
    }
  }

  @Override
  public void update(Tenant tenant, Organization organization) {
    OrganizationSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(organization);
    if (organization.hasAssignedTenants()) {
      executor.upsertAssignedTenants(organization);
    }
  }

  @Override
  public Organization get(Tenant tenant, OrganizationIdentifier identifier) {
    OrganizationSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(identifier);
    if (result == null || result.isEmpty()) {
      throw new OrganizationNotFoundException("Organization not found");
    }

    return ModelConvertor.convert(result);
  }
}
