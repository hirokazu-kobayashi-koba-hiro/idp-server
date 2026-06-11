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

package org.idp.server.core.adapters.datasource.token.query;

import java.util.List;
import java.util.Map;
import org.idp.server.core.adapters.datasource.token.OAuthTokenColumns;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.oauth.type.oauth.RefreshTokenEntity;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.crypto.HmacHasher;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class MysqlExecutor implements OAuthTokenSqlExecutor {

  @Override
  public Map<String, String> selectOneByAccessToken(
      Tenant tenant,
      AccessTokenEntity accessTokenEntity,
      AesCipher aesCipher,
      HmacHasher hmacHasher) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
        FROM oauth_token
        WHERE tenant_id = ? AND hashed_access_token = ?;
        """;

    List<Object> params =
        List.of(tenant.identifierValue(), hmacHasher.hash(accessTokenEntity.value()));
    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  @Override
  public Map<String, String> selectOneByRefreshToken(
      Tenant tenant,
      RefreshTokenEntity refreshTokenEntity,
      AesCipher aesCipher,
      HmacHasher hmacHasher) {
    SqlExecutor sqlExecutor = new SqlExecutor();
    String sqlTemplate =
        selectSql
            + """
            FROM oauth_token
            WHERE tenant_id = ? AND hashed_refresh_token = ?;
            """;

    List<Object> params =
        List.of(tenant.identifierValue(), hmacHasher.hash(refreshTokenEntity.value()));
    return sqlExecutor.selectOne(sqlTemplate, params);
  }

  String selectSql = OAuthTokenColumns.selectClause();
}
