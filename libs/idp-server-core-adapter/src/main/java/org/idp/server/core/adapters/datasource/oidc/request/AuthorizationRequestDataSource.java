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

package org.idp.server.core.adapters.datasource.oidc.request;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.oidc.exception.OAuthRequestNotFoundException;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthorizationRequestDataSource implements AuthorizationRequestRepository {

  AuthorizationRequestSqlExecutors executors;

  public AuthorizationRequestDataSource() {
    this.executors = new AuthorizationRequestSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, AuthorizationRequest authorizationRequest) {

    AuthorizationRequestSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, authorizationRequest);
  }

  @Override
  public AuthorizationRequest get(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    AuthorizationRequestSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> stringMap = executor.selectOne(tenant, authorizationRequestIdentifier);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new OAuthRequestNotFoundException(
          "invalid_request",
          String.format("not found oauth request (%s)", authorizationRequestIdentifier.value()));
    }

    return ModelConverter.convert(stringMap);
  }

  @Override
  public AuthorizationRequest find(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    AuthorizationRequestSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> stringMap = executor.selectOne(tenant, authorizationRequestIdentifier);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new AuthorizationRequest();
    }

    return ModelConverter.convert(stringMap);
  }

  @Override
  public void delete(Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    AuthorizationRequestSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(tenant, authorizationRequestIdentifier);
  }
}
