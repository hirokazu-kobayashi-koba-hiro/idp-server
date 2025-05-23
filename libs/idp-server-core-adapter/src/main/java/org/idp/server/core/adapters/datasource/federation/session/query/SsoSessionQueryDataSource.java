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

package org.idp.server.core.adapters.datasource.federation.session.query;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.oidc.federation.sso.SsoSessionIdentifier;
import org.idp.server.core.oidc.federation.sso.SsoSessionNotFoundException;
import org.idp.server.core.oidc.federation.sso.SsoSessionQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class SsoSessionQueryDataSource implements SsoSessionQueryRepository {

  SsoSessionQuerySqlExecutors executors;
  JsonConverter jsonConverter;

  public SsoSessionQueryDataSource() {
    this.executors = new SsoSessionQuerySqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public <T> T get(Tenant tenant, SsoSessionIdentifier ssoSessionIdentifier, Class<T> clazz) {
    SsoSessionQuerySqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(ssoSessionIdentifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      throw new SsoSessionNotFoundException(
          String.format("federation sso session is not found (%s)", ssoSessionIdentifier.value()));
    }

    return jsonConverter.read(result.get("payload"), clazz);
  }
}
