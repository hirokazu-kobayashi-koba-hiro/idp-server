/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.identity.command;

import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.UserIdentifier;
import org.idp.server.core.oidc.identity.repository.UserCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UserCommandDataSource implements UserCommandRepository {

  UserCommandSqlExecutors executors;

  public UserCommandDataSource() {
    this.executors = new UserCommandSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, User user) {
    UserCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, user);
    if (user.hasRoles()) {
      executor.upsertRoles(tenant, user);
    }
    if (user.hasAssignedTenants()) {
      executor.upsertAssignedTenants(tenant, user);
    }
    if (user.hasCurrentTenantId()) {
      executor.upsertCurrentTenant(tenant, user);
    }
    if (user.hasAssignedOrganizations()) {
      executor.upsertAssignedOrganizations(tenant, user);
    }
    if (user.hasCurrentOrganizationId()) {
      executor.upsertCurrentOrganization(tenant, user);
    }
  }

  @Override
  public void update(Tenant tenant, User user) {
    UserCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(tenant, user);
    if (user.hasRoles()) {
      executor.upsertRoles(tenant, user);
    }
    if (user.hasAssignedTenants()) {
      executor.upsertAssignedTenants(tenant, user);
    }
    if (user.hasCurrentTenantId()) {
      executor.upsertCurrentTenant(tenant, user);
    }
    if (user.hasAssignedOrganizations()) {
      executor.upsertAssignedOrganizations(tenant, user);
    }
    if (user.hasCurrentOrganizationId()) {
      executor.upsertCurrentOrganization(tenant, user);
    }
  }

  @Override
  public void delete(Tenant tenant, UserIdentifier userIdentifier) {
    UserCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(tenant, userIdentifier);
  }
}
