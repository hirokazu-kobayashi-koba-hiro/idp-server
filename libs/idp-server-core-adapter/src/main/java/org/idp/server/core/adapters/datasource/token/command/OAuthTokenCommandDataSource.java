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

import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.core.oidc.token.repository.OAuthTokenCommandRepository;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.crypto.HmacHasher;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OAuthTokenCommandDataSource implements OAuthTokenCommandRepository {

  OAuthTokenSqlExecutors executors;
  AesCipher aesCipher;
  HmacHasher hmacHasher;

  public OAuthTokenCommandDataSource(AesCipher aesCipher, HmacHasher hmacHasher) {
    this.executors = new OAuthTokenSqlExecutors();
    this.aesCipher = aesCipher;
    this.hmacHasher = hmacHasher;
  }

  @Override
  public void register(Tenant tenant, OAuthToken oAuthToken) {
    OAuthTokenSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(oAuthToken, aesCipher, hmacHasher);
  }

  @Override
  public void delete(Tenant tenant, OAuthToken oAuthToken) {
    OAuthTokenSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(oAuthToken, aesCipher, hmacHasher);
  }
}
