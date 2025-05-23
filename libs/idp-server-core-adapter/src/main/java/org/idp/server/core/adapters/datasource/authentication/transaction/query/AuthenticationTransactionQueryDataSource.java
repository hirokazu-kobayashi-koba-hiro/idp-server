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

import java.util.Map;
import org.idp.server.core.oidc.authentication.AuthenticationTransaction;
import org.idp.server.core.oidc.authentication.AuthorizationIdentifier;
import org.idp.server.core.oidc.authentication.exception.AuthenticationTransactionNotFoundException;
import org.idp.server.core.oidc.authentication.repository.AuthenticationTransactionQueryRepository;
import org.idp.server.core.oidc.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthenticationTransactionQueryDataSource
    implements AuthenticationTransactionQueryRepository {

  AuthenticationTransactionQuerySqlExecutors executors;

  public AuthenticationTransactionQueryDataSource() {
    this.executors = new AuthenticationTransactionQuerySqlExecutors();
  }

  @Override
  public AuthenticationTransaction get(Tenant tenant, AuthorizationIdentifier identifier) {
    AuthenticationTransactionQuerySqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (result == null || result.isEmpty()) {
      throw new AuthenticationTransactionNotFoundException(
          "Authentication transaction not found for identifier: " + identifier.value());
    }

    return ModelConverter.convert(result);
  }

  @Override
  public AuthenticationTransaction findLatest(
      Tenant tenant, AuthenticationDeviceIdentifier authenticationDeviceIdentifier) {

    AuthenticationTransactionQuerySqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result =
        executor.selectOneByDeviceId(tenant, authenticationDeviceIdentifier);

    if (result == null || result.isEmpty()) {
      return new AuthenticationTransaction();
    }

    return ModelConverter.convert(result);
  }
}
