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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.grant_management.AuthorizationGranted;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedIdentifier;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedQueries;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthorizationGrantedQueryDataSource implements AuthorizationGrantedQueryRepository {

  AuthorizationGrantedSqlExecutor executor;

  public AuthorizationGrantedQueryDataSource(AuthorizationGrantedSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public AuthorizationGranted find(Tenant tenant, AuthorizationGrantedIdentifier identifier) {
    Map<String, String> result = executor.selectById(tenant.identifier(), identifier);

    if (result == null || result.isEmpty()) {
      return new AuthorizationGranted();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public List<AuthorizationGranted> findList(Tenant tenant, AuthorizationGrantedQueries queries) {
    List<Map<String, String>> results = executor.selectList(tenant.identifier(), queries);

    if (results == null || results.isEmpty()) {
      return new ArrayList<>();
    }

    return results.stream().map(ModelConverter::convert).toList();
  }

  @Override
  public long findTotalCount(Tenant tenant, AuthorizationGrantedQueries queries) {
    return executor.selectCount(tenant.identifier(), queries);
  }
}
