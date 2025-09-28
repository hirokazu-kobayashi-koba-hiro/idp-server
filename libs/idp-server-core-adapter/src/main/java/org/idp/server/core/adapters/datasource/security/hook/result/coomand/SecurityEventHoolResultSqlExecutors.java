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

package org.idp.server.core.adapters.datasource.security.hook.result.coomand;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.datasource.DatabaseType;

public class SecurityEventHoolResultSqlExecutors {

  Map<DatabaseType, SecurityEventHoolResultSqlExecutor> executors;

  public SecurityEventHoolResultSqlExecutors() {
    this.executors = new HashMap<>();
    executors.put(DatabaseType.POSTGRESQL, new PostgresqlExecutor());
    executors.put(DatabaseType.MYSQL, new MysqlExecutor());
  }

  public SecurityEventHoolResultSqlExecutor get(DatabaseType databaseType) {
    SecurityEventHoolResultSqlExecutor executor = executors.get(databaseType);

    if (executor == null) {
      throw new IllegalArgumentException("Unknown dialect " + databaseType.name());
    }

    return executor;
  }
}
