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

package org.idp.server.core.adapters.datasource.ciba.grant;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.extension.ciba.exception.CibaGrantNotFoundException;
import org.idp.server.core.extension.ciba.grant.CibaGrant;
import org.idp.server.core.extension.ciba.repository.CibaGrantRepository;
import org.idp.server.core.extension.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.openid.oauth.type.ciba.AuthReqId;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class CibaGrantDataSource implements CibaGrantRepository {

  CibaGrantSqlExecutors executors;

  public CibaGrantDataSource() {
    this.executors = new CibaGrantSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, CibaGrant cibaGrant) {
    CibaGrantSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(cibaGrant);
  }

  @Override
  public void update(Tenant tenant, CibaGrant cibaGrant) {
    CibaGrantSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(cibaGrant);
  }

  @Override
  public CibaGrant find(Tenant tenant, AuthReqId authReqId) {
    CibaGrantSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> stringMap = executor.selectOne(authReqId);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new CibaGrant();
    }

    return ModelConverter.convert(stringMap);
  }

  @Override
  public CibaGrant get(
      Tenant tenant,
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier) {
    CibaGrantSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> stringMap = executor.selectOne(backchannelAuthenticationRequestIdentifier);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      throw new CibaGrantNotFoundException(
          String.format(
              "ciba grant not found (%s)", backchannelAuthenticationRequestIdentifier.value()));
    }

    return ModelConverter.convert(stringMap);
  }

  @Override
  public void delete(Tenant tenant, CibaGrant cibaGrant) {
    CibaGrantSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(cibaGrant);
  }
}
