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

package org.idp.server.core.adapters.datasource.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import org.idp.server.platform.datasource.DatabaseType;

public class HikariDatabaseConfig {

  Map<DatabaseType, HikariDataSource> writerConfigs;
  Map<DatabaseType, HikariDataSource> readerConfigs;

  public HikariDatabaseConfig() {}

  public HikariDatabaseConfig(
      Map<DatabaseType, HikariDataSource> writerConfigs,
      Map<DatabaseType, HikariDataSource> readerConfigs) {
    this.writerConfigs = writerConfigs;
    this.readerConfigs = readerConfigs;
  }

  public Map<DatabaseType, HikariDataSource> writerConfigs() {
    return writerConfigs;
  }

  public Map<DatabaseType, HikariDataSource> readerConfigs() {
    return readerConfigs;
  }
}
