package org.idp.server.adapters.springboot.property;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "idp.datasource")
public class DatabaseConfigProperties {
  private Map<String, DbConfigProperty> postgresql;
  private Map<String, DbConfigProperty> mysql;

  public Map<String, DbConfigProperty> getPostgresql() {
    return postgresql;
  }

  public void setPostgresql(Map<String, DbConfigProperty> postgresql) {
    this.postgresql = postgresql;
  }

  public Map<String, DbConfigProperty> getMysql() {
    return mysql;
  }

  public void setMysql(Map<String, DbConfigProperty> mysql) {
    this.mysql = mysql;
  }
}
