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

package org.idp.server.core.adapters.datasource.token.operation.command;

import org.idp.server.basic.crypto.AesCipher;
import org.idp.server.basic.crypto.HmacHasher;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.token.repository.OAuthTokenOperationCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OAuthTokenOperationCommandDataSource implements OAuthTokenOperationCommandRepository {

  OAuthTokenSqlExecutors executors;
  AesCipher aesCipher;
  HmacHasher hmacHasher;

  public OAuthTokenOperationCommandDataSource(AesCipher aesCipher, HmacHasher hmacHasher) {
    this.executors = new OAuthTokenSqlExecutors();
    this.aesCipher = aesCipher;
    this.hmacHasher = hmacHasher;
  }

  @Override
  public void deleteExpiredToken(Tenant tenant, int limit) {
    // TODO database type
    OAuthTokenSqlExecutor executor = executors.get(tenant.databaseType());
    executor.deleteExpiredToken(limit);
  }

  @Override
  public void deleteAll(Tenant tenant, User user) {
    OAuthTokenSqlExecutor executor = executors.get(tenant.databaseType());
    executor.deleteAll(tenant, user);
  }
}
