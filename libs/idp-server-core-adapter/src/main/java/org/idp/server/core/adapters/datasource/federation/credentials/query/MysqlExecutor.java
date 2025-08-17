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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements SsoCredentialsSqlExecutor {

  @Override
  public Map<String, String> selectBy(Tenant tenant, User user) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
        SELECT
         user_id,
         tenant_id,
         sso_provider,
         sso_credentials
        FROM idp_user_sso_credentials
        WHERE tenant_id= ?
        AND user_id= ?
        """;

    List<Object> params = new ArrayList<>();
    params.add(user.sub());
    params.add(tenant.identifierValue());

    return sqlExecutor.selectOne(sqlTemplate, params);
  }
}
