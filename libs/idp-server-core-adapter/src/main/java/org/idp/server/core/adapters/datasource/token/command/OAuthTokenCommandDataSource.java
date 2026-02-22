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

package org.idp.server.core.adapters.datasource.token.command;

import java.util.List;
import org.idp.server.core.adapters.datasource.token.OAuthTokenCacheKeyBuilder;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.crypto.HmacHasher;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.datasource.cache.NoOperationCacheStore;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OAuthTokenCommandDataSource implements OAuthTokenCommandRepository {

  OAuthTokenSqlExecutor executor;
  AesCipher aesCipher;
  HmacHasher hmacHasher;
  CacheStore cacheStore;

  public OAuthTokenCommandDataSource(
      OAuthTokenSqlExecutor executor, AesCipher aesCipher, HmacHasher hmacHasher) {
    this(executor, aesCipher, hmacHasher, new NoOperationCacheStore());
  }

  public OAuthTokenCommandDataSource(
      OAuthTokenSqlExecutor executor,
      AesCipher aesCipher,
      HmacHasher hmacHasher,
      CacheStore cacheStore) {
    this.executor = executor;
    this.aesCipher = aesCipher;
    this.hmacHasher = hmacHasher;
    this.cacheStore = cacheStore;
  }

  @Override
  public void register(Tenant tenant, OAuthToken oAuthToken) {
    executor.insert(oAuthToken, aesCipher, hmacHasher);
  }

  @Override
  public void delete(Tenant tenant, OAuthToken oAuthToken) {
    executor.delete(oAuthToken, aesCipher, hmacHasher);
    evictCache(tenant, oAuthToken.accessTokenEntity());
  }

  @Override
  public void deleteByUserAndClient(Tenant tenant, User user, RequestedClientId clientId) {
    List<String> hashedAccessTokens =
        executor.selectHashedAccessTokensByUserAndClient(
            tenant.identifierValue(), user.sub(), clientId.value());

    for (String hashedAccessToken : hashedAccessTokens) {
      cacheStore.delete(
          OAuthTokenCacheKeyBuilder.build(tenant.identifierValue(), hashedAccessToken));
    }

    executor.deleteByUserAndClient(tenant.identifierValue(), user.sub(), clientId.value());
  }

  private void evictCache(Tenant tenant, AccessTokenEntity accessTokenEntity) {
    String tokenHash = hmacHasher.hash(accessTokenEntity.value());
    cacheStore.delete(OAuthTokenCacheKeyBuilder.build(tenant.identifierValue(), tokenHash));
  }
}
