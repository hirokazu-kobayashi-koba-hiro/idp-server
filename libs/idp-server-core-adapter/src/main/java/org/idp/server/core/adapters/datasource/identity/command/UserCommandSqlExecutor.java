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
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface UserCommandSqlExecutor {
  void insert(Tenant tenant, User user);

  void update(Tenant tenant, User user);

  void updatePassword(Tenant tenant, User user);

  void delete(Tenant tenant, UserIdentifier userIdentifier);

  void upsertRoles(Tenant tenant, User user);

  void upsertAssignedTenants(Tenant tenant, User user);

  void upsertCurrentTenant(Tenant tenant, User user);

  void upsertAssignedOrganizations(Tenant tenant, User user);

  void upsertCurrentOrganization(Tenant tenant, User user);
}
