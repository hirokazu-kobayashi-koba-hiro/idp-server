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

package org.idp.server.core.adapters.datasource.identity;

import org.idp.server.core.openid.authentication.plugin.AuthenticationDependencyProvider;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.datasource.ApplicationDatabaseTypeProvider;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class UserQueryDataSourceProvider
    implements AuthenticationDependencyProvider<UserQueryRepository>,
        ApplicationComponentProvider<UserQueryRepository> {

  @Override
  public Class<UserQueryRepository> type() {
    return UserQueryRepository.class;
  }

  @Override
  public UserQueryRepository provide(ApplicationComponentDependencyContainer container) {
    ApplicationDatabaseTypeProvider databaseTypeProvider =
        container.resolve(ApplicationDatabaseTypeProvider.class);
    UserSqlExecutors executors = new UserSqlExecutors();
    UserSqlExecutor executor = executors.get(databaseTypeProvider.provide());
    return new UserQueryDataSource(executor);
  }

  @Override
  public UserQueryRepository provide() {
    throw new UnsupportedOperationException(
        "Default provide() method is not supported. Use provide(ApplicationComponentDependencyContainer) instead.");
  }
}
