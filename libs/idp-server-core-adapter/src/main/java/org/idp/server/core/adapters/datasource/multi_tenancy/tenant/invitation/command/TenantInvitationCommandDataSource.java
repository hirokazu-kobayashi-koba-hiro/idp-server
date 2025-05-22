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


package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.command;

import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitation;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TenantInvitationCommandDataSource implements TenantInvitationCommandRepository {

  TenantInvitationSqlExecutors executors;

  public TenantInvitationCommandDataSource() {
    this.executors = new TenantInvitationSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, TenantInvitation tenantInvitation) {
    TenantInvitationSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, tenantInvitation);
  }

  @Override
  public void update(Tenant tenant, TenantInvitation tenantInvitation) {
    TenantInvitationSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(tenant, tenantInvitation);
  }

  @Override
  public void delete(Tenant tenant, TenantInvitation tenantInvitation) {
    TenantInvitationSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(tenant, tenantInvitation);
  }
}
