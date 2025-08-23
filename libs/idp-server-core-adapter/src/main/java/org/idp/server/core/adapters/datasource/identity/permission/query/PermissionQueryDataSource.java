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

package org.idp.server.core.adapters.datasource.identity.permission.query;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.identity.permission.*;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class PermissionQueryDataSource implements PermissionQueryRepository {

  PermissionSqlExecutors executors;

  public PermissionQueryDataSource() {
    this.executors = new PermissionSqlExecutors();
  }

  @Override
  public Permission find(Tenant tenant, PermissionIdentifier identifier) {
    PermissionSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (result == null || result.isEmpty()) {
      return new Permission();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public Permission findByName(Tenant tenant, String name) {
    PermissionSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectOneByName(tenant, name);

    if (result == null || result.isEmpty()) {
      return new Permission();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public List<Permission> findAll(Tenant tenant) {
    PermissionSqlExecutor executor = executors.get(tenant.databaseType());
    List<Map<String, String>> results = executor.selectAll(tenant);

    if (results == null || results.isEmpty()) {
      return List.of();
    }

    return results.stream().map(ModelConverter::convert).toList();
  }

  @Override
  public long findTotalCount(Tenant tenant, PermissionQueries queries) {
    PermissionSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectCount(tenant, queries);

    if (result == null || result.isEmpty()) {
      return 0;
    }

    return Long.parseLong(result.get("count"));
  }

  @Override
  public List<Permission> findList(Tenant tenant, PermissionQueries queries) {
    PermissionSqlExecutor executor = executors.get(tenant.databaseType());
    List<Map<String, String>> results = executor.selectList(tenant, queries);

    if (results == null || results.isEmpty()) {
      return List.of();
    }

    return results.stream().map(ModelConverter::convert).toList();
  }
}
