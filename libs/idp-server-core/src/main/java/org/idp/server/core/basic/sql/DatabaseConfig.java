package org.idp.server.core.basic.sql;

import java.util.Map;

public class DatabaseConfig {
  Map<Dialect, DbCredentials> writerConfigs;
  Map<Dialect, DbCredentials> readerConfigs;

  public DatabaseConfig() {}

  public DatabaseConfig(
      Map<Dialect, DbCredentials> writerConfigs, Map<Dialect, DbCredentials> readerConfigs) {
    this.writerConfigs = writerConfigs;
    this.readerConfigs = readerConfigs;
  }

  public Map<Dialect, DbCredentials> writerConfigs() {
    return writerConfigs;
  }

  public Map<Dialect, DbCredentials> readerConfigs() {
    return readerConfigs;
  }
}
