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
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.oauth.type.oauth.RefreshTokenEntity;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.repository.OAuthTokenQueryRepository;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.crypto.HmacHasher;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OAuthTokenQueryDataSource implements OAuthTokenQueryRepository {

  OAuthTokenSqlExecutor executor;
  AesCipher aesCipher;
  HmacHasher hmacHasher;

  public OAuthTokenQueryDataSource(
      OAuthTokenSqlExecutor executor, AesCipher aesCipher, HmacHasher hmacHasher) {
    this.executor = executor;
    this.aesCipher = aesCipher;
    this.hmacHasher = hmacHasher;
  }

  @Override
  public OAuthToken find(Tenant tenant, AccessTokenEntity accessTokenEntity) {
    Map<String, String> stringMap =
        executor.selectOneByAccessToken(tenant, accessTokenEntity, aesCipher, hmacHasher);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new OAuthToken();
    }

    return ModelConverter.convert(stringMap, aesCipher);
  }

  @Override
  public OAuthToken find(Tenant tenant, RefreshTokenEntity refreshTokenEntity) {
    Map<String, String> stringMap =
        executor.selectOneByRefreshToken(tenant, refreshTokenEntity, aesCipher, hmacHasher);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new OAuthToken();
    }

    return ModelConverter.convert(stringMap, aesCipher);
  }
}
