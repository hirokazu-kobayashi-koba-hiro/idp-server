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

package org.idp.server.core.adapters.datasource.audit.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.audit.*;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuditLogQueryDataSource implements AuditLogQueryRepository {

  AuditLogSqlExecutors executors;

  public AuditLogQueryDataSource() {
    this.executors = new AuditLogSqlExecutors();
  }

  @Override
  public int findTotalCount(Tenant tenant, AuditLogQueries queries) {
    AuditLogSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectCount(tenant, queries);

    if (result == null || result.isEmpty()) {
      return 0;
    }

    return Integer.parseInt(result.get("count"));
  }

  @Override
  public List<AuditLog> findList(Tenant tenant, AuditLogQueries queries) {
    AuditLogSqlExecutor executor = executors.get(tenant.databaseType());
    List<Map<String, String>> results = executor.selectList(tenant, queries);

    if (results == null || results.isEmpty()) {
      return new ArrayList<>();
    }

    return results.stream().map(ModelConvertor::convert).toList();
  }

  @Override
  public AuditLog find(Tenant tenant, AuditLogIdentifier identifier) {
    AuditLogSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (result == null || result.isEmpty()) {
      return new AuditLog();
    }
    return ModelConvertor.convert(result);
  }
}
