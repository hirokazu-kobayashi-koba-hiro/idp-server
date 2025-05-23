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

package org.idp.server.core.adapters.datasource.authentication.config.query;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.core.oidc.authentication.AuthenticationConfiguration;
import org.idp.server.core.oidc.authentication.AuthenticationConfigurationIdentifier;
import org.idp.server.core.oidc.authentication.exception.AuthenticationConfigurationNotFoundException;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthenticationConfigurationQueryDataSource
    implements AuthenticationConfigurationQueryRepository {

  AuthenticationConfigSqlExecutors executors;
  JsonConverter jsonConverter;

  public AuthenticationConfigurationQueryDataSource() {
    this.executors = new AuthenticationConfigSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public <T> T get(Tenant tenant, String type, Class<T> clazz) {
    AuthenticationConfigSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectOne(tenant, type);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new AuthenticationConfigurationNotFoundException(
          String.format(
              "Mfa Configuration is Not Found (%s) (%s)", tenant.identifierValue(), type));
    }

    return jsonConverter.read(result.get("payload"), clazz);
  }

  @Override
  public AuthenticationConfiguration find(
      Tenant tenant, AuthenticationConfigurationIdentifier identifier) {
    AuthenticationConfigSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new AuthenticationConfiguration();
    }

    String id = result.get("id");
    String type = result.get("type");
    JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(result.get("payload"));

    return new AuthenticationConfiguration(id, type, jsonNodeWrapper.toMap());
  }

  @Override
  public List<AuthenticationConfiguration> findList(Tenant tenant, int limit, int offset) {
    return List.of();
  }
}
