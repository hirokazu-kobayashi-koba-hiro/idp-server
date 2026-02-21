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
import java.util.Optional;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.oauth.type.oauth.RefreshTokenEntity;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.repository.OAuthTokenQueryRepository;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.crypto.HmacHasher;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.datasource.cache.NoOperationCacheStore;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OAuthTokenQueryDataSource implements OAuthTokenQueryRepository {

  private static final String CACHE_KEY_PREFIX = "oauth_token:at:";
  private static final int DEFAULT_CACHE_TTL_SECONDS = 60;

  OAuthTokenSqlExecutor executor;
  AesCipher aesCipher;
  HmacHasher hmacHasher;
  CacheStore cacheStore;
  int cacheTtlSeconds;
  LoggerWrapper log = LoggerWrapper.getLogger(OAuthTokenQueryDataSource.class);

  public OAuthTokenQueryDataSource(
      OAuthTokenSqlExecutor executor, AesCipher aesCipher, HmacHasher hmacHasher) {
    this(executor, aesCipher, hmacHasher, new NoOperationCacheStore(), DEFAULT_CACHE_TTL_SECONDS);
  }

  public OAuthTokenQueryDataSource(
      OAuthTokenSqlExecutor executor,
      AesCipher aesCipher,
      HmacHasher hmacHasher,
      CacheStore cacheStore,
      int cacheTtlSeconds) {
    this.executor = executor;
    this.aesCipher = aesCipher;
    this.hmacHasher = hmacHasher;
    this.cacheStore = cacheStore;
    this.cacheTtlSeconds = cacheTtlSeconds;
  }

  @Override
  public OAuthToken find(Tenant tenant, AccessTokenEntity accessTokenEntity) {
    String cacheKey = buildCacheKey(tenant, accessTokenEntity);

    Optional<OAuthTokenCacheEntry> cached = cacheStore.find(cacheKey, OAuthTokenCacheEntry.class);
    if (cached.isPresent()) {
      log.debug("Cache hit for access token query. tenant:{}", tenant.identifierValue());
      return ModelConverter.convert(cached.get().values(), aesCipher);
    }

    Map<String, String> stringMap =
        executor.selectOneByAccessToken(tenant, accessTokenEntity, aesCipher, hmacHasher);

    if (Objects.isNull(stringMap) || stringMap.isEmpty()) {
      return new OAuthToken();
    }

    cacheStore.put(cacheKey, new OAuthTokenCacheEntry(stringMap), cacheTtlSeconds);
    log.debug(
        "Cached access token query result. tenant:{}, ttl:{}s",
        tenant.identifierValue(),
        cacheTtlSeconds);

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

  private String buildCacheKey(Tenant tenant, AccessTokenEntity accessTokenEntity) {
    String tokenHash = hmacHasher.hash(accessTokenEntity.value());
    return CACHE_KEY_PREFIX + tenant.identifierValue() + ":" + tokenHash;
  }
}
