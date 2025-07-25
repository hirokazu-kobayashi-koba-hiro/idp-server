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

import org.idp.server.core.oidc.token.repository.OAuthTokenQueryRepository;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.crypto.HmacHasher;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class OAuthTokenQueryDataSourceProvider
    implements ApplicationComponentProvider<OAuthTokenQueryRepository> {

  @Override
  public Class<OAuthTokenQueryRepository> type() {
    return OAuthTokenQueryRepository.class;
  }

  @Override
  public OAuthTokenQueryRepository provide(ApplicationComponentDependencyContainer container) {
    AesCipher aesCipher = container.resolve(AesCipher.class);
    HmacHasher hmacHasher = container.resolve(HmacHasher.class);
    return new OAuthTokenQueryDataSource(aesCipher, hmacHasher);
  }
}
