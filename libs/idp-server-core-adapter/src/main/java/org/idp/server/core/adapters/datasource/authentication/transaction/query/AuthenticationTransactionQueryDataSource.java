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

package org.idp.server.core.adapters.datasource.authentication.transaction.query;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.idp.server.core.openid.authentication.AuthenticationTransaction;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.AuthenticationTransactionQueries;
import org.idp.server.core.openid.authentication.AuthorizationIdentifier;
import org.idp.server.core.openid.authentication.exception.AuthenticationTransactionNotFoundException;
import org.idp.server.core.openid.authentication.repository.AuthenticationTransactionQueryRepository;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthenticationTransactionQueryDataSource
    implements AuthenticationTransactionQueryRepository {

  AuthenticationTransactionQuerySqlExecutor executor;

  public AuthenticationTransactionQueryDataSource(
      AuthenticationTransactionQuerySqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public AuthenticationTransaction get(
      Tenant tenant, AuthenticationTransactionIdentifier identifier) {
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (result == null || result.isEmpty()) {
      throw new AuthenticationTransactionNotFoundException(
          "Authentication transaction not found for identifier: " + identifier.value());
    }

    return ModelConverter.convert(result);
  }

  @Override
  public AuthenticationTransaction get(Tenant tenant, AuthorizationIdentifier identifier) {
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (result == null || result.isEmpty()) {
      throw new AuthenticationTransactionNotFoundException(
          "Authentication transaction not found for authorization identifier: "
              + identifier.value());
    }

    return ModelConverter.convert(result);
  }

  @Override
  public List<AuthenticationTransaction> findList(
      Tenant tenant,
      AuthenticationDeviceIdentifier authenticationDeviceIdentifier,
      AuthenticationTransactionQueries queries) {

    List<Map<String, String>> results =
        executor.selectListByDeviceId(tenant, authenticationDeviceIdentifier, queries);

    if (results == null || results.isEmpty()) {
      return List.of();
    }

    return results.stream().map(ModelConverter::convert).toList();
  }

  @Override
  public long findTotalCount(
      Tenant tenant,
      AuthenticationDeviceIdentifier authenticationDeviceIdentifier,
      AuthenticationTransactionQueries queries) {

    Map<String, String> result =
        executor.selectCountByDeviceId(tenant, authenticationDeviceIdentifier, queries);

    if (result == null || result.isEmpty()) {
      return 0;
    }

    return Long.parseLong(result.get("count"));
  }

  @Override
  public long findTotalCount(Tenant tenant, AuthenticationTransactionQueries queries) {

    Map<String, String> result = executor.selectCount(tenant, queries);

    if (result == null || result.isEmpty()) {
      return 0;
    }

    return Long.parseLong(result.get("count"));
  }

  @Override
  public List<AuthenticationTransaction> findList(
      Tenant tenant, AuthenticationTransactionQueries queries) {

    List<Map<String, String>> results = executor.selectList(tenant, queries);

    if (results == null || results.isEmpty()) {
      return List.of();
    }

    return results.stream().map(ModelConverter::convert).collect(Collectors.toList());
  }

  @Override
  public AuthenticationTransaction find(
      Tenant tenant, AuthenticationTransactionIdentifier identifier) {
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (result == null || result.isEmpty()) {
      return new AuthenticationTransaction();
    }

    return ModelConverter.convert(result);
  }
}
