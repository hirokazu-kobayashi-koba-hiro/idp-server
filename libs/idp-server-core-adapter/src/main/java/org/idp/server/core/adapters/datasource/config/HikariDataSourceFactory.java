package org.idp.server.core.adapters.datasource.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.datasource.DatabaseType;
import org.idp.server.core.basic.datasource.DbConfig;

public class HikariDataSourceFactory {

  public static Map<DatabaseType, HikariDataSource> create(Map<DatabaseType, DbConfig> dbConfigs) {
    Map<DatabaseType, HikariDataSource> configs = new HashMap<>();

    for (Map.Entry<DatabaseType, DbConfig> entry : dbConfigs.entrySet()) {
      HikariConfig hikariConfig = create(entry.getValue());
      configs.put(entry.getKey(), new HikariDataSource(hikariConfig));
    }

    return configs;
  }

  public static HikariConfig create(DbConfig config) {
    HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setJdbcUrl(config.url());
    hikariConfig.setUsername(config.username());
    hikariConfig.setPassword(config.password());
    hikariConfig.setMaximumPoolSize(config.maximumPoolSize());
    hikariConfig.setMinimumIdle(config.minimumIdle());
    hikariConfig.setConnectionTimeout(config.connectionTimeout());
    hikariConfig.setIdleTimeout(config.idleTimeout());
    hikariConfig.setMaxLifetime(config.maxLifetime());
    return hikariConfig;
  }
}
