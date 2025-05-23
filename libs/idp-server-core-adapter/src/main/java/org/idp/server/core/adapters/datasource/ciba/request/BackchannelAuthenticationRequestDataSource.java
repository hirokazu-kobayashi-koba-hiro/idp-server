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

package org.idp.server.core.adapters.datasource.ciba.request;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.extension.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestBuilder;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class BackchannelAuthenticationRequestDataSource
    implements BackchannelAuthenticationRequestRepository {

  BackchannelAuthenticationRequestSqlExecutors executors;
  JsonConverter jsonConverter;

  public BackchannelAuthenticationRequestDataSource() {
    this.executors = new BackchannelAuthenticationRequestSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void register(Tenant tenant, BackchannelAuthenticationRequest request) {
    BackchannelAuthenticationRequestSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(request);
  }

  @Override
  public BackchannelAuthenticationRequest find(
      Tenant tenant, BackchannelAuthenticationRequestIdentifier identifier) {
    BackchannelAuthenticationRequestSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> stringMap = executor.selectOne(identifier);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new BackchannelAuthenticationRequestBuilder().build();
    }

    return ModelConverter.convert(stringMap);
  }

  @Override
  public void delete(Tenant tenant, BackchannelAuthenticationRequestIdentifier identifier) {
    BackchannelAuthenticationRequestSqlExecutor executor = executors.get(tenant.databaseType());

    executor.delete(identifier);
  }
}
