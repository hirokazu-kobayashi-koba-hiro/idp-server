package org.idp.server.core.basic.sql;

import java.util.Map;

public class DatabaseConfig {
  Map<DatabaseType, DbCredentials> writerConfigs;
  Map<DatabaseType, DbCredentials> readerConfigs;

  public DatabaseConfig() {}

  public DatabaseConfig(
      Map<DatabaseType, DbCredentials> writerConfigs,
      Map<DatabaseType, DbCredentials> readerConfigs) {
    this.writerConfigs = writerConfigs;
    this.readerConfigs = readerConfigs;
  }

  public Map<DatabaseType, DbCredentials> writerConfigs() {
    return writerConfigs;
  }

  public Map<DatabaseType, DbCredentials> readerConfigs() {
    return readerConfigs;
  }
}
