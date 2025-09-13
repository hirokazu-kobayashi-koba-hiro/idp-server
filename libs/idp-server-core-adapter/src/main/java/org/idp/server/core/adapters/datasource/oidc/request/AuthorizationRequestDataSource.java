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
import org.idp.server.core.openid.oauth.exception.OAuthRequestNotFoundException;
import org.idp.server.core.openid.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthorizationRequestDataSource implements AuthorizationRequestRepository {

  AuthorizationRequestSqlExecutor executor;

  public AuthorizationRequestDataSource(AuthorizationRequestSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public void register(Tenant tenant, AuthorizationRequest authorizationRequest) {
    executor.insert(tenant, authorizationRequest);
  }

  @Override
  public AuthorizationRequest get(
      Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier) {
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
    Map<String, String> stringMap = executor.selectOne(tenant, authorizationRequestIdentifier);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new AuthorizationRequest();
    }

    return ModelConverter.convert(stringMap);
  }

  @Override
  public void delete(Tenant tenant, AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    executor.delete(tenant, authorizationRequestIdentifier);
  }
}
