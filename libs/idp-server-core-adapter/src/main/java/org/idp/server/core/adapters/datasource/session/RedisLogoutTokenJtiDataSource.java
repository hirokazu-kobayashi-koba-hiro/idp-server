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

package org.idp.server.core.adapters.datasource.session;

import org.idp.server.core.openid.session.logout.LogoutTokenJtiRepository;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.log.LoggerWrapper;

public class RedisLogoutTokenJtiDataSource implements LogoutTokenJtiRepository {

  private static final String KEY_PREFIX = "logout_jti:";

  private final CacheStore cacheStore;
  private final LoggerWrapper log = LoggerWrapper.getLogger(RedisLogoutTokenJtiDataSource.class);

  public RedisLogoutTokenJtiDataSource(CacheStore cacheStore) {
    this.cacheStore = cacheStore;
  }

  @Override
  public boolean isUsed(String jti) {
    String key = KEY_PREFIX + jti;
    return cacheStore.exists(key);
  }

  @Override
  public void markUsed(String jti, long ttlSeconds) {
    String key = KEY_PREFIX + jti;
    cacheStore.put(key, "1", (int) ttlSeconds);
    log.debug("Marked JTI as used. jti:{}, ttl:{}", jti, ttlSeconds);
  }
}
