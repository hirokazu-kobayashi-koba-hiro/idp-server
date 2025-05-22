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


package org.idp.server.core.adapters.datasource.authentication.transaction.command;

import org.idp.server.core.oidc.authentication.AuthenticationTransaction;
import org.idp.server.core.oidc.authentication.AuthorizationIdentifier;
import org.idp.server.core.oidc.authentication.repository.AuthenticationTransactionCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthenticationTransactionCommandDataSource
    implements AuthenticationTransactionCommandRepository {

  AuthenticationTransactionCommandSqlExecutors executors;

  public AuthenticationTransactionCommandDataSource() {
    this.executors = new AuthenticationTransactionCommandSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, AuthenticationTransaction authenticationTransaction) {
    AuthenticationTransactionCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, authenticationTransaction);
  }

  @Override
  public void update(Tenant tenant, AuthenticationTransaction authenticationTransaction) {
    AuthenticationTransactionCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(tenant, authenticationTransaction);
  }

  @Override
  public void delete(Tenant tenant, AuthorizationIdentifier identifier) {
    AuthenticationTransactionCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.delete(tenant, identifier);
  }
}
