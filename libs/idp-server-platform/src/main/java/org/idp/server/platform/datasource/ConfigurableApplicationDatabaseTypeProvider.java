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

package org.idp.server.platform.datasource;

/**
 * Configurable implementation of ApplicationDatabaseTypeProvider. Provides application-level
 * database type from configuration.
 */
public class ConfigurableApplicationDatabaseTypeProvider
    implements ApplicationDatabaseTypeProvider {

  private final DatabaseType databaseType;

  public ConfigurableApplicationDatabaseTypeProvider(String databaseTypeConfig) {
    this.databaseType = DatabaseType.of(databaseTypeConfig);
  }

  public ConfigurableApplicationDatabaseTypeProvider(DatabaseType databaseType) {
    this.databaseType = databaseType;
  }

  @Override
  public DatabaseType provide() {
    return databaseType;
  }
}
