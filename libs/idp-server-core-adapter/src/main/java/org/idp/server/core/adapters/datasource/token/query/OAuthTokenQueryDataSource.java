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

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.crypto.AesCipher;
import org.idp.server.basic.crypto.HmacHasher;
import org.idp.server.basic.type.oauth.AccessTokenEntity;
import org.idp.server.basic.type.oauth.RefreshTokenEntity;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.core.oidc.token.repository.OAuthTokenQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OAuthTokenQueryDataSource implements OAuthTokenQueryRepository {

  OAuthTokenSqlExecutors executors;
  AesCipher aesCipher;
  HmacHasher hmacHasher;

  public OAuthTokenQueryDataSource(AesCipher aesCipher, HmacHasher hmacHasher) {
    this.executors = new OAuthTokenSqlExecutors();
    this.aesCipher = aesCipher;
    this.hmacHasher = hmacHasher;
  }

  @Override
  public OAuthToken find(Tenant tenant, AccessTokenEntity accessTokenEntity) {
    OAuthTokenSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> stringMap =
        executor.selectOneByAccessToken(tenant, accessTokenEntity, aesCipher, hmacHasher);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new OAuthToken();
    }

    return ModelConverter.convert(stringMap, aesCipher);
  }

  @Override
  public OAuthToken find(Tenant tenant, RefreshTokenEntity refreshTokenEntity) {
    OAuthTokenSqlExecutor executor = executors.get(tenant.databaseType());
    Map<String, String> stringMap =
        executor.selectOneByRefreshToken(tenant, refreshTokenEntity, aesCipher, hmacHasher);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new OAuthToken();
    }

    return ModelConverter.convert(stringMap, aesCipher);
  }
}
