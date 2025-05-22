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


package org.idp.server.platform.datasource;

public record DbConfig(
    String url,
    String username,
    String password,
    int maximumPoolSize,
    int minimumIdle,
    long connectionTimeout,
    long idleTimeout,
    long maxLifetime) {

  public static DbConfig defaultConfig(String url, String username, String password) {
    return new DbConfig(
        url,
        username,
        password,
        10, // maximumPoolSize
        2, // minimumIdle
        3000, // connectionTimeout
        600000, // idleTimeout
        1800000 // maxLifetime
        );
  }
}
