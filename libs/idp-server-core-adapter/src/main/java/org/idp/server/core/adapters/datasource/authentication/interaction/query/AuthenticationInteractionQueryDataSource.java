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

package org.idp.server.core.adapters.datasource.authentication.interaction.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.idp.server.core.oidc.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.oidc.authentication.exception.MfaTransactionNotFoundException;
import org.idp.server.core.oidc.authentication.interaction.AuthenticationInteraction;
import org.idp.server.core.oidc.authentication.interaction.AuthenticationInteractionQueries;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthenticationInteractionQueryDataSource
    implements AuthenticationInteractionQueryRepository {

  AuthenticationInteractionQuerySqlExecutors executors;
  JsonConverter jsonConverter;

  public AuthenticationInteractionQueryDataSource() {
    this.executors = new AuthenticationInteractionQuerySqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public <T> T get(
      Tenant tenant, AuthenticationTransactionIdentifier identifier, String type, Class<T> clazz) {
    AuthenticationInteractionQuerySqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(tenant, identifier, type);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new MfaTransactionNotFoundException(
          String.format(
              "Authentication interaction is Not Found (%s) (%s)", identifier.value(), type));
    }

    return jsonConverter.read(result.get("payload"), clazz);
  }

  @Override
  public long findTotalCount(Tenant tenant, AuthenticationInteractionQueries queries) {
    AuthenticationInteractionQuerySqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectCount(tenant, queries);

    if (Objects.isNull(result) || result.isEmpty()) {
      return 0;
    }

    return Long.parseLong(result.get("count"));
  }

  @Override
  public List<AuthenticationInteraction> findList(
      Tenant tenant, AuthenticationInteractionQueries queries) {
    AuthenticationInteractionQuerySqlExecutor executor = executors.get(tenant.databaseType());
    List<Map<String, String>> results = executor.selectList(tenant, queries);

    if (Objects.isNull(results) || results.isEmpty()) {
      return new ArrayList<>();
    }

    return results.stream().map(ModelConvertor::convert).collect(Collectors.toList());
  }

  @Override
  public AuthenticationInteraction find(
      Tenant tenant, AuthenticationTransactionIdentifier identifier, String type) {
    AuthenticationInteractionQuerySqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectOne(tenant, identifier, type);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new AuthenticationInteraction();
    }

    return ModelConvertor.convert(result);
  }
}
