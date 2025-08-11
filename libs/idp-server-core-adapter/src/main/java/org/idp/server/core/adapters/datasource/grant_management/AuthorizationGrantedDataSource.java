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

package org.idp.server.core.adapters.datasource.grant_management;

import java.util.*;
import org.idp.server.core.openid.grant_management.AuthorizationGranted;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthorizationGrantedDataSource implements AuthorizationGrantedRepository {

  JsonConverter jsonConverter;
  AuthorizationGrantedSqlExecutors executors;

  public AuthorizationGrantedDataSource() {
    this.executors = new AuthorizationGrantedSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public void register(Tenant tenant, AuthorizationGranted authorizationGranted) {
    AuthorizationGrantedSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(authorizationGranted);
  }

  @Override
  public AuthorizationGranted find(Tenant tenant, RequestedClientId requestedClientId, User user) {
    AuthorizationGrantedSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(tenant.identifier(), requestedClientId, user);

    if (result == null || result.isEmpty()) {
      return new AuthorizationGranted();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public void update(Tenant tenant, AuthorizationGranted authorizationGranted) {
    AuthorizationGrantedSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(tenant, authorizationGranted);
  }

  private String toJson(Object value) {
    return jsonConverter.write(value);
  }
}
