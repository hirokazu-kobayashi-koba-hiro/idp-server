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

package org.idp.server.core.adapters.datasource.federation.credentials.command;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.openid.federation.sso.SsoCredentials;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements SsoCredentialsSqlExecutor {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public void insert(Tenant tenant, User user, SsoCredentials ssoCredentials) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
                INSERT INTO idp_user_sso_credentials (
                user_id,
                tenant_id,
                sso_provider,
                sso_credentials
                )
                VALUES (
                ?,
                ?,
                ?,
                ?
                )
                ON DUPLICATE KEY
                UPDATE SET sso_credentials = ?::jsonb, updated_at = now();
                """;

    String json = jsonConverter.write(ssoCredentials);
    List<Object> params = new ArrayList<>();
    params.add(user.sub());
    params.add(tenant.identifierValue());
    params.add(ssoCredentials.provider());
    params.add(json);
    params.add(json);

    sqlExecutor.execute(sqlTemplate, params);
  }
}
