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

package org.idp.server.core.adapters.datasource.identity.role.query;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.identity.role.*;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class RoleQueryDataSource implements RoleQueryRepository {

  RoleSqlExecutor executor;

  public RoleQueryDataSource(RoleSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public Role find(Tenant tenant, RoleIdentifier identifier) {
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (result == null || result.isEmpty()) {
      return new Role();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public Role findByName(Tenant tenant, String name) {
    Map<String, String> result = executor.selectOneByName(tenant, name);

    if (result == null || result.isEmpty()) {
      return new Role();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public Roles findAll(Tenant tenant) {
    List<Map<String, String>> results = executor.selectAll(tenant);

    if (results == null || results.isEmpty()) {
      return new Roles();
    }

    List<Role> list = results.stream().map(ModelConverter::convert).toList();
    return new Roles(list);
  }

  @Override
  public long findTotalCount(Tenant tenant, RoleQueries queries) {
    Map<String, String> result = executor.selectCount(tenant, queries);

    if (result == null || result.isEmpty()) {
      return 0;
    }

    return Long.parseLong(result.get("count"));
  }

  @Override
  public List<Role> findList(Tenant tenant, RoleQueries queries) {
    List<Map<String, String>> results = executor.selectList(tenant, queries);

    if (results == null || results.isEmpty()) {
      return List.of();
    }

    return results.stream().map(ModelConverter::convert).toList();
  }
}
