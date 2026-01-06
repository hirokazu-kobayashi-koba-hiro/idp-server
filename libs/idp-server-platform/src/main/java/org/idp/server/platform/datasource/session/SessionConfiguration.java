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

package org.idp.server.platform.datasource.session;

/**
 * SessionConfiguration
 *
 * <p>Configuration for session storage. Separate from CacheConfiguration to allow independent
 * configuration of cache and session storage backends.
 */
public class SessionConfiguration {

  private final String host;
  private final int port;
  private final int database;
  private final int timeout;
  private final String password;
  private final int maxTotal;
  private final int maxIdle;
  private final int minIdle;

  public SessionConfiguration(
      String host,
      int port,
      int database,
      int timeout,
      String password,
      int maxTotal,
      int maxIdle,
      int minIdle) {
    this.host = host;
    this.port = port;
    this.database = database;
    this.timeout = timeout;
    this.password = password;
    this.maxTotal = maxTotal;
    this.maxIdle = maxIdle;
    this.minIdle = minIdle;
  }

  public String host() {
    return host;
  }

  public int port() {
    return port;
  }

  public int database() {
    return database;
  }

  public int timeout() {
    return timeout;
  }

  public String password() {
    return password;
  }

  public int maxTotal() {
    return maxTotal;
  }

  public int maxIdle() {
    return maxIdle;
  }

  public int minIdle() {
    return minIdle;
  }

  public boolean hasPassword() {
    return password != null && !password.isEmpty();
  }
}
