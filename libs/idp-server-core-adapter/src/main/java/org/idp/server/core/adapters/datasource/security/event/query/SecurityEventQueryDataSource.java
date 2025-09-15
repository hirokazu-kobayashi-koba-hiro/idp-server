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

package org.idp.server.core.adapters.datasource.security.event.query;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEventQueries;
import org.idp.server.platform.security.event.SecurityEventIdentifier;
import org.idp.server.platform.security.repository.SecurityEventQueryRepository;

public class SecurityEventQueryDataSource implements SecurityEventQueryRepository {

  SecurityEventSqlExecutor executor;

  public SecurityEventQueryDataSource(SecurityEventSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public long findTotalCount(Tenant tenant, SecurityEventQueries queries) {
    Map<String, String> result = executor.selectCount(tenant, queries);

    if (result == null || result.isEmpty()) {
      return 0;
    }

    return Long.parseLong(result.get("count"));
  }

  @Override
  public List<SecurityEvent> findList(Tenant tenant, SecurityEventQueries queries) {
    List<Map<String, String>> results = executor.selectList(tenant, queries);

    if (results == null || results.isEmpty()) {
      return List.of();
    }

    return results.stream().map(ModelConvertor::convert).collect(Collectors.toList());
  }

  @Override
  public SecurityEvent find(Tenant tenant, SecurityEventIdentifier identifier) {
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (result == null || result.isEmpty()) {
      return new SecurityEvent();
    }

    return ModelConvertor.convert(result);
  }
}
