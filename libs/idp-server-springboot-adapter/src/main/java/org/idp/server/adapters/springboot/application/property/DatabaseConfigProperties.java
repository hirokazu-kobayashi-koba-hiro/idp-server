/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.adapters.springboot.application.property;

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
