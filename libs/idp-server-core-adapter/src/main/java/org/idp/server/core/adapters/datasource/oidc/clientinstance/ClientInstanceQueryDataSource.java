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

package org.idp.server.core.adapters.datasource.oidc.clientinstance;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.core.openid.clientinstance.ClientInstanceIdentifier;
import org.idp.server.core.openid.clientinstance.ClientInstanceQueryRepository;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class ClientInstanceQueryDataSource implements ClientInstanceQueryRepository {

  ClientInstanceSqlExecutor executor;

  public ClientInstanceQueryDataSource(ClientInstanceSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public ClientInstance find(
      Tenant tenant, RequestedClientId requestedClientId, ClientInstanceIdentifier identifier) {
    Map<String, String> result = executor.selectOne(tenant, requestedClientId, identifier);

    if (result == null || result.isEmpty()) {
      return new ClientInstance();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public List<ClientInstance> findList(
      Tenant tenant, RequestedClientId requestedClientId, int limit, int offset) {
    List<Map<String, String>> results =
        executor.selectList(tenant, requestedClientId, limit, offset);

    if (results == null || results.isEmpty()) {
      return List.of();
    }

    return results.stream().map(ModelConverter::convert).toList();
  }
}
