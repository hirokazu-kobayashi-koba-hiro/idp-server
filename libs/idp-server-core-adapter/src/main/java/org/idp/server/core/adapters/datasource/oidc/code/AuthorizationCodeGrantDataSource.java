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

package org.idp.server.core.adapters.datasource.oidc.code;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.openid.grant_management.grant.AuthorizationCodeGrant;
import org.idp.server.core.openid.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.openid.oauth.type.oauth.AuthorizationCode;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthorizationCodeGrantDataSource implements AuthorizationCodeGrantRepository {

  AuthorizationCodeGrantExecutors executors;

  public AuthorizationCodeGrantDataSource() {
    this.executors = new AuthorizationCodeGrantExecutors();
    ;
  }

  @Override
  public void register(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant) {

    AuthorizationCodeGrantExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, authorizationCodeGrant);
  }

  @Override
  public AuthorizationCodeGrant find(Tenant tenant, AuthorizationCode authorizationCode) {
    AuthorizationCodeGrantExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> stringMap = executor.selectOne(tenant, authorizationCode);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new AuthorizationCodeGrant();
    }
    return ModelConverter.convert(stringMap);
  }

  @Override
  public void delete(Tenant tenant, AuthorizationCodeGrant authorizationCodeGrant) {
    AuthorizationCodeGrantExecutor executor = executors.get(tenant.databaseType());
    executor.delete(tenant, authorizationCodeGrant);
  }
}
