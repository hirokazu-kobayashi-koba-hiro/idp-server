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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "idp.datasource.control-plane")
public class ControlPlaneDatabaseConfigProperties {
  private DbConfigProperty writer;
  private DbConfigProperty reader;

  public DbConfigProperty getWriter() {
    return writer;
  }

  public void setWriter(DbConfigProperty writer) {
    this.writer = writer;
  }

  public DbConfigProperty getReader() {
    return reader;
  }

  public void setReader(DbConfigProperty reader) {
    this.reader = reader;
  }
}
