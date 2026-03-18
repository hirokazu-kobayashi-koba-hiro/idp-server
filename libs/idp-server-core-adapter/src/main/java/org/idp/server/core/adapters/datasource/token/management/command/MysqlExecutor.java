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

package org.idp.server.core.adapters.datasource.token.management.command;

import java.util.List;
import org.idp.server.core.openid.token.OAuthTokenIdentifier;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements OAuthTokenManagementCommandSqlExecutor {

  @Override
  public void deleteById(Tenant tenant, OAuthTokenIdentifier identifier) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM oauth_token
            WHERE tenant_id = ?
            AND id = ?;
            """;
    List<Object> params = List.of(tenant.identifierValue(), identifier.value());
    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void deleteAllByUser(Tenant tenant, String userId) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        """
            DELETE FROM oauth_token
            WHERE tenant_id = ?
            AND user_id = ?;
            """;
    List<Object> params = List.of(tenant.identifierValue(), userId);
    sqlExecutor.execute(sqlTemplate, params);
  }
}
