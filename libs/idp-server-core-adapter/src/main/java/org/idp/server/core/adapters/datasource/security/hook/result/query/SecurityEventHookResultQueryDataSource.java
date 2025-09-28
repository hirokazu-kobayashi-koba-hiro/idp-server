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

package org.idp.server.core.adapters.datasource.security.hook.result.query;

import java.util.List;
import java.util.Map;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.exception.SecurityEventHookResultNotFoundException;
import org.idp.server.platform.security.hook.SecurityEventHookResult;
import org.idp.server.platform.security.hook.SecurityEventHookResultIdentifier;
import org.idp.server.platform.security.hook.SecurityEventHookResultQueries;
import org.idp.server.platform.security.repository.SecurityEventHookResultQueryRepository;

public class SecurityEventHookResultQueryDataSource
    implements SecurityEventHookResultQueryRepository {

  SecurityEventHookResultSqlExecutor executor;

  public SecurityEventHookResultQueryDataSource(SecurityEventHookResultSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public SecurityEventHookResult get(Tenant tenant, SecurityEventHookResultIdentifier identifier) {
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (result == null || result.isEmpty()) {
      throw new SecurityEventHookResultNotFoundException(
          String.format("Security event hook result not found. (%s)", identifier.value()));
    }

    return ModelConvertor.convert(result);
  }

  @Override
  public SecurityEventHookResult find(Tenant tenant, SecurityEventHookResultIdentifier identifier) {
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (result == null || result.isEmpty()) {
      return new SecurityEventHookResult();
    }

    return ModelConvertor.convert(result);
  }

  @Override
  public long findTotalCount(Tenant tenant, SecurityEventHookResultQueries queries) {
    Map<String, String> result = executor.selectCount(tenant, queries);

    if (result == null || result.isEmpty()) {
      return 0;
    }

    return Long.parseLong(result.get("count"));
  }

  @Override
  public List<SecurityEventHookResult> findList(
      Tenant tenant, SecurityEventHookResultQueries queries) {
    List<Map<String, String>> results = executor.selectList(tenant, queries);

    if (results == null || results.isEmpty()) {
      return List.of();
    }

    return results.stream().map(ModelConvertor::convert).toList();
  }
}
