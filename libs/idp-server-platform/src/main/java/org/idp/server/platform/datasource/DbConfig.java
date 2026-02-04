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
    long maxLifetime,
    long keepaliveTime,
    long validationTimeout) {

  public static DbConfig defaultConfig(String url, String username, String password) {
    return new DbConfig(
        url,
        username,
        password,
        10, // maximumPoolSize
        2, // minimumIdle
        30000, // connectionTimeout (30 seconds) - HikariCP default
        600000, // idleTimeout (10 minutes)
        1800000, // maxLifetime (30 minutes)
        180000, // keepaliveTime (3 minutes) - should be less than Aurora tcp_keepalives_idle (5
        // min)
        5000 // validationTimeout (5 seconds)
        );
  }
}
