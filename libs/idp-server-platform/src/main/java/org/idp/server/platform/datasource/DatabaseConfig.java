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
