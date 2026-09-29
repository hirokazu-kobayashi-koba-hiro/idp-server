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

package org.idp.server.core.adapters.datasource.oauth.authentication_proof;

import org.idp.server.core.openid.oauth.repository.AuthenticationProofRepository;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.datasource.cache.NoOperationCacheStore;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;
import org.idp.server.platform.log.LoggerWrapper;

public class AuthenticationProofDataSourceProvider
    implements ApplicationComponentProvider<AuthenticationProofRepository> {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthenticationProofDataSourceProvider.class);

  @Override
  public Class<AuthenticationProofRepository> type() {
    return AuthenticationProofRepository.class;
  }

  @Override
  public AuthenticationProofRepository provide(ApplicationComponentDependencyContainer container) {
    CacheStore cacheStore = container.resolve(CacheStore.class);

    // Said once, at startup, rather than per request. Without somewhere to keep a proof, every
    // authorize call running cross-site fails with "auth_proof is missing",
    // which reads like an attack and is a misconfiguration.
    if (cacheStore instanceof NoOperationCacheStore) {
      log.warn(
          "No cache is configured, so auth_proof cannot be stored. Tenants with"
              + " ui_config.cross_site, and clients with extension.cross_site_authorization_view,"
              + " enabled will reject every authorize call. Enable the cache or turn them off.");
    }

    return new AuthenticationProofDataSource(cacheStore);
  }
}
