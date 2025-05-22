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


package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.query;

import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitation;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationIdentifier;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TenantInvitationQueryDataSource implements TenantInvitationQueryRepository {

  TenantInvitationSqlExecutors executors;

  public TenantInvitationQueryDataSource() {
    this.executors = new TenantInvitationSqlExecutors();
  }

  @Override
  public List<TenantInvitation> findList(Tenant tenant, int limit, int offset) {
    TenantInvitationSqlExecutor executor = executors.get(tenant.databaseType());
    List<Map<String, String>> results = executor.selectList(tenant, limit, offset);

    if (results == null || results.isEmpty()) {
      return List.of();
    }

    return results.stream().map(ModelConvertor::convert).toList();
  }

  @Override
  public TenantInvitation find(Tenant tenant, TenantInvitationIdentifier identifier) {
    TenantInvitationSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (result == null || result.isEmpty()) {
      return new TenantInvitation();
    }

    return ModelConvertor.convert(result);
  }
}
