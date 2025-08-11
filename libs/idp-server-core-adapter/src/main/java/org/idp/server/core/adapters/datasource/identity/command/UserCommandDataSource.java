/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.adapters.datasource.identity.command;

import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
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
  public void updatePassword(Tenant tenant, User user) {
    UserCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.updatePassword(tenant, user);
  }

  @Override
  public void delete(Tenant tenant, UserIdentifier userIdentifier) {
    UserCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(tenant, userIdentifier);
  }
}
