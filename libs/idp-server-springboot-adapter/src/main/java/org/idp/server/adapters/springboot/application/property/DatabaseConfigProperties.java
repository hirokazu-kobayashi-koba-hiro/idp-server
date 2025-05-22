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
