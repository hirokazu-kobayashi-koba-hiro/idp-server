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

package org.idp.server.core.adapters.datasource.identity.verification.config.query;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.extension.identity.exception.IdentityVerificationConfigurationNotFoundException;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfigurationIdentifier;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationQueries;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class IdentityVerificationConfigurationQueryDataSource
    implements IdentityVerificationConfigurationQueryRepository {

  IdentityVerificationConfigSqlExecutor executor;
  JsonConverter jsonConverter;

  public IdentityVerificationConfigurationQueryDataSource(
      IdentityVerificationConfigSqlExecutor executor, JsonConverter jsonConverter) {
    this.executor = executor;
    this.jsonConverter = jsonConverter;
  }

  @Override
  public IdentityVerificationConfiguration get(Tenant tenant, IdentityVerificationType type) {
    Map<String, String> result = executor.selectOne(tenant, type);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new IdentityVerificationConfigurationNotFoundException(
          String.format(
              "IdentityVerification Configuration is Not Found (%s) (%s)",
              tenant.identifierValue(), type.name()));
    }

    return jsonConverter.read(result.get("payload"), IdentityVerificationConfiguration.class);
  }

  @Override
  public IdentityVerificationConfiguration find(
      Tenant tenant, IdentityVerificationConfigurationIdentifier identifier) {
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new IdentityVerificationConfiguration();
    }

    return jsonConverter.read(result.get("payload"), IdentityVerificationConfiguration.class);
  }

  @Override
  public long findTotalCount(Tenant tenant, IdentityVerificationQueries queries) {
    Map<String, String> result = executor.selectCount(tenant, queries);

    if (Objects.isNull(result) || result.isEmpty()) {
      return 0;
    }
    return Long.parseLong(result.get("count"));
  }

  @Override
  public List<IdentityVerificationConfiguration> findList(
      Tenant tenant, IdentityVerificationQueries queries) {
    List<Map<String, String>> results = executor.selectList(tenant, queries);

    if (Objects.isNull(results) || results.isEmpty()) {
      return List.of();
    }

    return results.stream()
        .map(
            result ->
                jsonConverter.read(result.get("payload"), IdentityVerificationConfiguration.class))
        .toList();
  }
}
