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

package org.idp.server.core.adapters.datasource.audit.command;

import org.idp.server.platform.audit.AuditLogCommandRepository;
import org.idp.server.platform.datasource.ApplicationDatabaseTypeProvider;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class AuditLogCommandDataSourceProvider
    implements ApplicationComponentProvider<AuditLogCommandRepository> {

  @Override
  public Class<AuditLogCommandRepository> type() {
    return AuditLogCommandRepository.class;
  }

  @Override
  public AuditLogCommandRepository provide(ApplicationComponentDependencyContainer container) {
    ApplicationDatabaseTypeProvider databaseTypeProvider =
        container.resolve(ApplicationDatabaseTypeProvider.class);
    AuditLogSqlExecutors executors = new AuditLogSqlExecutors();
    AuditLogSqlExecutor executor = executors.get(databaseTypeProvider.provide());
    return new AuditLogCommandDataSource(executor);
  }
}
