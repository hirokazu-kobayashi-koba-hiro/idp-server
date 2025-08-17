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

package org.idp.server.core.adapters.datasource.federation.credentials.query;

import java.util.Map;
import org.idp.server.core.openid.federation.sso.SsoCredentials;
import org.idp.server.core.openid.federation.sso.SsoCredentialsQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class SsoCredentialsQueryDataSource implements SsoCredentialsQueryRepository {

  SsoCredentialsSqlExecutors executors;
  JsonConverter jsonConverter;

  public SsoCredentialsQueryDataSource() {
    this.executors = new SsoCredentialsSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public SsoCredentials find(Tenant tenant, User user) {
    SsoCredentialsSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> result = executor.selectBy(tenant, user);

    if (result == null || result.isEmpty()) {
      return new SsoCredentials();
    }

    return jsonConverter.read(result.get("sso_credentials"), SsoCredentials.class);
  }
}
