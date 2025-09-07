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

package org.idp.server.core.adapters.datasource.federation.config.query;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.idp.server.core.openid.federation.FederationConfiguration;
import org.idp.server.core.openid.federation.FederationConfigurationIdentifier;
import org.idp.server.core.openid.federation.FederationConfigurationNotFoundException;
import org.idp.server.core.openid.federation.FederationType;
import org.idp.server.core.openid.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.openid.federation.sso.SsoProvider;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class FederationConfigurationQueryDataSource
    implements FederationConfigurationQueryRepository {

  FederationConfigurationSqlExecutors executors;
  JsonConverter jsonConverter;

  public FederationConfigurationQueryDataSource() {
    this.executors = new FederationConfigurationSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public <T> T get(
      Tenant tenant, FederationType federationType, SsoProvider ssoProvider, Class<T> clazz) {
    FederationConfigurationSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(tenant, federationType, ssoProvider);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new FederationConfigurationNotFoundException(
          String.format(
              "federation configuration is not found (%s) (%s) (%s)",
              tenant.identifierValue(), federationType.name(), ssoProvider.name()));
    }

    return jsonConverter.read(result.get("payload"), clazz);
  }

  @Override
  public FederationConfiguration find(Tenant tenant, FederationConfigurationIdentifier identifier) {
    FederationConfigurationSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new FederationConfiguration();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public FederationConfiguration findWithDisabled(
      Tenant tenant, FederationConfigurationIdentifier identifier, boolean includeDisabled) {
    FederationConfigurationSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(tenant, identifier, includeDisabled);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new FederationConfiguration();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public List<FederationConfiguration> findList(Tenant tenant, int limit, int offset) {
    FederationConfigurationSqlExecutor executor = executors.get(tenant.databaseType());

    List<Map<String, String>> results = executor.selectList(tenant, limit, offset);

    if (Objects.isNull(results) || results.isEmpty()) {
      return List.of();
    }

    return results.stream().map(ModelConverter::convert).collect(Collectors.toList());
  }
}
