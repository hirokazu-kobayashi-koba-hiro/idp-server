/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.datasource;

import java.util.Map;

public class DatabaseConfig {
  Map<DatabaseType, DbConfig> writerConfigs;
  Map<DatabaseType, DbConfig> readerConfigs;

  public DatabaseConfig() {}

  public DatabaseConfig(
      Map<DatabaseType, DbConfig> writerConfigs, Map<DatabaseType, DbConfig> readerConfigs) {
    this.writerConfigs = writerConfigs;
    this.readerConfigs = readerConfigs;
  }

  public Map<DatabaseType, DbConfig> writerConfigs() {
    return writerConfigs;
  }

  public Map<DatabaseType, DbConfig> readerConfigs() {
    return readerConfigs;
  }
}
